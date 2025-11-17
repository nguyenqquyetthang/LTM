package server.database;




import java.sql.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * MATCH REPOSITORY - QUẢN LÝ DỮ LIỆU VÁN ĐẤU
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Tạo ván đấu mới
 * - Kết thúc ván đấu
 * - Lưu kết quả từng người chơi
 * - Lấy lịch sử ván đấu
 * - Lấy chi tiết ván đấu
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class MatchRepository {
    private DatabaseConnection dbConnection;

    public MatchRepository(DatabaseConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Tạo ván đấu mới
     * 
     * @param totalPlayers Tổng số người chơi trong ván
     * @return MatchID hoặc null nếu thất bại
     */
    public Integer createMatch(int totalPlayers) {
        String sql = "INSERT INTO Matches(TotalPlayers) VALUES(?)";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, totalPlayers);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("MatchRepository createMatch error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Kết thúc ván đấu
     * 
     * @param matchId        ID của ván đấu
     * @param winnerPlayerId ID người thắng (có thể null)
     */
    public void endMatch(int matchId, Integer winnerPlayerId) {
        String sql = "UPDATE Matches SET EndTime = GETDATE(), WinnerID = ? WHERE MatchID = ?";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            if (winnerPlayerId == null)
                ps.setNull(1, Types.INTEGER);
            else
                ps.setInt(1, winnerPlayerId);
            ps.setInt(2, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("MatchRepository endMatch error: " + e.getMessage());
        }
    }

    /**
     * Lưu kết quả của 1 người chơi trong ván
     * 
     * @param matchId      ID ván đấu
     * @param playerId     ID người chơi
     * @param rankPosition Thứ hạng (1 = nhất, 2 = nhì, ...)
     * @param score        Điểm số
     * @param handType     Loại bài (HighCard, Flush, Straight, ...)
     * @param cardsText    Các lá bài (K♠,Q♠,J♠)
     */
    public void insertMatchResult(int matchId, int playerId, int rankPosition, int score,
            String handType, String cardsText) {
        String sql = "INSERT INTO MatchResults(MatchID, PlayerID, RankPosition, Score, HandType, Cards) " +
                "VALUES(?,?,?,?,?,?)";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, matchId);
            ps.setInt(2, playerId);
            ps.setInt(3, rankPosition);
            ps.setInt(4, score);
            ps.setString(5, handType);
            ps.setString(6, cardsText);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("MatchRepository insertMatchResult error: " + e.getMessage());
        }
    }

    /**
     * Lấy lịch sử ván đấu (danh sách tóm tắt)
     * Format: matchId|startTime|endTime|numPlayers|winner\n...
     * 
     * @param limit Số lượng ván gần nhất
     * @return String chứa lịch sử
     */
    public String getMatchHistory(int limit) {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT TOP(?) m.MatchID, m.StartTime, m.EndTime, m.TotalPlayers, " +
                "pw.Username AS WinnerName " +
                "FROM Matches m " +
                "LEFT JOIN Players pw ON m.WinnerID = pw.PlayerID " +
                "ORDER BY m.MatchID DESC";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int mid = rs.getInt("MatchID");
                    String start = rs.getTimestamp("StartTime").toString();
                    String end = rs.getTimestamp("EndTime") != null ? rs.getTimestamp("EndTime").toString() : "N/A";
                    int total = rs.getInt("TotalPlayers");
                    String winner = rs.getString("WinnerName");
                    if (winner == null)
                        winner = "N/A";

                    sb.append(mid).append("|").append(start).append("|").append(end).append("|");
                    sb.append(total).append("|").append(winner).append("\n");
                }
            }
        } catch (SQLException e) {
            System.out.println("MatchRepository getMatchHistory error: " + e.getMessage());
        }
        return sb.toString();
    }

    /**
     * Lấy lịch sử chi tiết (bao gồm tay bài của từng người)
     * Format:
     * MATCH|MatchID|StartTime|EndTime|TotalPlayers|WinnerName
     * RESULT|RankPosition|Username|HandType|Cards
     * RESULT|...
     * (blank line between matches)
     * 
     * @param limit Số lượng ván gần nhất
     * @return String chứa lịch sử chi tiết
     */
    public String getDetailedMatchHistory(int limit) {
        StringBuilder sb = new StringBuilder();
        String matchSql = "SELECT TOP(?) m.MatchID, m.StartTime, m.EndTime, m.TotalPlayers, " +
                "pw.Username AS WinnerName " +
                "FROM Matches m LEFT JOIN Players pw ON m.WinnerID = pw.PlayerID " +
                "ORDER BY m.MatchID DESC";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement psMatch = con.prepareStatement(matchSql)) {
            psMatch.setInt(1, limit);
            try (ResultSet rsMatch = psMatch.executeQuery()) {
                while (rsMatch.next()) {
                    int matchId = rsMatch.getInt("MatchID");
                    Timestamp startTs = rsMatch.getTimestamp("StartTime");
                    Timestamp endTs = rsMatch.getTimestamp("EndTime");
                    String start = startTs != null ? startTs.toString() : "N/A";
                    String end = endTs != null ? endTs.toString() : "N/A";
                    int total = rsMatch.getInt("TotalPlayers");
                    String winner = rsMatch.getString("WinnerName");
                    if (winner == null)
                        winner = "N/A";

                    sb.append("MATCH|").append(matchId).append("|").append(start).append("|")
                            .append(end).append("|").append(total).append("|").append(winner).append("\n");

                    // Query results for this match
                    String resultSql = "SELECT r.RankPosition, p.Username, r.HandType, r.Cards " +
                            "FROM MatchResults r JOIN Players p ON r.PlayerID = p.PlayerID " +
                            "WHERE r.MatchID = ? ORDER BY r.RankPosition ASC";
                    try (PreparedStatement psRes = con.prepareStatement(resultSql)) {
                        psRes.setInt(1, matchId);
                        try (ResultSet rsRes = psRes.executeQuery()) {
                            while (rsRes.next()) {
                                int rank = rsRes.getInt("RankPosition");
                                String user = rsRes.getString("Username");
                                String handType = rsRes.getString("HandType");
                                String cards = rsRes.getString("Cards");
                                if (cards == null)
                                    cards = "";

                                sb.append("RESULT|").append(rank).append("|").append(user)
                                        .append("|").append(handType).append("|").append(cards).append("\n");
                            }
                        }
                    }
                    sb.append("\n"); // separator between matches
                }
            }
        } catch (SQLException e) {
            System.out.println("MatchRepository getDetailedMatchHistory error: " + e.getMessage());
        }
        return sb.toString();
    }

    /**
     * Lấy chi tiết 1 ván đấu cụ thể
     * Format:
     * MATCH|MatchID|StartTime|EndTime|TotalPlayers|WinnerName
     * RESULT|RankPosition|Username|HandType|Cards
     * RESULT|...
     * 
     * @param matchId ID của ván đấu
     * @return String chứa chi tiết ván đấu
     */
    public String getMatchDetail(int matchId) {
        StringBuilder sb = new StringBuilder();
        String headerSql = "SELECT m.MatchID, m.StartTime, m.EndTime, m.TotalPlayers, " +
                "pw.Username AS WinnerName " +
                "FROM Matches m LEFT JOIN Players pw ON m.WinnerID = pw.PlayerID " +
                "WHERE m.MatchID = ?";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(headerSql)) {
            ps.setInt(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp startTs = rs.getTimestamp("StartTime");
                    Timestamp endTs = rs.getTimestamp("EndTime");
                    String start = startTs != null ? startTs.toString() : "N/A";
                    String end = endTs != null ? endTs.toString() : "N/A";
                    int total = rs.getInt("TotalPlayers");
                    String winner = rs.getString("WinnerName");
                    if (winner == null)
                        winner = "N/A";

                    sb.append("MATCH|").append(matchId).append("|").append(start).append("|")
                            .append(end).append("|").append(total).append("|").append(winner).append("\n");
                }
            }

            String resSql = "SELECT r.RankPosition, p.Username, r.HandType, r.Cards " +
                    "FROM MatchResults r JOIN Players p ON r.PlayerID = p.PlayerID " +
                    "WHERE r.MatchID = ? ORDER BY r.RankPosition";
            try (PreparedStatement psRes = con.prepareStatement(resSql)) {
                psRes.setInt(1, matchId);
                try (ResultSet rsRes = psRes.executeQuery()) {
                    while (rsRes.next()) {
                        int rank = rsRes.getInt("RankPosition");
                        String user = rsRes.getString("Username");
                        String handType = rsRes.getString("HandType");
                        String cards = rsRes.getString("Cards");
                        if (cards == null)
                            cards = "";

                        sb.append("RESULT|").append(rank).append("|").append(user).append("|")
                                .append(handType).append("|").append(cards).append("\n");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("MatchRepository getMatchDetail error: " + e.getMessage());
        }
        return sb.toString();
    }
}
