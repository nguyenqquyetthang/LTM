package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    // Dữ liệu dùng chung
    protected static Map<String, String> accounts = new HashMap<>();
    protected static Map<String, RoomThread> rooms = new HashMap<>();
    protected static List<ClientHandler> activeClients = new ArrayList<>();

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

            // Luồng cập nhật danh sách người chơi
            new Thread(() -> {
                try {
                    while (true) {
                        Thread.sleep(2000);
                        broadcastPlayerList();
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
            }).start();

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("👤 Người dùng mới kết nối.");
                new ClientHandler(socket, accounts, rooms, activeClients).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Gửi danh sách người chơi online
    private static synchronized void broadcastPlayerList() {
        StringBuilder sb = new StringBuilder("PLAYER_LIST|");
        for (ClientHandler client : activeClients) {
            if (client.username != null) {
                sb.append(client.username).append("|");
            }
        }
        String msg = sb.toString();
        for (ClientHandler client : activeClients) {
            client.sendMessage(msg);
        }
    }
}