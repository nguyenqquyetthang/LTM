package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * SERVER CHÍNH - GAME BÀI 3 LÁ
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 🔧 CẤU HÌNH QUAN TRỌNG:
 * 
 * 1. PORT SERVER (dòng 23):
 * ServerSocket serverSocket = new ServerSocket(5000);
 * *** THAY ĐỔI 5000 THÀNH PORT KHÁC NẾU CẦN ***
 * ⚠️ Client PHẢI dùng cùng port này (xem LoginScreen.java dòng 35)
 * 
 * 2. DATABASE (xem Database.java dòng 13):
 * String connectionUrl =
 * "jdbc:sqlserver://TÊN_SERVER:1433;databaseName=TÊN_DB;...";
 * *** CẦN CẤU HÌNH: SERVER_NAME, DB_NAME, USERNAME, PASSWORD ***
 * 
 * 3. TÌM IP CỦA SERVER:
 * Khi chạy server sẽ in ra console:
 * "📡 IP: 192.168.x.x"
 * → Dùng IP này cho client kết nối (nếu khác máy)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📡 PROTOCOL MESSAGES GỬI ĐI (Server → Client):
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * • PLAYER_LIST|user1:status1:pts1|user2:status2:pts2|...
 * → Danh sách người chơi online với điểm
 * → Gửi khi: có người login/logout, hoặc client request GET_PLAYER_LIST
 * → Parse ở: LobbyScreen.java
 * 
 * • ROOMS_LIST|room1:count1/6|room2:count2/6|...
 * → Danh sách phòng với số người/max
 * → Gửi khi: có phòng mới/xóa, hoặc client request GET_ROOMS
 * → Parse ở: LobbyScreen.java
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📨 PROTOCOL MESSAGES NHẬN VÀO (Client → Server):
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Xử lý bởi ClientHandler.java:
 * • LOGIN;username;password
 * • GET_PLAYER_LIST
 * • GET_ROOMS
 * • CREATE
 * • JOIN;RoomName
 * • GET_HISTORY
 * • GET_MATCH_DETAIL;matchId
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 🎨 CHÚ Ý CHO GIAO DIỆN:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * ⚠️ ĐÂY LÀ BẢN DEMO LOGIC - CẦN CẢI THIỆN GIAO DIỆN!
 * 
 * Để chuẩn bị cho UI mới:
 * 1. Tất cả protocol messages đã được documented chi tiết
 * 2. Format messages chuẩn, dễ parse (phân cách bởi | và :)
 * 3. Logic game hoàn chỉnh, chỉ cần wrap UI đẹp hơn
 * 4. Xem PROTOCOL.md để biết đầy đủ messages và format
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class Server {
    // Dữ liệu dùng chung - Thread-safe collections
    protected static Map<String, String> accounts = new HashMap<>(); // Username -> PasswordHash
    protected static Map<String, RoomThread> rooms = new ConcurrentHashMap<>();
    protected static List<ClientHandler> activeClients = Collections.synchronizedList(new ArrayList<>());
    protected static Map<String, Integer> playerScores = new ConcurrentHashMap<>(); // Điểm của người chơi (cache)

    // Database handler
    protected static Database db;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            // Init database & load accounts
            db = new Database();
            db.ensureCardsSeeded();
            accounts = db.loadAccounts();

            InetAddress localHost = InetAddress.getLocalHost();
            System.out.println("🟢 Server đang chạy trên cổng 5000");
            System.out.println("📡 IP: " + localHost.getHostAddress());

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("👤 Người dùng mới kết nối.");
                new ClientHandler(socket, accounts, rooms, activeClients, db).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * BROADCAST DANH SÁCH NGƯỜI CHƠI
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * 📤 FORMAT GỬI ĐI:
     * "PLAYER_LIST|user1:status1:pts1|user2:status2:pts2|user3:status3:pts3|"
     * 
     * VÍ DỤ:
     * "PLAYER_LIST|player1:free:10|player2:busy:5|player3:playing:-2|"
     * ^ ^ ^
     * | | điểm tích lũy (tổng qua các ván)
     * | status: free/busy/playing
     * username
     * 
     * 📥 PARSE Ở CLIENT (LobbyScreen.java dòng 157-178):
     * String[] tokens = players.split("\\|");
     * for (String t : tokens) {
     * String[] parts = t.split(":");
     * String name = parts[0];
     * String status = parts[1]; // free | busy | playing
     * String points = parts[2]; // điểm tích lũy
     * }
     * 
     * 🔄 KHI NÀO GỬI:
     * - Khi có người login/logout
     * - Khi client gửi GET_PLAYER_LIST
     * - Sau khi game kết thúc (điểm thay đổi)
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     */
    public static void broadcastPlayerList() {
        List<ClientHandler> snapshot;
        String msg;

        // Synchronized khi iterate để tránh ConcurrentModificationException
        synchronized (activeClients) {
            StringBuilder sb = new StringBuilder("PLAYER_LIST|");
            for (ClientHandler client : activeClients) {
                if (client.username != null) {
                    int pts = playerScores.getOrDefault(client.username, 0);
                    sb.append(client.username).append(":").append(client.getStatus())
                            .append(":").append(pts).append("|");
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

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * BROADCAST DANH SÁCH PHÒNG
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * 📤 FORMAT GỬI ĐI:
     * "ROOMS_LIST|room1:count1/6|room2:count2/6|room3:count3/6|"
     * 
     * VÍ DỤ:
     * "ROOMS_LIST|Room1:3/6|Room2:5/6|Room3:1/6|"
     * ^ ^ ^
     * | | max (luôn là 6)
     * | số người hiện tại
     * tên phòng
     * 
     * 📥 PARSE Ở CLIENT (LobbyScreen.java dòng 179-201):
     * String[] tokens = rooms.split("\\|");
     * for (String t : tokens) {
     * String[] parts = t.split(":");
     * String roomName = parts[0];
     * String occupancy = parts[1]; // "3/6"
     * }
     * 
     * 🔄 KHI NÀO GỬI:
     * - Khi có phòng mới được tạo
     * - Khi có người vào/rời phòng
     * - Khi phòng bị xóa (trống người)
     * - Khi client gửi GET_ROOMS
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     */
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