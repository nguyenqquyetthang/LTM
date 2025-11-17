# 📦 REFACTORING - CHIA NHỎ CÁC FILE SERVER

## 🎯 Mục đích

Chia nhỏ các file server lớn (800+ dòng) thành các component nhỏ hơn, dễ bảo trì và mở rộng.

## 📁 Cấu trúc mới

### ✅ Đã tạo các Helper Classes

#### 1. **GameLogic.java** (299 dòng)

**Vai trò:** Xử lý logic game - rút bài, tính điểm, xếp hạng

**Chức năng chính:**

- `initializeNewRound()` - Khởi tạo ván mới, tạo & xáo bài
- `drawCardForPlayer()` - Rút bài cho người chơi (tối đa 3 lá)
- `calculateAllRanks()` - Tính HandRank cho tất cả người chơi
- `calculateModScores()` - Tính điểm modulo cho HighCard
- `determineWinner()` - Xác định người thắng
- `sortPlayersByRank()` - Sắp xếp người chơi theo thứ hạng
- `buildShowHandsMessage()` - Tạo message SHOW_HANDS_ALL
- `buildHandRanksMessage()` - Tạo message HAND_RANKS

**Cách sử dụng:**

```java
// Trong RoomThread.java
private GameLogic gameLogic = new GameLogic();

// Khi bắt đầu ván
gameLogic.initializeNewRound(players);

// Khi rút bài
Card drawn = gameLogic.drawCardForPlayer(username);
if (drawn != null) {
    player.sendMessage("DRAW;" + drawn.toString());
}

// Khi kết thúc ván
Map<String, HandRank> ranks = gameLogic.calculateAllRanks();
Map<String, Integer> modScores = gameLogic.calculateModScores(ranks);
GameLogic.WinnerResult winner = gameLogic.determineWinner(ranks, modScores);
```

---

#### 2. **TurnManager.java** (186 dòng)

**Vai trò:** Quản lý lượt chơi, timer, timeout

**Chức năng chính:**

- `initializeTurn()` - Khởi tạo lượt đầu (host đi trước)
- `notifyCurrentTurn()` - Gửi YOUR_TURN/WAIT cho người chơi
- `nextTurn()` - Chuyển lượt (ngược chiều kim đồng hồ)
- `startTurnTimer()` - Bắt đầu timer 10 giây
- `cancelTimer()` - Hủy timer
- `handleTimeoutPlayer()` - Xử lý timeout, trả về username bị loại
- `adjustTurnAfterRemoval()` - Điều chỉnh currentTurn sau khi xóa người

**Cách sử dụng:**

```java
// Trong RoomThread.java
private TurnManager turnManager = new TurnManager(gameLogic);

// Trong constructor
turnManager.setRoomThread(this);

// Khi bắt đầu game
turnManager.initializeTurn(hostIndex);
turnManager.notifyCurrentTurn(players);
turnManager.startTurnTimer();

// Khi chuyển lượt
boolean hasNext = turnManager.nextTurn(players);
if (hasNext) {
    turnManager.notifyCurrentTurn(players);
    turnManager.startTurnTimer();
} else {
    endGame();
}

// Khi timeout
String timedOutUser = turnManager.handleTimeoutPlayer(players, hostIndex);
// ... xử lý kick người chơi
```

---

#### 3. **MessageHandler.java** (217 dòng)

**Vai trò:** Xử lý các loại messages từ client

**Chức năng chính:**

- `handleGetPlayerList()` - Xử lý GET_PLAYER_LIST
- `handleGetRooms()` - Xử lý GET_ROOMS
- `handleGetHistory()` - Xử lý GET_HISTORY
- `handleGetMatchDetail()` - Xử lý GET_MATCH_DETAIL;matchId
- `handleCreateRoom()` - Xử lý CREATE
- `handleJoinRoom()` - Xử lý JOIN;roomName
- `handleLeaveRoom()` - Xử lý LEAVE
- `handleReady()` - Xử lý READY;roomName
- `handleStartGame()` - Xử lý START;roomName
- `handleDrawCard()` - Xử lý DRAW;roomName
- `handleKickPlayer()` - Xử lý KICK_PLAYER;roomName;target
- `handleGetRoomUpdate()` - Xử lý GET_ROOM_UPDATE;roomName

**Cách sử dụng:**

```java
// Trong ClientHandler.java
private MessageHandler messageHandler;

// Trong constructor
messageHandler = new MessageHandler(db, rooms, activeClients);

// Trong message loop
if (msg.equalsIgnoreCase("GET_PLAYER_LIST")) {
    String response = messageHandler.handleGetPlayerList();
    sendMessage(response);
}

if (msg.equalsIgnoreCase("CREATE")) {
    String[] result = messageHandler.handleCreateRoom(this);
    if (result[0].equals("OK")) {
        sendMessage("ROOM_CREATED;" + result[1]);
    } else {
        sendMessage("CREATE_FAIL;" + result[1]);
    }
}

if (msg.startsWith("JOIN;")) {
    String roomName = msg.split(";")[1];
    String[] result = messageHandler.handleJoinRoom(this, roomName);
    if (result[0].equals("OK")) {
        sendMessage("JOIN_OK;" + result[1]);
    } else if (result[0].equals("FULL")) {
        sendMessage("ROOM_FULL");
    } else {
        sendMessage("JOIN_FAIL");
    }
}
```

---

#### 4. **DatabaseHelper.java** (80 dòng)

**Vai trò:** Đóng gói các thao tác database thường dùng

**Chức năng chính:**

- `authenticateOrCreate()` - Xác thực hoặc tạo tài khoản mới
- `getPlayerScore()` - Lấy điểm của người chơi
- `updatePlayerScore()` - Cập nhật điểm

**Cách sử dụng:**

```java
// Trong ClientHandler.java
private DatabaseHelper dbHelper = new DatabaseHelper(db);

// Khi đăng nhập
DatabaseHelper.LoginResult result = dbHelper.authenticateOrCreate(username, password);
if (result.success) {
    if (result.isNewAccount) {
        System.out.println("✅ Tạo tài khoản mới: " + username);
    }
    int score = dbHelper.getPlayerScore(username);
    Server.playerScores.put(username, score);
    sendMessage("LOGIN_OK");
} else {
    sendMessage("LOGIN_FAIL");
}

// Khi cập nhật điểm
dbHelper.updatePlayerScore(username, points);
```

---

## 🔄 Lộ trình Refactoring

### ✅ Hoàn thành (Phase 1)

- [x] Tạo GameLogic.java
- [x] Tạo TurnManager.java
- [x] Tạo MessageHandler.java
- [x] Tạo DatabaseHelper.java

### 📝 Kế hoạch tiếp theo (Phase 2)

- [ ] **Refactor RoomThread.java** (798 dòng → ~400 dòng)

  - Thay thế logic rút bài bằng `gameLogic`
  - Thay thế quản lý lượt bằng `turnManager`
  - Giữ lại logic quản lý phòng & người chơi

- [ ] **Refactor ClientHandler.java** (443 dòng → ~250 dòng)
  - Thay thế message handling bằng `messageHandler`
  - Thay thế database operations bằng `dbHelper`
  - Giữ lại socket I/O và authentication flow

### 🚀 Tương lai (Phase 3)

- [ ] **Tạo RoomManager.java**

  - Quản lý danh sách phòng
  - Broadcast room updates

- [ ] **Tạo ScoreManager.java**
  - Tập trung logic tính điểm
  - Persist scores to database

---

## 📊 So sánh Before/After

### Before

```
RoomThread.java       798 dòng  (quá lớn)
ClientHandler.java    443 dòng  (phức tạp)
Server.java           ~200 dòng
```

### After (thực tế)

```
RoomThread.java         798 dòng  (chưa refactor)
ClientHandler.java      443 dòng  (chưa refactor)
Server.java            ~200 dòng

GameLogic.java          299 dòng  (mới)
TurnManager.java        186 dòng  (mới)
MessageHandler.java     217 dòng  (mới)
DatabaseHelper.java      80 dòng  (mới)

Database.java            95 dòng  (↓ 71% từ 328 dòng)
DatabaseConnection.java  90 dòng  (mới)
PlayerRepository.java   145 dòng  (mới)
MatchRepository.java    248 dòng  (mới)
```

**Lợi ích:**

- ✅ Dễ bảo trì hơn (mỗi class có trách nhiệm riêng)
- ✅ Dễ test hơn (test từng component độc lập)
- ✅ Dễ mở rộng hơn (thêm tính năng không ảnh hưởng file khác)
- ✅ Code rõ ràng hơn (đọc hiểu nhanh hơn)

---

## ⚠️ Lưu ý

1. **Các helper class đã tạo nhưng chưa tích hợp** vào RoomThread và ClientHandler
2. **Code hiện tại vẫn hoạt động bình thường** - không có breaking changes
3. **Để tích hợp sau này**, chỉ cần thay thế logic cũ bằng gọi helper methods
4. **Không cần refactor ngay** - có thể làm dần từng phần

---

## 📚 Tài liệu tham khảo

- **Protocol messages:** Xem header comments trong từng file .java
- **Game flow:** Xem comments trong RoomThread.java
- **Database schema:** Xem Database.java

---

**Tạo bởi:** Refactoring Session - 2024  
**Mục tiêu:** Cải thiện maintainability và code quality
