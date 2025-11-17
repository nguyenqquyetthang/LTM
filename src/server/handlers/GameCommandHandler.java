package server.handlers;

import server.core.RoomThread;
import server.core.ClientHandler;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * GAME COMMAND HANDLER - XỬ LÝ CÁC LỆNH TRONG GAME
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - DRAW - Rút bài
 * - KICK_PLAYER - Kick người chơi
 * - INVITE - Mời người chơi
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class GameCommandHandler {
    private Map<String, RoomThread> rooms;
    private List<ClientHandler> activeClients;

    public GameCommandHandler(Map<String, RoomThread> rooms, List<ClientHandler> activeClients) {
        this.rooms = rooms;
        this.activeClients = activeClients;
    }

    /**
     * Xử lý DRAW - Rút bài
     * 📨 NHẬN: DRAW;RoomName
     * 📤 GỬI: DRAW;K♠ (từ RoomThread) hoặc NOT_YOUR_TURN
     * 
     * @param client   ClientHandler của người rút bài
     * @param roomName Tên phòng
     */
    public void handleDrawCard(ClientHandler client, String roomName) {
        String currentRoom = client.getCurrentRoom();

        if (currentRoom != null && currentRoom.equals(roomName) && rooms.containsKey(currentRoom)) {
            rooms.get(currentRoom).drawCard(client);
        }
    }

    /**
     * Xử lý KICK_PLAYER - Kick người chơi
     * 📨 NHẬN: KICK_PLAYER;targetUsername
     * 📤 GỬI: NOT_HOST hoặc KICK_BLOCKED;... (từ RoomThread)
     * 
     * @param client         ClientHandler của host
     * @param targetUsername Username của người bị kick
     */
    public void handleKickPlayer(ClientHandler client, String targetUsername) {
        String currentRoom = client.getCurrentRoom();

        if (currentRoom != null && rooms.containsKey(currentRoom)) {
            rooms.get(currentRoom).kickPlayer(targetUsername, client);
        }
    }

    /**
     * Xử lý INVITE - Mời người chơi vào phòng
     * 📨 NHẬN: INVITE;targetUsername
     * 📤 GỬI: INVITE;fromUsername;roomName (đến target)
     * 
     * @param client         ClientHandler của người mời
     * @param targetUsername Username của người được mời
     * @return true nếu gửi lời mời thành công
     */
    public boolean handleInvite(ClientHandler client, String targetUsername) {
        String currentRoom = client.getCurrentRoom();

        if (currentRoom == null || !rooms.containsKey(currentRoom)) {
            return false;
        }

        // Tìm target player trong activeClients
        ClientHandler targetClient = null;
        synchronized (activeClients) {
            for (ClientHandler c : activeClients) {
                if (c.username != null && c.username.equals(targetUsername)) {
                    targetClient = c;
                    break;
                }
            }
        }

        if (targetClient == null) {
            client.sendMessage("INVITE_FAIL;Người chơi không online");
            return false;
        }

        if (!targetClient.getStatus().equals("free")) {
            client.sendMessage("INVITE_FAIL;Người chơi đang bận");
            return false;
        }

        // Gửi lời mời đến target
        targetClient.sendMessage("INVITE;" + client.username + ";" + currentRoom);
        System.out.println("📧 " + client.username + " mời " + targetUsername + " vào " + currentRoom);
        return true;
    }
}
