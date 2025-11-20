# CẤU TRÚC SERVER - GAME BÀI 3 LÁ

## 📁 CẤU TRÚC FOLDERS

```
src/server/
├── 📂 core/           (3 files) - Lõi hệ thống
├── 📂 handlers/       (5 files) - Xử lý commands từ client
├── 📂 managers/       (9 files) - Quản lý game state & players
├── 📂 database/       (5 files) - Database operations
├── 📂 game/           (2 files) - Game logic
└── 📂 models/         (4 files) - Data structures
```

---

## 📂 CORE FILES (package server.core)

### **Server.java** - Server Chính

- **Vai trò:** Điểm khởi đầu của ứng dụng server, "trọng tài" chính của hệ thống
- **Chức năng:**
  - Mở ServerSocket trên port 5000, lắng nghe kết nối từ client
  - Tạo ClientHandler mới cho mỗi kết nối (multi-threading)
  - Quản lý danh sách: `activeClients`, `rooms`, `playerScores`, `accounts`
  - Broadcast thông tin: `broadcastPlayerList()`, `broadcastRoomsList()`
  - Tìm số phòng trống nhỏ nhất: `findSmallestAvailableRoomNumber()`
- **Dữ liệu quan trọng:**
  - `Map<String, RoomThread> rooms` - Danh sách phòng đang hoạt động
  - `List<ClientHandler> activeClients` - Người chơi online
  - `Map<String, Integer> playerScores` - Cache điểm của người chơi

### **ClientHandler.java** - Xử Lý Mỗi Client

- **Vai trò:** Thread riêng cho mỗi client kết nối, route messages đến đúng handler
- **Chức năng:**
  - Quản lý vòng đời kết nối: login → xử lý messages → cleanup khi disconnect
  - Route messages: LOGIN, GET\_\*, CREATE, JOIN, READY, START, DRAW, KICK, INVITE
  - Quản lý trạng thái: `status` (free/busy/playing), `currentRoom`
  - Gửi/nhận messages qua `DataInputStream`/`DataOutputStream`
- **Flow xử lý:**
  1. Đọc LOGIN → AuthenticationHandler
  2. Loop nhận messages → route đến handlers
  3. Disconnect → removePlayer, cleanup resources

### **RoomThread.java** - Quản Lý Phòng Chơi

- **Vai trò:** Thread riêng cho mỗi phòng, điều phối game trong phòng đó
- **Chức năng:**
  - Quản lý players, host, ready status
  - Điều phối game: start → rút bài → kết thúc → lưu kết quả
  - Kick player, timeout handling
  - Broadcast messages trong phòng
- **Dependencies:** GameFlowManager, RoomPlayerManager, KickManager, BroadcastManager
- **Lifecycle:** Tạo khi CREATE room → chạy cho đến khi phòng trống → interrupt

---

## 🎯 HANDLERS (package server.handlers)

### **AuthenticationHandler.java** - Xác Thực Người Dùng

- **Vai trò:** Xử lý đăng nhập và tạo tài khoản mới
- **Chức năng:**
  - `handleLogin(username, password)` - Kiểm tra/tạo tài khoản
  - Tự động tạo tài khoản mới nếu username chưa tồn tại (demo mode)
  - Load điểm từ database cho người chơi
- **Return:** `LoginResult` (success, points)

### **RoomCommandHandler.java** - Lệnh Phòng Chơi

- **Vai trò:** Xử lý các lệnh liên quan đến phòng
- **Commands xử lý:**
  - `CREATE` - Tạo phòng mới (tên = "Room\_" + username)
  - `JOIN;roomName` - Tham gia phòng (kiểm tra full/exists)
  - `READY;roomName` - Đánh dấu sẵn sàng (guest only)
  - `START;roomName` - Bắt đầu game (host only)
- **Return:** `RoomResult` (success, roomName, status)

### **GameCommandHandler.java** - Lệnh Trong Game

- **Vai trò:** Xử lý các lệnh khi đang chơi
- **Commands xử lý:**
  - `DRAW;roomName` - Rút bài (kiểm tra lượt)
  - `KICK_PLAYER;target` - Kick người chơi (host only)
  - `INVITE;target` - Mời người vào phòng
- **Kiểm tra:** Lượt chơi, quyền host, trạng thái game

### **ProtocolHandler.java** - Xử Lý Protocol (Deprecated)

- **Vai trò:** Legacy handler, chức năng đã được chia vào các handler khác
- **Note:** Cân nhắc xóa hoặc refactor thành utility class

### **MessageHandler.java** - Route Messages (Deprecated)

- **Vai trò:** Legacy message router
- **Note:** Chức năng đã được tích hợp vào ClientHandler

---

## 👥 MANAGERS (package server.managers)

### **GameStateManager.java** - Trạng Thái Game

- **Vai trò:** Quản lý state của game hiện tại
- **State quản lý:**
  - `gameStarted` - Game đang chạy hay không
  - `matchId` - ID của trận đấu trong database
  - `gameLogic` - Instance của GameLogic (deck, hands)
  - `turnManager` - Quản lý lượt chơi
- **Methods:** `startGame()`, `endGame()`, `isGameStarted()`

### **GameFlowManager.java** - Luồng Game

- **Vai trò:** Điều phối toàn bộ flow của game từ đầu đến cuối
- **Chức năng:**
  - `startGame()` - Khởi tạo game, reset state, broadcast GAME_START
  - `drawCard(player)` - Xử lý rút bài (kiểm tra lượt, max 3 lá)
  - `nextTurn()` - Chuyển lượt (ngược chiều kim đồng hồ)
  - `endGame()` - Tính kết quả, broadcast WINNER/RANKING, lưu DB
- **Dependencies:** GameLogic, TurnManager, ScoreManager, BroadcastManager

### **RoomPlayerManager.java** - Quản Lý Người Chơi

- **Vai trò:** Quản lý danh sách người chơi trong phòng
- **Chức năng:**
  - `addPlayer()`, `removePlayer()` - Thêm/xóa người (max 6)
  - `isFull()`, `getPlayerCount()` - Kiểm tra phòng đầy
  - `isHost()`, `getHostIndex()` - Quản lý host
  - `setPlayerReady()`, `allPlayersReady()` - Trạng thái sẵn sàng
  - `updateHostAfterRemoval()` - Chọn host mới khi host cũ rời
- **Broadcast:** ROOM_UPDATE, YOU_ARE_HOST khi có thay đổi

### **TurnManager.java** - Quản Lý Lượt Chơi

- **Vai trò:** Quản lý lượt chơi và timeout
- **Chức năng:**
  - `initializeTurn(hostIndex)` - Host đi trước
  - `nextTurn()` - Chuyển lượt (ngược chiều, skip người đã rút đủ)
  - `notifyCurrentTurn()` - Broadcast YOUR_TURN/WAIT
  - `startTurnTimer()` - Timer 10 giây, callback timeout
  - `handleTimeoutPlayer()` - Xử lý khi hết giờ
- **Timeout:** 10 giây/lượt, tự động kick và trừ 1 điểm

### **ScoreManager.java** - Quản Lý Điểm Số

- **Vai trò:** Tính toán và cập nhật điểm
- **Chức năng:**
  - `updateScores(winner, losers, timeoutPlayers)` - Cập nhật điểm sau ván
  - `applyTimeoutPenalty(username)` - Trừ 1 điểm cho timeout
  - `buildRankingMessage()` - Tạo message RANKING với điểm
- **Logic điểm:**
  - Người thắng: +N (N = tổng số người - 1)
  - Người thua: -1
  - Timeout: -1 (đã trừ khi timeout)

### **KickManager.java** - Xử Lý Kick

- **Vai trò:** Kick người chơi (do host hoặc timeout)
- **Chức năng:**
  - `kickPlayer(target, requester)` - Host kick người (không được khi đang chơi)
  - `handleTimeout()` - Timeout tự động kick
- **Kiểm tra:** Chỉ host, không kick khi game running, không kick chính mình
- **Return:** `KickResult` (status, targetPlayer), `TimeoutResult` (shouldContinue, timedOutPlayer)

### **BroadcastManager.java** - Broadcast Messages

- **Vai trò:** Gửi messages cho tất cả/một số người trong phòng
- **Chức năng:**
  - `broadcast(msg)` - Gửi cho tất cả
  - `broadcastRoomUpdate(hostIndex)` - ROOM_UPDATE|room|host|players
  - `broadcastReadyStatus()` - READY_STATUS|user1:true|user2:false|...
  - `sendRoomUpdateTo(target)` - Gửi cho 1 người cụ thể
- **Messages:** ROOM_UPDATE, READY_STATUS, GAME_START, YOUR_TURN, WAIT, etc.

### **BroadcastHelper.java** - Helper Broadcast

- **Vai trò:** Build messages để broadcast
- **Chức năng:**
  - `buildPlayerListMessage()` - PLAYER_LIST|user:status:pts|...
  - `buildRoomsListMessage()` - ROOMS_LIST|room:count/6|...
- **Usage:** Server.broadcastPlayerList(), Server.broadcastRoomsList()

---

## 💾 DATABASE (package server.database)

### **Database.java** - Database Facade

- **Vai trò:** Facade pattern, điểm truy cập duy nhất cho database operations
- **Chức năng:**
  - `loadAccounts()` - Load tất cả tài khoản (username → password)
  - `getPlayerId(username)` - Lấy ID người chơi
  - `getPlayerPoints(username)` - Lấy điểm tích lũy
  - `updatePlayerPoints(username, points)` - Cập nhật điểm
  - `createMatch(numPlayers)` - Tạo trận đấu mới, return matchId
  - `insertMatchResult()` - Lưu kết quả của từng người
  - `endMatch(matchId, winnerId)` - Kết thúc trận, ghi người thắng
  - `getMatchHistory(limit)` - Lấy lịch sử trận đấu
  - `getMatchDetail(matchId)` - Chi tiết 1 trận
  - `ensureCardsSeeded()` - Seed dữ liệu bài vào DB (lần đầu)
- **Pattern:** Delegate calls đến Repository classes

### **DatabaseConnection.java** - Connection Pool

- **Vai trô:** Quản lý connection pool đến SQL Server
- **Chức năng:**
  - `getConnection()` - Lấy connection từ pool
  - Connection string format: `jdbc:sqlserver://server:port;databaseName=...`
  - Auto-retry khi connection failed
- **Cấu hình:** Server name, database, username, password
- **Pool:** Tự động quản lý, đóng connections khi hết dùng

### **DatabaseHelper.java** - Helper Methods

- **Vai trò:** Utility methods cho DB operations
- **Chức năng:**
  - `executeUpdate(sql, params)` - Execute INSERT/UPDATE/DELETE
  - `executeQuery(sql, params)` - Execute SELECT
  - `closeResources()` - Đóng ResultSet, Statement, Connection
  - `mapRowToObject()` - Map DB row → Java object
- **Error handling:** Try-catch, log lỗi, auto-rollback

### **PlayerRepository.java** - CRUD Players

- **Vai trò:** Repository pattern cho table Players
- **Table:** `Players(PlayerID, Username, PasswordHash, TotalPoints, CreatedAt)`
- **Methods:**
  - `findByUsername(username)` - Tìm người chơi
  - `create(username, password)` - Tạo tài khoản mới
  - `updatePoints(playerId, points)` - Cập nhật điểm
  - `getPoints(playerId)` - Lấy điểm hiện tại
  - `loadAll()` - Load tất cả (cho cache)

### **MatchRepository.java** - CRUD Matches

- **Vai trò:** Repository pattern cho tables Matches & MatchResults
- **Tables:**
  - `Matches(MatchID, StartTime, EndTime, NumPlayers, WinnerID)`
  - `MatchResults(ResultID, MatchID, PlayerID, Rank, Score, HandType, Cards)`
- **Methods:**
  - `createMatch(numPlayers)` - Tạo trận mới
  - `insertResult(matchId, playerId, rank, score, handType, cards)` - Lưu kết quả
  - `endMatch(matchId, winnerId)` - Update EndTime, WinnerID
  - `getHistory(limit)` - Lấy N trận gần nhất
  - `getDetail(matchId)` - Chi tiết 1 trận (kèm kết quả từng người)

---

## 🎮 GAME LOGIC (package server.game)

### **GameLogic.java** - Logic Chính Game Bài

- **Vai trò:** Quản lý toàn bộ logic game: deck, hands, rút bài, đánh giá
- **Chức năng:**
  - `initializeNewRound(players)` - Khởi tạo ván mới (tạo deck mới)
  - `drawCardForPlayer(username)` - Rút 1 lá cho người chơi
  - `hasDrawnMax(username)` - Kiểm tra đã rút đủ 3 lá chưa
  - `getDrawCount(username)` - Số lá đã rút
  - `calculateAllRanks()` - Tính HandRank cho tất cả người chơi
  - `calculateModScores()` - Tính điểm mod 10 cho HighCard
  - `determineWinner()` - Xác định người thắng (so sánh HandRank)
  - `sortPlayersByRank()` - Sắp xếp theo thứ hạng bài
  - `buildShowHandsMessage()` - Build SHOW_HANDS_ALL message
  - `buildHandRanksMessage()` - Build HAND_RANKS message
- **Data structures:**
  - `Deck deck` - Bộ bài 52 lá
  - `Map<String, Hand> playerHands` - Tay bài của từng người
  - `Map<String, Integer> drawCounts` - Số lá đã rút

### **HandEvaluator.java** - Đánh Giá Tay Bài

- **Vai trò:** Đánh giá loại tay bài (poker-style)
- **Chức năng:**
  - `evaluate(hand)` - Đánh giá hand, return HandRank
  - Các loại tay (cao → thấp):
    1. **Three of a Kind** (category=5) - 3 lá cùng rank
    2. **Straight Flush** (category=4) - Sảnh + cùng chất
    3. **Straight** (category=3) - Sảnh (3 lá liên tiếp)
    4. **Flush** (category=2) - Cùng chất
    5. **High Card** (category=1) - Không thuộc loại nào
- **Tính điểm:**
  - Three of a Kind: baseScore × 100 + 500 (ưu tiên cao nhất)
  - Straight Flush: baseScore × 100 + 400
  - Straight: baseScore × 100 + 300
  - Flush: baseScore × 100 + 200
  - High Card: sum(ranks) mod 10
- **So sánh:** Category cao hơn thắng, nếu bằng thì so baseScore

---

## 🎴 MODELS (package server.models)

### **Card.java** - Model Lá Bài

- **Vai trò:** Đại diện cho 1 lá bài
- **Properties:**
  - `String rank` - Rank: "2" → "10", "J", "Q", "K", "A"
  - `String suit` - Chất: "Hearts", "Diamonds", "Clubs", "Spades"
  - `int numericValue` - Giá trị số (2-14, Ace=14)
- **Methods:**
  - `toString()` - Format "rank of suit" (vd: "A of Hearts")
  - `getNumericValue()` - Convert rank → số để so sánh

### **Deck.java** - Bộ Bài

- **Vai trò:** Quản lý bộ 52 lá bài
- **Chức năng:**
  - `Deck()` - Khởi tạo 52 lá (13 rank × 4 suit)
  - `shuffle()` - Trộn bài (Collections.shuffle)
  - `drawCard()` - Rút 1 lá từ đầu deck
  - `isEmpty()` - Kiểm tra deck còn bài không
- **Data:** `List<Card> cards` - Danh sách các lá bài

### **Hand.java** - Tay Bài Người Chơi

- **Vai trò:** Lưu các lá bài người chơi đang giữ
- **Chức năng:**
  - `addCard(card)` - Thêm 1 lá vào tay
  - `getCards()` - Lấy danh sách các lá
  - `size()` - Số lá đang giữ
  - `clear()` - Xóa tất cả lá (bắt đầu ván mới)
- **Limit:** Max 3 lá/tay trong game hiện tại
- **Data:** `List<Card> cards` - Danh sách lá bài

### **HandRank.java** - Xếp Hạng Tay Bài

- **Vai trò:** Kết quả đánh giá tay bài (từ HandEvaluator)
- **Properties:**
  - `int category` - Loại bài (1=HighCard → 5=ThreeOfAKind)
  - `int baseScore` - Điểm cơ bản (rank cao nhất hoặc mod 10)
  - `String name` - Tên loại bài ("Three of a Kind", "Straight Flush", etc.)
- **Methods:**
  - `compareTo(other)` - So sánh 2 tay (category trước, baseScore sau)
  - `toString()` - Format "{name} ({baseScore})"
- **Usage:** Dùng để xác định thắng/thua trong GameLogic

---

## 📊 THỐNG KÊ

- **Tổng số file:** 28 files
- **Dòng code:**
  - Core: ~770 lines (Server, ClientHandler, RoomThread)
  - Handlers: ~758 lines
  - Managers: ~1,419 lines
  - Game Logic: ~378 lines
  - Database: ~791 lines
  - Models: ~137 lines

---

## 🔄 FLOW HOẠT ĐỘNG

### 1️⃣ Kết nối & Đăng nhập

```
Client → Server → ClientHandler → AuthenticationHandler → Database
```

### 2️⃣ Tạo/Vào phòng

```
Client → ClientHandler → RoomCommandHandler → RoomThread → RoomPlayerManager
```

### 3️⃣ Chơi game

```
Client → ClientHandler → GameCommandHandler → RoomThread → GameFlowManager
  → GameLogic → TurnManager → ScoreManager → BroadcastManager
```

### 4️⃣ Kết thúc & Lưu kết quả

```
GameFlowManager → ScoreManager → Database → MatchRepository
```

---

## 🎯 REFACTORING HISTORY

### Phase 1: Database (328 → 81 lines, 75% ↓)

- Created: DatabaseConnection, PlayerRepository, MatchRepository, DatabaseHelper

### Phase 2: ClientHandler (443 → 345 lines, 22% ↓)

- Created: AuthenticationHandler, RoomCommandHandler, GameCommandHandler, BroadcastHelper

### Phase 3: RoomThread (504 → 227 lines, 55% ↓)

- Created: RoomPlayerManager, GameFlowManager, KickManager

**Total: 18 helper classes created**
**Original: 1,275 lines → Refactored: 653 lines (49% reduction)**

---

## 💡 TÌM FILE NHANH

| Cần làm gì?           | File nào?                                            |
| --------------------- | ---------------------------------------------------- |
| Thay đổi port server  | Server.java (line 82)                                |
| Cấu hình database     | DatabaseConnection.java                              |
| Thêm command mới      | RoomCommandHandler.java hoặc GameCommandHandler.java |
| Sửa logic rút bài     | GameLogic.java                                       |
| Sửa cách tính điểm    | ScoreManager.java                                    |
| Thêm timeout setting  | TurnManager.java                                     |
| Sửa protocol messages | ClientHandler.java, ProtocolHandler.java             |
| Đổi cách đánh giá bài | HandEvaluator.java                                   |
