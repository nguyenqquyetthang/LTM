package server.handlers;

import server.database.Database;
import server.core.ClientHandler;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * PROTOCOL HANDLER - XỬ LÝ MESSAGES THEO PROTOCOL
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này route messages đến đúng handler:
 * - Info requests → Database queries
 * - Room commands → RoomCommandHandler
 * - Game commands → GameCommandHandler
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class ProtocolHandler {
    private Database db;
    private RoomCommandHandler roomHandler;
    private GameCommandHandler gameHandler;

    public ProtocolHandler(Database db, RoomCommandHandler roomHandler, GameCommandHandler gameHandler) {
        this.db = db;
        this.roomHandler = roomHandler;
        this.gameHandler = gameHandler;
    }

    /**
     * Xử lý message từ client
     * 
     * @return true nếu tiếp tục loop, false nếu ngắt kết nối
     */
    public boolean handleMessage(String msg, ClientHandler client) {
        System.out.println("[" + client.username + "] gửi: " + msg);

        if (msg.equalsIgnoreCase("exit")) {
            return false;
        }

        // ═══════════════════════════════════════════════════════════════
        // INFO REQUESTS - Database queries
        // ═══════════════════════════════════════════════════════════════

        if (msg.equalsIgnoreCase("GET_PLAYER_LIST")) {
            String playerList = buildPlayerListMessage(client);
            client.sendMessage(playerList);
            return true;
        }

        if (msg.equalsIgnoreCase("GET_ROOMS")) {
            String roomsList = buildRoomsListMessage(client);
            client.sendMessage(roomsList);
            return true;
        }

        if (msg.equalsIgnoreCase("GET_HISTORY")) {
            String history = db.getMatchHistory(20);
            client.sendMessage("HISTORY_DATA|" + history);
            return true;
        }

        if (msg.equalsIgnoreCase("GET_HISTORY_DETAIL")) {
            String historyDetail = db.getDetailedMatchHistory(10);
            client.sendMessage("HISTORY_DETAIL_DATA|" + historyDetail);
            return true;
        }

        if (msg.startsWith("GET_MATCH_DETAIL;")) {
            handleGetMatchDetail(msg, client);
            return true;
        }

        // ═══════════════════════════════════════════════════════════════
        // ROOM COMMANDS
        // ═══════════════════════════════════════════════════════════════

        if (msg.equalsIgnoreCase("CREATE")) {
            RoomCommandHandler.RoomResult result = roomHandler.handleCreateRoom(client);
            if (result.success) {
                client.sendMessage("ROOM_CREATED;" + result.roomName);
            } else {
                client.sendMessage("CREATE_FAIL;" + result.status);
            }
            return true;
        }

        if (msg.startsWith("JOIN;")) {
            String roomName = msg.split(";")[1];
            RoomCommandHandler.RoomResult result = roomHandler.handleJoinRoom(client, roomName);
            if (result.success) {
                client.sendMessage("JOIN_OK;" + result.roomName);
            } else if (result.status.equals("FULL")) {
                client.sendMessage("ROOM_FULL");
            } else {
                client.sendMessage("JOIN_FAIL");
            }
            return true;
        }

        if (msg.startsWith("READY;")) {
            String roomName = msg.split(";")[1];
            roomHandler.handleReady(client, roomName);
            return true;
        }

        if (msg.startsWith("GET_ROOM_UPDATE;")) {
            String roomName = msg.split(";")[1];
            roomHandler.handleGetRoomUpdate(client, roomName);
            return true;
        }

        if (msg.startsWith("START;")) {
            String roomName = msg.split(";")[1];
            roomHandler.handleStartGame(roomName);
            return true;
        }

        if (msg.startsWith("LEAVE_ROOM;")) {
            roomHandler.handleLeaveRoom(client);
            return true;
        }

        // ═══════════════════════════════════════════════════════════════
        // GAME COMMANDS
        // ═══════════════════════════════════════════════════════════════

        if (msg.startsWith("DRAW;")) {
            String roomName = msg.split(";")[1];
            gameHandler.handleDrawCard(client, roomName);
            return true;
        }

        if (msg.startsWith("KICK_PLAYER;")) {
            String targetUsername = msg.split(";")[1];
            gameHandler.handleKickPlayer(client, targetUsername);
            return true;
        }

        if (msg.startsWith("INVITE;")) {
            String targetUsername = msg.split(";")[1];
            gameHandler.handleInvite(client, targetUsername);
            return true;
        }

        return true;
    }

    /**
     * Xử lý GET_MATCH_DETAIL;matchId
     */
    private void handleGetMatchDetail(String msg, ClientHandler client) {
        String[] parts = msg.split(";");
        if (parts.length >= 2) {
            try {
                int matchId = Integer.parseInt(parts[1]);
                String detail = db.getMatchDetail(matchId);
                client.sendMessage("MATCH_DETAIL_DATA|" + detail);
            } catch (NumberFormatException ex) {
                client.sendMessage("MATCH_DETAIL_DATA|ERROR Invalid MatchID");
            }
        }
    }

    /**
     * Build PLAYER_LIST message
     */
    private String buildPlayerListMessage(ClientHandler client) {
        // Delegate to BroadcastHelper hoặc tự build
        StringBuilder sb = new StringBuilder("PLAYER_LIST|");
        // Implementation sẽ cần access activeClients
        return sb.toString();
    }

    /**
     * Build ROOMS_LIST message
     */
    private String buildRoomsListMessage(ClientHandler client) {
        // Implementation sẽ cần access rooms
        StringBuilder sb = new StringBuilder("ROOMS_LIST|");
        return sb.toString();
    }
}
