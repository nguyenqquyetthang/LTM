package server.database;




import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * DATABASE - FACADE CLASS CHO TẤT CẢ DATABASE OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này là facade (giao diện) cho tất cả operations database.
 * Nội bộ sử dụng:
 * - DatabaseConnection: Quản lý kết nối
 * - PlayerRepository: Operations với Players table
 * - MatchRepository: Operations với Matches & MatchResults tables
 * 
 * Schema:
 * - Players(PlayerID, Username, PasswordHash, TotalPoints)
 * - Matches(MatchID, StartTime, EndTime, TotalPlayers, WinnerID)
 * - MatchResults(ResultID, MatchID, PlayerID, RankPosition, Score, HandType,
 * Cards)
 * - Cards(CardID, Rank, Suit)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class Database {
    private DatabaseConnection dbConnection;
    private PlayerRepository playerRepo;
    private MatchRepository matchRepo;

    public Database() {
        this.dbConnection = new DatabaseConnection();
        this.playerRepo = new PlayerRepository(dbConnection);
        this.matchRepo = new MatchRepository(dbConnection);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER OPERATIONS - Delegate to PlayerRepository
    // ═══════════════════════════════════════════════════════════════════

    public Integer getPlayerId(String username) {
        return playerRepo.getPlayerId(username);
    }

    public boolean authenticate(String username, String passwordHash) {
        return playerRepo.authenticate(username, passwordHash);
    }

    public Integer createPlayer(String username, String passwordHash) {
        return playerRepo.createPlayer(username, passwordHash);
    }

    public void updateTotalPoints(int playerId, int delta) {
        playerRepo.updateTotalPoints(playerId, delta);
    }

    public Integer getTotalPoints(String username) {
        return playerRepo.getTotalPoints(username);
    }

    public Map<String, String> loadAccounts() {
        return playerRepo.loadAccounts();
    }

    // ═══════════════════════════════════════════════════════════════════
    // MATCH OPERATIONS - Delegate to MatchRepository
    // ═══════════════════════════════════════════════════════════════════

    public Integer createMatch(int totalPlayers) {
        return matchRepo.createMatch(totalPlayers);
    }

    public void endMatch(int matchId, Integer winnerPlayerId) {
        matchRepo.endMatch(matchId, winnerPlayerId);
    }

    public void insertMatchResult(int matchId, int playerId, int rankPosition,
            int score, String handType, String cardsText) {
        matchRepo.insertMatchResult(matchId, playerId, rankPosition, score, handType, cardsText);
    }

    public String getMatchHistory(int limit) {
        return matchRepo.getMatchHistory(limit);
    }

    public String getDetailedMatchHistory(int limit) {
        return matchRepo.getDetailedMatchHistory(limit);
    }

    public String getMatchDetail(int matchId) {
        return matchRepo.getMatchDetail(matchId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INITIALIZATION - Delegate to DatabaseConnection
    // ═══════════════════════════════════════════════════════════════════

    public void ensureCardsSeeded() {
        dbConnection.ensureCardsSeeded();
    }
}
