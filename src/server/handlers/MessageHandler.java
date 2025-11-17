package server.handlers;

import server.core.RoomThread;
import server.core.Server;
import server.database.Database;
import server.core.ClientHandler;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * MESSAGE HANDLER - XỬ LÝ CÁC LOẠI MESSAGES TỪ CLIENT
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này tách logic xử lý messages ra khỏi ClientHandler:
 * - Room commands (CREATE, JOIN, LEAVE)
 * - Game commands (READY, START, DRAW, KICK)
 * - Info requests (GET_PLAYER_LIST, GET_ROOMS, GET_HISTORY)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class MessageHandler {
    private Database db;
    private Map<String, RoomThread> rooms;
    private List<ClientHandler> activeClients;

    public MessageHandler(Database db, Map<String, RoomThread> rooms, List<ClientHandler> activeClients) {
        this.db = db;
        this.rooms = rooms;
        this.activeClients = activeClients;
    }

    /**
     * Xử lý GET_PLAYER_LIST
     * 📤 GỬI: PLAYER_LIST|user1:status1:pts1|user2:status2:pts2|...
     * 📨 NHẬN: GET_PLAYER_LIST
     */
    public String handleGetPlayerList() {
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
     * Xử lý GET_ROOMS
     * 📤 GỬI: ROOMS_LIST|room1:count1/6|room2:count2/6|...
     * 📨 NHẬN: GET_ROOMS
     */
    public String handleGetRooms() {
        StringBuilder sb = new StringBuilder("ROOMS_LIST|");
        synchronized (rooms) {
            for (RoomThread r : rooms.values()) {
                sb.append(r.getRoomInfo()).append("|");
            }
        }
        return sb.toString();
    }

    /**
     * Xử lý GET_HISTORY
     * 📤 GỬI: HISTORY_DATA|matchId|startTime|endTime|numPlayers|winner\n...
     * 📨 NHẬN: GET_HISTORY
     */
    public String handleGetHistory(int limit) {
        String history = db.getMatchHistory(limit);
        return "HISTORY_DATA|" + history;
    }

    /**
     * Xử lý GET_HISTORY_DETAIL
     * 📤 GỬI: HISTORY_DETAIL_DATA|...
     * 📨 NHẬN: GET_HISTORY_DETAIL
     */
    public String handleGetHistoryDetail(int limit) {
        String historyDetail = db.getDetailedMatchHistory(limit);
        return "HISTORY_DETAIL_DATA|" + historyDetail;
    }

    /**
     * Xử lý GET_MATCH_DETAIL;matchId
     * 📤 GỬI: MATCH_DETAIL_DATA|MATCH|...|RESULT|...|RESULT|...
     * 📨 NHẬN: GET_MATCH_DETAIL;matchId
     */
    public String handleGetMatchDetail(int matchId) {
        String detail = db.getMatchDetail(matchId);
        return "MATCH_DETAIL_DATA|" + detail;
    }

    /**
     * Xử lý JOIN;roomName
     * 📤 GỬI: JOIN_OK;RoomName hoặc JOIN_FAIL hoặc ROOM_FULL
     * 📨 NHẬN: JOIN;roomName
     * 
     * @return [status, message] - status: "OK", "FAIL", "FULL"
     */
    public String[] handleJoinRoom(ClientHandler client, String roomName) {
        RoomThread room = rooms.get(roomName);
        if (room == null) {
            return new String[] { "FAIL", "Phòng không tồn tại" };
        }
        if (room.isFull()) {
            return new String[] { "FULL", "Phòng đã đầy" };
        }

        room.addPlayer(client);
        client.setCurrentRoom(roomName);
        Server.broadcastRoomsList();

        System.out.println("👤 " + client.username + " tham gia: " + roomName);
        return new String[] { "OK", roomName };
    }

    /**
     * Xử lý LEAVE
     * 📤 GỬI: LEAVE_OK
     * 📨 NHẬN: LEAVE
     */
    public void handleLeaveRoom(ClientHandler client) {
        String roomName = client.getCurrentRoom();
        if (roomName != null) {
            RoomThread room = rooms.get(roomName);
            if (room != null) {
                room.removePlayer(client);
            }
            client.resetCurrentRoom();
            System.out.println("🚪 " + client.username + " rời phòng: " + roomName);
        }
    }

    /**
     * Xử lý READY;roomName
     * 📤 GỬI: (chuyển đến RoomThread)
     * 📨 NHẬN: READY;roomName
     */
    public void handleReady(ClientHandler client, String roomName) {
        String currentRoom = client.getCurrentRoom();
        if (currentRoom != null && currentRoom.equals(roomName) && rooms.containsKey(currentRoom)) {
            rooms.get(currentRoom).setPlayerReady(client.username, true);
        }
    }

    /**
     * Xử lý START;roomName
     * 📤 GỬI: (chuyển đến RoomThread)
     * 📨 NHẬN: START;roomName
     */
    public void handleStartGame(String roomName) {
        RoomThread room = rooms.get(roomName);
        if (room != null) {
            room.startGame();
        }
    }

    /**
     * Xử lý DRAW;roomName
     * 📤 GỬI: (chuyển đến RoomThread)
     * 📨 NHẬN: DRAW;roomName
     */
    public void handleDrawCard(ClientHandler client, String roomName) {
        RoomThread room = rooms.get(roomName);
        if (room != null) {
            room.drawCard(client);
        }
    }

    /**
     * Xử lý KICK_PLAYER;roomName;targetUsername
     * 📤 GỬI: (chuyển đến RoomThread)
     * 📨 NHẬN: KICK_PLAYER;roomName;targetUsername
     */
    public void handleKickPlayer(ClientHandler client, String roomName, String targetUsername) {
        RoomThread room = rooms.get(roomName);
        if (room != null) {
            room.kickPlayer(targetUsername, client);
        }
    }

    /**
     * Xử lý GET_ROOM_UPDATE;roomName
     * 📤 GỬI: ROOM_UPDATE|...
     * 📨 NHẬN: GET_ROOM_UPDATE;roomName
     */
    public void handleGetRoomUpdate(ClientHandler client, String roomName) {
        RoomThread room = rooms.get(roomName);
        if (room != null) {
            room.sendRoomUpdateTo(client);
        }
    }
}
