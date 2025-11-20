package server.core;

import server.handlers.RoomCommandHandler;
import server.handlers.GameCommandHandler;
import server.handlers.AuthenticationHandler;
import server.managers.BroadcastHelper;
import server.database.Database;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CLIENT HANDLER - XỬ LÝ KẾT NỐI & MESSAGES CỦA MỖI CLIENT
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Mỗi client kết nối có 1 ClientHandler riêng xử lý:
 * - Đăng nhập/đăng ký
 * - Chuyển tiếp messages đến đúng phòng
 * - Gửi thông tin player/room list
 * - Quản lý lịch sử trận đấu
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📨 PROTOCOL MESSAGES NHẬN VÀO (Client → Server):
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * GỬI: "LOGIN;username;password"
 * TRẢ VỀ: "LOGIN_OK" hoặc "LOGIN_FAIL"
 * LOGIC: Tự động tạo tài khoản mới nếu username chưa tồn tại
 * ⚠️ Password lưu plain text (CHƯA MÃ HÓA - cần cải thiện bảo mật)
 * 
 * GỬI: "GET_PLAYER_LIST"
 * TRẢ VỀ: "PLAYER_LIST|user1:status1:pts1|user2:status2:pts2|..."
 * 
 * GỬI: "GET_ROOMS"
 * TRẢ VỀ: "ROOMS_LIST|room1:count1/6|room2:count2/6|..."
 * 
 * GỬI: "CREATE"
 * TRẢ VỀ: "ROOM_CREATED;RoomName" → sau đó "ROOM_UPDATE|..."
 * LOGIC: Tên phòng = "Room_" + username, người tạo là host
 * 
 * GỬI: "JOIN;RoomName"
 * TRẢ VỀ: "JOIN_OK;RoomName" hoặc "JOIN_FAIL" hoặc "ROOM_FULL"
 * LOGIC: Max 6 người/phòng
 * 
 * GỬI: "READY;true" hoặc "READY;false"
 * CHUYỂN ĐẾN: RoomThread (chỉ guest gửi, host không cần)
 * 
 * GỬI: "START_GAME"
 * CHUYỂN ĐẾN: RoomThread (chỉ host gửi, cần đủ người & tất cả ready)
 * 
 * GỬI: "DRAW_CARD"
 * CHUYỂN ĐẾN: RoomThread (phải đúng lượt)
 * 
 * GỬI: "KICK_PLAYER;targetUsername"
 * CHUYỂN ĐẾN: RoomThread (chỉ host gửi)
 * TRẢ VỀ: "NOT_HOST" nếu không phải host
 * "KICK_BLOCKED;..." nếu không thể kick (game đang chạy)
 * 
 * GỬI: "GET_HISTORY"
 * TRẢ VỀ: "HISTORY_DATA|matchId|startTime|endTime|numPlayers|winner\n..."
 * PARSE: LobbyScreen.java dòng 240-274
 * 
 * GỬI: "GET_MATCH_DETAIL;matchId"
 * TRẢ VỀ: "MATCH_DETAIL_DATA|MATCH|...|RESULT|...|RESULT|..."
 * PARSE: LobbyScreen.java dòng 293-320
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📡 PROTOCOL MESSAGES GỬI ĐI (Server → Client):
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Xem chi tiết ở Server.java và RoomThread.java
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 🔄 FLOW XỬ LÝ:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 1. Client kết nối → ClientHandler.run() bắt đầu
 * 2. Đợi LOGIN message → authenticate hoặc tạo tài khoản mới
 * 3. Loop lắng nghe messages:
 * - Request info (GET_*) → gửi trả về data
 * - Room actions (CREATE, JOIN) → tương tác với RoomThread
 * - Game actions (READY, START, DRAW, KICK) → chuyển đến RoomThread
 * 4. Client ngắt kết nối → cleanup (rời phòng, xóa khỏi activeClients)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 🎨 CHÚ Ý CHO GIAO DIỆN:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * ⚠️ ĐÂY LÀ BẢN DEMO LOGIC - CẦN CẢI THIỆN GIAO DIỆN!
 * 
 * File này xử lý backend, không cần sửa.
 * Chỉ cần tập trung vào UI ở các Screen (Login, Lobby, Game).
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class ClientHandler extends Thread {
    private Socket socket;// Socket nhan tu player
    private DataInputStream in; // Input
    private DataOutputStream out;// Output
    public String username; // username
    private String status = "free"; // free | busy | playing
    private String currentRoom;

    private Map<String, RoomThread> rooms;// danh sach phong
    private List<ClientHandler> activeClients;
    private Database db;

    // Helper classes
    private AuthenticationHandler authHandler;
    private RoomCommandHandler roomHandler;
    private GameCommandHandler gameHandler;
    private BroadcastHelper broadcastHelper;

    public ClientHandler(Socket socket, Map<String, String> accounts, Map<String, RoomThread> rooms,
            List<ClientHandler> activeClients, Database db) {
        this.socket = socket;
        this.rooms = rooms;
        this.activeClients = activeClients;
        this.db = db;

        // Initialize helper classes
        this.authHandler = new AuthenticationHandler(db, accounts);
        this.roomHandler = new RoomCommandHandler(rooms, db);
        this.gameHandler = new GameCommandHandler(rooms, activeClients);
        this.broadcastHelper = new BroadcastHelper(activeClients);
    }

    private void addActiveClient() {
        synchronized (activeClients) {
            activeClients.add(this);
        }
        status = "free";
    }

    private void removeActiveClient() {
        synchronized (activeClients) {
            activeClients.remove(this);
        }
    }

    //
    @Override //
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Đăng nhập
            String loginMsg = in.readUTF(); // 📨 NHẬN: "LOGIN;username;password"
            if (loginMsg.startsWith("LOGIN;")) {
                String[] parts = loginMsg.split(";");
                String user = parts[1];
                String pass = parts[2];

                AuthenticationHandler.LoginResult loginResult = authHandler.handleLogin(user, pass);

                if (loginResult.success) {
                    out.writeUTF("LOGIN_OK"); // 📤 GỬI: "LOGIN_OK" → đăng nhập thành công
                    username = user;
                    Server.playerScores.putIfAbsent(user, loginResult.points);
                    addActiveClient();
                    System.out.println("✅ " + user + " đăng nhập thành công.");
                    sendPlayerListToClient(); // snapshot
                    Server.broadcastPlayerList(); // thong bao moi nguoi
                } else {
                    out.writeUTF("LOGIN_FAIL"); // 📤 GỬI: "LOGIN_FAIL" → đăng nhập thất bại
                    socket.close();
                    return;
                }
            }

            // Message handling loop
            while (!socket.isClosed()) {
                String msg = in.readUTF();
                if (!handleMessage(msg)) {
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println("⚠️ Client ngắt kết nối: " + username);
        } finally {
            removeActiveClient();
            if (currentRoom != null && rooms.containsKey(currentRoom)) {
                rooms.get(currentRoom).removePlayer(this);// goi den thread phong tuong ung de loai bo nguoi choi
            }

            // Broadcast danh sách người chơi khi có người logout
            Server.broadcastPlayerList();

            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * Xử lý message từ client
     * 
     * @return true nếu tiếp tục loop, false nếu ngắt kết nối
     */
    private boolean handleMessage(String msg) throws IOException {
        System.out.println("[" + username + "] gửi: " + msg);

        if (msg.equalsIgnoreCase("exit")) {
            return false;
        }

        // ═══════════════════════════════════════════════════════════════
        // INFO REQUESTS - Database queries
        // ═══════════════════════════════════════════════════════════════

        if (msg.equalsIgnoreCase("GET_PLAYER_LIST")) { // 📨 NHẬN: "GET_PLAYER_LIST" → request danh sách người online
            sendPlayerListToClient(); // 📤 GỬI: "PLAYER_LIST|user1:status:pts|..."
            return true;
        }

        if (msg.equalsIgnoreCase("GET_ROOMS")) { // 📨 NHẬN: "GET_ROOMS" → request danh sách phòng
            sendRoomsListToClient(); // 📤 GỬI: "ROOMS_LIST|room1:count/6|..."
            return true;
        }

        if (msg.equalsIgnoreCase("GET_HISTORY")) { // 📨 NHẬN: "GET_HISTORY" → request lịch sử trận đấu
            String history = db.getMatchHistory(20);
            sendMessage("HISTORY_DATA|" + history); // 📤 GỬI: "HISTORY_DATA|matchId|startTime|..."
            return true;
        }

        if (msg.equalsIgnoreCase("GET_HISTORY_DETAIL")) {
            String historyDetail = db.getDetailedMatchHistory(10);
            sendMessage("HISTORY_DETAIL_DATA|" + historyDetail);
            return true;
        }

        if (msg.startsWith("GET_MATCH_DETAIL;")) { // 📨 NHẬN: "GET_MATCH_DETAIL;matchId" → request chi tiết trận đấu
            String[] parts = msg.split(";");
            if (parts.length >= 2) {
                try {
                    int matchId = Integer.parseInt(parts[1]);
                    String detail = db.getMatchDetail(matchId);
                    sendMessage("MATCH_DETAIL_DATA|" + detail); // 📤 GỬI: "MATCH_DETAIL_DATA|MATCH|...|RESULT|..."
                } catch (NumberFormatException ex) {
                    sendMessage("MATCH_DETAIL_DATA|ERROR Invalid MatchID"); // 📤 GỬI: "MATCH_DETAIL_DATA|ERROR ..."
                }
            }
            return true;
        }

        // ═══════════════════════════════════════════════════════════════
        // ROOM COMMANDS
        // ═══════════════════════════════════════════════════════════════

        if (msg.equalsIgnoreCase("CREATE")) { // 📨 NHẬN: "CREATE" → tạo phòng mới
            RoomCommandHandler.RoomResult result = roomHandler.handleCreateRoom(this);
            if (result.success) {
                currentRoom = result.roomName;
                out.writeUTF("ROOM_CREATED;" + result.roomName); // 📤 GỬI: "ROOM_CREATED;RoomName" → tạo phòng thành
                                                                 // công
                Server.broadcastRoomsList();
            } else {
                out.writeUTF("CREATE_FAIL;" + result.status); // 📤 GỬI: "CREATE_FAIL;..." → tạo phòng thất bại
            }
            return true;
        }

        if (msg.startsWith("JOIN;")) { // 📨 NHẬN: "JOIN;RoomName" → tham gia phòng
            String roomName = msg.split(";")[1];
            RoomCommandHandler.RoomResult result = roomHandler.handleJoinRoom(this, roomName);
            if (result.success) {
                currentRoom = result.roomName;
                out.writeUTF("JOIN_OK;" + result.roomName); // 📤 GỬI: "JOIN_OK;RoomName" → tham gia thành công
                status = "busy";
                Server.broadcastRoomsList();
            } else if (result.status.equals("FULL")) {
                out.writeUTF("ROOM_FULL"); // 📤 GỬI: "ROOM_FULL" → phòng đầy
            } else {
                out.writeUTF("JOIN_FAIL"); // 📤 GỬI: "JOIN_FAIL" → tham gia thất bại
            }
            return true;
        }

        if (msg.startsWith("READY;")) { // 📨 NHẬN: "READY;roomName" → sẵn sàng chơi
            String roomName = msg.split(";")[1];
            if (currentRoom != null && currentRoom.equals(roomName) && rooms.containsKey(currentRoom)) {
                rooms.get(currentRoom).setPlayerReady(username, true); // → broadcast "READY_STATUS|..."
            }
            return true;
        }

        if (msg.startsWith("GET_ROOM_UPDATE;")) {
            String roomName = msg.split(";")[1];
            RoomThread r = rooms.get(roomName);
            if (r != null) {
                r.sendRoomUpdateTo(this);
            }
            return true;
        }

        if (msg.startsWith("START;")) { // 📨 NHẬN: "START;roomName" → host bắt đầu game
            String roomName = msg.split(";")[1];
            RoomThread r = rooms.get(roomName);
            if (r != null) {
                r.startGame(); // → broadcast "GAME_START;RoomName"
            }
            return true;
        }

        if (msg.startsWith("LEAVE_ROOM;")) {
            if (currentRoom != null && rooms.containsKey(currentRoom)) {
                rooms.get(currentRoom).removePlayer(this);
                resetCurrentRoom();
                System.out.println("🚪 " + username + " đã thoát khỏi phòng");
                Server.broadcastPlayerList();
                Server.broadcastRoomsList();
            }
            return true;
        }

        // ═══════════════════════════════════════════════════════════════
        // GAME COMMANDS
        // ═══════════════════════════════════════════════════════════════

        if (msg.startsWith("DRAW;")) { // 📨 NHẬN: "DRAW;roomName" → rút bài
            String roomName = msg.split(";")[1];
            if (currentRoom != null && currentRoom.equals(roomName) && rooms.containsKey(currentRoom)) {
                rooms.get(currentRoom).drawCard(this); // → gửi "DRAW;K♠" hoặc "NOT_YOUR_TURN"
            }
            return true;
        }

        if (msg.startsWith("KICK_PLAYER;")) { // 📨 NHẬN: "KICK_PLAYER;targetUsername" → kick người chơi
            String targetUsername = msg.split(";")[1];
            if (currentRoom != null && rooms.containsKey(currentRoom)) {
                rooms.get(currentRoom).kickPlayer(targetUsername, this); // → gửi "KICKED;reason" cho target
            }
            return true;
        }

        if (msg.startsWith("INVITE;")) { // 📨 NHẬN: "INVITE;targetUsername" → mời người vào phòng
            String targetUsername = msg.split(";")[1];
            gameHandler.handleInvite(this, targetUsername); // → gửi "INVITE;fromUser;roomName" cho target
            return true;
        }

        // Legacy card message format
        if (msg.matches("\\d+:.*")) {
            String[] parts = msg.split(":");
            System.out.println("🃏 Người chơi " + parts[0] + ", bài là:" + parts[1]);
        }

        return true;
    }

    public void resetCurrentRoom() {
        this.currentRoom = null;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String newStatus) {
        this.status = newStatus;
        Server.broadcastPlayerList();
    }

    // CurrentRoom phòng hiện tại
    public String getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String roomName) {
        this.currentRoom = roomName;
    }

    // Gửi danh sách người chơi cho client này (không broadcast)
    private void sendPlayerListToClient() {
        String msg = broadcastHelper.buildPlayerListMessage();
        sendMessage(msg);
    }

    // Gửi danh sách phòng hiện có cho client này
    private void sendRoomsListToClient() {
        String msg = broadcastHelper.buildRoomsListMessage(rooms);
        sendMessage(msg);
    }

    public void sendMessage(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            System.out.println("❌ Gửi thất bại tới " + username);
        }
    }
}
