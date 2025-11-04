package server;

import java.sql.*;
import com.google.gson.Gson;

public class DatabaseManager {
    // 🔹 Thông tin kết nối SQL Server
    private final String url = "jdbc:sqlserver://localhost:1433;databaseName=LuckyDrawGame;encrypt=false";
    private final String user = "sa"; // đổi theo tài khoản SQL Server của bạn
    private final String password = "123"; // đổi mật khẩu tương ứng
    private final Gson gson = new Gson();

    public DatabaseManager() {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            System.out.println("✅ Kết nối SQL Server thành công (driver load OK).");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Đảm bảo người chơi tồn tại trong bảng Players, nếu chưa thì thêm mới */
    public void ensurePlayer(String username) {
        String sqlCheck = "SELECT PlayerID FROM Players WHERE Username = ?";
        String sqlInsert = "INSERT INTO Players (Username, PasswordHash, FullName, Status) VALUES (?, '', ?, 'Offline')";
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            try (PreparedStatement ps = conn.prepareStatement(sqlCheck)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    try (PreparedStatement ins = conn.prepareStatement(sqlInsert)) {
                        ins.setString(1, username);
                        ins.setString(2, username);
                        ins.executeUpdate();
                        System.out.println("🟢 Thêm người chơi mới: " + username);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi ensurePlayer: " + e.getMessage());
        }
    }

    /** Ghi lại kết quả một ván đấu */
    public void recordMatch(String infoJson) {
        String sql = "INSERT INTO Matches (TotalPlayers) VALUES (?)";
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            int totalPlayers = gson.fromJson(infoJson, java.util.Map.class)
                    .getOrDefault("ranking", java.util.List.of())
                    .toString().split(",").length;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, totalPlayers);
                ps.executeUpdate();
            }
            System.out.println("💾 Đã lưu thông tin trận đấu vào SQL Server.");
        } catch (SQLException e) {
            System.err.println("Lỗi recordMatch: " + e.getMessage());
        }
    }

    /** Cập nhật trạng thái người chơi */
    public void updatePlayerStatus(String username, String status) {
        String sql = "UPDATE Players SET Status=? WHERE Username=?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi updatePlayerStatus: " + e.getMessage());
        }
    }
}
