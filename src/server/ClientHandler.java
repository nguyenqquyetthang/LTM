package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import com.google.gson.*;
import common.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerMain server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private final Gson gson = new Gson();

    // ========== THÊM PHẦN QUẢN LÝ TRẠNG THÁI ==========
    private static final Map<String, Long> lastInviteTime = new ConcurrentHashMap<>();
    private static final Map<String, String> pendingInvites = new ConcurrentHashMap<>();
    private static final Set<String> busyPlayers = ConcurrentHashMap.newKeySet();

    public ClientHandler(Socket socket, ServerMain server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                Message msg = gson.fromJson(line, Message.class);
                handleMessage(msg);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + username);
        } finally {
            cleanup();
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.type) {
            // Đăng nhập
            case LOGIN -> {
                String user = (String) msg.data.get("username");
                if (user == null || user.isBlank()) {
                    send(new Message(MessageType.LOGIN_FAIL, "server", null, Map.of("reason", "invalid")));
                    return;
                }
                this.username = user;
                server.registerClient(user, this);
                send(new Message(MessageType.LOGIN_OK, "server", user, Map.of()));
                server.broadcastOnlineList();
            }

            // ======= GỬI LỜI MỜI =======
            case INVITE -> {
                String to = (String) msg.data.get("to");
                ClientHandler target = server.getHandler(to);
                long now = System.currentTimeMillis();

                if (target == null) {
                    send(new Message(MessageType.INFO, "server", username, Map.of("text", to + " không online.")));
                    return;
                }

                // kiểm tra cooldown 10s
                if (lastInviteTime.containsKey(username)) {
                    long last = lastInviteTime.get(username);
                    if (now - last < 10_000) {
                        send(new Message(MessageType.INFO, "server", username,
                                Map.of("text", "Bạn phải chờ 10 giây trước khi mời lại.")));
                        return;
                    }
                }

                // kiểm tra bận
                if (busyPlayers.contains(username)) {
                    send(new Message(MessageType.INFO, "server", username,
                            Map.of("text", "Bạn đang có lời mời đang chờ phản hồi.")));
                    return;
                }
                if (busyPlayers.contains(to)) {
                    send(new Message(MessageType.INFO, "server", username,
                            Map.of("text", to + " đang bận, không thể mời.")));
                    return;
                }

                // gửi lời mời
                pendingInvites.put(to, username);
                busyPlayers.add(username);
                busyPlayers.add(to);
                lastInviteTime.put(username, now);

                target.send(new Message(MessageType.INVITE, username, to, Map.of("from", username)));
                send(new Message(MessageType.INFO, "server", username,
                        Map.of("text", "Đã gửi lời mời đến " + to + ". Hết hạn sau 15 giây.")));

                // Hết hạn sau 15 giây
                new Thread(() -> {
                    try {
                        Thread.sleep(15_000);
                        if (pendingInvites.containsKey(to) && pendingInvites.get(to).equals(username)) {
                            pendingInvites.remove(to);
                            busyPlayers.remove(username);
                            busyPlayers.remove(to);
                            send(new Message(MessageType.INFO, "server", username,
                                    Map.of("text", to + " không phản hồi trong 15 giây.")));
                        }
                    } catch (InterruptedException ignored) {
                    }
                }).start();
            }

            // ======= PHẢN HỒI LỜI MỜI =======
            case INVITE_RESPONSE -> {
                String resp = (String) msg.data.get("response"); // "OK" hoặc "NO"
                String inviter = (String) msg.data.get("to");

                if (!pendingInvites.containsKey(username) || !pendingInvites.get(username).equals(inviter)) {
                    send(new Message(MessageType.INFO, "server", username,
                            Map.of("text", "Không có lời mời hợp lệ từ " + inviter)));
                    return;
                }

                pendingInvites.remove(username);

                if ("OK".equalsIgnoreCase(resp)) {
                    // tạo phòng
                    GameRoom room = server.createRoom();
                    room.addPlayer(inviter);
                    room.addPlayer(username);

                    server.sendToUser(inviter,
                            new Message(MessageType.JOIN_ROOM, "server", inviter, Map.of("roomId", room.roomId)));
                    server.sendToUser(username,
                            new Message(MessageType.JOIN_ROOM, "server", username, Map.of("roomId", room.roomId)));

                    server.broadcastRoomUpdate(room);
                } else {
                    // từ chối
                    server.sendToUser(inviter,
                            new Message(MessageType.INFO, "server", inviter,
                                    Map.of("text", username + " đã từ chối lời mời.")));
                    lastInviteTime.put(inviter, System.currentTimeMillis());
                }

                // mở khóa 2 người
                busyPlayers.remove(inviter);
                busyPlayers.remove(username);
            }

            // ======= BẮT ĐẦU GAME =======
            case START_GAME -> {
                String roomId = (String) msg.data.get("roomId");
                GameRoom room = server.getRoom(roomId);
                if (room != null) {
                    room.startGame();
                    for (String p : room.players) {
                        List<Card> cards = room.hands.get(p);
                        server.sendToUser(p, new Message(MessageType.DEAL_CARDS, "server", p, Map.of("cards", cards)));
                    }
                    server.broadcastRoomUpdate(room);
                }
            }

            case LEAVE -> {
                String roomId = (String) msg.data.get("roomId");
                GameRoom r = server.getRoom(roomId);
                if (r != null) {
                    r.removePlayer(username);
                    server.broadcastRoomUpdate(r);
                }
            }

            default -> {
                // có thể mở rộng thêm
            }
        }
    }

    public void send(Message m) {
        String s = gson.toJson(m);
        out.println(s);
    }

    private void cleanup() {
        try {
            if (username != null)
                server.unregisterClient(username);
            socket.close();
        } catch (IOException e) {
        } finally {
            busyPlayers.remove(username);
            pendingInvites.remove(username);
        }
    }
}
