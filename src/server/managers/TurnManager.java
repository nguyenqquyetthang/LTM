package server.managers;

import server.core.RoomThread;
import server.game.GameLogic;
import server.core.ClientHandler;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * TURN MANAGER - QUẢN LÝ LƯỢT CHƠI & TIMEOUT
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Quản lý lượt hiện tại (ngược chiều kim đồng hồ)
 * - Timer 10 giây cho mỗi lượt
 * - Xử lý timeout (kick người chơi, trừ điểm)
 * - Chuyển lượt
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class TurnManager {
    private int currentTurn = 0; // Index của người chơi hiện tại
    private Timer turnTimer;
    private List<String> timeoutPlayers = new ArrayList<>(); // Danh sách người bị timeout trong ván này
    private GameLogic gameLogic;
    private RoomThread roomThread; // Reference để callback

    public TurnManager(GameLogic gameLogic) {
        this.gameLogic = gameLogic;
    }

    public void setRoomThread(RoomThread roomThread) {
        this.roomThread = roomThread;
    }

    /**
     * Khởi tạo lượt đầu tiên (host đi trước)
     */
    public void initializeTurn(int hostIndex) {
        this.currentTurn = hostIndex;
        this.timeoutPlayers.clear();
    }

    /**
     * Lấy index lượt hiện tại
     */
    public int getCurrentTurn() {
        return currentTurn;
    }

    /**
     * Lấy danh sách người timeout trong ván này
     */
    public List<String> getTimeoutPlayers() {
        return timeoutPlayers;
    }

    /**
     * Thông báo lượt hiện tại cho tất cả người chơi
     * 📤 GỬI: "YOUR_TURN" (cho người được rút) hoặc "WAIT" (cho người khác)
     * 📨 CLIENT NHẬN: GameScreen.java dòng 268-284
     */
    public void notifyCurrentTurn(List<ClientHandler> players) {
        if (players.isEmpty())
            return;

        for (int i = 0; i < players.size(); i++) {
            if (i == currentTurn) {
                players.get(i).sendMessage("YOUR_TURN"); // 📤 GỬI: "YOUR_TURN" → đến lượt bạn rút bài
            } else {
                players.get(i).sendMessage("WAIT"); // 📤 GỬI: "WAIT" → chờ lượt
            }
        }
    }

    /**
     * Chuyển sang lượt tiếp theo (ngược chiều kim đồng hồ)
     * 📤 GỬI: YOUR_TURN, WAIT
     * 📨 NHẬN: Không nhận gì
     * 
     * @return true nếu còn lượt tiếp, false nếu hết (tất cả đã rút đủ)
     */
    public boolean nextTurn(List<ClientHandler> players) {
        if (players.isEmpty()) {
            return false;
        }

        int tried = 0;
        do {
            currentTurn--; // ngược chiều kim đồng hồ
            if (currentTurn < 0) {
                currentTurn = players.size() - 1;
            }
            tried++;

            // Nếu đã thử qua tất cả người chơi mà không ai còn lượt
            if (tried > players.size()) {
                return false; // Kết thúc game
            }
        } while (gameLogic.hasDrawnMax(players.get(currentTurn).username));

        return true;
    }

    /**
     * Bắt đầu timer 10 giây cho lượt hiện tại
     * 📤 GỬI: Không gửi (chỉ setup timer)
     * 📨 NHẬN: Không nhận gì
     */
    public void startTurnTimer() {
        // Hủy timer cũ nếu có
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }

        turnTimer = new Timer();
        turnTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (roomThread != null) {
                    roomThread.handleTimeout();
                }
            }
        }, 10000); // 10 giây
    }

    /**
     * Hủy timer (dùng khi game kết thúc)
     */
    public void cancelTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }

    /**
     * Xử lý timeout - cập nhật state
     * 📤 GỬI: ELIMINATED;Timeout...
     * 📨 NHẬN: Không nhận gì
     * 
     * @return Username của người bị timeout
     */
    public String handleTimeoutPlayer(List<ClientHandler> players, int hostIndex) {
        if (players.isEmpty())
            return null;

        ClientHandler timedOut = players.get(currentTurn);
        String username = timedOut.username;

        System.out.println("⏰ Timeout! Loại: " + username);

        // Thêm vào danh sách timeout
        timeoutPlayers.add(username);

        // Trả về username để RoomThread xử lý tiếp
        return username;
    }

    /**
     * Cập nhật currentTurn sau khi xóa người chơi
     */
    public void adjustTurnAfterRemoval(int removedIndex, int newSize) {
        if (currentTurn >= newSize && newSize > 0) {
            currentTurn = newSize - 1;
        }
    }

    /**
     * Reset state cho ván mới
     */
    public void reset() {
        currentTurn = 0;
        timeoutPlayers.clear();
        cancelTimer();
    }
}
