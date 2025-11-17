package server.managers;

import server.core.ClientHandler;
import server.core.RoomThread;
import server.core.Server;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * BROADCAST HELPER - HỖ TRỢ GỬI THÔNG TIN TỚI CLIENT
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Gửi danh sách người chơi
 * - Gửi danh sách phòng
 * - Format messages theo protocol
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class BroadcastHelper {
    private List<ClientHandler> activeClients;

    public BroadcastHelper(List<ClientHandler> activeClients) {
        this.activeClients = activeClients;
    }

    /**
     * Tạo message PLAYER_LIST
     * 📤 GỬI: PLAYER_LIST|user1:status1:pts1|user2:status2:pts2|...
     * 
     * @return Message string
     */
    public String buildPlayerListMessage() {
        StringBuilder sb = new StringBuilder("PLAYER_LIST|");
        synchronized (activeClients) {
            for (ClientHandler c : activeClients) {
                if (c.username != null) {
                    int pts = Server.playerScores.getOrDefault(c.username, 0);
                    sb.append(c.username).append(":").append(c.getStatus()).append(":").append(pts).append("|");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Tạo message ROOMS_LIST
     * 📤 GỬI: ROOMS_LIST|room1:count1/6|room2:count2/6|...
     * 
     * @param rooms Map of room names to RoomThread objects
     * @return Message string
     */
    public String buildRoomsListMessage(Map<String, RoomThread> rooms) {
        StringBuilder sb = new StringBuilder("ROOMS_LIST|");
        for (Map.Entry<String, RoomThread> e : rooms.entrySet()) {
            String name = e.getKey();
            RoomThread rt = e.getValue();
            sb.append(name).append(":").append(rt.getPlayerCount()).append("/").append(6).append("|");
        }
        return sb.toString();
    }

    /**
     * Broadcast message đến tất cả clients online
     * 
     * @param message Message cần gửi
     */
    public void broadcastToAll(String message) {
        synchronized (activeClients) {
            for (ClientHandler c : activeClients) {
                c.sendMessage(message);
            }
        }
    }

    /**
     * Broadcast PLAYER_LIST đến tất cả clients
     */
    public void broadcastPlayerList() {
        String playerListMsg = buildPlayerListMessage();
        broadcastToAll(playerListMsg);
    }
}
