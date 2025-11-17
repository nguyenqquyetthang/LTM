package server.models;




import java.util.*;

/**
 * Represents a ranked 3-card hand with tiered categories.
 * Category precedence (high to low):
 * 5 = Three of a Kind
 * 4 = Straight Flush
 * 3 = Straight
 * 2 = Flush
 * 1 = High Card (Points)
 */
public class HandRank implements Comparable<HandRank> {
    private final int category; // 1..5
    private final String categoryName; // human readable
    private final int primaryValue; // main tie-break (e.g. rank of trips, top of straight)
    private final List<Integer> tieBreakers; // remaining ranks for comparison desc

    public HandRank(int category, String categoryName, int primaryValue, List<Integer> tieBreakers) {
        this.category = category;
        this.categoryName = categoryName;
        this.primaryValue = primaryValue;
        this.tieBreakers = tieBreakers == null ? Collections.emptyList() : tieBreakers;
    }

    public int getCategory() {
        return category;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public int getPrimaryValue() {
        return primaryValue;
    }

    public List<Integer> getTieBreakers() {
        return tieBreakers;
    }

    // Composite score useful for DB numeric storage
    public int toCompositeScore() {
        int base = category * 1_000_000 + primaryValue * 10_000;
        int mult = 100;
        int acc = 0;
        for (int v : tieBreakers) {
            acc = acc * mult + v;
        }
        return base + acc;
    }

    @Override
    public int compareTo(HandRank o) {
        if (this.category != o.category)
            return Integer.compare(this.category, o.category);
        if (this.primaryValue != o.primaryValue)
            return Integer.compare(this.primaryValue, o.primaryValue);
        int size = Math.min(this.tieBreakers.size(), o.tieBreakers.size());
        for (int i = 0; i < size; i++) {
            int a = this.tieBreakers.get(i);
            int b = o.tieBreakers.get(i);
            if (a != b)
                return Integer.compare(a, b);
        }
        return Integer.compare(this.tieBreakers.size(), o.tieBreakers.size());
    }

    @Override
    public String toString() {
        return categoryName + "(cat=" + category + ",primary=" + primaryValue + ",tb=" + tieBreakers + ")";
    }
}
