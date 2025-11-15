package server;

import java.util.*;

public class HandEvaluator {
    // Đánh giá đơn giản: tổng điểm các rank + bonus cho số lá (demo)
    // 2..10 = giá trị số, J=11,Q=12,K=13,A=14
    public static HandRank evaluate(List<Card> cards) {
        int sum = 0;
        for (Card c : cards) {
            sum += rankValue(c.getRank());
        }
        return new HandRank(sum, cards.size());
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
}
