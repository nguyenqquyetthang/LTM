package common;

import java.util.Map;

public class Message {
    public MessageType type;
    public String from;
    public String to;
    public Map<String, Object> data;

    public Message() {
    }

    public Message(MessageType type, String from, String to, Map<String, Object> data) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.data = data;
    }
}
