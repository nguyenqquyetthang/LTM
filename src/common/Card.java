package common;

public class Card {
    public enum Suit {
        SPADES, CLUBS, DIAMONDS, HEARTS
    } // ♠ < ♣ < ♦ < ♥

    public int rank; // 2..14 (11=J,12=Q,13=K,14=A)
    public Suit suit;

    public Card(int rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    @Override
    public String toString() {
        String r;
        if (rank == 14)
            r = "A";
        else if (rank == 13)
            r = "K";
        else if (rank == 12)
            r = "Q";
        else if (rank == 11)
            r = "J";
        else
            r = String.valueOf(rank);
        String s = switch (suit) {
            case SPADES -> "♠";
            case CLUBS -> "♣";
            case DIAMONDS -> "♦";
            default -> "♥";
        };
        return r + s;
    }
}
