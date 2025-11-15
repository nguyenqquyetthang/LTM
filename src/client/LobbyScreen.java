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
        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        JButton btnCreate = new JButton("Tạo phòng");
        JButton btnJoin = new JButton("Tham gia phòng");
        buttonsPanel.add(btnCreate);
        buttonsPanel.add(btnJoin);

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
            network.sendMsg("GET_PLAYER_LIST");
            network.sendMsg("GET_ROOMS");
        } catch (IOException e) {
            System.err.println("⚠️ Không thể request danh sách người chơi/phòng");
        }

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
        System.out.println("📨 [Lobby] Nhận: " + msg);

        if (msg.startsWith("PLAYER_LIST|")) {
            String players = msg.substring("PLAYER_LIST|".length());
            // Parse dạng username:status|...
            SwingUtilities.invokeLater(() -> {
                StringBuilder sb = new StringBuilder();
                String[] tokens = players.split("\\|");
                for (String t : tokens) {
                    if (t == null || t.isEmpty())
                        continue;
                    String name = t;
                    String status = "";
                    int idx = t.indexOf(":");
                    if (idx > 0) {
                        name = t.substring(0, idx);
                        status = t.substring(idx + 1);
                    }
                    if (!name.isEmpty()) {
                        sb.append(name);
                        if (!status.isEmpty())
                            sb.append(" - ").append(status);
                        sb.append("\n");
                    }
                }
                playerListArea.setText(sb.toString());
            });
        } else if (msg.startsWith("ROOMS_LIST|")) {
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
        }
    }

    private void switchToGame(String roomName, boolean isHost) {
        // Ngừng lắng nghe ở lobby
        new GameScreen(username, network, isHost, roomName).setVisible(true);
        dispose();
    }
}