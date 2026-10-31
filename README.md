# Open Connect Chat Engine

A scalable, secure, real-time WebSocket and REST chat backend built with Spring Boot, Spring WebSocket (STOMP), JPA, and AES-256 GCM encryption.

---

## Key Features & Architecture

### 1. Room Creation & Identifier Sharing
- **Any user** can create a room by providing their user info (username, email) and room details (name, description).
- The creator is designated as the **Room Owner** (`owner_id`).
- The system generates a unique **Room ID (UUID)** and a 6-digit **Room Code** (e.g. `583921`).
- The owner can share either the **Room Code** or the **Room ID (UUID)** with other users to join.
- When created, the owner is automatically registered as the initial room member, and a `JOINED` presence notification is published in real time.

### 2. Flexible Room Joining & Real-time Notifications
- Users can join using either the **Room Code** or the **Room ID (UUID)**.
- Rejoining users (after network reconnect or page refresh) are idempotently handled without duplicate membership records.
- When any user joins:
  - Added to `room_members`.
  - Real-time `JOINED` event (`RoomEvent` record) is broadcast to both `/topic/room/{roomId}/presence` and `/topic/room/{roomId}` with member count and status.
  - If the room owner rejoins, `isHostOnline` becomes `true`.
- **Closed Room Protection**: Attempting to join a closed room throws `RoomClosedException` (400 Bad Request).

### 3. Member Leaving & Rejoining
- Any regular member can leave the room via REST (`PATCH /api/rooms/{id}/leave`) or WebSocket STOMP (`/app/room/{roomId}/leave`).
- The member is removed from `room_members`.
- A real-time `LEFT` event is broadcast to all active members.
- Members can leave and rejoin at any time as long as the room remains `OPEN`.

### 4. Owner Leaving & Rejoining Semantics
- The **owner can also leave the room** without closing it.
- When the owner leaves:
  - Owner is removed from `room_members`.
  - `isHostOnline` is set to `false`.
  - The room status **remains `OPEN`**.
  - All room members receive a `LEFT` notification indicating the host stepped out.
  - Remaining members can **continue sending and receiving messages** normally.
- When the owner rejoins:
  - Owner is added back to `room_members`.
  - `isHostOnline` is restored to `true`.
  - Members receive a `JOINED` notification indicating the host is back online.

### 5. Owner-Only Room Closing
- **Only the room owner who created the room can close the room.**
- If a non-owner attempts to close the room, the request is rejected with `RoomOperationNotAllowedException` (**403 Forbidden**).
- When the owner closes the room:
  - Room status permanently transitions to `CLOSED`.
  - `closedBy` is set to the owner and `closedAt` timestamp is recorded.
  - Real-time `CLOSED` notification is broadcast across `/topic/room/{roomId}/presence` and `/topic/room/{roomId}`.
  - **Message Termination**: Once closed, **NO user (neither members nor the owner) can send messages**.
  - Any attempt to send a message to a closed room throws `RoomClosedException` ("Chat room is closed. No further messages can be sent.").
  - Any attempt to join a closed room is rejected.

### 6. Optimized Unclosed Room Cleanup Scheduler
- Unclosed rooms left abandoned by creators without being closed are automatically cleaned up:
  - **Indexed Query**: Uses the composite DB index `idx_chat_rooms_status_created` (`status, created_at`) via `findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(RoomStatus.OPEN, cutoff)`.
  - **Auto-Close & Notification**: Stale unclosed rooms older than configured TTL (default 24 hours) are automatically closed, and subscribers are notified across `/topic/room/{roomId}/presence` and `/topic/room/{roomId}`.
  - **Chunked Processing**: Processes in bounded batches (100 per chunk, up to `max-batches`) to eliminate long database transaction locks and memory spikes.
  - **Stale Purge**: Closed rooms older than retention period (default 15 days) are purged along with their messages and member records.

---

## API Reference

### REST Endpoints (`/api/rooms`)
All endpoints support API Versioning (default `v1`, header `X-API-Version: v1`).

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/rooms/open` | Create a new room with owner details |
| `GET` | `/api/rooms/{id}` | Get room details and active members by room ID |
| `GET` | `/api/rooms/code/{roomCode}` | Get room details by 6-digit room code |
| `POST` | `/api/rooms/join` | Join a room using room code or room ID |
| `PATCH` | `/api/rooms/{id}/close` | Close room (**Owner only**, 403 for non-owner) |
| `PATCH` | `/api/rooms/{id}/leave` | Leave room (member or owner) |
| `POST` | `/api/rooms/{id}/messages` | Send message via REST (broadcasts to WebSocket) |
| `GET` | `/api/rooms/{id}/messages` | Keyset/cursor-based message pagination & search |

### WebSocket STOMP Endpoints

- **Connection URL**: `ws://localhost:8080/chat` (also supports SockJS fallback)
- **Application Destination Prefix**: `/app`
- **Broker Destinations**: `/topic`, `/queue`
- **User Destination Prefix**: `/user`

#### Subscriptions:
- `/topic/room/{roomId}`: Real-time chat messages and room events.
- `/topic/room/{roomId}/presence`: Real-time presence notifications (`JOINED`, `LEFT`, `CLOSED`).
- `/user/queue/errors`: Private queue for STOMP error notifications.

#### Mappings:
- `/app/sendMessage/{roomId}`: Send chat or typing message.
- `/app/room/{roomId}/send`: Alternative destination for sending message.
- `/app/room/{roomId}/leave`: Leave room via STOMP.
- `/app/room/{roomId}/close`: Close room via STOMP (**Owner only**).
- `/app/room/join`: Join room via STOMP.

---

## Security & Encryption
- All message contents stored in the database are encrypted at rest using **AES-256-GCM** via `MessageCryptoService` and JPA `AttributeConverter`.
- Typing indicator events (`MessageType.TYPING`) are transiently broadcast to peers without persisting unneeded data.

---

## Testing

Run the test suite:
```bash
./gradlew test
```
All business rules, scheduler tasks, and edge cases are covered by 25 tests in:
- `com.pranit.connect.service.RoomServiceImplTest`
- `com.pranit.connect.controller.RoomControllerTest`
- `com.pranit.connect.scheduler.RoomCleanupServiceTest`
