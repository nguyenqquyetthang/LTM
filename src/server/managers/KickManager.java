package server.managers;

import server.core.ClientHandler;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * KICK MANAGER - XỬ LÝ KICK VÀ TIMEOUT
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Kick người chơi (bởi host)
 * - Xử lý timeout (tự động kick)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class KickManager {
    private List<ClientHandler> players;

    // Dependencies
    private GameStateManager gameState;
    private RoomPlayerManager playerManager;
    private ScoreManager scoreManager;

    public KickManager(String roomName, List<ClientHandler> players,
            GameStateManager gameState, RoomPlayerManager playerManager,
            ScoreManager scoreManager, BroadcastManager broadcastManager) {
        this.players = players;
        this.gameState = gameState;
        this.playerManager = playerManager;
        this.scoreManager = scoreManager;
    }

    /**
     * Kick người chơi (chỉ host, chỉ khi chưa chơi)
     * 
     * @return callback để RoomThread thực hiện external calls (tránh deadlock)
     */
    public synchronized KickResult kickPlayer(String targetUsername, ClientHandler requester) {
        // Không cho kick khi game đang chạy
        if (gameState.isGameStarted()) {
            return new KickResult(KickStatus.GAME_RUNNING, null);
        }

        // Kiểm tra requester có phải host không
        if (!playerManager.isHost(requester)) {
            return new KickResult(KickStatus.NOT_HOST, null);
        }

        // Tìm target player
        ClientHandler targetPlayer = null;
        for (ClientHandler player : players) {
            if (player.username.equals(targetUsername)) {
                targetPlayer = player;
                break;
            }
        }

        if (targetPlayer == null) {
            return new KickResult(KickStatus.PLAYER_NOT_FOUND, null);
        }

        // Không cho kick chính mình (host)
        if (targetPlayer.equals(requester)) {
            return new KickResult(KickStatus.CANNOT_KICK_SELF, null);
        }

        return new KickResult(KickStatus.SUCCESS, targetPlayer);
    }

    /**
     * Xử lý timeout - kick người chơi
     * 
     * @return callback để GameFlowManager xử lý tiếp
     */
    public synchronized TimeoutResult handleTimeout() {
        if (!gameState.isGameStarted() || players.isEmpty()) {
            return new TimeoutResult(false, null, -1);
        }

        int currentTurn = gameState.getTurnManager().getCurrentTurn();
        ClientHandler timedOut = players.get(currentTurn);
        String username = timedOut.username;
        System.out.println("⏰ Timeout! Loại: " + username);

        // Trừ điểm cho người timeout
        scoreManager.applyTimeoutPenalty(username);

        // Lưu vào danh sách timeout
        gameState.getTurnManager().getTimeoutPlayers().add(username);

        // Thông báo bị loại
        timedOut.sendMessage("ELIMINATED;Timeout - không rút trong 10s. Bạn bị trừ 1 điểm!");

        // Loại khỏi phòng
        players.remove(currentTurn);
        gameState.getGameLogic().getPlayerHand(username); // Clear hand
        timedOut.setStatus("free");
        timedOut.resetCurrentRoom();

        // Cập nhật host nếu cần
        int newHostIndex = playerManager.getHostIndex();
        if (currentTurn == newHostIndex && !players.isEmpty()) {
            // Host bị timeout - chọn host mới
            players.get(0).sendMessage("YOU_ARE_HOST");
        } else if (newHostIndex > currentTurn) {
            // Điều chỉnh host index
        }

        return new TimeoutResult(true, timedOut, currentTurn);
    }

    /**
     * Kết quả của kick operation
     */
    public static class KickResult {
        public final KickStatus status;
        public final ClientHandler targetPlayer;

        public KickResult(KickStatus status, ClientHandler targetPlayer) {
            this.status = status;
            this.targetPlayer = targetPlayer;
        }
    }

    /**
     * Trạng thái kick
     */
    public enum KickStatus {
        SUCCESS,
        GAME_RUNNING,
        NOT_HOST,
        PLAYER_NOT_FOUND,
        CANNOT_KICK_SELF
    }

    /**
     * Kết quả của timeout operation
     */
    public static class TimeoutResult {
        public final boolean shouldContinue;
        public final ClientHandler timedOutPlayer;
        public final int removedIndex;

        public TimeoutResult(boolean shouldContinue, ClientHandler timedOutPlayer, int removedIndex) {
            this.shouldContinue = shouldContinue;
            this.timedOutPlayer = timedOutPlayer;
            this.removedIndex = removedIndex;
        }
    }
}
