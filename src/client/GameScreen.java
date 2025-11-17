package client;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * GAME SCREEN - MÀN HÌNH PHÒNG CHƠI & GAME
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Màn hình chính để chơi game, bao gồm:
 * - Hiển thị 6 vị trí ngồi (layout oval)
 * - Rút bài theo lượt (turn-based, 10s timeout)
 * - Hiển thị bài của mỗi người khi lật
 * - Xếp hạng và kết quả cuối game
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📨 MESSAGES NHẬN TỪ SERVER (parse trong handleGameMessage):
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * • GAME_START;RoomName
 * → Reset toàn bộ: bài, điểm rút, label, cache
 * → Parse: dòng 222-253
 * 
 * • YOUR_TURN
 * → Enable nút rút bài, bắt đầu đếm ngược 10s
 * → Parse: dòng 268-276
 * 
 * • WAIT
 * → Disable nút rút, dừng đếm ngược
 * → Parse: dòng 277-284
 * 
 * • DRAW;K♠
 * → Hiển thị lá bài vừa rút
 * → Parse: dòng 285-288
 * 
 * • SHOW_HANDS_ALL|user1=K♠,Q♠,J♠|user2=A♥,5♦,3♣|...
 * → Lật TẤT CẢ bài của mọi người lên màn hình
 * → Lưu vào cachedPlayerCards
 * → Parse: dòng 293-339
 * 
 * • HAND_RANKS|user1:4:Straight Flush:530|user2:1:HighCard:7|...
 * → Hiển thị loại tay bài trên label tên
 * → Chỉ show score cho HighCard (category=1)
 * → Lưu vào cachedHandRanks
 * → Parse: dòng 472-500
 * 
 * • WINNER player1 tay=Straight Flush
 * → Hiển thị popup người thắng
 * → Highlight panel người thắng (border vàng)
 * → Parse: dòng 278-292
 * 
 * • RANKING|user1:15:+3|user2:8:-1|...
 * → Hiển thị bảng xếp hạng đầy đủ với bài và loại tay
 * → Parse: dòng 518-545
 * 
 * • END;RoomName
 * → Ván kết thúc, KHÔNG reset bài (để xem)
 * → Reset ready cho ván mới
 * → Parse: dòng 546-564
 * 
 * • ROOM_UPDATE|roomName|hostIndex|player1,player2,player3,...
 * → Cập nhật vị trí ngồi của mọi người
 * → Parse: dòng 382-390
 * 
 * • READY_STATUS|user1:true|user2:false|...
 * → Hiển thị icon ✅/❌ trên tên
 * → Host check để enable nút Start
 * → Parse: dòng 406-444
 * 
 * • YOU_ARE_HOST
 * → Trở thành host mới (khi host cũ rời)
 * → Hiển thị nút Start, ẩn nút Ready
 * → Parse: dòng 369-380
 * 
 * • ELIMINATED;reason
 * → Bị timeout/kick, quay về lobby
 * → Parse: dòng 347-355
 * 
 * • KICKED;reason
 * → Bị host kick, quay về lobby
 * → Parse: dòng 356-368
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 📤 MESSAGES GỬI ĐẾN SERVER:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * • "GET_PLAYER_LIST" → Request danh sách người online
 * • "GET_ROOM_UPDATE;roomName" → Request cập nhật phòng
 * • "START;roomName" → Host bắt đầu game
 * • "READY;roomName" → Guest sẵn sàng
 * • "DRAW;roomName" → Rút 1 lá bài
 * • "INVITE;targetUsername" → Mời người vào phòng
 * • "KICK_PLAYER;targetUsername" → Host kick người
 * • "LEAVE_ROOM;roomName" → Thoát phòng
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 🎨 BIẾN STATE QUAN TRỌNG:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * • canDraw: Có thể rút bài không (YOUR_TURN)
 * • cardsDrawn: Số lá đã rút (max 3)
 * • myPosition: Vị trí của mình trong 6 panel (0-5)
 * • isHost: Có phải chủ phòng không
 * • playersReadyStatus: Map<username, ready> - trạng thái sẵn sàng
 * • cachedHandRanks: Map<username, "tay bài"> - cache cho RANKING
 * • cachedPlayerCards: Map<username, "bài"> - cache cho RANKING
 * • cardIconCache: Cache ảnh bài để không load lại
 * • countdownTimer: Timer đếm ngược 10s cho lượt
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 🎨 CHÚ Ý CHO GIAO DIỆN:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * ⚠️ ĐÂY LÀ BẢN DEMO LOGIC - CẦN CẢI THIỆN GIAO DIỆN!
 * 
 * Điểm cần cải thiện:
 * 1. Layout 6 vị trí: Hiện tại dùng absolute positioning
 * → Cải thiện: Dùng layout oval động theo kích thước cửa sổ
 * 
 * 2. Hiển thị bài: Hiện tại chỉ load ảnh cơ bản
 * → Cải thiện: Animation lật bài, hiệu ứng rút bài
 * 
 * 3. Timer: Chỉ hiển thị số giây còn lại
 * → Cải thiện: Progress bar, màu đổi khi gần hết giờ
 * 
 * 4. Người thắng: Chỉ đổi border
 * → Cải thiện: Animation sparkle, confetti effect
 * 
 * 5. Kết quả: Hiển thị bằng JOptionPane đơn giản
 * → Cải thiện: Custom dialog đẹp hơn với animation
 * 
 * 6. Sound: Không có
 * → Cải thiện: Thêm âm thanh rút bài, win, lose, timeout
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class GameScreen extends JFrame {
    private String username;
    private NetworkHandler network;
    private boolean isHost;
    private String roomName;

    // UI Components
    private JPanel[] playerPanels = new JPanel[6]; // 6 vị trí ngồi
    private JLabel[] playerNameLabels = new JLabel[6];
    private JLabel[][] cardLabels = new JLabel[6][3]; // Chỉ 3 ô rút bài
    private JButton btnStart;
    private JButton btnReady;
    private JButton btnDraw;
    private JLabel lblTurnInfo;
    private JLabel lblTimer;
    private DefaultListModel<String> onlineListModel;
    private JList<String> onlinePlayersList;
    private JButton btnInvite;
    private JButton btnKick;

    private boolean canDraw = false;
    private int cardsDrawn = 0;
    private List<Integer> drawnCards = new ArrayList<>();
    private Timer countdownTimer;
    private int timeLeft = 10;
    private int myPosition = -1; // Vị trí của mình trong phòng
    private Map<String, Boolean> playersReadyStatus = new HashMap<>(); // Trạng thái sẵn sàng
    // Cache thông tin cho kết quả ván đấu
    private Map<String, String> cachedHandRanks = new HashMap<>(); // user -> "categoryName (score)"
    private Map<String, String> cachedPlayerCards = new HashMap<>(); // user -> "card1,card2,card3"
    // Cache ảnh lá bài để tránh load lại nhiều lần
    private final Map<String, ImageIcon> cardIconCache = new HashMap<>();
    private static final int CARD_IMG_W = 50;
    private static final int CARD_IMG_H = 75;
    private static final String CARD_IMG_BASE = "PNG-cards-1.3/PNG-cards-1.3";

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * CONSTRUCTOR - KHỞI TẠO MÀN HÌNH GAME
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * @param username Tên người chơi
     * @param network  NetworkHandler đã kết nối
     * @param isHost   Có phải chủ phòng không (hiển thị nút Start/Kick)
     * @param roomName Tên phòng
     * 
     *                 Flow khởi tạo:
     *                 1. Tạo UI: 6 panel vị trí ngồi + buttons + timer
     *                 2. Bắt đầu lắng nghe messages từ server
     *                 3. Request danh sách người online và trạng thái phòng
     *                 4. Setup event listeners cho các nút
     * 
     *                 ═══════════════════════════════════════════════════════════════════════════
     */
    public GameScreen(String username, NetworkHandler network, boolean isHost, String roomName) {
        this.username = username;
        this.network = network;
        this.isHost = isHost;
        this.roomName = roomName;

        setTitle("Phòng " + roomName + " - " + username + (isHost ? " (Chủ phòng)" : ""));
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // ═════════════════════════════════════════════════════════════════════════
        // PANEL TRÁI: DANH SÁCH NGƯỜI CHƠI ONLINE (để mời vào phòng)
        // ═════════════════════════════════════════════════════════════════════════
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Người chơi Online"));

        onlineListModel = new DefaultListModel<>();
        onlinePlayersList = new JList<>(onlineListModel);
        JScrollPane scrollPane = new JScrollPane(onlinePlayersList);

        btnInvite = new JButton("Mời vào phòng");
        btnInvite.addActionListener(e -> invitePlayer());

        leftPanel.add(scrollPane, BorderLayout.CENTER);
        leftPanel.add(btnInvite, BorderLayout.SOUTH);

        // ═════════════════════════════════════════════════════════════════════════
        // PANEL GIỮA: BÀN CHƠI VỚI 6 VỊ TRÍ NGỒI
        // ═════════════════════════════════════════════════════════════════════════
        // Layout oval: Top, RightTop, RightBottom, Bottom (mình), LeftBottom, LeftTop
        // Mỗi vị trí có: Label tên + 3 ô cho 3 lá bài
        // ═════════════════════════════════════════════════════════════════════════
        JPanel centerPanel = new JPanel(new BorderLayout());

        // Top: Timer và thông tin lượt
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        lblTurnInfo = new JLabel("Chờ bắt đầu...", SwingConstants.CENTER);
        lblTurnInfo.setFont(new Font("Arial", Font.BOLD, 16));
        lblTimer = new JLabel("", SwingConstants.CENTER);
        lblTimer.setFont(new Font("Arial", Font.BOLD, 20));
        lblTimer.setForeground(Color.RED);
        topPanel.add(lblTurnInfo);
        topPanel.add(lblTimer);

        // Center: 6 vị trí ngồi xếp theo hình oval
        JPanel tablePanel = new JPanel(null); // Absolute positioning
        tablePanel.setPreferredSize(new Dimension(900, 550));
        tablePanel.setBackground(new Color(34, 139, 34)); // Màu xanh bàn chơi

        // ─────────────────────────────────────────────────────────────────────────
        // 6 VỊ TRÍ NGỒI - LAYOUT OVAL
        // ─────────────────────────────────────────────────────────────────────────
        // Index 3 (Bottom) thường là vị trí của mình (myPosition)
        // Server gửi danh sách player theo thứ tự, client map vào 6 vị trí này
        // ─────────────────────────────────────────────────────────────────────────
        int[][] positions = {
                { 350, 10 }, // 0: Top (đối diện)
                { 650, 120 }, // 1: Right-top
                { 650, 320 }, // 2: Right-bottom
                { 350, 400 }, // 3: Bottom (mình)
                { 50, 320 }, // 4: Left-bottom
                { 50, 120 } // 5: Left-top
        };

        for (int i = 0; i < 6; i++) {
            playerPanels[i] = new JPanel();
            playerPanels[i].setLayout(new BoxLayout(playerPanels[i], BoxLayout.Y_AXIS));
            playerPanels[i].setBounds(positions[i][0], positions[i][1], 200, 140);
            playerPanels[i].setBackground(Color.LIGHT_GRAY);
            playerPanels[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

            playerNameLabels[i] = new JLabel("[Trống]", SwingConstants.CENTER);
            playerNameLabels[i].setFont(new Font("Arial", Font.BOLD, 12));
            playerPanels[i].add(playerNameLabels[i]);

            // 3 ô rút bài cho mỗi người
            JPanel cardsPanel = new JPanel(new GridLayout(1, 3, 2, 2));
            for (int j = 0; j < 3; j++) {
                cardLabels[i][j] = new JLabel("", SwingConstants.CENTER);
                cardLabels[i][j].setPreferredSize(new Dimension(CARD_IMG_W, CARD_IMG_H));
                cardLabels[i][j].setOpaque(true);
                cardLabels[i][j].setBackground(Color.WHITE);
                cardLabels[i][j].setBorder(BorderFactory.createLineBorder(Color.BLACK));
                cardLabels[i][j].setHorizontalAlignment(SwingConstants.CENTER);
                cardLabels[i][j].setVerticalAlignment(SwingConstants.CENTER);
                cardsPanel.add(cardLabels[i][j]);
            }
            playerPanels[i].add(cardsPanel);
            tablePanel.add(playerPanels[i]);
        }

        // Bottom: Nút điều khiển
        JPanel bottomPanel = new JPanel();
        btnStart = new JButton("Bắt đầu");
        btnReady = new JButton("Sẵn sàng");
        btnDraw = new JButton("Rút bài");
        btnKick = new JButton("Kick người chơi");
        JButton btnLeave = new JButton("❌ Thoát phòng");

        btnStart.setEnabled(false); // Vô hiệu hóa cho đến khi mọi người sẵn sàng
        btnReady.setEnabled(!isHost); // Chỉ khách mới có nút sẵn sàng
        btnDraw.setEnabled(false);
        btnKick.setEnabled(isHost);
        btnLeave.setForeground(Color.RED);

        // Luôn thêm tất cả button, nhưng điều chỉnh visible dựa trên role
        btnStart.setVisible(isHost);
        btnReady.setVisible(!isHost);
        btnKick.setVisible(true); // Luôn có trong UI, nhưng enabled dựa trên isHost

        bottomPanel.add(btnStart);
        bottomPanel.add(btnReady);
        bottomPanel.add(btnDraw);
        bottomPanel.add(btnKick);
        bottomPanel.add(btnLeave);

        centerPanel.add(topPanel, BorderLayout.NORTH);
        centerPanel.add(tablePanel, BorderLayout.CENTER);
        centerPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);

        // ═════════════════════════════════════════════════════════════════════════
        // LẮNG NGHE SERVER - BẮT ĐẦU NHẬN MESSAGES
        // ═════════════════════════════════════════════════════════════════════════
        network.startListening(this::handleGameMessage);

        // ═════════════════════════════════════════════════════════════════════════
        // GỬI NGAY KHI VÀO PHÒNG
        // ═════════════════════════════════════════════════════════════════════════
        // 📤 GỬI: "GET_PLAYER_LIST" → nhận "PLAYER_LIST|..."
        // 📤 GỬI: "GET_ROOM_UPDATE;roomName" → nhận "ROOM_UPDATE|..."
        // ═════════════════════════════════════════════════════════════════════════
        try {
            network.sendMsg("GET_PLAYER_LIST");
            network.sendMsg("GET_ROOM_UPDATE;" + roomName);
        } catch (IOException e) {
            System.err.println("⚠️ Không thể request danh sách người chơi");
        }

        // ═════════════════════════════════════════════════════════════════════════
        // NÚT "BẮT ĐẦU" - CHỈ HOST
        // ═════════════════════════════════════════════════════════════════════════
        // 📤 GỬI: "START;roomName"
        // 📨 NHẬN SAU ĐÓ: "GAME_START;roomName" (broadcast cho tất cả)
        // ═════════════════════════════════════════════════════════════════════════
        btnStart.addActionListener(e -> {
            try {
                network.sendMsg("START;" + roomName);
                btnStart.setEnabled(false);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi gửi lệnh bắt đầu.");
            }
        });

        // ═════════════════════════════════════════════════════════════════════════
        // NÚT "SẴN SÀNG" - CHỈ GUEST
        // ═════════════════════════════════════════════════════════════════════════
        // 📤 GỬI: "READY;roomName"
        // 📨 NHẬN SAU ĐÓ: "READY_STATUS|user1:true|user2:false|..." (broadcast)
        // ═════════════════════════════════════════════════════════════════════════
        btnReady.addActionListener(e -> {
            try {
                network.sendMsg("READY;" + roomName);
                btnReady.setEnabled(false);
                btnReady.setText("✅ Đã sẵn sàng");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi gửi lệnh sẵn sàng.");
            }
        });

        // ═════════════════════════════════════════════════════════════════════════
        // NÚT "RÚT BÀI"
        // ═════════════════════════════════════════════════════════════════════════
        // 📤 GỬI: "DRAW;roomName"
        // 📨 NHẬN SAU ĐÓ:
        // - "DRAW;K♠" (lá bài vừa rút)
        // - "WAIT" (chuyển lượt)
        // - "NOT_YOUR_TURN" (nếu gửi sai lượt)
        // ═════════════════════════════════════════════════════════════════════════
        btnDraw.addActionListener(e -> {
            if (canDraw && cardsDrawn < 3) {
                try {
                    network.sendMsg("DRAW;" + roomName);
                    btnDraw.setEnabled(false);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "❌ Lỗi gửi yêu cầu rút bài.");
                }
            }
        });

        // ═════════════════════════════════════════════════════════════════════════
        // NÚT "KICK" - CHỈ HOST
        // ═════════════════════════════════════════════════════════════════════════
        // Xem chi tiết ở hàm kickPlayer()
        // ═════════════════════════════════════════════════════════════════════════
        btnKick.addActionListener(e -> kickPlayer());

        // ═════════════════════════════════════════════════════════════════════════
        // NÚT "THOÁT PHÒNG"
        // ═════════════════════════════════════════════════════════════════════════
        // Xem chi tiết ở hàm leaveRoom()
        // ═════════════════════════════════════════════════════════════════════════
        btnLeave.addActionListener(e -> leaveRoom());

        setVisible(true);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * HÀM XỬ LÝ TẤT CẢ MESSAGES NHẬN TỪ SERVER
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * 📨 MESSAGES NHẬN (tất cả đều parse ở đây):
     * 
     * • "GAME_START;RoomName" → Reset UI, xóa bài cũ, cache
     * • "YOUR_TURN" → Enable nút rút bài, bắt đầu timer 10s
     * • "WAIT" → Disable nút rút, dừng timer
     * • "DRAW;K♠" → Hiển thị lá bài vừa rút lên ô trống
     * • "SHOW_HANDS_ALL|user1=K♠,Q♠,J♠|user2=..." → Lật tất cả bài
     * • "HAND_RANKS|user1:4:Straight Flush:530|..." → Hiển thị loại tay
     * • "WINNER player1 tay=..." → Popup thông báo thắng, highlight
     * • "RANKING|user1:15:+3|user2:8:-1|..." → Dialog xếp hạng đầy đủ
     * • "END;RoomName" → Ván kết thúc, reset ready
     * • "ROOM_UPDATE|room|host|players" → Cập nhật vị trí ngồi
     * • "READY_STATUS|user1:true|user2:false|..." → Icon ✅/❌
     * • "YOU_ARE_HOST" → Trở thành host, đổi UI
     * • "ELIMINATED;reason" → Bị kick, về lobby
     * • "KICKED;reason" → Bị host kick, về lobby
     * • "PLAYER_LIST|user1:status:pts|..." → Update list online
     * • "INVITE;fromUser;roomName" → Nhận lời mời
     * • "ROOM_FULL" → Phòng đầy
     * • "NOT_HOST" → Không có quyền
     * • "NOT_YOUR_TURN" → Chưa đến lượt
     * 
     * ⚠️ KHÔNG GỬI MESSAGE NÀO TỪ HÀM NÀY
     * (Chỉ nhận và xử lý hiển thị)
     * 
     * ═══════════════════════════════════════════════════════════════════════════
     */
    private void handleGameMessage(String msg) {
        System.out.println("🎮 [Game] Nhận: " + msg);

        if (msg.startsWith("GAME_START")) {
            SwingUtilities.invokeLater(() -> {
                // Reset tất cả bài và thông tin tay bài cho ván mới
                for (int i = 0; i < 6; i++) {
                    // Reset các lá bài
                    for (int j = 0; j < 3; j++) {
                        resetCardLabel(cardLabels[i][j]);
                    }
                    // Reset label tên (loại bỏ thông tin tay bài)
                    String labelText = playerNameLabels[i].getText();
                    if (!labelText.equals("[Trống]") && labelText.contains(" - ")) {
                        String playerName = labelText.split(" - ")[0];
                        playerNameLabels[i].setText(playerName);
                    }
                    // Reset border và màu nền
                    if (!labelText.equals("[Trống]")) {
                        playerPanels[i].setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                        playerPanels[i].setBackground(new Color(240, 248, 255)); // AliceBlue
                    }
                }
                cardsDrawn = 0;
                drawnCards.clear();
                canDraw = false;
                btnDraw.setEnabled(false);
                lblTurnInfo.setText("Chờ lượt...");

                // Vô hiệu hóa nút Kick khi game bắt đầu
                btnKick.setEnabled(false);

                // Xóa cache kết quả ván trước
                cachedHandRanks.clear();
                cachedPlayerCards.clear();

                JOptionPane.showMessageDialog(this, "🎮 Trò chơi bắt đầu! Rút bài theo lượt.");
            });
        } else if (msg.startsWith("HAND ")) {
            // HAND v1,v2,v3: 3 lá đầu
            String data = msg.substring("HAND ".length());
            SwingUtilities.invokeLater(() -> {
                if (myPosition == -1)
                    myPosition = 3;
                // data có thể dạng "[2♠, 3♥, A♦]" hoặc "2♠,3♥,A♦"
                String cleaned = data.replace("[", "").replace("]", "").trim();
                String[] vals = cleaned.split(",");
                for (int j = 0; j < Math.min(3, vals.length); j++) {
                    String v = vals[j].trim();
                    setCardLabelImage(cardLabels[myPosition][j], v);
                }
                cardsDrawn = 0; // reset số lá đã rút thêm
            });
        } else if (msg.equals("YOUR_TURN")) {
            SwingUtilities.invokeLater(() -> {
                canDraw = true;
                btnDraw.setEnabled(cardsDrawn < 3); // Enable nếu chưa đủ 3 lá
                lblTurnInfo.setText("🟢 LƯỢT CỦA BẠN!");
                lblTurnInfo.setForeground(Color.GREEN);
                startCountdown();
            });
        } else if (msg.equals("WAIT")) {
            SwingUtilities.invokeLater(() -> {
                canDraw = false;
                btnDraw.setEnabled(false);
                lblTurnInfo.setText("⏳ Chờ lượt...");
                lblTurnInfo.setForeground(Color.GRAY);
                stopCountdown();
                lblTimer.setText("");
            });
        } else if (msg.startsWith("DRAW;")) {
            String cardStr = msg.split(";")[1];
            SwingUtilities.invokeLater(() -> updateCardDisplay(cardStr));
        } else if (msg.startsWith("RESULT ")) {
            String result = msg.substring("RESULT ".length());
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Kết quả của bạn: " + result);
            });
        } else if (msg.startsWith("WINNER ")) {
            String winMsg = msg.substring("WINNER ".length());
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "🏆 " + winMsg);
                // Highlight panel người thắng
                String winnerName = winMsg.trim();
                for (int i = 0; i < playerNameLabels.length; i++) {
                    if (playerNameLabels[i].getText().equals(winnerName)) {
                        playerPanels[i].setBorder(BorderFactory.createLineBorder(Color.ORANGE, 4));
                        playerPanels[i].setBackground(new Color(255, 250, 205)); // LemonChiffon
                    } else if (!playerNameLabels[i].getText().equals("[Trống]")) {
                        playerPanels[i].setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                    }
                }
            });
        } else if (msg.startsWith("SHOW_HANDS_ALL|")) {
            String payload = msg.substring("SHOW_HANDS_ALL|".length());
            SwingUtilities.invokeLater(() -> {
                // Map username->hand
                String[] entries = payload.split("\\|");
                cachedPlayerCards.clear(); // Reset cache
                for (int i = 0; i < 6; i++) {
                    for (int j = 0; j < 3; j++) {
                        resetCardLabel(cardLabels[i][j]);
                    }
                }
                for (String e : entries) {
                    if (e.isEmpty())
                        continue;
                    int eq = e.indexOf('=');
                    if (eq <= 0)
                        continue;
                    String user = e.substring(0, eq);
                    String cardsStr = e.substring(eq + 1);

                    // Lưu vào cache
                    if (!cardsStr.isEmpty()) {
                        cachedPlayerCards.put(user, cardsStr);
                    }

                    // Tìm vị trí user trong playerNameLabels
                    int pos = -1;
                    for (int i = 0; i < 6; i++) {
                        if (playerNameLabels[i].getText().contains(user)) {
                            pos = i;
                            break;
                        }
                    }
                    if (pos == -1)
                        continue;
                    if (!cardsStr.isEmpty()) {
                        String[] vals = cardsStr.split(",");
                        for (int j = 0; j < Math.min(3, vals.length); j++) {
                            String v = vals[j].trim();
                            setCardLabelImage(cardLabels[pos][j], v);
                        }
                    }
                }
            });
        } else if (msg.equals("NOT_YOUR_TURN")) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "⚠️ Chưa đến lượt bạn!");
            });
        } else if (msg.startsWith("ELIMINATED;")) {
            String reason = msg.substring("ELIMINATED;".length());
            SwingUtilities.invokeLater(() -> {
                stopCountdown();
                network.stopListening();
                JOptionPane.showMessageDialog(this, "❌ Bạn đã bị loại: " + reason);
                new LobbyScreen(username, network).setVisible(true);
                dispose();
            });
        } else if (msg.startsWith("KICKED;")) {
            String reason = msg.split(";")[1];
            System.out.println("🚪 [GameScreen] Received KICKED: " + reason);
            SwingUtilities.invokeLater(() -> {
                stopCountdown();

                System.out.println("🔄 [GameScreen] Creating LobbyScreen...");
                // Khởi tạo LobbyScreen mới - constructor sẽ startListening()
                // Server sẽ broadcast PLAYER_LIST và ROOMS_LIST sau KICKED
                LobbyScreen lobby = new LobbyScreen(username, network);
                System.out.println("✅ [GameScreen] LobbyScreen created");

                lobby.setVisible(true);
                System.out.println("✅ [GameScreen] LobbyScreen visible");

                dispose();
                System.out.println("✅ [GameScreen] GameScreen disposed");

                JOptionPane.showMessageDialog(lobby, "❌ Bạn đã bị kick: " + reason);
            });
        } else if (msg.startsWith("YOU_ARE_HOST")) {
            SwingUtilities.invokeLater(() -> {
                isHost = true;
                setTitle("Phòng " + roomName + " - " + username + " (Chủ phòng)");
                // Hiển thị nút Start cho host mới, ẩn nút Ready
                btnReady.setVisible(false);
                btnStart.setVisible(true);
                btnStart.setEnabled(false); // Chờ người khác ready
                btnKick.setVisible(true);
                btnKick.setEnabled(true);
                System.out.println(
                        "[DEBUG] YOU_ARE_HOST: Updated UI - btnStart visible, btnReady hidden, btnKick enabled");
            });
        } else if (msg.startsWith("ROOM_UPDATE")) {
            // Format: ROOM_UPDATE|roomName|hostIndex|player1,player2,player3
            SwingUtilities.invokeLater(() -> {
                String[] parts = msg.split("\\|");
                if (parts.length >= 4) {
                    String[] players = parts[3].split(",");
                    updateRoomPlayers(players);
                }
            });
        } else if (msg.startsWith("PLAYER_LIST")) {
            // Format: PLAYER_LIST|username:status|...
            SwingUtilities.invokeLater(() -> {
                String[] parts = msg.split("\\|");
                if (parts.length > 1) {
                    String[] players = new String[parts.length - 1];
                    System.arraycopy(parts, 1, players, 0, parts.length - 1);
                    updateOnlineList(players);
                }
            });
        } else if (msg.startsWith("INVITE;")) {
            // Nhận lời mời: INVITE;fromUser;roomName
            String[] parts = msg.split(";");
            String fromUser = parts[1];
            String inviteRoom = parts[2];
            SwingUtilities.invokeLater(() -> {
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        fromUser + " mời bạn vào phòng " + inviteRoom + ". Tham gia?",
                        "Lời mời",
                        JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        network.sendMsg("JOIN;" + inviteRoom);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "❌ Lỗi tham gia phòng.");
                    }
                }
            });
        } else if (msg.startsWith("READY_STATUS|")) {
            // Format: READY_STATUS|user1:true|user2:false|...
            SwingUtilities.invokeLater(() -> {
                String data = msg.substring("READY_STATUS|".length());
                String[] tokens = data.split("\\|");
                playersReadyStatus.clear();
                for (String token : tokens) {
                    if (token.isEmpty())
                        continue;
                    String[] kv = token.split(":");
                    if (kv.length == 2) {
                        playersReadyStatus.put(kv[0], Boolean.parseBoolean(kv[1]));
                    }
                }
                updateReadyDisplay();
                // Nếu là host, kiểm tra xem tất cả sẵn sàng chưa để bật nút Start
                if (isHost) {
                    // Đếm tổng số người và số khách đã ready
                    int totalPlayers = playersReadyStatus.size();
                    int guestsReady = 0;
                    int totalGuests = 0;

                    for (Map.Entry<String, Boolean> e : playersReadyStatus.entrySet()) {
                        if (!e.getKey().equals(username)) { // Không phải host
                            totalGuests++;
                            if (e.getValue()) {
                                guestsReady++;
                            }
                        }
                    }

                    // Enable Start CHỈ KHI: có ít nhất 2 người (host + 1 khách) && TẤT CẢ khách đã
                    // ready
                    // totalPlayers >= 2 có nghĩa là có host + ít nhất 1 khách
                    // totalGuests >= 1 đảm bảo có ít nhất 1 khách
                    // guestsReady == totalGuests đảm bảo TẤT CẢ khách đều ready
                    boolean canStart = totalPlayers >= 2 && totalGuests >= 1 && guestsReady == totalGuests;
                    btnStart.setEnabled(canStart);

                    System.out.println("DEBUG Ready Check:");
                    System.out.println("  Total players: " + totalPlayers);
                    System.out.println("  Total guests: " + totalGuests);
                    System.out.println("  Guests ready: " + guestsReady);
                    System.out.println("  Can start: " + canStart);
                    System.out.println("  Ready map: " + playersReadyStatus);
                }
            });
        } else if (msg.equals("ROOM_FULL")) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "❌ Phòng đã đầy (tối đa 6 người)!");
            });
        } else if (msg.equals("NOT_HOST")) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "❌ Chỉ chủ phòng mới có quyền này!");
            });
        } else if (msg.startsWith("HAND_RANKS|")) {
            // Format: HAND_RANKS|user1:category:categoryName:score|user2:...
            String payload = msg.substring("HAND_RANKS|".length());
            SwingUtilities.invokeLater(() -> {
                cachedHandRanks.clear(); // Reset cache
                String[] entries = payload.split("\\|");
                for (String entry : entries) {
                    if (entry.isEmpty())
                        continue;
                    String[] parts = entry.split(":");
                    if (parts.length >= 4) {
                        String user = parts[0];
                        int category = Integer.parseInt(parts[1]);
                        String categoryName = parts[2];
                        String score = parts[3];

                        // Chỉ hiển thị điểm cho HighCard (category = 1)
                        String displayText = (category == 1) ? categoryName + " (" + score + ")" : categoryName;
                        cachedHandRanks.put(user, displayText);
                    }
                }
                // Hiển thị thứ hạng tay bài trên label của từng người
                for (int i = 0; i < 6; i++) {
                    String labelText = playerNameLabels[i].getText();
                    if (labelText.equals("[Trống]"))
                        continue;

                    // Tách tên người chơi (loại bỏ phần " - ..." nếu có)
                    String playerName = labelText.split(" - ")[0];
                    if (cachedHandRanks.containsKey(playerName)) {
                        playerNameLabels[i].setText(playerName + " - " + cachedHandRanks.get(playerName));
                    }
                }
            });
        } else if (msg.startsWith("RANKING|")) {
            // Format: RANKING|user1:totalPoints:changePoints|user2:...
            String payload = msg.substring("RANKING|".length());
            SwingUtilities.invokeLater(() -> {
                StringBuilder rankingMsg = new StringBuilder("🏆 KẾT QUẢ VÁN ĐẤU 🏆\n\n");
                String[] entries = payload.split("\\|");
                int rank = 1;
                for (String entry : entries) {
                    if (entry.isEmpty())
                        continue;
                    String[] parts = entry.split(":");
                    if (parts.length >= 3) {
                        String user = parts[0];
                        String totalPts = parts[1];
                        int change = Integer.parseInt(parts[2]);
                        String changeStr = (change > 0) ? "+" + change : String.valueOf(change);

                        // Lấy thông tin tay bài và bài từ cache
                        String handRank = cachedHandRanks.getOrDefault(user, "N/A");
                        String cards = cachedPlayerCards.getOrDefault(user, "N/A");

                        rankingMsg.append(rank).append(". ").append(user)
                                .append(": ").append(totalPts).append(" điểm ")
                                .append("(").append(changeStr).append(")\n")
                                .append("   Bài: ").append(cards).append("\n")
                                .append("   Tay: ").append(handRank).append("\n\n");
                        rank++;
                    }
                }
                JOptionPane.showMessageDialog(this, rankingMsg.toString(), "Xếp hạng", JOptionPane.INFORMATION_MESSAGE);
            });
        } else if (msg.startsWith("END")) {
            SwingUtilities.invokeLater(() -> {
                stopCountdown();
                canDraw = false;
                btnDraw.setEnabled(false);
                lblTurnInfo.setText("🏁 Game kết thúc! Mọi người xem bài nhau. Sẵn sàng cho ván mới nào!");

                // Enable lại nút Kick cho host khi game kết thúc
                if (isHost) {
                    btnKick.setEnabled(true);
                }

                // Không reset bài ở đây - để mọi người vẫn thấy bài đã lật
                // Bài sẽ được reset khi GAME_START mới
                // Reset ready cho ván mới
                if (!isHost) {
                    btnReady.setEnabled(true);
                    btnReady.setText("Sẵn sàng");
                } else {
                    btnStart.setEnabled(false); // Chờ mọi người ready
                }
            });
        }
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * CẬP NHẬT HIỂN THỊ BÀI VỪA RÚT
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: Được gọi từ handleGameMessage khi nhận "DRAW;K♠"
     * 📤 GỬI: KHÔNG gửi message nào
     * 
     * Logic: Tìm ô trống đầu tiên trong 3 ô bài của mình, hiển thị ảnh lá bài
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void updateCardDisplay(String value) {
        if (myPosition == -1)
            myPosition = 3; // vị trí mặc định của mình
        for (int j = 0; j < 3; j++) {
            if (cardLabels[myPosition][j].getIcon() == null && cardLabels[myPosition][j].getText().isEmpty()) {
                setCardLabelImage(cardLabels[myPosition][j], value);
                cardsDrawn++;
                break;
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS ẢNH LÁ BÀI - KHÔNG GỬI/NHẬN MESSAGE
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Reset 1 ô bài về trạng thái trống
     * 📨 NHẬN: KHÔNG | 📤 GỬI: KHÔNG
     */
    private void resetCardLabel(JLabel lbl) {
        lbl.setText("");
        lbl.setIcon(null);
        lbl.setBackground(Color.WHITE);
    }

    /**
     * Hiển thị ảnh lá bài lên label
     * 📨 NHẬN: KHÔNG | 📤 GỬI: KHÔNG
     * 
     * @param cardValue Format: "K♠", "A♥", "10♦", etc.
     */
    private void setCardLabelImage(JLabel lbl, String cardValue) {
        ImageIcon icon = loadCardIcon(cardValue);
        if (icon != null) {
            lbl.setIcon(icon);
            lbl.setText("");
            lbl.setBackground(new Color(255, 215, 0));
        } else {
            lbl.setText(cardValue);
            lbl.setBackground(new Color(255, 215, 0));
        }
    }

    /**
     * Load ảnh lá bài từ thư mục PNG-cards-1.3/
     * 📨 NHẬN: KHÔNG | 📤 GỬI: KHÔNG
     * 
     * Map: K♠ → king_of_spades.png
     * A♥ → ace_of_hearts.png
     * 10♦ → 10_of_diamonds.png
     * 
     * Cache ảnh để không load lại nhiều lần
     */
    private ImageIcon loadCardIcon(String cardValue) {
        if (cardValue == null || cardValue.isEmpty())
            return null;
        char suitChar = cardValue.charAt(cardValue.length() - 1); // ký tự cuối
        String rankPart = cardValue.substring(0, cardValue.length() - 1); // phần đầu
        String rankEng;
        switch (rankPart) {
            case "A":
                rankEng = "ace";
                break;
            case "K":
                rankEng = "king";
                break;
            case "Q":
                rankEng = "queen";
                break;
            case "J":
                rankEng = "jack";
                break;
            default:
                rankEng = rankPart; // 2..10
        }
        String suitEng;
        switch (suitChar) {
            case '\u2660':
                suitEng = "spades";
                break; // ♠
            case '\u2665':
                suitEng = "hearts";
                break; // ♥
            case '\u2666':
                suitEng = "diamonds";
                break; // ♦
            case '\u2663':
                suitEng = "clubs";
                break; // ♣
            default:
                suitEng = "unknown";
                break;
        }
        if ("unknown".equals(suitEng))
            return null;
        String fileName = rankEng + "_of_" + suitEng + ".png"; // ace_of_spades.png
        String cacheKey = fileName.toLowerCase();
        if (cardIconCache.containsKey(cacheKey))
            return cardIconCache.get(cacheKey);
        File imgFile = new File(CARD_IMG_BASE, fileName);
        if (!imgFile.exists()) {
            imgFile = new File(CARD_IMG_BASE, fileName.toLowerCase());
            if (!imgFile.exists())
                return null;
        }
        ImageIcon raw = new ImageIcon(imgFile.getAbsolutePath());
        if (raw.getIconWidth() <= 0)
            return null;
        Image scaled = raw.getImage().getScaledInstance(CARD_IMG_W, CARD_IMG_H, Image.SCALE_SMOOTH);
        ImageIcon scaledIcon = new ImageIcon(scaled);
        cardIconCache.put(cacheKey, scaledIcon);
        return scaledIcon;
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * CẬP NHẬT VỊ TRÍ NGỒI CỦA MỌI NGƯỜI
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: Được gọi từ handleGameMessage khi nhận:
     * "ROOM_UPDATE|roomName|hostIndex|player1,player2,player3"
     * 
     * 📤 GỬI: KHÔNG gửi message nào
     * 
     * Logic: Map danh sách players vào 6 panel vị trí ngồi
     * Highlight panel của mình bằng màu xanh dương
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void updateRoomPlayers(String[] players) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < 6; i++) {
                if (i < players.length) {
                    playerNameLabels[i].setText(players[i]);
                    playerPanels[i].setBackground(new Color(144, 238, 144)); // Màu xanh nhạt
                    if (players[i].equals(username)) {
                        myPosition = i;
                        playerPanels[i].setBackground(new Color(173, 216, 230)); // Màu xanh dương nhạt (highlight)
                    }
                } else {
                    playerNameLabels[i].setText("[Trống]");
                    playerPanels[i].setBackground(Color.LIGHT_GRAY);
                }
            }
            updateReadyDisplay();
        });
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * CẬP NHẬT ICON SẴN SÀNG (✅/❌) TRÊN TÊN NGƯỜI CHƠI
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: Được gọi sau khi parse "READY_STATUS|user1:true|user2:false|..."
     * 📤 GỬI: KHÔNG gửi message nào
     * 
     * Logic: Dựa vào playersReadyStatus map để hiển thị ✅ (ready) hoặc ❌ (not
     * ready)
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void updateReadyDisplay() {
        // Hiển thị trạng thái sẵn sàng trên tên người chơi
        for (int i = 0; i < 6; i++) {
            String name = playerNameLabels[i].getText();
            if (!name.equals("[Trống]") && !name.startsWith("[")) {
                // Loại bỏ icon cũ (nếu có)
                String cleanName = name.replaceAll("✅|❌", "").trim();
                Boolean ready = playersReadyStatus.get(cleanName);
                if (ready != null && ready) {
                    playerNameLabels[i].setText("✅ " + cleanName);
                } else if (ready != null) {
                    playerNameLabels[i].setText("❌ " + cleanName);
                } else {
                    playerNameLabels[i].setText(cleanName);
                }
            }
        }
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * CẬP NHẬT DANH SÁCH NGƯỜI CHƠI ONLINE (PANEL TRÁI)
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: Được gọi từ handleGameMessage khi nhận:
     * "PLAYER_LIST|user1:status:pts|user2:status:pts|..."
     * 
     * 📤 GỬI: KHÔNG gửi message nào
     * 
     * Logic: Parse format "username:status:points" và hiển thị "name (status)"
     * Loại bỏ chính mình khỏi list
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void updateOnlineList(String[] players) {
        SwingUtilities.invokeLater(() -> {
            onlineListModel.clear();
            for (String player : players) {
                if (player == null || player.isEmpty())
                    continue;
                String name = player;
                String status = "";
                int idx = player.indexOf(":");
                if (idx > 0) {
                    name = player.substring(0, idx);
                    status = player.substring(idx + 1);
                }
                if (!name.equals(username)) {
                    String display = status.isEmpty() ? name : (name + " (" + status + ")");
                    onlineListModel.addElement(display);
                }
            }
        });
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * MỜI NGƯỜI CHƠI VÀO PHÒNG
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: KHÔNG nhận message nào (chỉ gửi)
     * 
     * 📤 GỬI: "INVITE;targetUsername"
     * Ví dụ: "INVITE;player2"
     * 
     * Logic: Lấy người được chọn từ list online, gửi lời mời
     * Người nhận sẽ nhận được "INVITE;fromUser;roomName"
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void invitePlayer() {
        String selected = onlinePlayersList.getSelectedValue();
        if (selected != null) {
            try {
                String target = selected;
                int p = selected.indexOf(" (");
                if (p > 0)
                    target = selected.substring(0, p);
                network.sendMsg("INVITE;" + target);
                JOptionPane.showMessageDialog(this, "Đã gửi lời mời đến " + selected);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi gửi lời mời.");
            }
        } else {
            JOptionPane.showMessageDialog(this, "Chọn người chơi để mời!");
        }
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * KICK NGƯỜI CHƠI (CHỈ HOST)
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: Server có thể trả về:
     * "NOT_HOST" - nếu không phải host
     * "KICK_BLOCKED;..." - nếu game đang chạy
     * 
     * 📤 GỬI: "KICK_PLAYER;targetUsername"
     * Ví dụ: "KICK_PLAYER;player3"
     * 
     * Logic: Chỉ host mới được kick
     * Chọn người từ dropdown, gửi lệnh kick
     * Người bị kick sẽ nhận "KICKED;reason"
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void kickPlayer() {
        if (!isHost) {
            JOptionPane.showMessageDialog(this, "Chỉ chủ phòng mới có thể kick!");
            return;
        }

        String[] players = new String[6];
        int count = 0;
        for (int i = 0; i < 6; i++) {
            String name = playerNameLabels[i].getText();
            // Loại bỏ emoji ✅ hoặc ❌ nếu có
            name = name.replaceAll("^[✅❌]\\s*", "");
            if (!name.equals("[Trống]") && !name.equals(username)) {
                players[count++] = name;
            }
        }

        if (count == 0) {
            JOptionPane.showMessageDialog(this, "Không có người chơi nào để kick!");
            return;
        }

        String[] validPlayers = new String[count];
        System.arraycopy(players, 0, validPlayers, 0, count);

        String selected = (String) JOptionPane.showInputDialog(
                this,
                "Chọn người chơi để kick:",
                "Kick người chơi",
                JOptionPane.PLAIN_MESSAGE,
                null,
                validPlayers,
                validPlayers[0]);

        if (selected != null) {
            try {
                network.sendMsg("KICK_PLAYER;" + selected);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi kick người chơi.");
            }
        }
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * THOÁT KHỎI PHÒNG
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: KHÔNG nhận message trả về (chỉ gửi)
     * 
     * 📤 GỬI: "LEAVE_ROOM;roomName"
     * Ví dụ: "LEAVE_ROOM;Room1"
     * 
     * Logic: Dừng timer, gửi lệnh thoát, về LobbyScreen
     * Server sẽ removePlayer và broadcast ROOM_UPDATE cho người còn lại
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void leaveRoom() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc muốn thoát khỏi phòng?",
                "Xác nhận thoát",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                stopCountdown();
                network.sendMsg("LEAVE_ROOM;" + roomName);
                network.stopListening();
                // Quay về LobbyScreen
                new LobbyScreen(username, network).setVisible(true);
                dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi khi thoát phòng.");
            }
        }
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * BẮT ĐẦU ĐẾM NGƯỢC 10 GIÂY
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: Được gọi khi nhận "YOUR_TURN" từ server
     * 📤 GỬI: KHÔNG gửi message nào
     * 
     * Logic: Timer đếm ngược từ 10→0
     * Màu đỏ khi ≤3s
     * Nếu hết giờ, server tự động kick (nhận "ELIMINATED")
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void startCountdown() {
        stopCountdown();
        timeLeft = 10;
        lblTimer.setText("⏱ " + timeLeft + "s");

        countdownTimer = new Timer();
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                timeLeft--;
                SwingUtilities.invokeLater(() -> {
                    if (timeLeft > 0) {
                        lblTimer.setText("⏱ " + timeLeft + "s");
                        if (timeLeft <= 3) {
                            lblTimer.setForeground(Color.RED);
                        }
                    } else {
                        lblTimer.setText("⏰ HẾT GIỜ!");
                        stopCountdown();
                    }
                });
            }
        }, 1000, 1000);
    }

    /**
     * ───────────────────────────────────────────────────────────────────────────
     * DỪNG ĐẾM NGƯỢC
     * ───────────────────────────────────────────────────────────────────────────
     * 
     * 📨 NHẬN: Được gọi khi nhận "WAIT" hoặc "END" từ server
     * 📤 GỬI: KHÔNG gửi message nào
     * 
     * Logic: Hủy timer, reset timeLeft về 10
     * 
     * ───────────────────────────────────────────────────────────────────────────
     */
    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        timeLeft = 10;
    }
}
