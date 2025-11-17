package server.database;



/**
 * ═══════════════════════════════════════════════════════════════════════════
 * DATABASE HELPER - XỬ LÝ CÁC THAO TÁC DATABASE
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này đóng gói các thao tác database thường dùng:
 * - Authentication & Player management
 * - Score updates
 * - Match history queries
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class DatabaseHelper {
    private Database db;

    public DatabaseHelper(Database db) {
        this.db = db;
    }

    /**
     * Xác thực hoặc tạo tài khoản mới
     * 
     * @return [success, isNewAccount, userId]
     */
    public LoginResult authenticateOrCreate(String username, String password) {
        boolean authenticated = db.authenticate(username, password);

        if (authenticated) {
            Integer userId = db.getPlayerId(username);
            return new LoginResult(true, false, userId);
        }

        // Kiểm tra user có tồn tại chưa
        Integer existingId = db.getPlayerId(username);
        if (existingId == null) { // Chưa tồn tại -> tạo mới
            Integer newId = db.createPlayer(username, password);
            if (newId != null) {
                return new LoginResult(true, true, newId);
            }
        }

        // Sai mật khẩu hoặc lỗi tạo tài khoản
        return new LoginResult(false, false, null);
    }

    /**
     * Lấy điểm của người chơi
     */
    public int getPlayerScore(String username) {
        Integer pts = db.getTotalPoints(username);
        return pts == null ? 0 : pts;
    }

    /**
     * Cập nhật điểm cho người chơi
     */
    public void updatePlayerScore(String username, int points) {
        Integer playerId = db.getPlayerId(username);
        if (playerId != null) {
            db.updateTotalPoints(playerId, points);
        }
    }

    /**
     * Inner class kết quả đăng nhập
     */
    public static class LoginResult {
        public final boolean success;
        public final boolean isNewAccount;
        public final Integer userId;

        public LoginResult(boolean success, boolean isNewAccount, Integer userId) {
            this.success = success;
            this.isNewAccount = isNewAccount;
            this.userId = userId;
        }
    }
}
