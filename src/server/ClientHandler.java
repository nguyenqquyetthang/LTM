package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class ClientHandler extends Thread {
    private Socket socket;// Socket nhan tu player
    private DataInputStream in; // Input
    private DataOutputStream out;// Output
    public String username; // username
    private String status = "free"; // free | busy | playing
    private String currentRoom;

    private Map<String, String> accounts; // Danh sach accout
    private Map<String, RoomThread> rooms;// danh sach phong
    private List<ClientHandler> activeClients;

    public ClientHandler(Socket socket, Map<String, String> accounts, Map<String, RoomThread> rooms,
            List<ClientHandler> activeClients) {
        this.socket = socket;
        this.accounts = accounts;
        this.rooms = rooms;
        this.activeClients = activeClients;
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

    @Override
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
                if (accounts.containsKey(user) && accounts.get(user).equals(pass)) {
                    out.writeUTF("LOGIN_OK");
                    username = user;
                    Server.playerScores.putIfAbsent(user, 0);
                    addActiveClient();
                    System.out.println("✅ " + user + " đăng nhập thành công.");

                    // Gửi danh sách hiện tại cho client mới login ngay lập tức
                    sendPlayerListToClient();

                    // Broadcast cho TẤT CẢ clients khác (để họ biết có người mới)
                    Server.broadcastPlayerList();
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

            newRoom = new RoomThread(roomName, rooms);
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
        synchronized (activeClients) {
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
                    sb.append(client.username).append(":").append(client.getStatus()).append("|");
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