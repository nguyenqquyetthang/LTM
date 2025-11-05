package client;

import java.io.*;
import java.net.*;
import com.google.gson.*;
import common.Message;
import common.MessageType;

public class ClientMain {
    private final String host = "localhost";
    private final int port = 2206;
    Socket socket;
    PrintWriter out;
    BufferedReader in;
    Gson gson = new Gson();
    ConsoleUI ui;
    public String username;

    public static void main(String[] args) {
        new ClientMain().start();
    }

    public void start() {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            ui = new ConsoleUI(this);

            // Thread đọc message từ server
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        Message m = gson.fromJson(line, Message.class);
                        if (m.type == MessageType.LOGIN_OK) {
                            this.username = m.from;
                        }
                        ui.onReceive(m);
                    }
                } catch (Exception e) {
                    System.out.println("Disconnected from server.");
                }
            }).start();

            // Start UI
            ui.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(Message m) {
        if (m.from != null && !m.from.isBlank())
            this.username = m.from;
        String s = gson.toJson(m);
        out.println(s);
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
        }
        System.exit(0);
    }
}
