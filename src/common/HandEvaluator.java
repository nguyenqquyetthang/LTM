package common;

import java.util.*;
import common.Card.Suit;

/**
 * Xếp hạng bài cho 3 lá theo luật đề bài.
 * Trả về một ComparableKey: higher = stronger.
 */
public class HandEvaluator {
    public enum HandType {
        THREE_OF_A_KIND, STRAIGHT_FLUSH, STRAIGHT, FLUSH, POINT
    }

    public static class Result implements Comparable<Result> {
        public HandType type;
        public int topRank; // lá cao nhất theo quy tắc (A=14)
        public Card.Suit topSuit;
        public int point; // chỉ dùng cho POINT
        public int totalSum;
        public List<Card> cards;

        @Override
        public int compareTo(Result o) {
            if (this.type != o.type)
                return this.type.ordinal() - o.type.ordinal(); // lower ordinal = stronger? we'll reorder
            // We want stronger > weaker; we will invert ordinal usage by mapping ordinal to
            // priority
            // So better to compare by hand strength index externally; but for simplicity
            // reorder ordinal in caller.
            // For simplicity here, higher topRank wins:
            if (this.topRank != o.topRank)
                return Integer.compare(this.topRank, o.topRank);
            if (this.point != o.point)
                return Integer.compare(this.point, o.point);
            if (this.totalSum != o.totalSum)
                return Integer.compare(this.totalSum, o.totalSum);
            // suit order for tie-break: ♠ < ♣ < ♦ < ♥. We'll map to ints:
            return Integer.compare(suitOrder(this.topSuit), suitOrder(o.topSuit));
        }

        private int suitOrder(Suit s) {
            return switch (s) {
                case SPADES -> 0;
                case CLUBS -> 1;
                case DIAMONDS -> 2;
                default -> 3;
            };
        }
    }

    // Higher strength ordering index: 5=best ... 1=worst
    private static int typePriority(HandType t) {
        return switch (t) {
            case THREE_OF_A_KIND -> 5;
            case STRAIGHT_FLUSH -> 4;
            case STRAIGHT -> 3;
            case FLUSH -> 2;
            default -> 1;
        };
    }

    public static Result evaluate(List<Card> cards) {
        List<Card> c = new ArrayList<>(cards);
        c.sort((a, b) -> Integer.compare(a.rank, b.rank));
        boolean sameSuit = c.stream().allMatch(x -> x.suit == c.get(0).suit);
        boolean threeSameValue = c.get(0).rank == c.get(1).rank && c.get(1).rank == c.get(2).rank;
        boolean straight = isStraight(c);

        Result res = new Result();
        res.cards = c;
        res.totalSum = c.get(0).rank + c.get(1).rank + c.get(2).rank;

        if (threeSameValue) {
            res.type = HandType.THREE_OF_A_KIND;
            res.topRank = c.get(2).rank;
            res.topSuit = c.get(2).suit;
        } else if (straight && sameSuit) {
            res.type = HandType.STRAIGHT_FLUSH;
            res.topRank = topOfStraight(c);
            res.topSuit = c.get(2).suit; // highest card suit
        } else if (straight) {
            res.type = HandType.STRAIGHT;
            res.topRank = topOfStraight(c);
            res.topSuit = c.get(2).suit;
        } else if (sameSuit) {
            res.type = HandType.FLUSH;
            res.topRank = c.get(2).rank;
            res.topSuit = c.get(2).suit;
        } else {
            res.type = HandType.POINT;
            res.point = res.totalSum % 10;
            res.topRank = c.get(2).rank;
            res.topSuit = c.get(2).suit;
        }
        return res;
    }

    private static boolean isStraight(List<Card> c) {
        // Handle A-2-3 and Q-K-A
        List<Integer> r = Arrays.asList(c.get(0).rank, c.get(1).rank, c.get(2).rank);
        // normal consecutive
        if (r.get(0) + 1 == r.get(1) && r.get(1) + 1 == r.get(2))
            return true;
        // A-2-3 case: ranks 2,3,14 sorted -> 2,3,14 won't satisfy; we detect specially
        if (r.get(0) == 2 && r.get(1) == 3 && r.get(2) == 14)
            return true; // A as low
        // Q-K-A: ranks 12,13,14
        if (r.get(0) == 12 && r.get(1) == 13 && r.get(2) == 14)
            return true;
        return false;
    }

    private static int topOfStraight(List<Card> c) {
        // If A-2-3 => top is 3.
        if (c.get(0).rank == 2 && c.get(1).rank == 3 && c.get(2).rank == 14)
            return 3;
        return c.get(2).rank; // sorted ascending
    }

    // Compare two Results: return >0 if a stronger than b
    public static int compare(Result a, Result b) {
        if (a == null || b == null)
            return (a == null) ? -1 : 1;
        int pa = typePriority(a.type);
        int pb = typePriority(b.type);
        if (pa != pb)
            return Integer.compare(pa, pb);
        // Same hand type: use rules
        if (pa == 5) { // three of a kind: higher value wins
            if (a.topRank != b.topRank)
                return Integer.compare(a.topRank, b.topRank);
        } else if (pa == 4) { // straight flush: compare topRank, then suit: ♠ < ♣ < ♦ < ♥
            if (a.topRank != b.topRank)
                return Integer.compare(a.topRank, b.topRank);
            return Integer.compare(suitOrder(a.topSuit), suitOrder(b.topSuit));
        } else if (pa == 3) { // straight: topRank then suit of top card but suit order reversed (♥ > ♦ > ♣ >
                              // ♠)
            if (a.topRank != b.topRank)
                return Integer.compare(a.topRank, b.topRank);
            return Integer.compare(straightSuitOrder(a.topSuit), straightSuitOrder(b.topSuit));
        } else if (pa == 2) { // flush: compare suit (♠ < ♣ < ♦ < ♥), if same suit compare topRank
            if (suitOrder(a.topSuit) != suitOrder(b.topSuit))
                return Integer.compare(suitOrder(a.topSuit), suitOrder(b.topSuit));
            if (a.topRank != b.topRank)
                return Integer.compare(a.topRank, b.topRank);
        } else { // point
            if (a.point != b.point)
                return Integer.compare(a.point, b.point);
            if (a.totalSum != b.totalSum)
                return Integer.compare(a.totalSum, b.totalSum);
            if (a.topRank != b.topRank)
                return Integer.compare(a.topRank, b.topRank);
            return Integer.compare(straightSuitOrder(a.topSuit), straightSuitOrder(b.topSuit));
        }
        return 0;
    }

    private static int suitOrder(Suit s) { // ♠ < ♣ < ♦ < ♥
        return switch (s) {
            case SPADES -> 0;
            case CLUBS -> 1;
            case DIAMONDS -> 2;
            default -> 3;
        };
    }

    private static int straightSuitOrder(Suit s) { // for straight and point tie: ♥ > ♦ > ♣ > ♠
        return switch (s) {
            case HEARTS -> 4;
            case DIAMONDS -> 3;
            case CLUBS -> 2;
            default -> 1;
        };
    }
}