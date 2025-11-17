package server;

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

    private Map<String, String> accounts; // Danh sach accout (cache từ DB)
    private Map<String, RoomThread> rooms;// danh sach phong
    private List<ClientHandler> activeClients;
    private Database db;

    public ClientHandler(Socket socket, Map<String, String> accounts, Map<String, RoomThread> rooms,
            List<ClientHandler> activeClients, Database db) {
        this.socket = socket;
        this.accounts = accounts;
        this.rooms = rooms;
        this.activeClients = activeClients;
        this.db = db;
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
            String loginMsg = in.readUTF();
            if (loginMsg.startsWith("LOGIN;")) {
                String[] parts = loginMsg.split(";");
                String user = parts[1];
                String pass = parts[2];
                boolean ok = db.authenticate(user, pass);
                if (!ok) {
                    // Kiểm tra user có tồn tại chưa
                    Integer existingId = db.getPlayerId(user);
                    if (existingId == null) { // Chưa tồn tại -> tạo mới
                        Integer newId = db.createPlayer(user, pass);
                        if (newId != null) {
                            accounts.put(user, pass); // cập nhật cache
                            ok = true;
                        }
                    } // Nếu đã tồn tại nhưng sai mật khẩu -> vẫn thất bại như cũ
                }
                if (ok) {
                    out.writeUTF("LOGIN_OK");
                    username = user;
                    Integer pts = db.getTotalPoints(user);
                    Server.playerScores.putIfAbsent(user, pts == null ? 0 : pts);
                    addActiveClient();
                    System.out.println("✅ " + user + " đăng nhập thành công.");
                    sendPlayerListToClient(); // snapshot
                    Server.broadcastPlayerList(); // thông báo mọi người
                } else {
                    out.writeUTF("LOGIN_FAIL");
                    socket.close();
                    return;
                }
            }
            // nghe msg tu nguoi choi
            while (!socket.isClosed()) {
                String msg = in.readUTF();
                System.out.println("[" + username + "] gửi: " + msg);
                if (msg.equalsIgnoreCase("exit"))
                    break;

                // Request danh sách người chơi online
                if (msg.equalsIgnoreCase("GET_PLAYER_LIST")) {
                    sendPlayerListToClient();
                    continue;
                }

                // Request danh sách phòng hiện có
                if (msg.equalsIgnoreCase("GET_ROOMS")) {
                    sendRoomsListToClient();
                    continue;
                }

                // Request lịch sử trận đấu
                if (msg.equalsIgnoreCase("GET_HISTORY")) {
                    String history = db.getMatchHistory(20); // lấy 20 trận gần nhất
                    sendMessage("HISTORY_DATA|" + history);
                    continue;
                }

                // Request lịch sử chi tiết (tay bài + xếp hạng)
                if (msg.equalsIgnoreCase("GET_HISTORY_DETAIL")) {
                    String historyDetail = db.getDetailedMatchHistory(10); // lấy 10 trận gần nhất (chi tiết)
                    sendMessage("HISTORY_DETAIL_DATA|" + historyDetail);
                    continue;
                }

                // Request chi tiết 1 match: GET_MATCH_DETAIL;MatchID
                if (msg.startsWith("GET_MATCH_DETAIL;")) {
                    String[] parts = msg.split(";");
                    if (parts.length >= 2) {
                        try {
                            int mid = Integer.parseInt(parts[1]);
                            String detail = db.getMatchDetail(mid);
                            sendMessage("MATCH_DETAIL_DATA|" + detail);
                        } catch (NumberFormatException ex) {
                            sendMessage("MATCH_DETAIL_DATA|ERROR Invalid MatchID");
                        }
                    }
                    continue;
                }

                // Tạo phòng
                if (msg.equalsIgnoreCase("CREATE")) {
                    handleCreateRoom(username);
                    continue;
                }

                // Tham gia phòng
                if (msg.startsWith("JOIN;")) {
                    String roomName = msg.split(";")[1];
                    handleJoinRoom(roomName);
                    continue;
                }

                // Người chơi sẵn sàng
                if (msg.startsWith("READY;")) {
                    String roomName = msg.split(";")[1];
                    if (currentRoom != null && currentRoom.equals(roomName) && rooms.containsKey(currentRoom)) {
                        rooms.get(currentRoom).setPlayerReady(username, true);
                    }
                    continue;
                }

                // Client yêu cầu cập nhật trạng thái phòng ngay lập tức
                if (msg.startsWith("GET_ROOM_UPDATE;")) {
                    String roomName = msg.split(";")[1];
                    RoomThread r = rooms.get(roomName);
                    if (r != null) {
                        r.sendRoomUpdateTo(this);
                    }
                    continue;
                }

                // Bắt đầu game
                if (msg.startsWith("START;")) {
                    String roomName = msg.split(";")[1];
                    RoomThread r = rooms.get(roomName);
                    if (r != null)
                        r.startGame();
                    continue;
                }

                // Rút bài dùng
                if (msg.startsWith("DRAW;")) {
                    String roomName = msg.split(";")[1];
                    if (currentRoom != null && currentRoom.equals(roomName) && rooms.containsKey(currentRoom)) {
                        // Chuyển sang logic draw mới theo lượt
                        rooms.get(currentRoom).drawCard(this);
                    }
                    continue;
                }

                // Mời người chơi vào phòng
                if (msg.startsWith("INVITE;")) {
                    String[] parts = msg.split(";");
                    String targetUsername = parts[1];
                    handleInvite(targetUsername);
                    continue;
                }

                // Kick người chơi
                if (msg.startsWith("KICK_PLAYER;")) {
                    String[] parts = msg.split(";");
                    String targetUsername = parts[1];
                    if (currentRoom != null && rooms.containsKey(currentRoom)) {
                        rooms.get(currentRoom).kickPlayer(targetUsername, this);
                    }
                    continue;
                }

                // Thoát khỏi phòng
                if (msg.startsWith("LEAVE_ROOM;")) {
                    if (currentRoom != null && rooms.containsKey(currentRoom)) {
                        rooms.get(currentRoom).removePlayer(this);
                        resetCurrentRoom();
                        System.out.println("🚪 " + username + " đã thoát khỏi phòng");

                        // Broadcast danh sách người chơi online
                        Server.broadcastPlayerList();
                        // Cập nhật danh sách phòng và số người trong phòng
                        Server.broadcastRoomsList();
                    }
                    continue;
                }

                // Nhận bài đã chọn
                if (msg.matches("\\d+:.*")) {
                    String[] parts = msg.split(":");
                    System.out.println("🃏 Người chơi " + parts[0] + ", bài là:" + parts[1]);
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

    private void handleCreateRoom(String user) throws IOException {
        // Synchronized để tránh race condition khi tạo tên phòng
        String roomName;
        RoomThread newRoom;

        synchronized (rooms) {
            // Tạo tên phòng unique trong synchronized block
            int roomNumber = rooms.size() + 1;
            do {
                roomName = "Room_" + roomNumber;
                roomNumber++;
            } while (rooms.containsKey(roomName));

            newRoom = new RoomThread(roomName, rooms, db);
            rooms.put(roomName, newRoom);
        }

        // Start thread và add player bên ngoài synchronized block
        newRoom.start();
        currentRoom = roomName;
        newRoom.addPlayer(this);
        out.writeUTF("ROOM_CREATED;" + roomName);
        System.out.println("🏠 " + user + " đã tạo phòng: " + roomName);
        // Cập nhật danh sách phòng cho tất cả client
        Server.broadcastRoomsList();
    }

    private void handleJoinRoom(String roomName) throws IOException {
        if (rooms.containsKey(roomName)) {
            RoomThread room = rooms.get(roomName);
            if (room.isFull()) {
                out.writeUTF("ROOM_FULL");
                return;
            }
            currentRoom = roomName;
            room.addPlayer(this);
            out.writeUTF("JOIN_OK;" + roomName);
            System.out.println("👥 " + username + " tham gia phòng " + roomName);
            status = "busy"; // vào phòng nhưng chưa chơi
            // Cập nhật số người trong phòng trên lobby
            Server.broadcastRoomsList();
        } else {
            out.writeUTF("JOIN_FAIL");
        }
    }

    private void handleInvite(String targetUsername) {
        if (currentRoom == null) {
            sendMessage("NOT_IN_ROOM");
            return;
        }

        // Tìm người chơi được mời (synchronized để tránh
        // ConcurrentModificationException)
        ClientHandler targetClient = null;
        synchronized (activeClients) { // 
            for (ClientHandler client : activeClients) {
                if (client.username != null && client.username.equals(targetUsername)) {
                    targetClient = client;
                    break;
                }
            }
        }

        // Gửi message bên ngoài synchronized block
        if (targetClient != null) {
            targetClient.sendMessage("INVITE;" + username + ";" + currentRoom);
            sendMessage("INVITE_SENT;" + targetUsername);
            System.out.println("📧 " + username + " mời " + targetUsername + " vào " + currentRoom);
        } else {
            sendMessage("USER_NOT_FOUND");
        }
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

    // Gửi danh sách người chơi cho client này (không broadcast)
    private void sendPlayerListToClient() {
        synchronized (activeClients) {
            StringBuilder sb = new StringBuilder("PLAYER_LIST|");
            for (ClientHandler client : activeClients) {
                if (client.username != null) {
                    int pts = Server.playerScores.getOrDefault(client.username, 0);
                    sb.append(client.username).append(":").append(client.getStatus())
                            .append(":").append(pts).append("|");
                }
            }
            sendMessage(sb.toString());
        }
    }

    // Gửi danh sách phòng hiện có cho client này
    private void sendRoomsListToClient() {
        StringBuilder sb = new StringBuilder("ROOMS_LIST|");
        for (Map.Entry<String, RoomThread> e : rooms.entrySet()) {
            String name = e.getKey();
            RoomThread rt = e.getValue();
            sb.append(name).append(":").append(rt.getPlayerCount()).append("/").append(6).append("|");
        }
        sendMessage(sb.toString());
    }

    public void sendMessage(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            System.out.println("❌ Gửi thất bại tới " + username);
        }
    }
}