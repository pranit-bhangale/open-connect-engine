# Open Connect Engine

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25%20(Virtual%20Threads)-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![React](https://img.shields.io/badge/React-19.3.1-blue.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue.svg)](https://www.typescriptlang.org/)
[![Nginx](https://img.shields.io/badge/Nginx-1.27%20Alpine-green.svg)](https://nginx.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20Alpine-336791.svg)](https://www.postgresql.org/)
[![Encryption](https://img.shields.io/badge/Security-AES--256--GCM-red.svg)](https://csrc.nist.gov/publications/detail/sp/800-38d/final)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

A production-grade, secure, real-time ephemeral chat application and engine. Built with modern **Java 25 (Virtual Threads)**, **Spring Boot 4**, **Spring WebSocket (STOMP)**, **AES-256-GCM encryption at rest**, **React 19**, and a standalone **Nginx Gateway with SSL termination**.

---

## 🏛️ System Architecture & Topology

Open Connect uses a containerized, decoupled micro-architecture where all inter-container traffic is isolated inside an internal Docker network, and all external client communication is routed through a dedicated Nginx reverse proxy.

```
                                    Client / Browser
                             (https://localhost:443, :80)
                                          │
                                          ▼
            ┌───────────────────────────────────────────────────────────┐
            │                 CONTAINER 1: NGINX GATEWAY                │
            │            (container_name: open-connect-gateway)         │
            │   • Edge Ingress: Ports 80 & 443 (HTTP/2 + TLS 1.2/1.3)   │
            │   • SSL Termination (open-connect.crt / auto-generated)   │
            │   • HTTP -> HTTPS 301 redirection                         │
            │   • Dedicated /chat & /chat/websocket STOMP proxying      │
            │   • Upstream connection pooling & health probes           │
            └──────────────┬─────────────────────────────┬──────────────┘
                           │                             │
             /api, /chat   │                             │  / (Root & SPA Assets)
                           ▼                             ▼
            ┌─────────────────────────────┐ ┌───────────────────────────┐
            │     CONTAINER 2: BACKEND    │ │    CONTAINER 3: FRONTEND  │
            │  (open-connect-backend)     │ │  (open-connect-frontend)  │
            │  • Spring Boot 4 / Java 25  │ │  • Pure Node 22 Runtime   │
            │  • Virtual Threads enabled  │ │  • React 19 + Vite SPA    │
            │  • REST & STOMP controllers │ │  • serve static engine    │
            │  • Internal Port: 8080      │ │  • Internal Port: 3000    │
            └──────────────┬──────────────┘ └───────────────────────────┘
                           │
                JDBC :5432 │ (HikariCP Connection Pool)
                           ▼
            ┌─────────────────────────────┐
            │    CONTAINER 4: DATABASE    │
            │      (open-connect-db)      │
            │  • PostgreSQL 16 Alpine     │
            │  • Flyway automated schema  │
            │  • Persistent Data Volume   │
            │  • Internal Port: 5432      │
            └─────────────────────────────┘
```

---

## 🌟 Key Features & Capabilities

### 1. Zero-Email Streamlined Onboarding
- Simplified user access: Users only provide their **Display Name** and optional room details.
- No mandatory email addresses or password barriers; users are authenticated by room membership context.

### 2. Ephemeral Room Lifecycle & Dual Identifiers
- **Creation**: Room creator is assigned as **Room Owner** (`owner_id`).
- **Identifiers**: Each room generates a **Room ID (UUID)** and a human-friendly **6-digit Room Code** (e.g., `961547`).
- **Joining**: Peers can join using either the 6-digit Room Code or the Room UUID.
- **Idempotent Membership**: Refreshing the browser or reconnecting reuses existing membership without duplicate join records.

### 3. Owner-Controlled Room Closure & 5-Second Countdown
- **Strict Role Authorization**: Only the room creator can close the room. Non-owner close attempts are rejected with `403 Forbidden`.
- **Message Termination**: Once closed, message transmission is locked for all participants.
- **Graceful UI Transition**: Closing the room triggers a synchronized 5-second countdown banner with automatic redirect back to Home.
- **Owner Leaving vs. Closing**: The owner can leave temporarily without closing the room (`isHostOnline` becomes `false`), allowing members to continue chatting until the owner explicitly closes it.

### 4. Real-Time Bidirectional Communication
- **STOMP over WebSocket**: Dedicated topics for chat messages (`/topic/room/{id}`) and presence notifications (`/topic/room/{id}/presence`).
- **Typing Indicators**: Transient peer notifications that stream typing states without database persistence overhead.
- **Instant Presence**: Dynamic member count updates and join/leave event tracking.

### 5. Enterprise Security & Encryption
- **AES-256-GCM at Rest**: All message payloads are encrypted prior to database insertion via JPA `AttributeConverter` and decrypted transparently on retrieval.
- **TLS 1.2 / 1.3 Transport Security**: Strict cipher suites, HSTS, X-Content-Type-Options, and frame restrictions configured in Nginx.
- **Resilient SSL Bootstrapping**: Automatically mounts `open-connect.crt` and `open-connect.key`, or generates a valid 10-year self-signed certificate on initial boot.

### 6. Automated Room Cleanup Scheduler
- Scheduled background task queries stale open rooms using composite index `idx_chat_rooms_status_created`.
- Automatically transitions abandoned unclosed rooms (>24h TTL) to `CLOSED` and broadcasts notifications.
- Automatically purges closed rooms and related messages beyond the retention period (default 15 days).

### 7. Modern Interactive UI
- **React 19 & Tailwind CSS**: Dark-themed, glassmorphic layout with responsive mobile support.
- **Space Animated Particles**: Ambient background starfield animation with zero layout shifts.
- **Live Backend Health Indicator**: Instant status pill (pulsing green dot for online, red dot for offline).
- **Welcome Modal**: 1-click room code copying, shareable links, and owner status badges.

---

## 🧰 Tech Stack

| Domain | Technology | Description |
|---|---|---|
| **Backend Engine** | Java 25 | Latest OpenJDK with Virtual Threads (`spring.threads.virtual.enabled=true`) |
| **Framework** | Spring Boot 4.1.2 | Spring WebMVC, Spring WebSocket, Spring Data JPA, Actuator |
| **Database** | PostgreSQL 16 Alpine | Relational storage with Flyway database migrations |
| **Security** | AES-256-GCM | Authenticated cipher encryption for stored chat messages |
| **Frontend UI** | React 19.3.1 | React 19, TypeScript 5, Vite 8, Tailwind CSS, Framer Motion |
| **Static Server** | Node 22 Alpine / `serve` | High-performance SPA static asset server |
| **Reverse Proxy** | Nginx 1.27 Alpine | HTTP/2, TLS termination, WebSocket upgrade router, health probes |
| **Orchestration** | Docker & Docker Compose | Multi-stage caching, non-root users, health-dependent startup |

---

## 📁 Repository Structure

```
open-connect-engine/
├── .dockerignore                     # Root Docker context exclusion
├── .env                              # Environment variable configuration
├── docker-compose.yml                # Production orchestration definition
├── README.md                         # Project documentation
│
├── backend/open-connect/             # Spring Boot Backend Service
│   ├── .dockerignore                 # Backend build exclusion
│   ├── Dockerfile                    # Multi-stage JDK 25 builder -> JRE 25 runtime
│   ├── build.gradle                  # Gradle build dependencies & toolchain
│   ├── gradlew / gradlew.bat         # Gradle wrapper executables
│   └── src/                          # Application source code
│       └── main/
│           ├── java/com/pranit/connect/
│           │   ├── config/           # WebSocket, CORS, Async, Security configs
│           │   ├── controller/       # RoomController, WebSocket STOMP controllers
│           │   ├── entity/           # User, ChatRoom, ChatMessage, RoomMember entities
│           │   ├── repository/       # Spring Data JPA repositories with custom queries
│           │   ├── scheduler/        # Automated room cleanup & retention scheduler
│           │   └── service/          # RoomService, MessageCryptoService implementations
│           └── resources/
│               ├── application.yaml  # Spring configuration profile
│               └── db/migration/     # Flyway V1 schema migrations
│
├── frontend/open-connect/            # React 19 Frontend Service
│   ├── .dockerignore                 # Frontend build exclusion
│   ├── Dockerfile                    # Multi-stage Node builder -> Node runtime (serve)
│   ├── package.json                  # Dependencies & build scripts
│   ├── vite.config.ts                # Vite build and proxy configuration
│   └── src/
│       ├── App.tsx                   # Main application root & health polling
│       ├── components/chat/          # ChatWindow, ChatInput, ChatHeader, Modals
│       │   ├── chatApi.ts            # REST API client & backend health check
│       │   ├── StompClient.ts        # STOMP WebSocket client over native WebSocket
│       │   ├── types.ts              # TypeScript interface definitions
│       │   ├── UserAvatar.tsx        # Avatar initials with deterministic colors
│       │   └── WelcomePopup.tsx      # Room join popup & code copier
│       └── components/common/
│           └── SpaceBackground.tsx   # Canvas particle ambient background
│
└── nginx/                            # Nginx Gateway Service
    ├── Dockerfile                    # Nginx 1.27 Alpine + OpenSSL + entrypoint
    ├── entrypoint.sh                 # Unix LF SSL bootstrapping & cert verification
    ├── nginx.conf                    # Consolidated production reverse proxy config
    └── ssl/                          # SSL certificate directory
        ├── open-connect.crt          # SSL Certificate
        └── open-connect.key          # SSL Private Key
```

---

## 📡 API & WebSocket Protocols

### REST Endpoints (`/api/rooms`)
All endpoints support versioning with default fallback to `v1`.

| Method | Endpoint | Query / Body Params | Description |
|---|---|---|---|
| `POST` | `/api/rooms/open` | `{ "ownerName": "...", "roomName": "...", "roomDescription": "..." }` | Creates a new room and returns UUID & 6-digit code |
| `POST` | `/api/rooms/join` | `{ "roomCode": "...", "userName": "..." }` | Joins a room using 6-digit code or UUID |
| `GET` | `/api/rooms/{id}` | Path: `id` (UUID) | Retrieves room metadata, member list, and host status |
| `GET` | `/api/rooms/code/{code}`| Path: `code` (6-digit) | Retrieves room metadata by 6-digit code |
| `PATCH`| `/api/rooms/{id}/close`| Headers: `X-User-Id` (UUID) | Closes room permanently (**Room owner only**) |
| `PATCH`| `/api/rooms/{id}/leave`| Headers: `X-User-Id` (UUID) | Leaves room gracefully |
| `POST` | `/api/rooms/{id}/messages` | `{ "senderId": "...", "content": "..." }` | Sends a message via REST (broadcasts to WebSocket) |
| `GET` | `/api/rooms/{id}/messages` | `cursor`, `limit`, `direction` | Keyset-paginated message history |

### WebSocket STOMP Protocol
- **Endpoint URL**: `wss://localhost/chat/websocket` (or `ws://localhost:8080/chat/websocket` in direct dev mode)
- **Application Destination Prefix**: `/app`
- **Broker Destinations**: `/topic`, `/queue`
- **User Destination Prefix**: `/user`

#### Topic Subscriptions
- `/topic/room/{roomId}`: Real-time broadcast of chat messages and message events.
- `/topic/room/{roomId}/presence`: Real-time member events (`JOINED`, `LEFT`, `CLOSED`) and host presence status.
- `/user/queue/errors`: Private error messages sent to individual users on failed operations.

#### Inbound Application Destinations
- `/app/sendMessage/{roomId}`: Broadcasts chat or typing message.
- `/app/room/{roomId}/leave`: Leaves room via STOMP frame.
- `/app/room/{roomId}/close`: Closes room via STOMP (**Owner only**).
- `/app/room/join`: Joins room via STOMP frame.

---

## ⚙️ Environment Variables (`.env`)

| Variable | Default Value | Description |
|---|---|---|
| `DB_HOST` | `postgres` (compose) / `localhost` (dev) | PostgreSQL host address |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `chat` | Database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `140503` | Database password |
| `CHAT_ENCRYPTION_KEY` | `Acezms5Z7VJBTvziMIDVtG5oMVLQxBpsdAlWW2ckV0o=` | 32-byte Base64 AES-256 secret key |
| `LOG_PATH` | `logs/backend.log` | Backend log file output location |
| `LOG_LEVEL_ROOT` | `INFO` | Root logger log level |
| `LOG_LEVEL` | `DEBUG` | Application package logger log level |
| `CLEANUP_ENABLED` | `true` | Enables scheduled cleanup of stale unclosed rooms |
| `CLEANUP_CRON` | `0 0 3 * * ?` | Cron schedule for cleanup (default: 3:00 AM daily) |
| `CLEANUP_UNCLOSED_TTL_HOURS` | `24` | Hours after creation an unclosed room is marked stale |
| `CLEANUP_RETENTION_DAYS` | `15` | Days to retain closed room data before hard deletion |

---

## 🚀 Deployment & Getting Started

### Prerequisites
- [Docker](https://www.docker.com/) and [Docker Compose](https://docs.docker.com/compose/) installed
- Ports `80`, `443`, and `5432` available

### Running with Docker Compose (Recommended)

1. **Clone and enter the workspace:**
   ```bash
   cd P:\WebSocket\open-connect-engine
   ```

2. **Verify or customize environment variables:**
   Review `.env` in the project root.

3. **Build and launch the complete stack:**
   ```bash
   docker-compose up -d --build
   ```

4. **Verify container health:**
   ```bash
   docker-compose ps
   ```
   All 4 containers will report `healthy`:
   - `open-connect-db` (Port 5432)
   - `open-connect-backend` (Port 8080 internal)
   - `open-connect-frontend` (Port 3000 internal)
   - `open-connect-gateway` (Ports 80 & 443 external)

5. **Access the application:**
   - Web UI: [https://localhost](https://localhost) (or `http://localhost`, which redirects to HTTPS)
   - Actuator Health: [https://localhost/actuator/health](https://localhost/actuator/health)
   - Swagger / OpenAPI: [https://localhost/swagger-ui/index.html](https://localhost/swagger-ui/index.html)
   - Gateway Health Probe: [http://localhost/healthz](http://localhost/healthz)

---

### Running in Local Development Mode

#### 1. Database
Start PostgreSQL using Docker:
```bash
docker run --name open-connect-db -e POSTGRES_DB=chat -e POSTGRES_PASSWORD=140503 -p 5432:5432 -d postgres:16-alpine
```

#### 2. Backend Service
```bash
cd backend/open-connect
./gradlew bootRun
```
The backend initializes database tables via Flyway and starts on `http://localhost:8080`.

#### 3. Frontend UI
```bash
cd frontend/open-connect
npm install
npm run dev
```
The frontend starts on `http://localhost:5173` and proxies API/WebSocket calls directly to `http://localhost:8080`.

---

## 🔐 SSL Certificates & Management

The Nginx Gateway uses [`nginx/ssl/open-connect.crt`](file:///P:/WebSocket/open-connect-engine/nginx/ssl/open-connect.crt) and [`nginx/ssl/open-connect.key`](file:///P:/WebSocket/open-connect-engine/nginx/ssl/open-connect.key).

- **Custom / Production Certificates**: Place your signed certificates directly into `nginx/ssl/` as `open-connect.crt` and `open-connect.key`.
- **Automatic Fallback**: If certificates are missing upon container startup, [`nginx/entrypoint.sh`](file:///P:/WebSocket/open-connect-engine/nginx/entrypoint.sh) generates a 10-year self-signed certificate for `localhost` and `127.0.0.1` automatically.

---

## 📄 License
This project is open-source under the [MIT License](LICENSE).
