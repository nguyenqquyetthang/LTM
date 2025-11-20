package server.core;

import server.managers.GameStateManager;
import server.database.Database;
import server.managers.BroadcastManager;
import server.managers.ScoreManager;
import server.managers.RoomPlayerManager;
import server.managers.GameFlowManager;
import server.managers.KickManager;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ROOM THREAD - XỬ LÝ LOGIC PHÒNG CHƠI & GAME
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Mỗi phòng có 1 RoomThread riêng xử lý:
 * - Quản lý người chơi (thêm/xóa, host, ready status)
 * - Logic game (rút bài, turn-based, timeout)
 * - Tính điểm & xếp hạng
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📡 PROTOCOL MESSAGES GỬI ĐI (Server → Client):
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * MESSAGES PHÒNG CHƠ:
 * • ROOM_UPDATE|roomName|hostIndex|player1,player2,player3,...
 * → Cập nhật danh sách người trong phòng
 * → Parse: GameScreen.java dòng 382-390
 * 
 * • READY_STATUS|user1:true|user2:false|user3:true|...
 * → Trạng thái sẵn sàng của từng người (guest only, host luôn ready)
 * → Parse: GameScreen.java dòng 406-444
 * 
 * • YOU_ARE_HOST
 * → Thông báo bạn trở thành host (khi host cũ rời)
 * → Parse: GameScreen.java dòng 369-380
 * 
 * • KICKED;reason
 * → Bạn bị host kick khỏi phòng
 * 
 * MESSAGES TRONG GAME:
 * • GAME_START;RoomName
 * → Ván bài bắt đầu, reset tất cả
 * → Parse: GameScreen.java dòng 222-253
 * 
 * • YOUR_TURN
 * → Đến lượt bạn rút bài (10 giây timeout)
 * 
 * • WAIT
 * → Chưa đến lượt, chờ người khác
 * 
 * • DRAW;K♠
 * → Bạn rút được lá bài (format: Rank+Suit)
 * → Rank: A,2-10,J,Q,K | Suit: ♠♥♦♣
 * → Parse: GameScreen.java dòng 568-579
 * 
 * • SHOW_HANDS_ALL|player1=K♠,Q♠,J♠|player2=A♥,5♦,3♣|...
 * → Lật tất cả bài của mọi người lên (khi đủ 3 lá)
 * → Parse: GameScreen.java dòng 293-339
 * 
 * • HAND_RANKS|player1:4:Straight Flush:530|player2:1:HighCard:7|...
 * → Xếp loại tay bài của từng người
 * → Category: 5=ThreeOfAKind, 4=StraightFlush, 3=Straight, 2=Flush, 1=HighCard
 * → Điểm: chỉ hiện cho HighCard (modulo 10), loại khác ẩn composite score
 * → Parse: GameScreen.java dòng 472-500
 * 
 * • WINNER player1 tay=Straight Flush
 * → Thông báo người thắng
 * → Parse: GameScreen.java dòng 278-292
 * 
 * • RANKING|player1:15:+3|player2:8:-1|player3:5:-1|...
 * → Bảng xếp hạng kết quả ván (thứ tự từ cao xuống thấp theo bài)
 * → Format: username:điểm_tổng:điểm_thay_đổi
 * → Parse: GameScreen.java dòng 518-545
 * 
 * • END;RoomName
 * → Ván kết thúc, sẵn sàng cho ván mới
 * → ⚠️ Bài KHÔNG xóa ở đây, chỉ xóa khi GAME_START
 * 
 * • ELIMINATED;Timeout - không rút trong 10s. Bạn bị trừ 1 điểm!
 * → Bạn bị loại do timeout, kick khỏi phòng
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📨 PROTOCOL MESSAGES NHẬN VÀO (Client → Server):
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Xử lý bởi ClientHandler.java:
 * • READY;true hoặc READY;false
 * → Guest bật/tắt trạng thái sẵn sàng
 * 
 * • START_GAME
 * → Host bắt đầu game (cần đủ người & tất cả ready)
 * 
 * • DRAW_CARD
 * → Người chơi rút bài (phải đúng lượt)
 * 
 * • KICK_PLAYER;targetUsername
 * → Host kick người chơi
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 🎮 LOGIC GAME:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 1. Mỗi người rút tối đa 3 lá (turn-based)
 * 2. Timeout 10 giây/lượt → -1 điểm, kick khỏi phòng
 * 3. Xếp hạng bài: ThreeOfAKind > StraightFlush > Straight > Flush > HighCard
 * 4. Điểm người thắng = (tổng số người bao gồm timeout - 1)
 * 5. Người timeout đã bị -1 ngay, không trừ thêm ở cuối
 * 6. Kết quả xếp theo bài mạnh nhất (không theo điểm tích lũy)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 🎨 CHÚ Ý CHO GIAO DIỆN:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * ⚠️ ĐÂY LÀ BẢN DEMO LOGIC - CẦN CẢI THIỆN GIAO DIỆN!
 * 
 * Logic game đã hoàn chỉnh, chỉ cần wrap UI đẹp hơn:
 * - Animation rút bài
 * - Effect lật bài
 * - Timer đếm ngược đẹp hơn
 * - Highlight người thắng với effect
 * - Sound effects (rút bài, win, lose, timeout)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RoomThread extends Thread {
    private String roomName;
    private List<ClientHandler> players = Collections.synchronizedList(new ArrayList<>());
    private Map<String, Boolean> playerReady = new HashMap<>(); // Trạng thái sẵn sàng của từng người

    // Helper classes
    private GameStateManager gameState;
    private BroadcastManager broadcastManager;
    private ScoreManager scoreManager;
    private RoomPlayerManager playerManager;
    private GameFlowManager gameFlowManager;
    private KickManager kickManager;

    public RoomThread(String name, Map<String, RoomThread> rooms, Database db) {
        this.roomName = name;

        // Initialize helpers
        this.gameState = new GameStateManager();
        this.gameState.getTurnManager().setRoomThread(this);
        this.broadcastManager = new BroadcastManager(roomName, players, playerReady);
        this.scoreManager = new ScoreManager(db);
        this.playerManager = new RoomPlayerManager(roomName, players, playerReady, broadcastManager, rooms);
        this.gameFlowManager = new GameFlowManager(roomName, players, db, gameState, broadcastManager, playerManager,
                scoreManager);
        this.kickManager = new KickManager(roomName, players, gameState, playerManager, scoreManager, broadcastManager);
    }

    public synchronized boolean isFull() {
        return playerManager.isFull();
    }

    public synchronized int getPlayerCount() {
        return playerManager.getPlayerCount();
    }

    public synchronized void addPlayer(ClientHandler p) {
        playerManager.addPlayer(p);
    }

    public synchronized void removePlayer(ClientHandler p) {
        boolean roomEmpty = playerManager.removePlayer(p, gameState);
        if (roomEmpty) {
            this.interrupt();
        }
    }

    public int getPlayerIndex(ClientHandler p) {
        return playerManager.getPlayerIndex(p);
    }

    public void run() {
        System.out.println("🧩 Phòng " + roomName + " đã sẵn sàng.");

        // Chờ thread kết thúc
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                System.out.println("🛑 [" + roomName + "] đã dừng.");
                break;
            }
        }
    }

    public synchronized void setPlayerReady(String username, boolean ready) {
        playerManager.setPlayerReady(username, ready);
    }

    public synchronized boolean allPlayersReady() {
        return playerManager.allPlayersReady();
    }

    public void startGame() {
        gameFlowManager.startGame();
    }

    // legacy shuffle removed; using Deck instead

    public synchronized void playerDrawCard(int playerID) {
        gameFlowManager.playerDrawCard(playerID);
    }

    public synchronized void drawCard(ClientHandler player) {
        gameFlowManager.drawCard(player);
    }

    // Xử lý timeout - kick người chơi (called by TurnManager)
    public synchronized void handleTimeout() {
        KickManager.TimeoutResult result = kickManager.handleTimeout();
        if (!result.shouldContinue)
            return;

        // Cập nhật lobby
        Server.broadcastPlayerList();
        Server.broadcastRoomsList();

        if (players.isEmpty() || players.size() == 1) {
            gameFlowManager.endGame();
            return;
        }

        // Tiếp tục lượt
        broadcastManager.broadcastRoomUpdate(playerManager.getHostIndex());
        gameFlowManager.nextTurn();
    }

    // Gửi ROOM_UPDATE chỉ cho 1 client (dùng khi client mới vào phòng cần snapshot)
    public synchronized void sendRoomUpdateTo(ClientHandler target) {
        broadcastManager.sendRoomUpdateTo(target, playerManager.getHostIndex());
    }

    // Kick người chơi (chỉ host mới được kick, và chỉ khi chưa chơi)
    public void kickPlayer(String targetUsername, ClientHandler requester) {
        KickManager.KickResult result = kickManager.kickPlayer(targetUsername, requester);

        switch (result.status) {
            case GAME_RUNNING:
                requester.sendMessage("KICK_BLOCKED;Không thể kick khi đang chơi"); // 📤 GỬI: "KICK_BLOCKED;..." →
                                                                                    // không thể kick
                return;
            case NOT_HOST:
                requester.sendMessage("NOT_HOST"); // 📤 GỬI: "NOT_HOST" → không phải host
                return;
            case PLAYER_NOT_FOUND:
                return;
            case CANNOT_KICK_SELF:
                requester.sendMessage("KICK_BLOCKED;Không thể kick chính mình"); // 📤 GỬI: "KICK_BLOCKED;..." → không
                                                                                 // thể kick
                return;
            case SUCCESS:
                ClientHandler targetPlayer = result.targetPlayer;
                targetPlayer.resetCurrentRoom();

                // XÓA NGƯỜI CHƠI KHỎI PHÒNG TRƯỚC khi gửi message
                removePlayer(targetPlayer);

                System.out.println("👢 [Server] Kicking " + targetUsername + "...");

                // Chỉ gửi KICKED cho người bị kick
                // LobbyScreen sẽ tự request GET_PLAYER_LIST và GET_ROOMS
                targetPlayer.sendMessage("KICKED;Bị chủ phòng kick"); // 📤 GỬI: "KICKED;reason" → bị kick, quay về
                                                                      // lobby
                System.out.println("📤 [Server] Sent KICKED to " + targetUsername);

                // Broadcast cho các client KHÁC (không gửi cho người bị kick)
                Server.broadcastPlayerList();
                Server.broadcastRoomsList();

                System.out.println("✅ [Server] " + targetUsername + " kicked by " + requester.username);
                break;
        }
    }

    // Lấy thông tin phòng để hiển thị
    public synchronized String getRoomInfo() {
        return roomName + "|" + playerManager.getPlayerCount() + "/6";
    }
}
