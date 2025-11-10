package client;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class LobbyScreen extends JFrame {
    private String username;
    private NetworkHandler network;
    private JTextArea playerListArea;

    public LobbyScreen(String username, NetworkHandler network) {
        this.username = username;
        this.network = network;

        setTitle("Sảnh chờ - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ===== Panel trái: nút tạo / tham gia phòng =====
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        JButton btnCreate = new JButton("Tạo phòng");
        JButton btnJoin = new JButton("Tham gia phòng");
        leftPanel.add(btnCreate);
        leftPanel.add(btnJoin);

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
            String room = JOptionPane.showInputDialog(this, "Nhập tên phòng:");
            if (room != null && !room.isEmpty()) {
                try {
                    network.sendMsg("JOIN;" + room);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "❌ Lỗi khi gửi yêu cầu tham gia phòng.");
                }
            }
        });
    }

    private void handleServerMessage(String msg) {
        System.out.println("📨 [Lobby] Nhận: " + msg);

        if (msg.startsWith("PLAYER_LIST|")) {
            String players = msg.substring("PLAYER_LIST|".length());
            SwingUtilities.invokeLater(() -> playerListArea.setText(players.replace("|", "\n")));
        } else if (msg.startsWith("ROOM_CREATED;")) {
            String roomName = msg.split(";")[1];
            network.stopListening();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "🏠 Đã tạo phòng: " + roomName);
                switchToGame(roomName, true);
            });
        } else if (msg.startsWith("JOIN_OK;")) {
            String roomName = msg.split(";")[1];
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "✅ Đã vào phòng: " + roomName);
                switchToGame(roomName, false);
            });
        } else if (msg.startsWith("JOIN_FAIL")) {
            JOptionPane.showMessageDialog(this, "❌ Không tìm thấy phòng!");
        }
    }

    private void switchToGame(String roomName, boolean isHost) {
        // Ngừng lắng nghe ở lobby
        new GameScreen(username, network, isHost, roomName).setVisible(true);
        dispose();
    }
}