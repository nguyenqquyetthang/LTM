package server;

import java.util.*;

public class RoomThread extends Thread {
    private String roomName;
    private List<ClientHandler> players = Collections.synchronizedList(new ArrayList<>());
    private int[] cards = new int[52];
    private int drawCount = 0;
    private int totalDraws = 0;
    private Map<String, RoomThread> rooms;

    public RoomThread(String name, Map<String, RoomThread> rooms) {
        this.roomName = name;
        this.rooms = rooms;
        for (int i = 0; i < 52; i++)
            cards[i] = i;
    }

    public synchronized void addPlayer(ClientHandler p) {
        players.add(p);
    }

    public synchronized void removePlayer(ClientHandler p) {
        players.remove(p);
        if (players.isEmpty()) {
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
        shuffleCards();
        totalDraws = players.size() * 3;
        drawCount = 0;
        broadcast("READY;" + roomName);
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
        if (drawCount >= totalDraws)
            return;

        for (int i = 0; i < 52; i++) {
            if (cards[i] != -1) {
                int cardValue = cards[i];
                cards[i] = -1;
                players.get(playerID).sendMessage("DRAW;" + cardValue);
                drawCount++;
                System.out.println("🂠 Player " + playerID + " rút bài: " + cardValue);
                break;
            }
        }

        if (drawCount >= totalDraws) {
            broadcast("END;" + roomName);
            System.out.println("🏁 Vòng rút bài kết thúc trong " + roomName);
        }
    }

    private void broadcast(String msg) {
        for (ClientHandler p : players)
            p.sendMessage(msg);
    }
}