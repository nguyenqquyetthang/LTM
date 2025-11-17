package server.database;




import java.sql.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * DATABASE CONNECTION - QUẢN LÝ KẾT NỐI DATABASE
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Class này xử lý:
 * - Kết nối đến SQL Server
 * - Load JDBC driver
 * - Seed dữ liệu Cards
 * 
 * ⚠️ QUAN TRỌNG: Cấu hình database connection tại đây
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class DatabaseConnection {
    // ⚠️ THAY ĐỔI CẤU HÌNH NÀY CHO DATABASE CỦA BẠN
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=LuckyDrawGame;encrypt=false;";
    private static final String USER = "sa";
    private static final String PASS = "123";

    public DatabaseConnection() {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLServer JDBC Driver not found", e);
        }
    }

    /**
     * Lấy connection đến database
     * 
     * @return Connection object
     * @throws SQLException nếu không kết nối được
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /**
     * Khởi tạo 52 lá bài vào bảng Cards (nếu chưa có)
     * Chỉ chạy 1 lần khi khởi động server
     */
    public void ensureCardsSeeded() {
        String countSql = "SELECT COUNT(*) FROM Cards";
        try (Connection con = getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) == 52) {
                return; // Đã có đủ 52 lá
            }
        } catch (SQLException e) {
            // Proceed to seed if table exists but error reading count
        }

        String[] suits = { "♥", "♠", "♦", "♣" };
        String[] ranks = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" };
        String insert = "INSERT INTO Cards(Rank,Suit) VALUES(?,?)";

        try (Connection con = getConnection();
                PreparedStatement ps = con.prepareStatement(insert)) {
            // Xóa dữ liệu cũ nếu có (partial data)
            try (Statement st = con.createStatement()) {
                st.executeUpdate("DELETE FROM Cards");
            }

            // Insert 52 lá bài
            for (String r : ranks) {
                for (String s : suits) {
                    ps.setString(1, r);
                    ps.setString(2, s);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            System.out.println("✅ Đã seed 52 lá bài vào database");
        } catch (SQLException e) {
            System.out.println("DatabaseConnection ensureCardsSeeded error: " + e.getMessage());
        }
    }
}
