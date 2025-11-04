package client;

import java.util.*;
import java.io.IOException;
import com.google.gson.*;
import common.Card;
import common.Message;
import common.MessageType;

public class ConsoleUI {
    private final ClientMain client;
    private final Scanner sc = new Scanner(System.in);
    private final Gson gson = new Gson();

    public ConsoleUI(ClientMain client) {
        this.client = client;
    }

    public void start() {
        System.out.print("Username: ");
        String user = sc.nextLine().trim();
        client.sendMessage(new Message(MessageType.LOGIN, user, "server", Map.of("username", user)));
        System.out.println("Waiting for server...");

        while (true) {
            System.out.println("Commands: list, invite <user>, start <roomId>, quit");
            String line = sc.nextLine().trim();

            if (line.equalsIgnoreCase("list")) {
                System.out.println("Waiting for online list from server...");
            } else if (line.startsWith("invite ")) {
                String to = line.substring(7).trim();
                if (!to.isEmpty())
                    client.sendMessage(new Message(MessageType.INVITE, client.username, to, Map.of("to", to)));
            } else if (line.startsWith("start ")) {
                String roomId = line.substring(6).trim();
                if (!roomId.isEmpty())
                    client.sendMessage(
                            new Message(MessageType.START_GAME, client.username, "server", Map.of("roomId", roomId)));
            } else if (line.equalsIgnoreCase("quit")) {
                System.out.println("Bye");
                client.close();
                break;
            }
        }
    }

    public void onReceive(Message m) {
        switch (m.type) {
            case LOGIN_OK -> System.out.println("Login success.");
            case LOGIN_FAIL -> System.out.println("Login failed: " + m.data.get("reason"));
            case ONLINE_LIST -> {
                List<?> list = (List<?>) m.data.get("list");
                System.out.println("Online players:");
                for (Object o : list)
                    System.out.println(" - " + ((Map) o).get("username"));
            }
            case INVITE -> handleInvite(m);
            case JOIN_ROOM -> System.out.println("Joined room " + m.data.get("roomId"));
            case DEAL_CARDS -> System.out.println("Your cards: " + gson.toJson(m.data.get("cards")));
            case ROOM_UPDATE -> System.out.println("Room update: " + gson.toJson(m.data));
            case GAME_RESULT -> System.out.println("Result: " + gson.toJson(m.data.get("ranking")));
            case INFO -> System.out.println("Info: " + m.data.get("text"));
            default -> System.out.println("Recv: " + m.type);
        }
    }

    private void handleInvite(Message m) {
        String inviter = m.from;
        System.out.println("Invite from " + inviter + ". Accept? (y/n) [15s to respond]");

        // Thread để xử lý input + timeout
        new Thread(() -> {
            String resp = "REJECT";
            try {
                long start = System.currentTimeMillis();
                Scanner scanner = new Scanner(System.in);
                while (System.currentTimeMillis() - start < 15_000) {
                    if (System.in.available() > 0) {
                        String ans = scanner.nextLine().trim();
                        if (ans.equalsIgnoreCase("y"))
                            resp = "OK";
                        break;
                    }
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException ignored) {
            }
            client.sendMessage(new Message(MessageType.INVITE_RESPONSE, client.username, inviter,
                    Map.of("response", resp, "to", inviter)));
        }).start();
    }
}
