package server.managers;




import server.core.Server;
import server.database.Database;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * SCORE MANAGER - QUẢN LÝ ĐIỂM SỐ & XẾP HẠNG
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Tính điểm thắng/thua
 * - Cập nhật điểm vào Server.playerScores và Database
 * - Tạo bảng xếp hạng (RANKING message)
 * - Xử lý điểm timeout
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class ScoreManager {
    private Database db;

    public ScoreManager(Database db) {
        this.db = db;
    }

    /**
     * Cập nhật điểm cho người thắng và người thua
     * 
     * @param winner         Username của người thắng
     * @param losers         List username của những người thua (không bao gồm
     *                       timeout)
     * @param timeoutPlayers List username của những người timeout
     * @return ScoreUpdate chứa thông tin điểm thay đổi
     */
    public ScoreUpdate updateScores(String winner, List<String> losers, List<String> timeoutPlayers) {
        Map<String, Integer> scoreChanges = new HashMap<>();

        // Tổng số người tham gia (kể cả timeout)
        int totalParticipants = 1 + losers.size() + timeoutPlayers.size();

        if (totalParticipants < 2) {
            return new ScoreUpdate(scoreChanges);
        }

        // Điểm người thắng = tổng số người - 1
        int winnerPoints = totalParticipants - 1;

        // Khởi tạo điểm cho tất cả người chơi nếu chưa có
        Server.playerScores.putIfAbsent(winner, 0);
        for (String loser : losers) {
            Server.playerScores.putIfAbsent(loser, 0);
        }

        // Cập nhật điểm thắng
        Server.playerScores.put(winner, Server.playerScores.get(winner) + winnerPoints);
        scoreChanges.put(winner, winnerPoints);

        // Người thua bị trừ 1 điểm
        for (String loser : losers) {
            Server.playerScores.put(loser, Server.playerScores.get(loser) - 1);
            scoreChanges.put(loser, -1);
        }

        // Persist vào database
        if (db != null) {
            Integer winId = db.getPlayerId(winner);
            if (winId != null) {
                db.updateTotalPoints(winId, winnerPoints);
            }

            for (String loser : losers) {
                Integer loserId = db.getPlayerId(loser);
                if (loserId != null) {
                    db.updateTotalPoints(loserId, -1);
                }
            }
        }

        return new ScoreUpdate(scoreChanges);
    }

    /**
     * Xử lý trừ điểm cho người timeout
     * 
     * @param username Username của người timeout
     */
    public void applyTimeoutPenalty(String username) {
        Server.playerScores.putIfAbsent(username, 0);
        Server.playerScores.put(username, Server.playerScores.get(username) - 1);

        if (db != null) {
            Integer playerId = db.getPlayerId(username);
            if (playerId != null) {
                db.updateTotalPoints(playerId, -1);
            }
        }
    }

    /**
     * Tạo message RANKING
     * 📤 GỬI: RANKING|player1:15:+3|player2:8:-1|player3:5:-1|...
     * 
     * @param sortedPlayers Danh sách người chơi đã được sắp xếp
     * @param scoreChanges  Map username -> điểm thay đổi
     * @return RANKING message
     */
    public String buildRankingMessage(List<String> sortedPlayers, Map<String, Integer> scoreChanges) {
        StringBuilder sb = new StringBuilder("RANKING|");

        for (String username : sortedPlayers) {
            int totalPoints = Server.playerScores.getOrDefault(username, 0);
            int change = scoreChanges.getOrDefault(username, 0);

            sb.append(username).append(":")
                    .append(totalPoints).append(":")
                    .append(change >= 0 ? "+" + change : change)
                    .append("|");
        }

        return sb.toString();
    }

    /**
     * Inner class chứa thông tin cập nhật điểm
     */
    public static class ScoreUpdate {
        public final Map<String, Integer> scoreChanges; // username -> điểm thay đổi

        public ScoreUpdate(Map<String, Integer> scoreChanges) {
            this.scoreChanges = scoreChanges;
        }
    }
}
