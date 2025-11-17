package client;

import java.io.*;
import java.net.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * NETWORK HANDLER - XỬ LÝ KẾT NỐI VỚI SERVER
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 🌐 CẤU HÌNH KẾT NỐI:
 * 
 * Constructor: new NetworkHandler(host, port)
 * 
 * 1. CHẠY LOCAL (cùng máy với server):
 * new NetworkHandler("localhost", 5000)
 * hoặc
 * new NetworkHandler("127.0.0.1", 5000)
 * 
 * 2. CHẠY TRÊN MẠNG LAN (khác máy, cùng wifi/mạng nội bộ):
 * new NetworkHandler("192.168.1.4", 5000)
 * ^^^^^^^^^^^^^ IP máy chạy server
 * 
 * ❓ TÌM IP SERVER NHƯ THẾ NÀO?
 * → Chạy Server.java, xem console sẽ có dòng:
 * "📡 IP: 192.168.x.x"
 * → Dùng IP đó thay vào đây
 * 
 * 3. CHẠY QUA INTERNET (WAN):
 * new NetworkHandler("public-ip-hoặc-domain", 5000)
 * ⚠️ Cần cấu hình port forwarding trên router
 * ⚠️ Không khuyến khích (bảo mật yếu)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📡 CÁCH SỬ DỤNG:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * // Kết nối
 * NetworkHandler network = new NetworkHandler("192.168.1.4", 5000);
 * 
 * // Đăng nhập
 * boolean ok = network.login("username", "password");
 * 
 * // Gửi message bất kỳ
 * network.sendMsg("CREATE");
 * network.sendMsg("JOIN;Room1");
 * network.sendMsg("DRAW_CARD");
 * 
 * // Lắng nghe message từ server (chạy trong thread riêng)
 * network.startListening(msg -> {
 * // msg là chuỗi nhận được từ server
 * // Parse và xử lý ở đây
 * });
 * 
 * // Ngắt kết nối
 * network.close();
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📨 PROTOCOL MESSAGES:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Xem chi tiết đầy đủ ở PROTOCOL.md
 * 
 * GỬI ĐI (Client → Server):
 * • "LOGIN;username;password"
 * • "GET_PLAYER_LIST"
 * • "GET_ROOMS"
 * • "CREATE"
 * • "JOIN;RoomName"
 * • "READY;true" hoặc "READY;false"
 * • "START_GAME"
 * • "DRAW_CARD"
 * • "KICK_PLAYER;targetUsername"
 * • "GET_HISTORY"
 * • "GET_MATCH_DETAIL;matchId"
 * 
 * NHẬN VÀO (Server → Client):
 * Parse bởi các Screen (LoginScreen, LobbyScreen, GameScreen)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 🎨 CHÚ Ý CHO GIAO DIỆN:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * ⚠️ ĐÂY LÀ BẢN DEMO LOGIC - CẦN CẢI THIỆN GIAO DIỆN!
 * 
 * Class này CHỈ xử lý kết nối & gửi/nhận messages.
 * Không cần sửa logic, chỉ cần wrap UI đẹp hơn ở các Screen.
 * 
 * Gợi ý cải thiện:
 * - Thêm loading indicator khi kết nối
 * - Hiển thị trạng thái kết nối (connected/disconnected)
 * - Retry logic khi mất kết nối
 * - Thông báo lỗi mạng user-friendly hơn
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class NetworkHandler {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread listenThread;

    public interface MessageHandler {
        void onMessage(String msg);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * CONSTRUCTOR - TẠO KẾT NỐI ĐẾN SERVER
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * @param host IP hoặc hostname của server
     *             - "localhost" hoặc "127.0.0.1" nếu cùng máy
     *             - "192.168.x.x" nếu khác máy trong LAN
     *             - Public IP nếu qua internet
     * 
     * @param port Port của server (mặc định 5000)
     *             ⚠️ PHẢI KHỚP với Server.java (dòng 23)
     * 
     * @throws IOException Nếu không kết nối được
     *                     Lý do thường gặp:
     *                     - Sai IP/Port
     *                     - Server chưa chạy
     *                     - Firewall chặn
     *                     - Không cùng mạng (nếu dùng LAN IP)
     * 
     *                     💡 VÍ DỤ SỬ DỤNG:
     * 
     *                     // Cùng máy với server:
     *                     NetworkHandler net = new NetworkHandler("localhost",
     *                     5000);
     * 
     *                     // Khác máy, lấy IP từ console server:
     *                     NetworkHandler net = new NetworkHandler("192.168.1.4",
     *                     5000);
     * 
     *                     ═══════════════════════════════════════════════════════════════════════════
     */
    public NetworkHandler(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    public boolean login(String username, String password) throws IOException {
        out.writeUTF("LOGIN;" + username + ";" + password);
        String response = in.readUTF();
        return response.equals("LOGIN_OK");
    }

    public void sendMsg(String msg) throws IOException {
        out.writeUTF(msg);
    }

    public void startListening(MessageHandler handler) {
        stopListening(); // Ngừng luồng cũ nếu có
        listenThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String msg = in.readUTF();
                    handler.onMessage(msg);
                }
            } catch (IOException e) {
                System.out.println("⚠️ Kết nối bị ngắt khi lắng nghe.");
            }
        });
        listenThread.start();
    }

    public void stopListening() {
        if (listenThread != null && listenThread.isAlive()) {
            listenThread.interrupt();
        }
    }

    public void close() throws IOException {
        stopListening();
        socket.close();
    }
}