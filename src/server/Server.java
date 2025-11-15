package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    // Dữ liệu dùng chung - Thread-safe collections
    protected static Map<String, String> accounts = new HashMap<>();
    protected static Map<String, RoomThread> rooms = new ConcurrentHashMap<>();
    protected static List<ClientHandler> activeClients = Collections.synchronizedList(new ArrayList<>());
    protected static Map<String, Integer> playerScores = new ConcurrentHashMap<>(); // Điểm của người chơi

    static {
        accounts.put("admin", "123");
        accounts.put("user1", "abc");
        accounts.put("user2", "xyz");
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            InetAddress localHost = InetAddress.getLocalHost();
            System.out.println("🟢 Server đang chạy trên cổng 5000");
            System.out.println("📡 IP: " + localHost.getHostAddress());

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("👤 Người dùng mới kết nối.");
                new ClientHandler(socket, accounts, rooms, activeClients).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Gửi danh sách người chơi online (event-driven)
    public static void broadcastPlayerList() {
        List<ClientHandler> snapshot;
        String msg;

        // Synchronized khi iterate để tránh ConcurrentModificationException
        synchronized (activeClients) {
            StringBuilder sb = new StringBuilder("PLAYER_LIST|");
            for (ClientHandler client : activeClients) {
                if (client.username != null) {
                    sb.append(client.username).append(":").append(client.getStatus()).append("|");
                }
            }
            msg = sb.toString();
            // Tạo snapshot để send bên ngoài synchronized block
            snapshot = new ArrayList<>(activeClients);
        }

        // Send message bên ngoài synchronized block để tránh block lâu
        for (ClientHandler client : snapshot) {
            client.sendMessage(msg);
        }
    }

    // Gửi danh sách phòng hiện có cùng số người trong từng phòng
    public static void broadcastRoomsList() {
        List<ClientHandler> snapshot;
        String msg;

        synchronized (activeClients) {
            StringBuilder sb = new StringBuilder("ROOMS_LIST|");
            for (Map.Entry<String, RoomThread> e : rooms.entrySet()) {
                String name = e.getKey();
                RoomThread rt = e.getValue();
                int count = rt.getPlayerCount();
                sb.append(name).append(":").append(count).append("/").append(6).append("|");
            }
            msg = sb.toString();
            snapshot = new ArrayList<>(activeClients);
        }

        for (ClientHandler client : snapshot) {
            client.sendMessage(msg);
        }
    }
}