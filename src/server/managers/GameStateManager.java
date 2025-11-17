package server.managers;

import server.game.GameLogic;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * GAME STATE MANAGER - QUẢN LÝ TRẠNG THÁI GAME
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này quản lý:
 * - Trạng thái game (bắt đầu/kết thúc)
 * - Match ID trong database
 * - Trạng thái ready của players
 * - Delegate game logic (deck, hands, draw counts) → GameLogic
 * - Delegate turn logic (current turn, timer, timeout) → TurnManager
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class GameStateManager {
    private boolean gameStarted = false;
    private Integer matchId; // ID ván đấu trong DB

    // Delegates
    private GameLogic gameLogic;
    private TurnManager turnManager;

    public GameStateManager() {
        this.gameLogic = new GameLogic();
        this.turnManager = new TurnManager(gameLogic);
    }

    // ═══════════════════════════════════════════════════════════════
    // GAME STATE CONTROL
    // ═══════════════════════════════════════════════════════════════

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void startGame() {
        gameStarted = true;
    }

    public void endGame() {
        gameStarted = false;
        turnManager.cancelTimer();
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE TO GameLogic
    // ═══════════════════════════════════════════════════════════════

    public GameLogic getGameLogic() {
        return gameLogic;
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATE TO TurnManager
    // ═══════════════════════════════════════════════════════════════

    public TurnManager getTurnManager() {
        return turnManager;
    }

    // ═══════════════════════════════════════════════════════════════
    // RESET
    // ═══════════════════════════════════════════════════════════════

    public void reset() {
        gameStarted = false;
        matchId = null;
        gameLogic.reset();
        turnManager.reset();
    }
}
