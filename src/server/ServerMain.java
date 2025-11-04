package server;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;
import common.*;

public class ServerMain {
    private final int port = 2206;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final DatabaseManager db = new DatabaseManager(); // Kết nối SQL Server
    private final Gson gson = new Gson();
    private int roomCounter = 1;

    public static void main(String[] args) {
        new ServerMain().start();
    }

    public void start() {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("=== Game Server is running on port " + port + " ===");
            // db.initDatabase(); // đã có bảng sẵn, không cần tạo
            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                Socket s = ss.accept();
                ClientHandler ch = new ClientHandler(s, this);
                pool.submit(ch);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Khi người chơi đăng nhập
    public synchronized void registerClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        db.ensurePlayer(username); // Đảm bảo người chơi tồn tại trong DB
        db.updatePlayerStatus(username, "Online"); // Cập nhật trạng thái Online
        System.out.println("User logged in: " + username);
        broadcastOnlineList();
    }

    // Khi người chơi thoát
    public synchronized void unregisterClient(String username) {
        clients.remove(username);
        db.updatePlayerStatus(username, "Offline"); // Cập nhật trạng thái Offline
        System.out.println("User logged out: " + username);
        broadcastOnlineList();
    }

    public ClientHandler getHandler(String username) {
        return clients.get(username);
    }

    // Gửi danh sách online đến tất cả người chơi
    public void broadcastOnlineList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String u : clients.keySet()) {
            list.add(Map.of("username", u, "status", "Online"));
        }
        Message m = new Message(MessageType.ONLINE_LIST, "server", null, Map.of("list", list));
        broadcast(m);
    }

    public void broadcast(Message m) {
        for (ClientHandler ch : clients.values())
            ch.send(m);
    }

    // Quản lý phòng chơi
    public GameRoom createRoom() {
        String id = "R" + (roomCounter++);
        GameRoom r = new GameRoom(id);
        rooms.put(id, r);
        return r;
    }

    public GameRoom getRoom(String id) {
        return rooms.get(id);
    }

    public void broadcastRoomUpdate(GameRoom room) {
        Message m = new Message(MessageType.ROOM_UPDATE, "server", null,
                Map.of("roomId", room.roomId, "players", room.players));
        for (String p : room.players) {
            sendToUser(p, m);
        }
    }

    public void sendToUser(String username, Message m) {
        ClientHandler ch = clients.get(username);
        if (ch != null)
            ch.send(m);
    }

    // Ghi lại kết quả trận đấu
    public void recordGameResult(String roomId) {
        GameRoom r = rooms.get(roomId);
        if (r == null)
            return;

        Map<String, HandEvaluator.Result> eval = r.evaluateAll();
        List<String> ranking = r.rankPlayersDesc();

        Map<String, Object> info = new HashMap<>();
        info.put("roomId", roomId);
        info.put("ranking", ranking);
        info.put("hands", eval);
        db.recordMatch(gson.toJson(info)); // lưu vào DB

        Message m = new Message(MessageType.GAME_RESULT, "server", null, Map.of("ranking", ranking));
        for (String p : r.players)
            sendToUser(p, m);
    }
}
