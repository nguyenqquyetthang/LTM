package client;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class GameScreen extends JFrame {
    private String username;
    private NetworkHandler network;
    private boolean isHost;
    private String roomName;

    private JLabel[] cardLabels = new JLabel[3];
    private JButton btnStart;
    private JButton btnDraw;
    private JLabel lblTurnInfo;
    private JLabel lblTimer;
    private boolean canDraw = false;
    private int cardsDrawn = 0;
    private List<Integer> drawnCards = new ArrayList<>();
    private Timer countdownTimer;
    private int timeLeft = 10;

    public GameScreen(String username, NetworkHandler network, boolean isHost, String roomName) {
        this.username = username;
        this.network = network;
        this.isHost = isHost;
        this.roomName = roomName;

        setTitle("Phòng " + roomName + " - " + username + (isHost ? " (Chủ phòng)" : ""));
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ===== Panel trên: Thông tin lượt và timer =====
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        lblTurnInfo = new JLabel("Chờ bắt đầu...", SwingConstants.CENTER);
        lblTurnInfo.setFont(new Font("Arial", Font.BOLD, 16));
        lblTimer = new JLabel("", SwingConstants.CENTER);
        lblTimer.setFont(new Font("Arial", Font.BOLD, 20));
        lblTimer.setForeground(Color.RED);
        topPanel.add(lblTurnInfo);
        topPanel.add(lblTimer);

        // ===== Bàn chơi =====
        JPanel gamePanel = new JPanel(new GridLayout(1, 3, 10, 10));
        for (int i = 0; i < 3; i++) {
            cardLabels[i] = new JLabel("[Chưa rút]", SwingConstants.CENTER);
            cardLabels[i].setOpaque(true);
            cardLabels[i].setBackground(Color.LIGHT_GRAY);
            gamePanel.add(cardLabels[i]);
        }

        // ===== Nút điều khiển =====
        JPanel bottomPanel = new JPanel();
        btnStart = new JButton("Bắt đầu");

        btnDraw = new JButton("Rút bài");

        btnStart.setEnabled(isHost);
        btnDraw.setEnabled(false);

        bottomPanel.add(btnStart);
        bottomPanel.add(btnDraw);

        add(topPanel, BorderLayout.NORTH);
        add(gamePanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // ===== Lắng nghe server =====
        network.startListening(this::handleGameMessage);

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
                    btnDraw.setEnabled(false); // Tạm thời tắt cho đến khi server trả kết quả
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "❌ Lỗi gửi yêu cầu rút bài.");
                }
            }
        });

        setVisible(true);
    }

    private void handleGameMessage(String msg) {
        System.out.println("🎮 [Game] Nhận: " + msg);

        if (msg.startsWith("READY")) {
            SwingUtilities.invokeLater(() -> {
                for (JLabel label : cardLabels)
                    label.setText("[Chưa rút]");
                cardsDrawn = 0;
                drawnCards.clear();
                canDraw = false;
                btnDraw.setEnabled(false);
                lblTurnInfo.setText("Chờ lượt...");
                JOptionPane.showMessageDialog(this, "Trò chơi bắt đầu!");
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
            int card = Integer.parseInt(msg.split(";")[1]);
            SwingUtilities.invokeLater(() -> updateCard(card));
        } else if (msg.equals("NOT_YOUR_TURN")) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "⚠️ Chưa đến lượt bạn!");
            });
        } else if (msg.startsWith("KICKED;")) {
            String reason = msg.split(";")[1];
            SwingUtilities.invokeLater(() -> {
                stopCountdown();
                JOptionPane.showMessageDialog(this, "❌ Bạn đã bị kick: " + reason);
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
                // Có thể hiển thị danh sách người chơi nếu cần
            });
        } else if (msg.startsWith("END")) {
            SwingUtilities.invokeLater(() -> {
                stopCountdown();
                canDraw = false;
                btnDraw.setEnabled(false);
                lblTurnInfo.setText("🏁 Game kết thúc!");
                JOptionPane.showMessageDialog(this, "Game kết thúc!");
            });
        }
    }

    private void updateCard(int value) {
        for (JLabel label : cardLabels) {
            if (label.getText().equals("[Chưa rút]")) {
                label.setText("Bài: " + value);
                cardsDrawn++;
                drawnCards.add(value);
                // Sau khi rút, server sẽ gửi WAIT hoặc YOUR_TURN tùy logic
                break;
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
