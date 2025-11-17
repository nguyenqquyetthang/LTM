package server.handlers;

import server.core.RoomThread;
import server.core.Server;
import server.core.ClientHandler;
import server.database.Database;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ROOM COMMAND HANDLER - XỬ LÝ CÁC LỆNH LIÊN QUAN ĐẾN PHÒNG
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - CREATE - Tạo phòng mới
 * - JOIN - Tham gia phòng
 * - LEAVE - Rời phòng
 * - READY - Đánh dấu sẵn sàng
 * - START - Bắt đầu game
 * - GET_ROOM_UPDATE - Lấy cập nhật phòng
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RoomCommandHandler {
    private Map<String, RoomThread> rooms;
    private Database db;

    public RoomCommandHandler(Map<String, RoomThread> rooms, Database db) {
        this.rooms = rooms;
        this.db = db;
    }

    /**
     * Xử lý CREATE - Tạo phòng mới
     * 📨 NHẬN: CREATE
     * 📤 GỬI: ROOM_CREATED;RoomName hoặc ROOM_EXISTS
     * 
     * @param client ClientHandler của người tạo phòng
     * @return RoomResult với status và roomName
     */
    public RoomResult handleCreateRoom(ClientHandler client) {
        String username = client.username;
        int roomNumber = Server.findSmallestAvailableRoomNumber();
        String roomName = "Room_" + roomNumber;

        synchronized (rooms) {
            RoomThread newRoom = new RoomThread(roomName, rooms, db);
            rooms.put(roomName, newRoom);
            newRoom.start();
            newRoom.addPlayer(client);
            client.setCurrentRoom(roomName);
        }

        Server.broadcastRoomsList();
        System.out.println("🏠 " + username + " tạo phòng: " + roomName);
        return new RoomResult(true, "CREATED", roomName);
    }

    /**
     * Xử lý JOIN - Tham gia phòng
     * 📨 NHẬN: JOIN;RoomName
     * 📤 GỬI: JOIN_OK;RoomName hoặc JOIN_FAIL hoặc ROOM_FULL
     * 
     * @param client   ClientHandler của người tham gia
     * @param roomName Tên phòng muốn join
     * @return RoomResult với status
     */
    public RoomResult handleJoinRoom(ClientHandler client, String roomName) {
        RoomThread room = rooms.get(roomName);

        if (room == null) {
            return new RoomResult(false, "NOT_FOUND", roomName);
        }

        if (room.isFull()) {
            return new RoomResult(false, "FULL", roomName);
        }

        room.addPlayer(client);
        client.setCurrentRoom(roomName);
        Server.broadcastRoomsList();

        System.out.println("👤 " + client.username + " tham gia: " + roomName);
        return new RoomResult(true, "OK", roomName);
    }

    /**
     * Xử lý LEAVE - Rời phòng
     * 📨 NHẬN: LEAVE_ROOM;
     * 📤 GỬI: (không trả về message, chỉ cleanup)
     * 
     * @param client ClientHandler của người rời
     */
    public void handleLeaveRoom(ClientHandler client) {
        String currentRoom = client.getCurrentRoom();

        if (currentRoom != null && rooms.containsKey(currentRoom)) {
            rooms.get(currentRoom).removePlayer(client);
            client.resetCurrentRoom();
            System.out.println("🚪 " + client.username + " rời phòng: " + currentRoom);

            Server.broadcastPlayerList();
            Server.broadcastRoomsList();
        }
    }

    /**
     * Xử lý READY - Đánh dấu sẵn sàng
     * 📨 NHẬN: READY;RoomName
     * 📤 GỬI: (chuyển đến RoomThread để broadcast)
     * 
     * @param client   ClientHandler của người ready
     * @param roomName Tên phòng
     */
    public void handleReady(ClientHandler client, String roomName) {
        String currentRoom = client.getCurrentRoom();

        if (currentRoom != null && currentRoom.equals(roomName) && rooms.containsKey(currentRoom)) {
            rooms.get(currentRoom).setPlayerReady(client.username, true);
        }
    }

    /**
     * Xử lý START - Bắt đầu game
     * 📨 NHẬN: START;RoomName
     * 📤 GỬI: (chuyển đến RoomThread để xử lý)
     * 
     * @param roomName Tên phòng
     */
    public void handleStartGame(String roomName) {
        RoomThread room = rooms.get(roomName);
        if (room != null) {
            room.startGame();
        }
    }

    /**
     * Xử lý GET_ROOM_UPDATE - Lấy cập nhật phòng
     * 📨 NHẬN: GET_ROOM_UPDATE;RoomName
     * 📤 GỬI: ROOM_UPDATE|... (từ RoomThread)
     * 
     * @param client   ClientHandler yêu cầu update
     * @param roomName Tên phòng
     */
    public void handleGetRoomUpdate(ClientHandler client, String roomName) {
        RoomThread room = rooms.get(roomName);
        if (room != null) {
            room.sendRoomUpdateTo(client);
        }
    }

    /**
     * Inner class chứa kết quả room operations
     */
    public static class RoomResult {
        public final boolean success;
        public final String status; // OK, CREATED, NOT_FOUND, FULL, ROOM_EXISTS
        public final String roomName;

        public RoomResult(boolean success, String status, String roomName) {
            this.success = success;
            this.status = status;
            this.roomName = roomName;
        }
    }
}
