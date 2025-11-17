package server.managers;




import server.core.ClientHandler;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * BROADCAST MANAGER - QUẢN LÝ GỬI MESSAGES TRONG PHÒNG
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Broadcast messages đến tất cả players trong phòng
 * - Build & send ROOM_UPDATE messages
 * - Build & send READY_STATUS messages
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class BroadcastManager {
    private String roomName;
    private List<ClientHandler> players;
    private Map<String, Boolean> playerReady;

    public BroadcastManager(String roomName, List<ClientHandler> players, Map<String, Boolean> playerReady) {
        this.roomName = roomName;
        this.players = players;
        this.playerReady = playerReady;
    }

    /**
     * Broadcast message đến tất cả players trong phòng
     */
    public void broadcast(String msg) {
        for (ClientHandler p : players) {
            p.sendMessage(msg);
        }
    }

    /**
     * Broadcast ROOM_UPDATE message
     * Format: ROOM_UPDATE|roomName|hostIndex|player1,player2,player3,...
     */
    public void broadcastRoomUpdate(int hostIndex) {
        String msg = buildRoomUpdateMessage(hostIndex);
        broadcast(msg);
    }

    /**
     * Send ROOM_UPDATE chỉ cho 1 client cụ thể
     */
    public void sendRoomUpdateTo(ClientHandler target, int hostIndex) {
        String msg = buildRoomUpdateMessage(hostIndex);
        target.sendMessage(msg);
    }

    /**
     * Build ROOM_UPDATE message
     */
    private String buildRoomUpdateMessage(int hostIndex) {
        StringBuilder sb = new StringBuilder("ROOM_UPDATE|");
        sb.append(roomName).append("|");
        sb.append(hostIndex).append("|");

        synchronized (players) {
            for (int i = 0; i < players.size(); i++) {
                sb.append(players.get(i).username);
                if (i < players.size() - 1) {
                    sb.append(",");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Broadcast READY_STATUS message
     * Format: READY_STATUS|user1:true|user2:false|...
     */
    public void broadcastReadyStatus() {
        StringBuilder sb = new StringBuilder("READY_STATUS|");

        synchronized (players) {
            for (ClientHandler p : players) {
                boolean ready = playerReady.getOrDefault(p.username, false);
                sb.append(p.username).append(":").append(ready).append("|");
            }
        }

        String msg = sb.toString();
        System.out.println("📡 Broadcasting: " + msg);
        broadcast(msg);
    }
}
