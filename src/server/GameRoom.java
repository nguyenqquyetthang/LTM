package server;

import java.util.*;
import common.*;
import common.HandEvaluator.*;

public class GameRoom {
    public final String roomId;
    public final List<String> players = new ArrayList<>(); // usernames
    public final Map<String, List<Card>> hands = new HashMap<>();
    public Deck deck;
    public boolean started = false;

    public GameRoom(String roomId) {
        this.roomId = roomId;
    }

    public synchronized boolean addPlayer(String username) {
        if (players.size() >= 17)
            return false;
        if (!players.contains(username))
            players.add(username);
        return true;
    }

    public synchronized void removePlayer(String username) {
        players.remove(username);
        hands.remove(username);
    }

    public synchronized void startGame() {
        this.deck = new Deck();
        deck.shuffle();
        hands.clear();
        // each player draw 3 cards
        for (String p : players) {
            List<Card> h = new ArrayList<>();
            for (int i = 0; i < 3; i++)
                h.add(deck.draw());
            hands.put(p, h);
        }
        started = true;
    }

    public synchronized Map<String, Result> evaluateAll() {
        Map<String, Result> map = new HashMap<>();
        for (String p : players) {
            List<Card> h = hands.get(p);
            Result r = HandEvaluator.evaluate(h);
            map.put(p, r);
        }
        return map;
    }

    public List<String> rankPlayersDesc() {
        Map<String, Result> m = evaluateAll();
        List<String> list = new ArrayList<>(players);
        list.sort((a, b) -> -HandEvaluator.compare(m.get(a), m.get(b))); // descending
        return list;
    }
}
