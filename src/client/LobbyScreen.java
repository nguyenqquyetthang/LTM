package client;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class LobbyScreen extends JFrame {
    private String username;
    private NetworkHandler network;
    private JTextArea playerListArea;
    private DefaultListModel<String> roomsModel;
    private JList<String> roomsList;

    public LobbyScreen(String username, NetworkHandler network) {
        this.username = username;
        this.network = network;

        setTitle("Sảnh chờ - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ===== Panel trái: danh sách phòng + nút tạo / tham gia =====
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        JPanel buttonsPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        JButton btnCreate = new JButton("Tạo phòng");
        JButton btnJoin = new JButton("Tham gia phòng");
        JButton btnHistory = new JButton("Lịch sử trận");
        buttonsPanel.add(btnCreate);
        buttonsPanel.add(btnJoin);
        buttonsPanel.add(btnHistory);

        roomsModel = new DefaultListModel<>();
        roomsList = new JList<>(roomsModel);
        roomsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomsList.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel roomsPanel = new JPanel(new BorderLayout());
        roomsPanel.add(new JLabel("🧩 Phòng đang có:"), BorderLayout.NORTH);
        roomsPanel.add(new JScrollPane(roomsList), BorderLayout.CENTER);

        leftPanel.add(roomsPanel, BorderLayout.CENTER);
        leftPanel.add(buttonsPanel, BorderLayout.SOUTH);

        // ===== Panel phải: danh sách người chơi =====
        JPanel rightPanel = new JPanel(new BorderLayout());
        playerListArea = new JTextArea("Đang tải danh sách người chơi...");
        playerListArea.setWrapStyleWord(true);
        playerListArea.setLineWrap(true);
        playerListArea.setEditable(false);
        playerListArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        rightPanel.add(new JLabel("👥 Người chơi online:"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(playerListArea), BorderLayout.CENTER);

        add(leftPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // Lắng nghe tin nhắn từ server
        network.startListening(this::handleServerMessage);

        // ===== Request danh sách người online + phòng ngay khi vào lobby =====
        try {
            System.out.println("📤 [Lobby] Request GET_PLAYER_LIST");
            network.sendMsg("GET_PLAYER_LIST");
            System.out.println("📤 [Lobby] Request GET_ROOMS");
            network.sendMsg("GET_ROOMS");
        } catch (IOException e) {
            System.err.println("⚠️ Không thể request danh sách người chơi/phòng");
        }

        // Yêu cầu lại danh sách người chơi sau 500ms đề phòng message đầu bị mất khi
        // chuyển màn hình
        javax.swing.Timer retryPlayerListTimer = new javax.swing.Timer(500, ev -> {
            try {
                System.out.println("🔄 Retry GET_PLAYER_LIST");
                network.sendMsg("GET_PLAYER_LIST");
            } catch (IOException ex) {
                System.err.println("⚠️ Retry GET_PLAYER_LIST thất bại");
            }
        });
        retryPlayerListTimer.setRepeats(false);
        retryPlayerListTimer.start();

        // Yêu cầu lại danh sách phòng sau 600ms để đảm bảo đồng bộ sau khi bị kick /
        // rời phòng
        javax.swing.Timer retryRoomsTimer = new javax.swing.Timer(600, ev -> {
            try {
                System.out.println("🔄 Retry GET_ROOMS");
                network.sendMsg("GET_ROOMS");
            } catch (IOException ex) {
                System.err.println("⚠️ Retry GET_ROOMS thất bại");
            }
        });
        retryRoomsTimer.setRepeats(false);
        retryRoomsTimer.start();

        // Nút tạo phòng
        btnCreate.addActionListener(e -> {
            try {
                network.sendMsg("CREATE");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi khi gửi yêu cầu tạo phòng.");
            }
        });

        // Nút tham gia phòng
        btnJoin.addActionListener(e -> {
            String selected = roomsList.getSelectedValue();
            String room = null;
            if (selected != null && !selected.isEmpty()) {
                int idx = selected.indexOf(" (");
                room = (idx > 0) ? selected.substring(0, idx) : selected;
            } else {
                room = JOptionPane.showInputDialog(this, "Nhập tên phòng:");
            }
            if (room != null && !room.isEmpty()) {
                try {
                    network.sendMsg("JOIN;" + room);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "❌ Lỗi khi gửi yêu cầu tham gia phòng.");
                }
            }
        });

        // Nút lịch sử (danh sách)
        btnHistory.addActionListener(e -> {
            try {
                network.sendMsg("GET_HISTORY");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi khi lấy lịch sử.");
            }
        });

        // Double-click vào phòng để tham gia nhanh
        roomsList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selected = roomsList.getSelectedValue();
                    if (selected != null) {
                        String roomName = selected;
                        int idx = selected.indexOf(" (");
                        if (idx > 0)
                            roomName = selected.substring(0, idx);
                        try {
                            network.sendMsg("JOIN;" + roomName);
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(LobbyScreen.this, "❌ Lỗi khi gửi yêu cầu tham gia phòng.");
                        }
                    }
                }
            }
        });
    }

    private void handleServerMessage(String msg) {
        System.out.println("📨 [Lobby] Received: " + msg.substring(0, Math.min(50, msg.length())) + "...");

        if (msg.startsWith("PLAYER_LIST|")) {
            System.out.println("✅ [Lobby] Processing PLAYER_LIST");
            String players = msg.substring("PLAYER_LIST|".length());
            // Parse dạng username:status:points|...
            SwingUtilities.invokeLater(() -> {
                StringBuilder sb = new StringBuilder();
                String[] tokens = players.split("\\|");
                for (String t : tokens) {
                    if (t == null || t.isEmpty())
                        continue;
                    String[] parts = t.split(":");
                    if (parts.length > 0 && !parts[0].isEmpty()) {
                        String name = parts[0];
                        String status = parts.length > 1 ? parts[1] : "";
                        String points = parts.length > 2 ? parts[2] : "0";

                        sb.append(name);
                        if (!status.isEmpty())
                            sb.append(" - ").append(status);
                        sb.append(" (").append(points).append(" điểm)");
                        sb.append("\n");
                    }
                }
                String text = sb.toString();
                playerListArea.setText(text);
                System.out.println("✅ [Lobby] Updated player list: " + text.split("\\n").length + " players");
            });
        } else if (msg.startsWith("ROOMS_LIST|")) {
            System.out.println("✅ [Lobby] Processing ROOMS_LIST");
            String rooms = msg.substring("ROOMS_LIST|".length());
            SwingUtilities.invokeLater(() -> {
                roomsModel.clear();
                if (rooms != null && !rooms.isEmpty()) {
                    String[] tokens = rooms.split("\\|");
                    for (String t : tokens) {
                        if (t == null || t.isEmpty())
                            continue;
                        // t format: RoomName:count/6
                        int colon = t.indexOf(":");
                        String name = (colon > 0) ? t.substring(0, colon) : t;
                        String occ = (colon > 0) ? t.substring(colon + 1) : "";
                        if (!name.isEmpty()) {
                            String display = name + (occ.isEmpty() ? "" : " (" + occ + ")");
                            roomsModel.addElement(display);
                        }
                    }
                }
                System.out.println("✅ [Lobby] Updated rooms list: " + roomsModel.getSize() + " rooms");
            });
        } else if (msg.startsWith("ROOM_CREATED;")) {
            String roomName = msg.split(";")[1];
            network.stopListening();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "🏠 Đã tạo phòng: " + roomName);
                switchToGame(roomName, true);
            });
        } else if (msg.startsWith("JOIN_OK;")) {
            String roomName = msg.split(";")[1];
            // Ngừng lắng nghe ở Lobby trước khi chuyển sang Game để tránh mất message
            network.stopListening();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "✅ Đã vào phòng: " + roomName);
                switchToGame(roomName, false);
            });
        } else if (msg.startsWith("JOIN_FAIL")) {
            JOptionPane.showMessageDialog(this, "❌ Không tìm thấy phòng!");
        } else if (msg.startsWith("INVITE;")) {
            // Nhận lời mời: INVITE;fromUser;roomName
            String[] parts = msg.split(";");
            if (parts.length >= 3) {
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
            }
        } else if (msg.startsWith("HISTORY_DATA|")) {
            String data = msg.substring("HISTORY_DATA|".length());
            SwingUtilities.invokeLater(() -> showHistoryDialog(data));
        } else if (msg.startsWith("MATCH_DETAIL_DATA|")) {
            String data = msg.substring("MATCH_DETAIL_DATA|".length());
            SwingUtilities.invokeLater(() -> showMatchDetailDialog(data));
        }
    }

    private void switchToGame(String roomName, boolean isHost) {
        // Ngừng lắng nghe ở lobby
        new GameScreen(username, network, isHost, roomName).setVisible(true);
        dispose();
    }

    private void showHistoryDialog(String data) {
        JDialog dialog = new JDialog(this, "📜 Lịch sử trận đấu", true);
        dialog.setSize(700, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        String[] columns = { "MatchID", "Bắt đầu", "Kết thúc", "Số người", "Người thắng" };
        String[] lines = data.split("\n");
        String[][] rows = new String[lines.length][5];
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty())
                continue;
            String[] fields = lines[i].split("\\|");
            for (int j = 0; j < 5 && j < fields.length; j++) {
                rows[i][j] = fields[j];
            }
        }

        // Dùng model không cho sửa ô nhưng vẫn chọn & double-click
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(rows, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // khóa chỉnh sửa
            }
        };
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) { // double click
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        String matchIdStr = (String) table.getValueAt(row, 0);
                        if (matchIdStr != null && !matchIdStr.isEmpty()) {
                            try {
                                network.sendMsg("GET_MATCH_DETAIL;" + matchIdStr);
                            } catch (IOException ex) {
                                JOptionPane.showMessageDialog(dialog, "❌ Lỗi yêu cầu chi tiết trận.");
                            }
                        }
                    }
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Đóng");
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel btnPanel = new JPanel();
        btnPanel.add(closeBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void showMatchDetailDialog(String data) {
        JDialog dialog = new JDialog(this, "🧾 Chi tiết trận", true);
        dialog.setSize(750, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));

        StringBuilder out = new StringBuilder();
        String[] lines = data.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty())
                continue;
            if (line.startsWith("MATCH|")) {
                String[] p = line.split("\\|");
                if (p.length >= 6) {
                    out.append("Trận #").append(p[1]).append("  Người chơi: ").append(p[4])
                            .append("  Thắng: ").append(p[5]).append("\n");
                    out.append("Thời gian: ").append(p[2]).append(" -> ").append(p[3]).append("\n\n");
                    out.append("Xếp hạng:\n");
                }
            } else if (line.startsWith("RESULT|")) {
                String[] p = line.split("\\|");
                if (p.length >= 5) {
                    out.append(String.format("%2s. %-15s %-15s %s\n", p[1], p[2], p[3], p[4]));
                }
            }
        }

        area.setText(out.toString());
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);

        JButton closeBtn = new JButton("Đóng");
        closeBtn.addActionListener(ev -> dialog.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeBtn);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    // Add table row click inside history dialog without modifying original method:
    // We'll override the showHistoryDialog by adding mouse listener to table.
}