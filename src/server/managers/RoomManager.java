package server.managers;




import server.core.ClientHandler;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ROOM MANAGER - QUẢN LÝ NGƯỜI CHƠI TRONG PHÒNG
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Thêm/xóa người chơi
 * - Quản lý host
 * - Quản lý trạng thái ready
 * - Broadcast ROOM_UPDATE và READY_STATUS
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RoomManager {
    private static final int MAX_PLAYERS = 6;

    private String roomName;
    private List<ClientHandler> players;
    private int hostIndex = 0;
    private Map<String, Boolean> playerReady = new HashMap<>();

    public RoomManager(String roomName, List<ClientHandler> players) {
        this.roomName = roomName;
        this.players = players;
    }

    /**
     * Kiểm tra phòng đã đầy chưa
     */
    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    /**
     * Lấy số lượng người chơi
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Lấy index của host
     */
    public int getHostIndex() {
        return hostIndex;
    }

    /**
     * Set player ready status
     */
    public void setPlayerReady(String username, boolean ready) {
        playerReady.put(username, ready);
        broadcastReadyStatus();
    }

    /**
     * Kiểm tra tất cả người chơi đã ready chưa (không tính host)
     */
    public boolean allPlayersReady() {
        if (players.size() < 2)
            return false;

        for (ClientHandler p : players) {
            int idx = players.indexOf(p);
            if (idx != hostIndex) { // Bỏ qua host
                if (!playerReady.getOrDefault(p.username, false)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Reset trạng thái ready
     */
    public void resetReady() {
        playerReady.clear();
    }

    /**
     * Cập nhật host sau khi xóa người chơi
     */
    public void updateHostAfterRemoval(int removedIndex) {
        if (removedIndex == hostIndex && !players.isEmpty()) {
            hostIndex = 0;
            players.get(0).sendMessage("YOU_ARE_HOST");
        } else if (removedIndex < hostIndex) {
            hostIndex--;
        }
    }

    /**
     * Broadcast ROOM_UPDATE
     * 📤 GỬI: ROOM_UPDATE|roomName|hostIndex|player1,player2,player3,...
     */
    public void broadcastRoomUpdate() {
        StringBuilder sb = new StringBuilder("ROOM_UPDATE|");
        sb.append(roomName).append("|");
        sb.append(hostIndex).append("|");

        for (int i = 0; i < players.size(); i++) {
            sb.append(players.get(i).username);
            if (i < players.size() - 1) {
                sb.append(",");
            }
        }

        broadcast(sb.toString());
    }

    /**
     * Gửi ROOM_UPDATE cho 1 client cụ thể
     */
    public void sendRoomUpdateTo(ClientHandler target) {
        StringBuilder sb = new StringBuilder("ROOM_UPDATE|");
        sb.append(roomName).append("|");
        sb.append(hostIndex).append("|");

        for (int i = 0; i < players.size(); i++) {
            sb.append(players.get(i).username);
            if (i < players.size() - 1) {
                sb.append(",");
            }
        }

        target.sendMessage(sb.toString());
    }

    /**
     * Broadcast READY_STATUS
     * 📤 GỬI: READY_STATUS|user1:true|user2:false|user3:true|...
     */
    public void broadcastReadyStatus() {
        StringBuilder sb = new StringBuilder("READY_STATUS|");
        for (ClientHandler p : players) {
            boolean ready = playerReady.getOrDefault(p.username, false);
            sb.append(p.username).append(":").append(ready).append("|");
        }
        broadcast(sb.toString());
    }

    /**
     * Lấy thông tin phòng để hiển thị
     * 
     * @return Format: roomName|playerCount/maxPlayers
     */
    public String getRoomInfo() {
        return roomName + "|" + players.size() + "/" + MAX_PLAYERS;
    }

    /**
     * Broadcast message tới tất cả người trong phòng
     */
    private void broadcast(String msg) {
        for (ClientHandler p : players) {
            p.sendMessage(msg);
        }
    }
}
