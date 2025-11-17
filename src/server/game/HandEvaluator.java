package server.game;

import server.models.Card;
import server.models.HandRank;
import java.util.*;

public class HandEvaluator {
    /**
     * Evaluate a 3-card hand into tiers:
     * 5 Three of a Kind
     * 4 Straight Flush
     * 3 Straight
     * 2 Flush
     * 1 High Card (Points)
     */
    public static HandRank evaluate(List<Card> cards) {
        if (cards == null || cards.size() == 0) {
            return new HandRank(1, "HighCard", 0, Collections.emptyList());
        }
        // Map rank string -> value 2..14
        List<Integer> ranks = new ArrayList<>();
        List<Integer> suits = new ArrayList<>();
        for (Card c : cards) {
            ranks.add(rankValue(c.getRank()));
            suits.add(suitValue(c.getSuit()));
        }
        Collections.sort(ranks); // ascending

        boolean isFlush = allEqual(suits);
        boolean isThreeKind = allEqual(ranks);
        boolean isStraight = isSequential(ranks);

        // THREE OF A KIND
        if (isThreeKind && cards.size() == 3) {
            int val = ranks.get(0); // all same
            return new HandRank(5, "ThreeKind", val, Collections.emptyList());
        }
        // STRAIGHT FLUSH
        if (isStraight && isFlush) {
            int top = highestInStraight(ranks);
            return new HandRank(4, "StraightFlush", top, Collections.emptyList());
        }
        // STRAIGHT
        if (isStraight) {
            int top = highestInStraight(ranks);
            return new HandRank(3, "Straight", top, Collections.emptyList());
        }
        // FLUSH
        if (isFlush) {
            List<Integer> desc = new ArrayList<>(ranks);
            Collections.sort(desc, Collections.reverseOrder());
            return new HandRank(2, "Flush", desc.get(0), desc.subList(1, desc.size()));
        }
        // HIGH CARD
        List<Integer> desc = new ArrayList<>(ranks);
        Collections.sort(desc, Collections.reverseOrder());
        return new HandRank(1, "HighCard", desc.get(0), desc.subList(1, desc.size()));
    }

    private static boolean allEqual(List<Integer> list) {
        for (int i = 1; i < list.size(); i++) {
            if (!list.get(i).equals(list.get(0)))
                return false;
        }
        return list.size() > 0;
    }

    private static boolean isSequential(List<Integer> ranksSortedAsc) {
        if (ranksSortedAsc.size() != 3)
            return false;
        // Normal straight
        if (ranksSortedAsc.get(1) == ranksSortedAsc.get(0) + 1 && ranksSortedAsc.get(2) == ranksSortedAsc.get(1) + 1)
            return true;
        // Handle A-2-3 (treat Ace as 1) -> ranks could be [2,3,14]
        return ranksSortedAsc.get(0) == 2 && ranksSortedAsc.get(1) == 3 && ranksSortedAsc.get(2) == 14; // A-2-3
    }

    private static int highestInStraight(List<Integer> ranksSortedAsc) {
        // For A-2-3 straight, highest should be 3 not Ace
        if (ranksSortedAsc.get(0) == 2 && ranksSortedAsc.get(1) == 3 && ranksSortedAsc.get(2) == 14)
            return 3;
        return ranksSortedAsc.get(ranksSortedAsc.size() - 1);
    }

    private static int rankValue(String r) {
        switch (r) {
            case "J":
                return 11;
            case "Q":
                return 12;
            case "K":
                return 13;
            case "A":
                return 14;
            default:
                try {
                    return Integer.parseInt(r);
                } catch (NumberFormatException e) {
                    return 0;
                }
        }
    }

    private static int suitValue(String s) {
        // Map suits to 0..3 deterministic
        switch (s) {
            case "♠":
                return 0;
            case "♥":
                return 1;
            case "♦":
                return 2;
            case "♣":
                return 3;
            default:
                return -1;
        }
    }
}
