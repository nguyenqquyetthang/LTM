package common;

import java.util.*;
import common.Card.Suit;

public class Deck {
    private final List<Card> cards = new ArrayList<>();
    private final Random rnd = new Random();

    public Deck() {
        cards.clear();
        for (Suit s : Suit.values()) {
            for (int r = 2; r <= 14; r++)
                cards.add(new Card(r, s));
        }
    }

    public void shuffle() {
        Collections.shuffle(cards, rnd);
    }

    public Card draw() {
        if (cards.isEmpty())
            return null;
        return cards.remove(0);
    }

    public int remaining() {
        return cards.size();
    }
}
