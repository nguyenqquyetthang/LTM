package server.models;



// Lá bài với chất và hạng hiển thị (ví dụ: A♠)
public class Card {
    private final String suit; // ♠ ♥ ♦ ♣
    private final String rank; // 2–10, J, Q, K, A

    public Card(String suit, String rank) {
        this.suit = suit;
        this.rank = rank;
    }

    // them setter cho từng thuộc tính
    public String getSuit() {
        return suit;
    }

    public String getRank() {
        return rank;
    }

    @Override
    public String toString() {
        return rank + suit;
    }
}
