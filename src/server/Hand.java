package server;

import java.util.*;

public class Hand {
    private final List<Card> cards = new ArrayList<>();

    public Hand(Card... initial) {
        cards.addAll(Arrays.asList(initial));
    }

    public void addCard(Card c) {
        if (c != null)
            cards.add(c);
    }

    public int getCardCount() {
        return cards.size();
    }

    public List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            sb.append(cards.get(i).toString());
            if (i < cards.size() - 1)
                sb.append(",");
        }
        return sb.toString();
    }

    public HandRank getRank() {
        return HandEvaluator.evaluate(cards);
    }
}
