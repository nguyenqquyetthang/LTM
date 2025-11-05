# LTM

RutBaiMayMan/
│
├── lib/ ← Chứa thư viện ngoài (.jar)
│ └── gson-2.10.1.jar
│
├── src/ ← Chứa toàn bộ mã nguồn Java
│ ├── server/ ← Mã phía Server
│ │ ├── ServerMain.java
│ │ ├── ClientHandler.java
│ │ ├── GameRoom.java
│ │ ├── Card.java
│ │ ├── Deck.java
│ │ ├── GameLogic.java
│ │ └── DatabaseManager.java
│ │
│ ├── client/ ← Mã phía Client
│ │ ├── ClientMain.java
│ │ ├── LoginUI.java
│ │ ├── GameUI.java
│ │ └── NetworkHandler.java
│ │
│ └── common/ ← Dùng chung cho cả Server & Client
│ ├── Message.java
│ ├── MessageType.java
│ └── Utils.java
│
├── data/ ← Dữ liệu / CSDL / Lưu kết quả
│ └── database.db ← (nếu dùng SQLite)
│
├── run_server.bat ← File chạy server nhanh
├── run_client.bat ← File chạy client nhanh
└── README.md ← Ghi chú dự án
java -cp ".;out;lib\gson-2.10.1.jar;lib\mssql-jdbc-12.8.1.jre11.jar" server.ServerMain // chạy server
java -cp ".;out;lib\gson-2.10.1.jar;lib\mssql-jdbc-12.8.1.jre11.jar" client.ClientMain // chạy client
cd C:\Users\nguye\Desktop\LTM
