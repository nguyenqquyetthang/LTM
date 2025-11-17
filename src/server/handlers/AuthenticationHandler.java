package server.handlers;




import server.database.Database;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * AUTHENTICATION HANDLER - XỬ LÝ ĐĂNG NHẬP
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Xác thực username/password
 * - Tự động tạo tài khoản mới nếu chưa tồn tại
 * - Load điểm số từ database
 * - Cập nhật cache accounts
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class AuthenticationHandler {
    private Database db;
    private Map<String, String> accountsCache;

    public AuthenticationHandler(Database db, Map<String, String> accountsCache) {
        this.db = db;
        this.accountsCache = accountsCache;
    }

    /**
     * Xử lý đăng nhập
     * 📨 NHẬN: LOGIN;username;password
     * 📤 GỬI: LOGIN_OK hoặc LOGIN_FAIL
     * 
     * LOGIC:
     * 1. Thử authenticate với username/password
     * 2. Nếu fail → kiểm tra username có tồn tại chưa
     * 3. Nếu chưa tồn tại → tạo tài khoản mới
     * 4. Load điểm số từ database
     * 
     * @return LoginResult với thông tin đăng nhập
     */
    public LoginResult handleLogin(String username, String password) {
        boolean authenticated = db.authenticate(username, password);

        if (!authenticated) {
            // Kiểm tra user có tồn tại chưa
            Integer existingId = db.getPlayerId(username);
            if (existingId == null) {
                // Chưa tồn tại → tạo mới
                Integer newId = db.createPlayer(username, password);
                if (newId != null) {
                    accountsCache.put(username, password); // Cập nhật cache
                    authenticated = true;
                    System.out.println("🆕 Tạo tài khoản mới: " + username);
                }
            }
            // Nếu đã tồn tại nhưng sai mật khẩu → vẫn thất bại
        }

        if (authenticated) {
            // Load điểm số từ database
            Integer pts = db.getTotalPoints(username);
            int points = pts == null ? 0 : pts;

            return new LoginResult(true, username, points);
        }

        return new LoginResult(false, null, 0);
    }

    /**
     * Inner class chứa kết quả đăng nhập
     */
    public static class LoginResult {
        public final boolean success;
        public final String username;
        public final int points;

        public LoginResult(boolean success, String username, int points) {
            this.success = success;
            this.username = username;
            this.points = points;
        }
    }
}
