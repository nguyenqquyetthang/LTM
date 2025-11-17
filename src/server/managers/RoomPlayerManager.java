package server.managers;

import server.core.ClientHandler;
import server.core.RoomThread;
import server.core.Server;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ROOM PLAYER MANAGER - QUẢN LÝ NGƯỜI CHƠI TRONG PHÒNG
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Thêm/xóa người chơi
 * - Quản lý host (chủ phòng)
 * - Trạng thái ready của từng người
 * - Kiểm tra điều kiện bắt đầu game
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RoomPlayerManager {
    private static final int MAX_PLAYERS = 6;

    private String roomName;
    private List<ClientHandler> players;
    private Map<String, Boolean> playerReady;
    private int hostIndex = 0;

    // Dependencies
    private BroadcastManager broadcastManager;
    private Map<String, RoomThread> rooms;

    public RoomPlayerManager(String roomName, List<ClientHandler> players,
            Map<String, Boolean> playerReady, BroadcastManager broadcastManager,
            Map<String, RoomThread> rooms) {
        this.roomName = roomName;
        this.players = players;
        this.playerReady = playerReady;
        this.broadcastManager = broadcastManager;
        this.rooms = rooms;
    }

    /**
     * Thêm người chơi vào phòng
     */
    public synchronized boolean addPlayer(ClientHandler p) {
        if (players.size() >= MAX_PLAYERS) {
            p.sendMessage("ROOM_FULL");
            return false;
        }
        players.add(p);
        p.setStatus("busy");
        playerReady.put(p.username, false);
        broadcastManager.broadcastRoomUpdate(hostIndex);
        broadcastManager.broadcastReadyStatus();
        return true;
    }

    /**
     * Xóa người chơi khỏi phòng
     * 
     * @return true nếu phòng trống (cần xóa), false nếu còn người
     */
    public synchronized boolean removePlayer(ClientHandler p, GameStateManager gameState) {
        int removedIndex = players.indexOf(p);
        players.remove(p);
        p.setStatus("free");
        playerReady.remove(p.username);

        // Cập nhật host nếu cần
        updateHostAfterRemoval(removedIndex);

        // Cập nhật currentTurn nếu game đang chạy
        if (gameState.isGameStarted() && !players.isEmpty()) {
            int currentTurn = gameState.getTurnManager().getCurrentTurn();
            if (currentTurn >= players.size()) {
                gameState.getTurnManager().adjustTurnAfterRemoval(currentTurn, players.size());
            }
            gameState.getTurnManager().notifyCurrentTurn(players);
        }

        // Broadcast cập nhật nếu còn người
        if (!players.isEmpty()) {
            broadcastManager.broadcastRoomUpdate(hostIndex);
            broadcastManager.broadcastReadyStatus();
            Server.broadcastRoomsList();
            return false; // Phòng không trống
        }

        // Phòng trống - cần dọn dẹp
        gameState.getTurnManager().cancelTimer();
        rooms.remove(roomName);
        Server.broadcastRoomsList();
        return true; // Phòng trống
    }

    /**
     * Cập nhật host sau khi có người rời
     */
    private void updateHostAfterRemoval(int removedIndex) {
        if (removedIndex == hostIndex && !players.isEmpty()) {
            hostIndex = 0;
            players.get(0).sendMessage("YOU_ARE_HOST");
        } else if (removedIndex < hostIndex) {
            hostIndex--;
        }
    }

    /**
     * Lấy index của người chơi
     */
    public int getPlayerIndex(ClientHandler p) {
        return players.indexOf(p);
    }

    /**
     * Set trạng thái ready của người chơi
     */
    public synchronized void setPlayerReady(String username, boolean ready) {
        playerReady.put(username, ready);
        System.out.println("🔴 [" + roomName + "] " + username + " ready=" + ready);
        System.out.println("   Ready map: " + playerReady);
        System.out.println("   All ready? " + allPlayersReady());
        broadcastManager.broadcastReadyStatus();
    }

    /**
     * Kiểm tra tất cả người chơi đã ready chưa
     */
    public synchronized boolean allPlayersReady() {
        if (players.size() < 2)
            return false;

        // Host luôn sẵn sàng, chỉ check các người khác
        for (ClientHandler p : players) {
            int idx = players.indexOf(p);
            if (idx != hostIndex) {
                if (!playerReady.getOrDefault(p.username, false)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Kiểm tra có đủ người chơi không
     */

    public boolean hasEnoughPlayers() {
        return players.size() >= 2;
    }

    /**
     * Reset trạng thái ready của tất cả người chơi
     */
    public void resetAllReady() {
        playerReady.clear();
    }

    /**
     * Set tất cả người chơi về trạng thái busy
     */
    public synchronized void setAllPlayersBusy() {
        for (ClientHandler c : players) {
            c.setStatus("busy");
            playerReady.put(c.username, false);
        }
    }

    /**
     * Set tất cả người chơi về trạng thái playing
     */
    public synchronized void setAllPlayersPlaying() {
        for (ClientHandler c : players) {
            c.setStatus("playing");
        }
    }

    /**
     * Lấy host index
     */
    public int getHostIndex() {
        return hostIndex;
    }

    /**
     * Kiểm tra player có phải host không
     */
    public boolean isHost(ClientHandler player) {
        return players.indexOf(player) == hostIndex;
    }

    /**
     * Lấy danh sách players
     */
    public List<ClientHandler> getPlayers() {
        return players;
    }

    /**
     * Lấy số lượng người chơi
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Kiểm tra phòng đầy chưa
     */
    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }
}
