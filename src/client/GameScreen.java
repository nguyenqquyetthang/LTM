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
    // Cache ảnh lá bài để tránh load lại nhiều lần
    private final Map<String, ImageIcon> cardIconCache = new HashMap<>();
    private static final int CARD_IMG_W = 50;
    private static final int CARD_IMG_H = 75;
    private static final String CARD_IMG_BASE = "PNG-cards-1.3/PNG-cards-1.3";

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

        // ===== Panel trái: Danh sách người chơi online =====
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

        // ===== Panel giữa: Bàn chơi với 6 vị trí =====
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

        // Tạo 6 vị trí ngồi (top, right-top, right-bottom, bottom, left-bottom,
        // left-top)
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
        btnDraw = new JButton("Rút bài");
        btnKick = new JButton("Kick người chơi");
        JButton btnLeave = new JButton("❌ Thoát phòng");

        btnStart.setEnabled(isHost);
        btnDraw.setEnabled(false);
        btnKick.setEnabled(isHost);
        btnLeave.setForeground(Color.RED);

        bottomPanel.add(btnStart);
        bottomPanel.add(btnDraw);
        if (isHost) {
            bottomPanel.add(btnKick);
        }
        bottomPanel.add(btnLeave);

        centerPanel.add(topPanel, BorderLayout.NORTH);
        centerPanel.add(tablePanel, BorderLayout.CENTER);
        centerPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);

        // ===== Lắng nghe server =====
        network.startListening(this::handleGameMessage);

        // ===== Request danh sách người online ngay khi vào phòng =====
        try {
            network.sendMsg("GET_PLAYER_LIST");
            // Yêu cầu trạng thái phòng ngay khi vào để không bị lỡ ROOM_UPDATE trước đó
            network.sendMsg("GET_ROOM_UPDATE;" + roomName);
        } catch (IOException e) {
            System.err.println("⚠️ Không thể request danh sách người chơi");
        }

        // ===== Nút "Bắt đầu" =====
        btnStart.addActionListener(e -> {
            try {
                network.sendMsg("START;" + roomName);
                btnStart.setEnabled(false);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi gửi lệnh bắt đầu.");
            }
        });

        // ===== Nút "Rút bài" =====
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

        // ===== Nút "Kick" =====
        btnKick.addActionListener(e -> kickPlayer());

        // ===== Nút "Thoát phòng" =====
        btnLeave.addActionListener(e -> leaveRoom());

        setVisible(true);
    }

    private void handleGameMessage(String msg) {
        System.out.println("🎮 [Game] Nhận: " + msg);

        if (msg.startsWith("READY")) {
            SwingUtilities.invokeLater(() -> {
                // Reset tất cả bài (text + icon)
                for (int i = 0; i < 6; i++) {
                    for (int j = 0; j < 3; j++) {
                        resetCardLabel(cardLabels[i][j]);
                    }
                }
                cardsDrawn = 0;
                drawnCards.clear();
                canDraw = false;
                btnDraw.setEnabled(false);
                lblTurnInfo.setText("Chờ lượt...");
                JOptionPane.showMessageDialog(this, "Trò chơi bắt đầu!");
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
                    // Tìm vị trí user trong playerNameLabels
                    int pos = -1;
                    for (int i = 0; i < 6; i++) {
                        if (playerNameLabels[i].getText().equals(user)) {
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
            SwingUtilities.invokeLater(() -> {
                stopCountdown();
                // Stop listening trước khi quay về lobby
                network.stopListening();
                JOptionPane.showMessageDialog(this, "❌ Bạn đã bị kick: " + reason);
                // Quay về LobbyScreen
                new LobbyScreen(username, network).setVisible(true);
                dispose();
            });
        } else if (msg.startsWith("YOU_ARE_HOST")) {
            SwingUtilities.invokeLater(() -> {
                isHost = true;
                setTitle("Phòng " + roomName + " - " + username + " (Chủ phòng)");
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
        } else if (msg.equals("ROOM_FULL")) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "❌ Phòng đã đầy (tối đa 6 người)!");
            });
        } else if (msg.equals("NOT_HOST")) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "❌ Chỉ chủ phòng mới có quyền này!");
            });
        } else if (msg.startsWith("RANKING|")) {
            String payload = msg.substring("RANKING|".length());
            SwingUtilities.invokeLater(() -> {
                StringBuilder rankingMsg = new StringBuilder("🏆 BẢNG XẾP HẠNG 🏆\n\n");
                String[] entries = payload.split("\\|");
                for (String entry : entries) {
                    if (!entry.isEmpty()) {
                        rankingMsg.append(entry).append("\n");
                    }
                }
                JOptionPane.showMessageDialog(this, rankingMsg.toString(), "Xếp hạng", JOptionPane.INFORMATION_MESSAGE);
            });
        } else if (msg.startsWith("END")) {
            SwingUtilities.invokeLater(() -> {
                stopCountdown();
                canDraw = false;
                btnDraw.setEnabled(false);
                lblTurnInfo.setText("🏁 Game kết thúc!");
            });
        }
    }

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

    // ===== Helpers ảnh lá bài =====
    private void resetCardLabel(JLabel lbl) {
        lbl.setText("");
        lbl.setIcon(null);
        lbl.setBackground(Color.WHITE);
    }

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

    // Cập nhật danh sách người chơi trong phòng
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
        });
    }

    // Cập nhật danh sách người chơi online
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

    // Mời người chơi
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

    // Kick người chơi (chỉ host)
    private void kickPlayer() {
        if (!isHost) {
            JOptionPane.showMessageDialog(this, "Chỉ chủ phòng mới có thể kick!");
            return;
        }

        String[] players = new String[6];
        int count = 0;
        for (int i = 0; i < 6; i++) {
            String name = playerNameLabels[i].getText();
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

    // Thoát khỏi phòng
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

    // Bắt đầu countdown 10s
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

    // Dừng countdown
    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        timeLeft = 10;
    }
}
