package client;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class LoginScreen extends JFrame {
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnLogin;

    public LoginScreen() {
        setTitle("Đăng nhập");
        setSize(400, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(4, 1, 10, 10));

        txtUsername = new JTextField();
        txtPassword = new JPasswordField();
        btnLogin = new JButton("Đăng nhập");

        add(new JLabel("Tên đăng nhập:", SwingConstants.CENTER));
        add(txtUsername);
        add(new JLabel("Mật khẩu:", SwingConstants.CENTER));
        add(txtPassword);
        add(btnLogin);

        btnLogin.addActionListener(e -> loginAction());
    }

    private void loginAction() {
        try {
            // Đảm bảo IP và Port này khớp với Server.java
            NetworkHandler network = new NetworkHandler("192.168.0.3", 5000);
            String username = txtUsername.getText();
            String password = new String(txtPassword.getPassword());

            if (network.login(username, password)) {
                JOptionPane.showMessageDialog(this, "✅ Đăng nhập thành công!");
                new LobbyScreen(username, network).setVisible(true);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "❌ Sai tài khoản hoặc mật khẩu.");
                network.close();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "⚠️ Không thể kết nối đến server.");
        }
    }
}