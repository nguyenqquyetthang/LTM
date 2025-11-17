package server.game;

import server.models.Card;
import server.core.ClientHandler;
import server.models.Deck;
import server.models.Hand;
import server.models.HandRank;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * GAME LOGIC - XỬ LÝ LOGIC RÚT BÀI, TÍNH ĐIỂM, XẾP HẠNG
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Khởi tạo & quản lý bộ bài (Deck)
 * - Rút bài cho người chơi
 * - Tính toán tay bài & xếp hạng
 * - Xác định người thắng
 * - Tính điểm thắng/thua
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class GameLogic {
    private Deck deck; // Bộ bài hiện tại
    private Map<String, Hand> playerHands = new HashMap<>(); // Bài của từng người
    private Map<String, Integer> drawCounts = new HashMap<>(); // Số lần rút của mỗi người

    /**
     * Khởi tạo ván mới: tạo bộ bài mới & xáo bài
     * 📤 GỬI: Không gửi gì
     * 📨 NHẬN: Không nhận gì
     */
    public void initializeNewRound(List<ClientHandler> players) {
        deck = new Deck();
        deck.shuffle();
        playerHands.clear();
        drawCounts.clear();

        // Khởi tạo drawCount cho mỗi người
        for (ClientHandler p : players) {
            drawCounts.put(p.username, 0);
        }

        System.out.println("🎮 Khởi tạo ván mới, đã xáo bài");
    }

    /**
     * Rút bài cho người chơi
     * 📤 GỬI: DRAW;K♠ (lá bài rút được)
     * 📨 NHẬN: DRAW_CARD (từ ClientHandler)
     * 
     * @return Card rút được, hoặc null nếu không rút được
     */
    public Card drawCardForPlayer(String username) {
        // Kiểm tra đã rút đủ chưa
        int cnt = drawCounts.getOrDefault(username, 0);
        if (cnt >= 3) {
            return null; // Đã rút đủ 3 lá
        }

        // Rút bài từ deck
        Card drawn = deck.drawCard();
        if (drawn == null) {
            return null; // Hết bài
        }

        // Thêm vào tay bài
        Hand hand = playerHands.computeIfAbsent(username, k -> new Hand());
        hand.addCard(drawn);
        drawCounts.put(username, cnt + 1);

        System.out.println("🂠 " + username + " rút: " + drawn + " (" + (cnt + 1) + "/3)");
        return drawn;
    }

    /**
     * Kiểm tra người chơi đã rút đủ bài chưa
     */
    public boolean hasDrawnMax(String username) {
        return drawCounts.getOrDefault(username, 0) >= 3;
    }

    /**
     * Lấy số lần đã rút của người chơi
     */
    public int getDrawCount(String username) {
        return drawCounts.getOrDefault(username, 0);
    }

    /**
     * Lấy tay bài của người chơi
     */
    public Hand getPlayerHand(String username) {
        return playerHands.get(username);
    }

    /**
     * Tính toán xếp hạng tất cả người chơi
     * 📤 GỬI: HAND_RANKS|user1:category:categoryName:score|...
     * 📨 NHẬN: Không nhận gì
     * 
     * @return Map username -> HandRank
     */
    public Map<String, HandRank> calculateAllRanks() {
        Map<String, HandRank> ranks = new HashMap<>();
        for (Map.Entry<String, Hand> entry : playerHands.entrySet()) {
            String username = entry.getKey();
            Hand hand = entry.getValue();
            HandRank rank = hand.getRank();
            ranks.put(username, rank);
        }
        return ranks;
    }

    /**
     * Tính điểm modulo cho HighCard (tổng % 10)
     * A=1, J=11, Q=12, K=13, còn lại là số
     */
    public Map<String, Integer> calculateModScores(Map<String, HandRank> ranks) {
        Map<String, Integer> modScores = new HashMap<>();
        for (Map.Entry<String, HandRank> entry : ranks.entrySet()) {
            String username = entry.getKey();
            HandRank rank = entry.getValue();
            if (rank.getCategory() == 1) { // HighCard
                Hand hand = playerHands.get(username);
                if (hand != null) {
                    modScores.put(username, computeModScore(hand));
                }
            }
        }
        return modScores;
    }

    /**
     * Tính tổng điểm 3 lá % 10
     */
    private int computeModScore(Hand h) {
        int sum = 0;
        for (Card c : h.getCards()) {
            String r = c.getRank();
            int val;
            switch (r) {
                case "A":
                    val = 1;
                    break;
                case "J":
                    val = 11;
                    break;
                case "Q":
                    val = 12;
                    break;
                case "K":
                    val = 13;
                    break;
                default:
                    try {
                        val = Integer.parseInt(r);
                    } catch (NumberFormatException ex) {
                        val = 0;
                    }
            }
            sum += val;
        }
        return sum % 10; // 0..9
    }

    /**
     * Xác định người thắng
     * 📤 GỬI: WINNER player1 tay=Straight Flush
     * 📨 NHẬN: Không nhận gì
     * 
     * @return [winner_username, winner_rank, winner_mod_score]
     */
    public WinnerResult determineWinner(Map<String, HandRank> ranks, Map<String, Integer> modScores) {
        String winner = null;
        HandRank winnerRank = null;
        int winnerModScore = -1;

        for (Map.Entry<String, HandRank> entry : ranks.entrySet()) {
            String user = entry.getKey();
            HandRank hr = entry.getValue();
            int ms = hr.getCategory() == 1 ? modScores.getOrDefault(user, -1) : -1;

            if (winner == null) {
                winner = user;
                winnerRank = hr;
                winnerModScore = ms;
                continue;
            }

            // So sánh category trước
            if (hr.getCategory() > winnerRank.getCategory()) {
                winner = user;
                winnerRank = hr;
                winnerModScore = ms;
            } else if (hr.getCategory() == winnerRank.getCategory()) {
                if (hr.getCategory() == 1) { // HighCard
                    if (ms > winnerModScore || (ms == winnerModScore && hr.compareTo(winnerRank) > 0)) {
                        winner = user;
                        winnerRank = hr;
                        winnerModScore = ms;
                    }
                } else { // Special hand tie-break
                    if (hr.compareTo(winnerRank) > 0) {
                        winner = user;
                        winnerRank = hr;
                        winnerModScore = ms;
                    }
                }
            }
        }

        return new WinnerResult(winner, winnerRank, winnerModScore);
    }

    /**
     * Sắp xếp người chơi theo thứ hạng tay bài (winner đầu tiên)
     */
    public List<String> sortPlayersByRank(Map<String, HandRank> ranks, Map<String, Integer> modScores) {
        List<String> sortedPlayers = new ArrayList<>(ranks.keySet());
        sortedPlayers.sort((u1, u2) -> {
            HandRank hr1 = ranks.get(u1);
            HandRank hr2 = ranks.get(u2);

            // So sánh category trước
            if (hr1.getCategory() != hr2.getCategory()) {
                return hr2.getCategory() - hr1.getCategory(); // Category cao hơn lên trước
            }

            // Cùng category
            if (hr1.getCategory() == 1) { // HighCard - so modulo
                int mod1 = modScores.getOrDefault(u1, 0);
                int mod2 = modScores.getOrDefault(u2, 0);
                if (mod1 != mod2) {
                    return mod2 - mod1; // Modulo cao hơn lên trước
                }
                return hr2.compareTo(hr1); // Tie-break
            } else { // Special hand
                return hr2.compareTo(hr1); // compareTo cao hơn lên trước
            }
        });
        return sortedPlayers;
    }

    /**
     * Tạo message SHOW_HANDS_ALL để hiển thị tất cả bài
     * 📤 GỬI: SHOW_HANDS_ALL|player1=K♠,Q♠,J♠|player2=A♥,5♦,3♣|...
     */
    public String buildShowHandsMessage(List<ClientHandler> players) {
        StringBuilder sb = new StringBuilder("SHOW_HANDS_ALL|");
        for (ClientHandler p : players) {
            Hand h = playerHands.get(p.username);
            if (h != null) {
                sb.append(p.username).append("=").append(h.toShortString()).append("|");
            } else {
                sb.append(p.username).append("=").append("").append("|");
            }
        }
        return sb.toString();
    }

    /**
     * Tạo message HAND_RANKS
     * 📤 GỬI: HAND_RANKS|player1:4:Straight Flush:530|player2:1:HighCard:7|...
     */
    public String buildHandRanksMessage(Map<String, HandRank> ranks, Map<String, Integer> modScores) {
        StringBuilder sb = new StringBuilder("HAND_RANKS|");
        for (Map.Entry<String, HandRank> entry : ranks.entrySet()) {
            String user = entry.getKey();
            HandRank hr = entry.getValue();
            int displayScore = (hr.getCategory() == 1) ? modScores.getOrDefault(user, 0) : hr.toCompositeScore();
            String categoryName = hr.getCategoryName();
            sb.append(user).append(":").append(hr.getCategory())
                    .append(":").append(categoryName).append(":").append(displayScore).append("|");
        }
        return sb.toString();
    }

    /**
     * Reset state để chuẩn bị ván mới
     */
    public void reset() {
        deck = null;
        playerHands.clear();
        drawCounts.clear();
    }

    /**
     * Inner class để trả về kết quả winner
     */
    public static class WinnerResult {
        public final String username;
        public final HandRank rank;
        public final int modScore;

        public WinnerResult(String username, HandRank rank, int modScore) {
            this.username = username;
            this.rank = rank;
            this.modScore = modScore;
        }
    }
}
