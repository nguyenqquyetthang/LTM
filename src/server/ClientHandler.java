package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class ClientHandler extends Thread {
    private Socket socket;// Socket nhan tu player
    private DataInputStream in; // Input
    private DataOutputStream out;// Output
    public String username; // username
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

    private synchronized void addActiveClient() {
        activeClients.add(this);
    }

    private synchronized void removeActiveClient() {
        activeClients.remove(this);
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
                    addActiveClient();
                    System.out.println("✅ " + user + " đăng nhập thành công.");
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

                // Bắt đầu game
                if (msg.startsWith("START;")) {
                    String roomName = msg.split(";")[1];
                    RoomThread r = rooms.get(roomName);
                    if (r != null)
                        r.startGame();
                    continue;
                }

                // Rút bài
                if (msg.contains(":Draw")) {
                    if (currentRoom != null && rooms.containsKey(currentRoom)) {
                        int playerID = rooms.get(currentRoom).getPlayerIndex(this);
                        if (playerID != -1) {
                            rooms.get(currentRoom).playerDrawCard(playerID);
                        }
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
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private void handleCreateRoom(String user) throws IOException {
        String roomName = "Room_" + (rooms.size() + 1);
        if (!rooms.containsKey(roomName)) {
            RoomThread newRoom = new RoomThread(roomName, rooms);
            rooms.put(roomName, newRoom);
            newRoom.start();
            currentRoom = roomName;
            newRoom.addPlayer(this);
            out.writeUTF("ROOM_CREATED;" + roomName);
            System.out.println("🏠 " + user + " đã tạo phòng: " + roomName);
        }
    }

    private void handleJoinRoom(String roomName) throws IOException {
        if (rooms.containsKey(roomName)) {
            currentRoom = roomName;
            rooms.get(roomName).addPlayer(this);
            out.writeUTF("JOIN_OK;" + roomName);
            System.out.println("👥 " + username + " tham gia phòng " + roomName);
        } else {
            out.writeUTF("JOIN_FAIL");
        }
    }

    public void sendMessage(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            System.out.println("❌ Gửi thất bại tới " + username);
        }
    }
}