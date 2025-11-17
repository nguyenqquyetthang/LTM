package server.managers;

import server.models.Card;
import server.models.Hand;
import server.core.Server;
import server.core.ClientHandler;
import server.database.Database;
import server.game.GameLogic;
import server.models.HandRank;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * GAME FLOW MANAGER - QUẢN LÝ FLOW GAME
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Bắt đầu game
 * - Rút bài theo lượt
 * - Chuyển lượt
 * - Kết thúc game và tính điểm
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class GameFlowManager {
    private String roomName;
    private List<ClientHandler> players;
    private Database db;

    // Dependencies
    private GameStateManager gameState;
    private BroadcastManager broadcastManager;
    private RoomPlayerManager playerManager;
    private ScoreManager scoreManager;

    public GameFlowManager(String roomName, List<ClientHandler> players, Database db,
            GameStateManager gameState, BroadcastManager broadcastManager,
            RoomPlayerManager playerManager, ScoreManager scoreManager) {
        this.roomName = roomName;
        this.players = players;
        this.db = db;
        this.gameState = gameState;
        this.broadcastManager = broadcastManager;
        this.playerManager = playerManager;
        this.scoreManager = scoreManager;
    }

    /**
     * Bắt đầu game
     */
    public synchronized void startGame() {
        if (!playerManager.hasEnoughPlayers()) {
            broadcastManager.broadcast("SYSTEM Chưa đủ người chơi để bắt đầu!");
            return;
        }
        if (!playerManager.allPlayersReady()) {
            broadcastManager.broadcast("SYSTEM Chưa đủ người sẵn sàng!");
            return;
        }

        gameState.startGame();

        // Khởi tạo game logic & turn manager
        gameState.getGameLogic().initializeNewRound(players);
        gameState.getTurnManager().initializeTurn(playerManager.getHostIndex());

        playerManager.resetAllReady();
        playerManager.setAllPlayersPlaying();

        // Thông báo bắt đầu ván
        broadcastManager.broadcast("GAME_START;" + roomName);
        broadcastManager.broadcast("SYSTEM Ván bài bắt đầu! Rút theo lượt, mỗi người tối đa 3 lá.");
        broadcastManager.broadcastRoomUpdate(playerManager.getHostIndex());
        gameState.getTurnManager().notifyCurrentTurn(players);
        gameState.getTurnManager().startTurnTimer();

        // Tạo mới bản ghi Matches
        Integer matchId = db != null ? db.createMatch(players.size()) : null;
        gameState.setMatchId(matchId);
        System.out.println("🎮 " + roomName + " bắt đầu, MatchID=" + matchId + ", không chia bài ban đầu.");
    }

    /**
     * Rút bài (legacy method - giữ để tương thích)
     */
    public synchronized void playerDrawCard(int playerID) {
        if (!gameState.isGameStarted())
            return;

        int currentTurn = gameState.getTurnManager().getCurrentTurn();
        if (playerID != currentTurn) {
            if (playerID >= 0 && playerID < players.size())
                players.get(playerID).sendMessage("NOT_YOUR_TURN");
            return;
        }
        drawCard(players.get(playerID));
    }

    /**
     * Rút bài theo lượt
     */
    public synchronized void drawCard(ClientHandler player) {
        if (!gameState.isGameStarted() || players.isEmpty())
            return;

        int idx = players.indexOf(player);
        int currentTurn = gameState.getTurnManager().getCurrentTurn();
        if (idx != currentTurn) {
            player.sendMessage("NOT_YOUR_TURN");
            return;
        }

        // Kiểm tra đã rút đủ chưa
        if (gameState.getGameLogic().hasDrawnMax(player.username)) {
            player.sendMessage("SYSTEM Bạn đã rút đủ 3 lá!");
            nextTurn();
            return;
        }

        // Rút bài
        Card drawn = gameState.getGameLogic().drawCardForPlayer(player.username);
        if (drawn == null) {
            player.sendMessage("SYSTEM Hết bài!");
            nextTurn();
            return;
        }

        // Gửi lá rút cho người chơi
        int cnt = gameState.getGameLogic().getDrawCount(player.username);
        player.sendMessage("DRAW;" + drawn.toString());
        System.out.println("🂠 " + player.username + " rút: " + drawn + " (" + cnt + "/3)");

        nextTurn();
    }

    /**
     * Chuyển sang lượt tiếp theo
     */
    public synchronized void nextTurn() {
        if (players.isEmpty()) {
            endGame();
            return;
        }

        boolean hasNextTurn = gameState.getTurnManager().nextTurn(players);
        if (!hasNextTurn) {
            endGame();
            return;
        }

        gameState.getTurnManager().notifyCurrentTurn(players);
        gameState.getTurnManager().startTurnTimer();
    }

    /**
     * Kết thúc game
     */
    public synchronized void endGame() {
        gameState.endGame();

        // Trường hợp đặc biệt: Chỉ còn 1 người
        List<String> timeoutPlayers = gameState.getTurnManager().getTimeoutPlayers();
        if (players.size() == 1 && timeoutPlayers.size() > 0) {
            handleSinglePlayerWin(timeoutPlayers);
            return;
        }

        // Thu thập tay bài & xếp hạng
        Map<String, HandRank> ranks = gameState.getGameLogic().calculateAllRanks();
        Map<String, Integer> modScores = gameState.getGameLogic().calculateModScores(ranks);

        // Broadcast toàn bộ bài
        String showAllMsg = gameState.getGameLogic().buildShowHandsMessage(players);
        broadcastManager.broadcast(showAllMsg);

        // Xác định người thắng
        GameLogic.WinnerResult winnerResult = gameState.getGameLogic().determineWinner(ranks, modScores);
        String winner = winnerResult.username;
        HandRank winnerRank = winnerResult.rank;
        int winnerModScore = winnerResult.modScore;

        // Cập nhật điểm số
        updateScoresAndBroadcast(ranks, modScores, winner, winnerRank, winnerModScore, timeoutPlayers);

        // Lưu vào database
        saveMatchResults(ranks, modScores, winner);

        // Reset cho ván mới
        broadcastManager.broadcast("END;" + roomName);
        playerManager.setAllPlayersBusy();
        gameState.getGameLogic().reset();
        broadcastManager.broadcastReadyStatus();
        Server.broadcastPlayerList();

        System.out.println("🏁 Vòng rút bài kết thúc trong " + roomName + ", sẵn sàng cho ván mới.");
    }

    /**
     * Xử lý trường hợp chỉ còn 1 người thắng
     */
    private void handleSinglePlayerWin(List<String> timeoutPlayers) {
        ClientHandler lastPlayer = players.get(0);
        String winner = lastPlayer.username;
        int totalParticipants = 1 + timeoutPlayers.size();
        int winnerPoints = totalParticipants - 1;

        scoreManager.updateScores(winner, new ArrayList<>(), timeoutPlayers);

        broadcastManager.broadcast("WINNER " + winner + " - Chiến thắng do đối thủ timeout!");
        broadcastManager
                .broadcast("RANKING|" + winner + ":" + Server.playerScores.get(winner) + ":+" + winnerPoints + "|");
        broadcastManager.broadcast("END;" + roomName);

        lastPlayer.setStatus("busy");
        Map<String, Boolean> playerReady = new HashMap<>();
        playerReady.put(winner, false);
        gameState.getGameLogic().reset();
        broadcastManager.broadcastReadyStatus();
        Server.broadcastPlayerList();

        System.out.println("🏁 " + winner + " thắng do đối thủ timeout, nhận +" + winnerPoints + " điểm.");
    }

    /**
     * Cập nhật điểm và broadcast kết quả
     */
    private void updateScoresAndBroadcast(Map<String, HandRank> ranks, Map<String, Integer> modScores,
            String winner, HandRank winnerRank, int winnerModScore,
            List<String> timeoutPlayers) {
        int numPlayers = ranks.size();
        int totalParticipants = numPlayers + timeoutPlayers.size();

        if (winner != null && totalParticipants > 1) {
            List<String> losers = new ArrayList<>(ranks.keySet());
            losers.remove(winner);
            scoreManager.updateScores(winner, losers, timeoutPlayers);
        }

        // Gửi thông tin chi tiết về tay bài
        String handRanksMsg = gameState.getGameLogic().buildHandRanksMessage(ranks, modScores);
        broadcastManager.broadcast(handRanksMsg);

        if (winner != null) {
            if (winnerRank.getCategory() == 1) {
                broadcastManager.broadcast("WINNER " + winner + " tay=HighCard điểm=" + winnerModScore);
            } else {
                broadcastManager.broadcast("WINNER " + winner + " tay=" + winnerRank.getCategoryName());
            }
        }

        // Gửi bảng xếp hạng
        List<String> sortedPlayers = gameState.getGameLogic().sortPlayersByRank(ranks, modScores);
        Map<String, Integer> scoreChanges = new HashMap<>();
        for (String user : sortedPlayers) {
            int change = user.equals(winner) ? (totalParticipants - 1) : -1;
            scoreChanges.put(user, change);
        }

        String rankingMsg = scoreManager.buildRankingMessage(sortedPlayers, scoreChanges);
        broadcastManager.broadcast(rankingMsg);
    }

    /**
     * Lưu kết quả vào database
     */
    private void saveMatchResults(Map<String, HandRank> ranks, Map<String, Integer> modScores, String winner) {
        Integer matchId = gameState.getMatchId();
        if (db == null || matchId == null)
            return;

        List<String> sortedPlayers = gameState.getGameLogic().sortPlayersByRank(ranks, modScores);
        for (int i = 0; i < sortedPlayers.size(); i++) {
            String user = sortedPlayers.get(i);
            Hand h = gameState.getGameLogic().getPlayerHand(user);
            HandRank r = ranks.get(user);
            Integer pid = db.getPlayerId(user);

            if (pid != null && h != null && r != null) {
                int scoreVal = (r.getCategory() == 1) ? modScores.getOrDefault(user, 0) : r.toCompositeScore();
                db.insertMatchResult(matchId, pid, i + 1, scoreVal, r.getCategoryName(), h.toShortString());
            }
        }

        Integer winPid = winner != null ? db.getPlayerId(winner) : null;
        db.endMatch(matchId, winPid);
    }
}
