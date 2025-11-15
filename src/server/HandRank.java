package server;

// Đơn giản: so sánh theo tổng điểm rồi đến số lá
public class HandRank implements Comparable<HandRank> {
    private final int score;
    private final int size;

    public HandRank(int score, int size) {
        this.score = score;
        this.size = size;
    }

    @Override
    public int compareTo(HandRank o) {
        if (this.score != o.score)
            return Integer.compare(this.score, o.score);
        return Integer.compare(this.size, o.size);
    }

    @Override
    public String toString() {
        return "Score=" + score + ",Cards=" + size;
    }
}
