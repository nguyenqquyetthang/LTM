package server;

import java.util.*;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    public Deck() {
        String[] suits = { "♠", "♥", "♦", "♣" };
        String[] ranks = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" };
        for (String s : suits) {
            for (String r : ranks) {
                cards.add(new Card(s, r));
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public synchronized Card drawCard() {
        if (cards.isEmpty())
            return null;
        return cards.remove(0);
    }
}
