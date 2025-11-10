package client;

import java.io.*;
import java.net.*;

public class NetworkHandler {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread listenThread;

    public interface MessageHandler {
        void onMessage(String msg);
    }

    public NetworkHandler(String host, int port) throws IOException {
        // Cần thay đổi IP (192.168.1.122) nếu server chạy ở máy khác
        socket = new Socket(host, port);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    public boolean login(String username, String password) throws IOException {
        out.writeUTF("LOGIN;" + username + ";" + password);
        String response = in.readUTF();
        return response.equals("LOGIN_OK");
    }

    public void sendMsg(String msg) throws IOException {
        out.writeUTF(msg);
    }

    public void startListening(MessageHandler handler) {
        stopListening(); // Ngừng luồng cũ nếu có
        listenThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String msg = in.readUTF();
                    handler.onMessage(msg);
                }
            } catch (IOException e) {
                System.out.println("⚠️ Kết nối bị ngắt khi lắng nghe.");
            }
        });
        listenThread.start();
    }

    public void stopListening() {
        if (listenThread != null && listenThread.isAlive()) {
            listenThread.interrupt();
        }
    }

    public void close() throws IOException {
        stopListening();
        socket.close();
    }
}