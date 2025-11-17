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

- **Server.java** - Main server, quản lý kết nối, rooms, players
- **ClientHandler.java** - Xử lý từng client connection (1 thread/client)
- **RoomThread.java** - Quản lý 1 phòng chơi (1 thread/room)

---

## 🎯 HANDLERS (package server.handlers)

- **AuthenticationHandler.java** - Xử lý đăng nhập/đăng ký
- **MessageHandler.java** - Route messages đến handlers khác
- **ProtocolHandler.java** - Parse protocol messages
- **RoomCommandHandler.java** - CREATE, JOIN, READY, LEAVE, START
- **GameCommandHandler.java** - DRAW, KICK, INVITE

---

## 👥 MANAGERS (package server.managers)

- **GameStateManager.java** - Trạng thái game (started, matchId)
- **GameFlowManager.java** - Flow game (startGame, drawCard, endGame)
- **RoomPlayerManager.java** - Quản lý players trong room
- **RoomManager.java** - Quản lý room (full, count)
- **TurnManager.java** - Quản lý lượt chơi & timer
- **ScoreManager.java** - Tính điểm & ranking
- **KickManager.java** - Kick player & timeout
- **BroadcastManager.java** - Gửi messages cho players
- **BroadcastHelper.java** - Helper cho broadcast

---

## 💾 DATABASE (package server.database)

- **Database.java** - Facade cho tất cả DB operations
- **DatabaseConnection.java** - Quản lý connection pool
- **DatabaseHelper.java** - Helper methods
- **PlayerRepository.java** - CRUD players
- **MatchRepository.java** - CRUD matches & results

---

## 🎮 GAME LOGIC (package server.game)

- **GameLogic.java** - Logic game (deck, hands, draw, ranks)
- **HandEvaluator.java** - Đánh giá tay bài (Flush, Straight, etc.)

---

## 🎴 MODELS (package server.models)

- **Card.java** - Lá bài (rank, suit)
- **Deck.java** - Bộ bài (52 lá)
- **Hand.java** - Tay bài (danh sách cards)
- **HandRank.java** - Xếp hạng tay bài

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
