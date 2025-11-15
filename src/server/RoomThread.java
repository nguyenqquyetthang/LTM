package server;

import java.util.*;

public class RoomThread extends Thread {
    private static final int MAX_PLAYERS = 6; // Giới hạn tối đa 6 người
    private String roomName;
    private List<ClientHandler> players = Collections.synchronizedList(new ArrayList<>());
    private Map<String, RoomThread> rooms;

    // Turn-based system
    private int currentTurn = 0; // Index của người chơi hiện tại
    private int hostIndex = 0; // Index của chủ phòng
    // private Map<Integer, Integer> playerDrawnCount = new HashMap<>(); // legacy
    private Timer turnTimer;
    private boolean gameStarted = false;

    // New round-based state
    private Deck deck; // Bộ bài mới
    private Map<String, Hand> playerHands = new HashMap<>(); // Bài của từng người
    private Map<String, Integer> drawCounts = new HashMap<>(); // Mỗi người tối đa 3 lần rút

    public RoomThread(String name, Map<String, RoomThread> rooms) {
        this.roomName = name;
        this.rooms = rooms;
    }

    public synchronized boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public synchronized int getPlayerCount() {
        return players.size();
    }

    public synchronized void addPlayer(ClientHandler p) {
        if (players.size() >= MAX_PLAYERS) {
            p.sendMessage("ROOM_FULL");
            return;
        }
        players.add(p);
        p.setStatus("busy");
        broadcastRoomUpdate();
    }

    public synchronized void removePlayer(ClientHandler p) {
        int removedIndex = players.indexOf(p);
        players.remove(p);
        p.setStatus("free");

        // Nếu người bị remove là host, chọn host mới
        if (removedIndex == hostIndex && !players.isEmpty()) {
            hostIndex = 0; // Host mới là người đầu tiên
            players.get(0).sendMessage("YOU_ARE_HOST");
        }

        // Cập nhật currentTurn nếu cần
        if (gameStarted && !players.isEmpty()) {
            if (currentTurn >= players.size()) {
                currentTurn = 0;
            }
            notifyCurrentTurn();
        }

        // LUÔN broadcast ROOM_UPDATE khi có người rời phòng
        if (!players.isEmpty()) {
            broadcastRoomUpdate();
        }

        if (players.isEmpty()) {
            if (turnTimer != null) {
                turnTimer.cancel();
            }
            rooms.remove(roomName);
            this.interrupt();
            // Sau khi phòng bị xóa (trống) -> broadcast danh sách phòng
            Server.broadcastRoomsList();
        } else {
            // Có người rời nhưng phòng vẫn còn người -> cập nhật danh sách phòng
            Server.broadcastRoomsList();
        }
    }

    public int getPlayerIndex(ClientHandler p) {
        return players.indexOf(p);
    }

    public void run() {
        System.out.println("🧩 Phòng " + roomName + " đã sẵn sàng.");
    }

    public void startGame() {
        if (players.size() < 2) {
            broadcast("SYSTEM Chưa đủ người chơi để bắt đầu!");
            return;
        }
        gameStarted = true;
        deck = new Deck();
        deck.shuffle();
        // Không chia lá ban đầu: mọi người bắt đầu với 0 lá
        playerHands.clear();
        drawCounts.clear();
        synchronized (players) {
            for (ClientHandler c : players) {
                c.setStatus("playing");
                drawCounts.put(c.username, 0);
            }
        }
        // Thông báo bắt đầu ván; UI sẽ reset từ READY
        broadcast("READY;" + roomName);
        broadcast("SYSTEM Ván bài bắt đầu! Rút theo lượt, mỗi người tối đa 3 lá.");
        currentTurn = hostIndex; // Chủ phòng đi trước
        broadcastRoomUpdate();
        notifyCurrentTurn();
        startTurnTimer();
        System.out.println("🎮 " + roomName + " bắt đầu, không chia bài ban đầu.");
    }

    // legacy shuffle removed; using Deck instead

    public synchronized void playerDrawCard(int playerID) {
        // Giữ method cũ (không dùng nữa) để tương thích nếu còn tham chiếu
        if (!gameStarted)
            return;
        if (playerID != currentTurn) {
            if (playerID >= 0 && playerID < players.size())
                players.get(playerID).sendMessage("NOT_YOUR_TURN");
            return;
        }
        // Chuyển sang phương thức mới dựa trên ClientHandler
        drawCard(players.get(playerID));
    }

    // Rút bài theo lượt mới: mỗi người rút thêm tối đa 3 lá để đạt 6 lá tổng
    public synchronized void drawCard(ClientHandler player) {
        if (!gameStarted || players.isEmpty())
            return;
        int idx = players.indexOf(player);
        if (idx != currentTurn) {
            player.sendMessage("NOT_YOUR_TURN");
            return;
        }
        // Tạo tay bài nếu chưa có
        Hand hand = playerHands.computeIfAbsent(player.username, k -> new Hand());
        int cnt = drawCounts.getOrDefault(player.username, 0);
        if (cnt >= 3) {
            player.sendMessage("SYSTEM Bạn đã rút đủ 3 lá!");
            nextTurn();
            return;
        }
        Card drawn = deck.drawCard();
        if (drawn == null) {
            player.sendMessage("SYSTEM Hết bài!");
            nextTurn();
            return;
        }
        hand.addCard(drawn);
        drawCounts.put(player.username, cnt + 1);
        // Gửi lá rút cho người chơi đó
        player.sendMessage("DRAW;" + drawn.toString());
        System.out.println("🂠 " + player.username + " rút: " + drawn + " (" + (cnt + 1) + "/3)");
        // Mỗi lượt chỉ rút 1 lá -> chuyển lượt (ngược chiều kim đồng hồ)
        nextTurn();
    }

    private void broadcast(String msg) {
        for (ClientHandler p : players)
            p.sendMessage(msg);
    }

    // Chuyển sang lượt tiếp theo (ngược chiều kim đồng hồ)
    private synchronized void nextTurn() {
        if (players.isEmpty()) {
            endGame();
            return;
        }
        int tried = 0;
        do {
            currentTurn--; // ngược chiều kim đồng hồ
            if (currentTurn < 0)
                currentTurn = players.size() - 1;
            tried++;
            // kết thúc nếu đã thử qua tất cả người chơi
            if (tried > players.size()) {
                endGame();
                return;
            }
        } while (drawCounts.getOrDefault(players.get(currentTurn).username, 0) >= 3);

        notifyCurrentTurn();
        startTurnTimer();
    }

    // Thông báo lượt hiện tại
    private synchronized void notifyCurrentTurn() {
        if (players.isEmpty() || !gameStarted)
            return;

        for (int i = 0; i < players.size(); i++) {
            if (i == currentTurn) {
                players.get(i).sendMessage("YOUR_TURN");
            } else {
                players.get(i).sendMessage("WAIT");
            }
        }
    }

    // Bắt đầu timer 10s
    private synchronized void startTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null; // Fix memory leak
        }

        turnTimer = new Timer();
        turnTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                handleTimeout();
            }
        }, 10000); // 10 giây
    }

    // Xử lý timeout - kick người chơi
    private synchronized void handleTimeout() {
        if (!gameStarted || players.isEmpty())
            return;
        ClientHandler timedOut = players.get(currentTurn);
        System.out.println("⏰ Timeout! Loại: " + timedOut.username);
        // Thông báo bị loại
        timedOut.sendMessage("ELIMINATED;Timeout - không rút trong 10s");
        // Loại khỏi phòng
        players.remove(currentTurn);
        playerHands.remove(timedOut.username);
        drawCounts.remove(timedOut.username);
        timedOut.setStatus("free");
        timedOut.resetCurrentRoom();
        // Cập nhật host nếu cần
        if (currentTurn == hostIndex && !players.isEmpty()) {
            hostIndex = 0;
            players.get(0).sendMessage("YOU_ARE_HOST");
        } else if (hostIndex > currentTurn) {
            hostIndex--;
        }
        // Điều chỉnh currentTurn
        if (currentTurn >= players.size())
            currentTurn = players.size() - 1;

        // Cập nhật lobby
        Server.broadcastPlayerList();
        Server.broadcastRoomsList();

        if (players.isEmpty()) {
            endGame();
            return;
        }
        // Nếu chỉ còn 1 người => kết thúc ngay
        if (players.size() == 1) {
            endGame();
            return;
        }
        // Tiếp tục lượt (ngược chiều)
        broadcastRoomUpdate();
        nextTurn();
    }

    // Broadcast danh sách người chơi
    private synchronized void broadcastRoomUpdate() {
        StringBuilder sb = new StringBuilder("ROOM_UPDATE|");
        sb.append(roomName).append("|");
        sb.append(hostIndex).append("|");
        for (int i = 0; i < players.size(); i++) {
            sb.append(players.get(i).username);
            if (i < players.size() - 1) {
                sb.append(",");
            }
        }
        broadcast(sb.toString());
    }

    // Gửi ROOM_UPDATE chỉ cho 1 client (dùng khi client mới vào phòng cần snapshot)
    public synchronized void sendRoomUpdateTo(ClientHandler target) {
        StringBuilder sb = new StringBuilder("ROOM_UPDATE|");
        sb.append(roomName).append("|");
        sb.append(hostIndex).append("|");
        for (int i = 0; i < players.size(); i++) {
            sb.append(players.get(i).username);
            if (i < players.size() - 1) {
                sb.append(",");
            }
        }
        target.sendMessage(sb.toString());
    }

    // Kết thúc game
    private synchronized void endGame() {
        gameStarted = false;
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
        // Tính điểm
        Map<String, HandRank> ranks = new HashMap<>();
        for (ClientHandler p : players) {
            Hand h = playerHands.get(p.username);
            if (h != null) {
                ranks.put(p.username, h.getRank());
            }
        }
        // Broadcast toàn bộ bài của tất cả người chơi
        // Hiển thị bài của TẤT CẢ người chơi còn lại để client vẽ lên 3 ô của từng vị
        // trí
        StringBuilder showAll = new StringBuilder("SHOW_HANDS_ALL|");
        for (ClientHandler p : players) {
            Hand h = playerHands.get(p.username);
            if (h != null) {
                showAll.append(p.username).append("=").append(h.toShortString()).append("|");
            } else {
                showAll.append(p.username).append("=").append("").append("|");
            }
        }
        broadcast(showAll.toString());

        // Công bố người thắng (trong những người còn lại)
        String winner = null;
        HandRank best = null;
        for (Map.Entry<String, HandRank> e : ranks.entrySet()) {
            if (winner == null || e.getValue().compareTo(best) > 0) {
                winner = e.getKey();
                best = e.getValue();
            }
        }

        // Cập nhật điểm số
        int numPlayers = ranks.size();
        if (winner != null && numPlayers > 1) {
            int winnerPoints = numPlayers - 1; // Người thắng +n-1

            // Khởi tạo điểm cho người chưa có
            for (String user : ranks.keySet()) {
                Server.playerScores.putIfAbsent(user, 0);
            }

            // Cập nhật điểm
            Server.playerScores.put(winner, Server.playerScores.get(winner) + winnerPoints);
            for (String user : ranks.keySet()) {
                if (!user.equals(winner)) {
                    Server.playerScores.put(user, Server.playerScores.get(user) - 1);
                }
            }
        }

        if (winner != null)
            broadcast("WINNER " + winner + " với bài " + best);

        // Gửi bảng xếp hạng (chỉ trong phòng này)
        StringBuilder ranking = new StringBuilder("RANKING|");
        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>();
        for (String user : ranks.keySet()) {
            sortedScores.add(new AbstractMap.SimpleEntry<>(user, Server.playerScores.getOrDefault(user, 0)));
        }
        sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue())); // Sắp xếp giảm dần

        for (int i = 0; i < sortedScores.size(); i++) {
            Map.Entry<String, Integer> entry = sortedScores.get(i);
            ranking.append((i + 1)).append(". ").append(entry.getKey())
                    .append(": ").append(entry.getValue()).append(" điểm|");
        }
        broadcast(ranking.toString());

        broadcast("END;" + roomName);
        synchronized (players) {
            for (ClientHandler c : players)
                c.setStatus("busy");
        }
        System.out.println("🏁 Vòng rút bài kết thúc trong " + roomName);
    }

    // Kick người chơi (chỉ host mới được kick)
    public void kickPlayer(String targetUsername, ClientHandler requester) {
        // Tìm target player trong synchronized block
        ClientHandler targetPlayer = null;
        boolean isHost = false;

        synchronized (this) {
            int requesterIndex = players.indexOf(requester);
            if (requesterIndex != hostIndex) {
                requester.sendMessage("NOT_HOST");
                return;
            }

            isHost = true;
            for (ClientHandler player : players) {
                if (player.username.equals(targetUsername)) {
                    targetPlayer = player;
                    break;
                }
            }
        }

        // Thực hiện external calls bên ngoài synchronized block để tránh deadlock
        if (isHost && targetPlayer != null) {
            targetPlayer.resetCurrentRoom();
            targetPlayer.sendMessage("KICKED;Bị chủ phòng kick");
            removePlayer(targetPlayer);
            System.out.println("👢 " + targetUsername + " bị kick bởi " + requester.username);

            // Broadcast danh sách người chơi online để cập nhật cho lobby
            Server.broadcastPlayerList();
            // Broadcast danh sách phòng để cập nhật số người chơi
            Server.broadcastRoomsList();
        }
    }

    // Lấy thông tin phòng để hiển thị
    public synchronized String getRoomInfo() {
        return roomName + "|" + players.size() + "/" + MAX_PLAYERS;
    }
}