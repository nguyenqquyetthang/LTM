package server;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * JDBC helper for SQL Server schema:
 * Players(PlayerID, Username, PasswordHash, TotalPoints)
 * Matches(MatchID, StartTime, EndTime, TotalPlayers, WinnerID)
 * MatchResults(ResultID, MatchID, PlayerID, RankPosition, Score, HandType,
 * Cards, CreatedAt)
 * Cards(CardID, Rank, Suit, CardName)
 * MatchCards(MatchCardID, MatchID, PlayerID, CardID, CardOrder)
 */
public class Database {
    // Adjust credentials as needed
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=LuckyDrawGame;encrypt=false;";
    private static final String USER = "sa";
    private static final String PASS = "123";

    public Database() {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLServer JDBC Driver not found", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // Players -----------------------------------------------------
    public Integer getPlayerId(String username) {
        String sql = "SELECT PlayerID FROM Players WHERE Username = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("DB getPlayerId error: " + e.getMessage());
        }
        return null;
    }

    public boolean authenticate(String username, String passwordHash) {
        String sql = "SELECT 1 FROM Players WHERE Username = ? AND PasswordHash = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.out.println("DB authenticate error: " + e.getMessage());
        }
        return false;
    }

    public Integer createPlayer(String username, String passwordHash) {
        String sql = "INSERT INTO Players(Username, PasswordHash, TotalPoints) VALUES(?,?,0)";
        try (Connection con = getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("DB createPlayer error: " + e.getMessage());
        }
        return null;
    }

    public void updateTotalPoints(int playerId, int delta) {
        String sql = "UPDATE Players SET TotalPoints = TotalPoints + ? WHERE PlayerID = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("DB updateTotalPoints error: " + e.getMessage());
        }
    }

    public Map<String, String> loadAccounts() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT Username, PasswordHash FROM Players";
        try (Connection con = getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            System.out.println("DB loadAccounts error: " + e.getMessage());
        }
        return map;
    }

    public Integer getTotalPoints(String username) {
        String sql = "SELECT TotalPoints FROM Players WHERE Username = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("DB getTotalPoints error: " + e.getMessage());
        }
        return null;
    }

    // Cards seeding -----------------------------------------------
    public void ensureCardsSeeded() {
        String countSql = "SELECT COUNT(*) FROM Cards";
        try (Connection con = getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) == 52) {
                return; // already seeded
            }
        } catch (SQLException e) {
            // proceed to seed if table exists but error reading count
        }
        String[] suits = { "♥", "♠", "♦", "♣" };
        String[] ranks = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" };
        String insert = "INSERT INTO Cards(Rank,Suit) VALUES(?,?)";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(insert)) {
            // Clear existing if partial
            try (Statement st = con.createStatement()) {
                st.executeUpdate("DELETE FROM Cards");
            }
            for (String r : ranks) {
                for (String s : suits) {
                    ps.setString(1, r);
                    ps.setString(2, s);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        } catch (SQLException e) {
            System.out.println("DB ensureCardsSeeded error: " + e.getMessage());
        }
    }

    // Matches -----------------------------------------------------
    public Integer createMatch(int totalPlayers) {
        String sql = "INSERT INTO Matches(TotalPlayers) VALUES(?)";
        try (Connection con = getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, totalPlayers);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("DB createMatch error: " + e.getMessage());
        }
        return null;
    }

    public void endMatch(int matchId, Integer winnerPlayerId) {
        String sql = "UPDATE Matches SET EndTime = GETDATE(), WinnerID = ? WHERE MatchID = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (winnerPlayerId == null)
                ps.setNull(1, Types.INTEGER);
            else
                ps.setInt(1, winnerPlayerId);
            ps.setInt(2, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("DB endMatch error: " + e.getMessage());
        }
    }

    public void insertMatchResult(int matchId, int playerId, int rankPosition, int score, String handType,
            String cardsText) {
        String sql = "INSERT INTO MatchResults(MatchID, PlayerID, RankPosition, Score, HandType, Cards) VALUES(?,?,?,?,?,?)";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, matchId);
            ps.setInt(2, playerId);
            ps.setInt(3, rankPosition);
            ps.setInt(4, score);
            ps.setString(5, handType);
            ps.setString(6, cardsText);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("DB insertMatchResult error: " + e.getMessage());
        }
    }

    // Match history for lobby display
    public String getMatchHistory(int limit) {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT TOP(?) m.MatchID, m.StartTime, m.EndTime, m.TotalPlayers, " +
                "pw.Username AS WinnerName " +
                "FROM Matches m " +
                "LEFT JOIN Players pw ON m.WinnerID = pw.PlayerID " +
                "ORDER BY m.MatchID DESC";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
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
            System.out.println("DB getMatchHistory error: " + e.getMessage());
        }
        return sb.toString();
    }

    // Detailed match history: include each player's hand and rank for recent
    // matches
    // Format:
    // MATCH|MatchID|StartTime|EndTime|TotalPlayers|WinnerName
    // RESULT|RankPosition|Username|HandType|Cards
    // RESULT|...
    // (blank line between matches)
    public String getDetailedMatchHistory(int limit) {
        StringBuilder sb = new StringBuilder();
        String matchSql = "SELECT TOP(?) m.MatchID, m.StartTime, m.EndTime, m.TotalPlayers, pw.Username AS WinnerName "
                +
                "FROM Matches m LEFT JOIN Players pw ON m.WinnerID = pw.PlayerID ORDER BY m.MatchID DESC";
        try (Connection con = getConnection(); PreparedStatement psMatch = con.prepareStatement(matchSql)) {
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
                    sb.append("MATCH|").append(matchId).append("|").append(start).append("|").append(end)
                            .append("|").append(total).append("|").append(winner).append("\n");
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
            System.out.println("DB getDetailedMatchHistory error: " + e.getMessage());
        }
        return sb.toString();
    }

    // Single match detail (for click on history row)
    public String getMatchDetail(int matchId) {
        StringBuilder sb = new StringBuilder();
        String headerSql = "SELECT m.MatchID, m.StartTime, m.EndTime, m.TotalPlayers, pw.Username AS WinnerName " +
                "FROM Matches m LEFT JOIN Players pw ON m.WinnerID = pw.PlayerID WHERE m.MatchID = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(headerSql)) {
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
                    sb.append("MATCH|").append(matchId).append("|").append(start).append("|").append(end)
                            .append("|").append(total).append("|").append(winner).append("\n");
                }
            }
            String resSql = "SELECT r.RankPosition, p.Username, r.HandType, r.Cards FROM MatchResults r " +
                    "JOIN Players p ON r.PlayerID = p.PlayerID WHERE r.MatchID = ? ORDER BY r.RankPosition";
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
            System.out.println("DB getMatchDetail error: " + e.getMessage());
        }
        return sb.toString();
    }
}
