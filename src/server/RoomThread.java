package server;

import java.util.*;

public class RoomThread extends Thread {
    private String roomName;
    private List<ClientHandler> players = Collections.synchronizedList(new ArrayList<>());
    private int[] cards = new int[52];
    private int drawCount = 0;
    private int totalDraws = 0;
    private Map<String, RoomThread> rooms;

    // Turn-based system
    private int currentTurn = 0; // Index của người chơi hiện tại
    private int hostIndex = 0; // Index của chủ phòng
    private Map<Integer, Integer> playerDrawnCount = new HashMap<>(); // Số lá đã rút của mỗi người
    private Timer turnTimer;
    private boolean gameStarted = false;

    public RoomThread(String name, Map<String, RoomThread> rooms) {
        this.roomName = name;
        this.rooms = rooms;
        for (int i = 0; i < 52; i++)
            cards[i] = i;
    }

    public synchronized void addPlayer(ClientHandler p) {
        players.add(p);
        broadcastRoomUpdate();
    }

    public synchronized void removePlayer(ClientHandler p) {
        int removedIndex = players.indexOf(p);
        players.remove(p);

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
            broadcastRoomUpdate();
            notifyCurrentTurn();
        }

        if (players.isEmpty()) {
            if (turnTimer != null) {
                turnTimer.cancel();
            }
            rooms.remove(roomName);
            this.interrupt();
        }
    }

    public int getPlayerIndex(ClientHandler p) {
        return players.indexOf(p);
    }

    public void run() {
        System.out.println("🧩 Phòng " + roomName + " đã sẵn sàng.");
    }

    public void startGame() {
        gameStarted = true;
        shuffleCards();
        totalDraws = players.size() * 3;
        drawCount = 0;
        currentTurn = hostIndex; // Chủ phòng đi trước

        // Reset số lá đã rút
        playerDrawnCount.clear();
        for (int i = 0; i < players.size(); i++) {
            playerDrawnCount.put(i, 0);
        }

        broadcast("READY;" + roomName);
        broadcastRoomUpdate();
        notifyCurrentTurn();
        startTurnTimer();
        System.out.println("🎮 " + roomName + " bắt đầu, bài đã được tráo!");
    }

    private void shuffleCards() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 52; i++)
            list.add(i);
        Collections.shuffle(list);
        for (int i = 0; i < 52; i++)
            cards[i] = list.get(i);
    }

    public synchronized void playerDrawCard(int playerID) {
        if (!gameStarted || drawCount >= totalDraws) {
            return;
        }

        // Kiểm tra có phải lượt của người này không
        if (playerID != currentTurn) {
            players.get(playerID).sendMessage("NOT_YOUR_TURN");
            return;
        }

        // Kiểm tra người này đã rút đủ 3 lá chưa
        int drawn = playerDrawnCount.getOrDefault(playerID, 0);
        if (drawn >= 3) {
            // Người này đã đủ 3 lá rồi, chuyển lượt
            nextTurn();
            return;
        }

        // Tìm bài chưa rút
        for (int i = 0; i < 52; i++) {
            if (cards[i] != -1) {
                int cardValue = cards[i];
                cards[i] = -1;
                players.get(playerID).sendMessage("DRAW;" + cardValue);
                drawCount++;
                playerDrawnCount.put(playerID, drawn + 1);
                // thêm để xem rút đén lượt bn
                System.out.println("🂠 Player " + playerID + " (" + players.get(playerID).username + ") rút bài: "
                        + cardValue + " (" + (drawn + 1) + "/3)");

                // SAU MỖI LẦN RÚT 1 LÁ → CHUYỂN LƯỢT NGAY
                nextTurn();
                break;
            }
        }

        if (drawCount >= totalDraws) {
            endGame();
        }
    }

    private void broadcast(String msg) {
        for (ClientHandler p : players)
            p.sendMessage(msg);
    }

    // Chuyển sang lượt tiếp theo (ngược chiều kim đồng hồ)
    private synchronized void nextTurn() {
        if (players.isEmpty())
            return;

        // Tìm người tiếp theo chưa rút đủ 3 lá
        int attempts = 0;
        do {
            currentTurn--;
            if (currentTurn < 0) {
                currentTurn = players.size() - 1;
            }
            attempts++;
            if (attempts > players.size()) {
                // Tất cả đã rút đủ
                endGame();
                return;
            }
        } while (playerDrawnCount.getOrDefault(currentTurn, 0) >= 3);

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

        System.out.println("⏰ Timeout! Kick player " + currentTurn + " (" + players.get(currentTurn).username + ")");

        ClientHandler kickedPlayer = players.get(currentTurn);
        boolean wasHost = (currentTurn == hostIndex);

        // Gửi thông báo bị kick
        kickedPlayer.sendMessage("KICKED;Timeout - không rút bài trong 10s");

        // Remove player
        players.remove(currentTurn);
        playerDrawnCount.remove(currentTurn);

        // Cập nhật map và index
        Map<Integer, Integer> newMap = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            Integer oldDrawn = playerDrawnCount.get(i >= currentTurn ? i + 1 : i);
            newMap.put(i, oldDrawn != null ? oldDrawn : 0);
        }
        playerDrawnCount = newMap;

        // Cập nhật host nếu cần
        if (wasHost && !players.isEmpty()) {
            hostIndex = 0;
            players.get(0).sendMessage("YOU_ARE_HOST");
        } else if (hostIndex > currentTurn) {
            hostIndex--;
        }

        // Cập nhật currentTurn
        if (!players.isEmpty()) {
            if (currentTurn >= players.size()) {
                currentTurn = players.size() - 1;
            }
            broadcastRoomUpdate();
            notifyCurrentTurn();
            startTurnTimer();
        } else {
            endGame();
        }
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

    // Kết thúc game
    private synchronized void endGame() {
        gameStarted = false;
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
        broadcast("END;" + roomName);
        System.out.println("🏁 Vòng rút bài kết thúc trong " + roomName);
    }
}