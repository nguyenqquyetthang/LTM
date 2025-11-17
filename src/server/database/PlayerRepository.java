package server.database;




import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * PLAYER REPOSITORY - QUẢN LÝ DỮ LIỆU NGƯỜI CHƠI
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Xác thực đăng nhập
 * - Tạo tài khoản mới
 * - Lấy/cập nhật điểm người chơi
 * - Load danh sách tài khoản
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class PlayerRepository {
    private DatabaseConnection dbConnection;

    public PlayerRepository(DatabaseConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Lấy PlayerID từ username
     * 
     * @return PlayerID hoặc null nếu không tìm thấy
     */
    public Integer getPlayerId(String username) {
        String sql = "SELECT PlayerID FROM Players WHERE Username = ?";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("PlayerRepository getPlayerId error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Xác thực đăng nhập
     * 
     * @return true nếu username và password đúng
     */
    public boolean authenticate(String username, String passwordHash) {
        String sql = "SELECT 1 FROM Players WHERE Username = ? AND PasswordHash = ?";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.out.println("PlayerRepository authenticate error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Tạo tài khoản người chơi mới
     * 
     * @return PlayerID của tài khoản mới, hoặc null nếu thất bại
     */
    public Integer createPlayer(String username, String passwordHash) {
        String sql = "INSERT INTO Players(Username, PasswordHash, TotalPoints) VALUES(?,?,0)";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("PlayerRepository createPlayer error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cập nhật điểm của người chơi
     * 
     * @param playerId ID của người chơi
     * @param delta    Điểm thay đổi (dương hoặc âm)
     */
    public void updateTotalPoints(int playerId, int delta) {
        String sql = "UPDATE Players SET TotalPoints = TotalPoints + ? WHERE PlayerID = ?";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("PlayerRepository updateTotalPoints error: " + e.getMessage());
        }
    }

    /**
     * Lấy tổng điểm của người chơi
     * 
     * @return Tổng điểm hoặc null nếu không tìm thấy
     */
    public Integer getTotalPoints(String username) {
        String sql = "SELECT TotalPoints FROM Players WHERE Username = ?";
        try (Connection con = dbConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("PlayerRepository getTotalPoints error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Load tất cả tài khoản vào cache
     * 
     * @return Map<Username, PasswordHash>
     */
    public Map<String, String> loadAccounts() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT Username, PasswordHash FROM Players";
        try (Connection con = dbConnection.getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            System.out.println("PlayerRepository loadAccounts error: " + e.getMessage());
        }
        return map;
    }
}
