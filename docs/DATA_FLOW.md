# Open Connect Engine: End-to-End Data Flow Architecture (HLD & LLD)

> **Document Type:** Mixed High-Level Design (HLD) & Low-Level Design (LLD) Specification  
> **Target Scope:** UI Request Lifecycle, Reverse Proxy Routing, WebSocket / STOMP Protocol Handshake, Topic Subscription Mechanics, In-Memory Message Broker Dispatching, AES-256-GCM Encryption, and Subscriber UI Rendering.  
> **Target System:** Open Connect Engine (Spring Boot 4.1.2, Java 25 Virtual Threads, React 19, Nginx Gateway, PostgreSQL 16)

---

## 1. Executive Summary & Architectural Scope

The **Open Connect Engine** provides ephemeral, real-time multi-user communication with strict room-level isolation, end-to-end security at rest, and resilient connection handling.

This document details the lifecycle of all communications across the system:
1. **High-Level Design (HLD):** The macro topology, edge termination, protocol translation, container boundaries, security perimeter, and broker domains.
2. **Low-Level Design (LLD):** The granular internals of the STOMP 1.2 frame pipeline, Spring Framework message channels (`clientInboundChannel`, `clientOutboundChannel`, `brokerChannel`), simple broker subscription registries, thread virtualization, cryptographic transformation (`AES-256-GCM`), and React 19 reactive subscriber state management.

---

## 2. High-Level Design (HLD): System Topology & Component Model

The system operates across four isolated container environments behind an Nginx gateway that terminates SSL and routes traffic dynamically.

```mermaid
graph TD
    subgraph ClientBrowser["Client Environment (Browser)"]
        UserA["Client A (Sender UI - React 19)"]
        UserB["Client B (Subscriber UI - React 19)"]
        UserC["Client C (Subscriber UI - React 19)"]
    end

    subgraph EdgeGateway["Container 1: Edge Reverse Proxy & Gateway"]
        Nginx["Nginx 1.27 Alpine Reverse Proxy<br/>Ports: 80 (HTTP 301) & 443 (HTTPS/WSS)<br/>SSL Termination: TLS 1.2 / TLS 1.3"]
    end

    subgraph StaticFrontend["Container 3: Frontend SPA Server"]
        NodeSPA["Node.js 22 Runtime (serve)<br/>Vite + React 19 Production Bundle<br/>Port: 3000"]
    end

    subgraph BackendCore["Container 2: Backend Application Core (Java 25 / Spring Boot 4)"]
        direction TB
        Tomcat["Embedded Tomcat / NIO Connector<br/>Virtual Threads Enabled"]
        WebSocketEngine["Spring WebSocket STOMP Engine<br/>Endpoint: /chat & /chat/websocket"]
        RestControllers["REST Controllers<br/>(/api/rooms/**)"]
        BrokerEngine["Spring Simple Message Broker<br/>Prefixes: /topic, /queue, /user"]
        ServiceLayer["Service & Business Domain<br/>(RoomService, MessageCryptoService)"]
    end

    subgraph Persistence["Container 4: Relational Persistence"]
        Postgres["PostgreSQL 16 Alpine<br/>Flyway Migration Engine<br/>AES-256-GCM Encrypted Storage"]
    end

    %% Network Connections
    UserA -->|"HTTPS / WSS (Port 443)"| Nginx
    UserB -->|"HTTPS / WSS (Port 443)"| Nginx
    UserC -->|"HTTPS / WSS (Port 443)"| Nginx

    Nginx -->|"Proxy: GET / (Static SPA Assets)"| NodeSPA
    Nginx -->|"Proxy: /api, /actuator"| RestControllers
    Nginx -->|"WebSocket Upgrade: Connection: Upgrade"| WebSocketEngine

    Tomcat --> WebSocketEngine
    Tomcat --> RestControllers
    RestControllers --> ServiceLayer
    WebSocketEngine --- BrokerEngine
    ServiceLayer --- BrokerEngine
    ServiceLayer -->|"HikariCP Pool (JDBC :5432)"| Postgres
```

### High-Level Component Responsibilities

| Tier / Component | Technology | Primary Responsibilities |
|---|---|---|
| **Client UI** | React 19, TypeScript 5, Vite 8 | Manages user session state, native WebSocket lifecycle, STOMP frame construction, optimistic rendering, typing debounce, and UI countdowns. |
| **Edge Gateway** | Nginx 1.27 Alpine | Terminates TLS (HTTPS/WSS), handles HTTP-to-HTTPS 301 redirects, proxies static SPA requests, applies connection timeouts (`proxy_read_timeout 86400s`), and converts HTTP 1.1 upgrade headers into full-duplex TCP tunnels. |
| **Frontend Static Server** | Node.js 22 (`serve`) | Serves minified single-page application (HTML, CSS, JS) assets to the client browser. |
| **Backend Core** | Spring Boot 4.1.2, Java 25 | Runs on OpenJDK 25 with Virtual Threads (`spring.threads.virtual.enabled=true`). Houses STOMP message broker, REST endpoints, JPA repositories, scheduled room cleaners, and crypto converters. |
| **Message Broker** | In-Memory Simple Broker | Maintains subscription trees (`DefaultSubscriptionRegistry`), resolves active subscribers per topic (`/topic/room/{roomId}` and `/topic/room/{roomId}/presence`), and replicates frames across client sessions. |
| **Database** | PostgreSQL 16 Alpine | Stores persistent room entities, memberships, and chat payloads. Chat messages are protected at rest via authenticated AES-256-GCM cipher encryption. |

---

## 3. Low-Level Design (LLD): Spring WebSocket Messaging Pipeline

The Spring Boot backend architecture decouples network transport, protocol decoding, destination routing, and thread management into distinct asynchronous pipelines.

```mermaid
flowchart TD
    subgraph NetworkTransport["1. Transport & Inbound Protocol Layer"]
        TCPFrame["Inbound WebSocket TCP Frame<br/>(via Nginx Proxy)"]
        SubHandler["SubProtocolWebSocketHandler<br/>(Manages WebSocket Sessions)"]
        StompDecoder["StompSubProtocolHandler<br/>(Decodes STOMP Frames to Spring Message)"]
        InboundChannel["clientInboundChannel<br/>(Thread: VirtualThread-executor)"]
    end

    subgraph InternalRouting["2. Destination Router & Dispatcher"]
        AppPrefixRouter{"Destination Prefix<br/>Resolution"}
        AnnotationHandler["SimpAnnotationMethodMessageHandler<br/>(MessageMapping Handler)"]
        ChatCtrl["ChatController<br/>sendMessage() / joinRoom() / leave() / close()"]
        SimpleBroker["SimpleBrokerMessageHandler<br/>(In-Memory Topic/Queue Router)"]
        SubRegistry["DefaultSubscriptionRegistry<br/>(Destination-to-Session Registry Tree)"]
    end

    subgraph BusinessDomain["3. Business Logic & Security Domain"]
        RoomSvc["RoomServiceImpl"]
        MemberRepo["RoomMemberRepository<br/>(Active Membership Verification)"]
        CryptoSvc["MessageCryptoService<br/>(AES-256-GCM + 12-byte IV + 128-bit Tag)"]
        JPAConverter["EncryptedContentConverter<br/>(JPA AttributeConverter)"]
        DB[(PostgreSQL 16<br/>chat_messages table)]
    end

    subgraph OutboundPipeline["4. Outbound Broadcast & Delivery Layer"]
        SimpTemplate["SimpMessageSendingOperations<br/>(messagingTemplate.convertAndSend)"]
        BrokerChannel["brokerChannel<br/>(Internal Message Channel)"]
        OutboundChannel["clientOutboundChannel<br/>(Outbound Message Channel)"]
        StompEncoder["StompSubProtocolHandler<br/>(Encodes Payload to STOMP MESSAGE Frame)"]
        ClientA_Socket["Client A Session"]
        ClientB_Socket["Client B Session (Subscriber)"]
        ClientC_Socket["Client C Session (Subscriber)"]
    end

    %% Pipeline Flow
    TCPFrame --> SubHandler
    SubHandler --> StompDecoder
    StompDecoder --> InboundChannel
    InboundChannel --> AppPrefixRouter

    AppPrefixRouter -->|Prefix: /app| AnnotationHandler
    AppPrefixRouter -->|Prefix: /topic or /queue| SimpleBroker

    AnnotationHandler --> ChatCtrl
    ChatCtrl --> RoomSvc

    RoomSvc --> MemberRepo
    RoomSvc --> JPAConverter
    JPAConverter --> CryptoSvc
    CryptoSvc --> DB

    RoomSvc -->|Return MessageResponse or Publish Presence| SimpTemplate
    ChatCtrl -->|SendTo Return Value| BrokerChannel

    SimpTemplate --> BrokerChannel
    BrokerChannel --> SimpleBroker

    SimpleBroker --- SubRegistry
    SimpleBroker --> OutboundChannel
    OutboundChannel --> StompEncoder
    StompEncoder --> ClientA_Socket
    StompEncoder --> ClientB_Socket
    StompEncoder --> ClientC_Socket
```

---

## 4. End-to-End Sequence Diagrams

### 4.1 Connection Establishment & STOMP Handshake Flow

Before any subscription or publishing can occur, the browser establishes a secure WebSocket connection through the Nginx gateway and negotiates the STOMP protocol.

```mermaid
sequenceDiagram
    autonumber
    participant UI as Browser (React 19 / StompClient)
    participant Nginx as Nginx Gateway (:443)
    participant Tomcat as Spring Boot Tomcat / NIO
    participant StompHandler as StompSubProtocolHandler
    participant InChannel as clientInboundChannel
    participant OutChannel as clientOutboundChannel

    Note over UI, Tomcat: Phase 1: HTTP-to-WebSocket Protocol Upgrade
    UI->>Nginx: GET /chat/websocket HTTP/1.1<br/>Upgrade: websocket<br/>Connection: Upgrade<br/>Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==<br/>Sec-WebSocket-Version: 13
    Nginx->>Tomcat: Proxy Upgrade with proxy_http_version 1.1<br/>Host: backend_service<br/>Upgrade: websocket<br/>Connection: Upgrade
    Tomcat-->>Nginx: HTTP/1.1 101 Switching Protocols<br/>Upgrade: websocket<br/>Connection: Upgrade
    Nginx-->>UI: HTTP/1.1 101 Switching Protocols (Tunnel Established)

    Note over UI, OutChannel: Phase 2: STOMP Protocol Handshake
    UI->>Nginx: STOMP CONNECT Frame<br/>accept-version: 1.1,1.2<br/>heart-beat: 10000,10000
    Nginx->>Tomcat: Forward STOMP Frame
    Tomcat->>StompHandler: Decode STOMP frame bytes
    StompHandler->>InChannel: SimpMessageType.CONNECT
    InChannel-->>OutChannel: SimpMessageType.CONNECTED
    OutChannel->>StompHandler: Encode CONNECTED frame
    StompHandler->>Tomcat: Frame bytes
    Tomcat->>Nginx: STOMP CONNECTED Frame<br/>version: 1.2<br/>heart-beat: 10000,10000
    Nginx->>UI: Forward STOMP CONNECTED Frame
    Note over UI: StompClient sets connected = true<br/>Fires onConnect callbacks
```

---

### 4.2 Topic Subscription Flow (UI to In-Memory Broker)

When entering a room, the UI client registers subscriptions to receive chat messages and room presence events.

```mermaid
sequenceDiagram
    autonumber
    participant UI as Subscriber UI (ChatWindow.tsx)
    participant StompClient as StompClient.ts
    participant Nginx as Nginx Gateway
    participant Backend as Spring SubProtocolWebSocketHandler
    participant InChannel as clientInboundChannel
    participant Broker as SimpleBrokerMessageHandler
    participant Registry as DefaultSubscriptionRegistry

    Note over UI, Registry: Client registers subscription for Chat & Presence
    UI->>StompClient: subscribe("/topic/room/" + roomId, callback)
    StompClient->>StompClient: Generate subId: "sub-1"<br/>Store in internal Map: subscriptions.set("sub-1", sub)
    StompClient->>Nginx: STOMP SUBSCRIBE Frame<br/>id: sub-1<br/>destination: /topic/room/{roomId}<br/>ack: auto
    Nginx->>Backend: Forward TCP WebSocket frame

    UI->>StompClient: subscribe("/topic/room/" + roomId + "/presence", callback)
    StompClient->>StompClient: Generate subId: "sub-2"<br/>Store in internal Map: subscriptions.set("sub-2", sub)
    StompClient->>Nginx: STOMP SUBSCRIBE Frame<br/>id: sub-2<br/>destination: /topic/room/{roomId}/presence<br/>ack: auto
    Nginx->>Backend: Forward TCP WebSocket frame

    Backend->>InChannel: Decode frame to Message<SimpMessageType.SUBSCRIBE>
    InChannel->>Broker: Route SUBSCRIBE to SimpleBrokerMessageHandler
    Broker->>Registry: registerSubscription(sessionInfo, "sub-1", "/topic/room/{roomId}")
    Broker->>Registry: registerSubscription(sessionInfo, "sub-2", "/topic/room/{roomId}/presence")
    Registry-->>Broker: Subscription recorded in destination lookup tree
    Note over Registry: Registry now maps destination pattern to:<br/>Session ID: ws-sess-8942<br/>Subscription ID: sub-1 / sub-2
```

---

### 4.3 Message Dispatch Flow: Sender UI -> Server Processing -> Subscribers

This diagram represents the entire pipeline of a user sending a message until all subscribed peers receive the message and re-render their views.

```mermaid
sequenceDiagram
    autonumber
    participant SenderUI as Sender (Client A UI)
    participant SenderWS as StompClient (Client A)
    participant Nginx as Nginx Gateway
    participant SpringSTOMP as Spring Inbound Pipeline
    participant ChatCtrl as ChatController
    participant RoomSvc as RoomServiceImpl
    participant Converter as EncryptedContentConverter
    participant Crypto as MessageCryptoService
    participant DB as PostgreSQL 16
    participant Broker as SimpleBrokerMessageHandler
    participant OutChannel as clientOutboundChannel
    participant SubWS as StompClient (Client B)
    participant SubUI as Subscriber (Client B UI)

    Note over SenderUI, SenderWS: 1. User Types & Clicks Send
    SenderUI->>SenderWS: handleSendMessage("Hello Room!")
    SenderWS->>Nginx: STOMP SEND Frame<br/>destination: /app/sendMessage/{roomId}<br/>content-type: application/json<br/>{"roomId":"...","senderId":"...","content":"Hello Room!","type":"CHAT"}
    Nginx->>SpringSTOMP: Forward frame over active TCP socket

    Note over SpringSTOMP, ChatCtrl: 2. Spring Inbound Channel Dispatching
    SpringSTOMP->>SpringSTOMP: StompSubProtocolHandler decodes frame
    SpringSTOMP->>ChatCtrl: SimpAnnotationMethodMessageHandler routes to<br/>sendMessage(roomId, request)

    Note over ChatCtrl, DB: 3. Business Validation & Encrypted Persistence
    ChatCtrl->>RoomSvc: sendMessage(roomId, request)
    RoomSvc->>RoomSvc: Validate room != CLOSED (else throw RoomClosedException)
    RoomSvc->>RoomSvc: Validate sender is active member in room_members table
    RoomSvc->>RoomSvc: Instantiate Message entity (MessageType.CHAT)
    RoomSvc->>Converter: JPA save(message) triggers convertToDatabaseColumn()
    Converter->>Crypto: encrypt("Hello Room!")
    Crypto->>Crypto: Generate 12-byte random IV via SecureRandom<br/>AES-256-GCM cipher encryption with derived key<br/>Produce Base64(IV + CipherText + 128-bit Tag)
    Crypto-->>Converter: Return "enc:v1:qK8...x9L"
    Converter-->>DB: INSERT INTO chat_messages (content, room_id, sender_id, ...) VALUES ("enc:v1:qK8...", ...)
    DB-->>RoomSvc: Commit transaction
    RoomSvc-->>ChatCtrl: Return MessageResponse DTO

    Note over ChatCtrl, Broker: 4. Broadcast via Broker & Subscription Matching
    ChatCtrl-->>SpringSTOMP: SendTo topic captures MessageResponse
    SpringSTOMP->>Broker: Push Spring Message to brokerChannel
    Broker->>Broker: Query DefaultSubscriptionRegistry for "/topic/room/{roomId}"
    Note over Broker: Finds subscribers:<br/>• Session A (Sender: sub-1)<br/>• Session B (Peer: sub-1)<br/>• Session C (Peer: sub-1)

    Note over Broker, SubUI: 5. Frame Serialization & Subscriber Delivery
    Broker->>OutChannel: Build STOMP MESSAGE frame for Session B
    OutChannel->>Nginx: STOMP MESSAGE Frame<br/>destination: /topic/room/{roomId}<br/>subscription: sub-1<br/>{"id":"...","content":{"message":"Hello Room!","messageType":"CHAT"}}
    Nginx->>SubWS: Push TCP frame to Client B
    SubWS->>SubWS: handleIncomingData(): parse frame, match sub-1 callback
    SubWS->>SubUI: Trigger registered onMessage callback
    SubUI->>SubUI: Parse JSON, remove sender from activeTypers<br/>Deduplicate by message.id<br/>setMessages(prev => [...prev, newMsg])
    Note over SubUI: React 19 triggers Virtual DOM re-render<br/>ChatMessageList auto-scrolls into view
```

---

### 4.4 Room Lifecycle & Presence Event Flow (`JOIN`, `LEAVE`, `CLOSE`)

Presence events (when participants join, leave, or the owner closes the room) follow a dedicated lifecycle broadcast to `/topic/room/{roomId}/presence`.

```mermaid
sequenceDiagram
    autonumber
    participant OwnerUI as Owner / Host UI
    participant PeerUI as Peer UI (Subscriber)
    participant Nginx as Nginx Gateway
    participant RoomCtrl as RoomController / ChatController
    participant RoomSvc as RoomServiceImpl
    participant SimpTemplate as SimpMessageSendingOperations
    participant Broker as SimpleBrokerMessageHandler
    participant DB as PostgreSQL 16

    Note over OwnerUI, DB: Owner closes the room
    OwnerUI->>Nginx: PATCH /api/rooms/{id}/close (or STOMP /app/room/{id}/close)
    Nginx->>RoomCtrl: Route to closeChatRoom(roomId, detail)
    RoomCtrl->>RoomSvc: closeRoom(roomId, detail)
    RoomSvc->>RoomSvc: Verify closer is owner (403 if non-owner)
    RoomSvc->>DB: UPDATE chat_rooms SET status = 'CLOSED', closed_at = NOW()
    RoomSvc->>RoomSvc: build RoomEvent(eventType: CLOSED, isHost: true)
    RoomSvc->>SimpTemplate: convertAndSend("/topic/room/{id}/presence", roomEvent)
    SimpTemplate->>Broker: Forward to brokerChannel
    Broker->>Broker: Lookup subscribers of "/topic/room/{id}/presence"

    par Broadcast to Owner
        Broker-->>OwnerUI: STOMP MESSAGE (/topic/room/{id}/presence)<br/>{ eventType: "CLOSED", message: "Room has been closed..." }
        OwnerUI->>OwnerUI: Set roomStatus = 'CLOSED'<br/>Trigger 5-second auto-redirect countdown
    and Broadcast to Peer
        Broker-->>PeerUI: STOMP MESSAGE (/topic/room/{id}/presence)<br/>{ eventType: "CLOSED", message: "Room has been closed..." }
        PeerUI->>PeerUI: Set roomStatus = 'CLOSED'<br/>Display OwnerCloseBanner<br/>Trigger 5-second auto-redirect countdown
    end
```

---

## 5. Wire Protocol & Frame Specification

The STOMP (Simple Text Oriented Messaging Protocol) 1.2 format is used across the WebSocket connection. Frames are delimited by a `NULL` byte (`\0`).

### 5.1 Connection Frames

#### Inbound: Client to Server (`CONNECT`)
```stomp
CONNECT
accept-version:1.1,1.2
heart-beat:10000,10000

^@
```

#### Outbound: Server to Client (`CONNECTED`)
```stomp
CONNECTED
version:1.2
heart-beat:10000,10000

^@
```

---

### 5.2 Subscription Frames

#### Client Subscribe to Room Messages
```stomp
SUBSCRIBE
id:sub-1
destination:/topic/room/873f4e22-c23d-4c31-8f51-24db765ba19e
ack:auto

^@
```

#### Client Subscribe to Room Presence
```stomp
SUBSCRIBE
id:sub-2
destination:/topic/room/873f4e22-c23d-4c31-8f51-24db765ba19e/presence
ack:auto

^@
```

---

### 5.3 Publishing Frames

#### Inbound: Send Chat Message (`SEND`)
```stomp
SEND
destination:/app/sendMessage/873f4e22-c23d-4c31-8f51-24db765ba19e
content-type:application/json;charset=UTF-8
content-length:134

{"roomId":"873f4e22-c23d-4c31-8f51-24db765ba19e","senderId":"4a92c101-0982-4112-92a1-128a3f8901b2","content":"Hello world!","type":"CHAT"}^@
```

#### Inbound: Send Typing Indicator (`SEND`)
```stomp
SEND
destination:/app/sendMessage/873f4e22-c23d-4c31-8f51-24db765ba19e
content-type:application/json;charset=UTF-8
content-length:128

{"roomId":"873f4e22-c23d-4c31-8f51-24db765ba19e","senderId":"4a92c101-0982-4112-92a1-128a3f8901b2","content":"...","type":"TYPING"}^@
```

---

### 5.4 Delivery Frames

#### Outbound: Broadcast Message to Subscribers (`MESSAGE`)
```stomp
MESSAGE
destination:/topic/room/873f4e22-c23d-4c31-8f51-24db765ba19e
content-type:application/json
subscription:sub-1
message-id:msg-382910-18
content-length:412

{
  "id": "e2f18392-5819-42b7-9ce2-90184bda2819",
  "roomDetail": {
    "id": "873f4e22-c23d-4c31-8f51-24db765ba19e",
    "roomCode": "849201",
    "roomName": "Dev Room",
    "description": "General discussions",
    "owner": "alice",
    "roomStatus": "OPEN"
  },
  "userDetail": {
    "id": "4a92c101-0982-4112-92a1-128a3f8901b2",
    "username": "alice"
  },
  "content": {
    "message": "Hello world!",
    "messageType": "CHAT"
  },
  "sentAt": "2026-10-31T20:50:00.124Z"
}^@
```

#### Outbound: Broadcast Presence Event to Subscribers (`MESSAGE`)
```stomp
MESSAGE
destination:/topic/room/873f4e22-c23d-4c31-8f51-24db765ba19e/presence
content-type:application/json
subscription:sub-2
message-id:msg-382910-19
content-length:336

{
  "roomId": "873f4e22-c23d-4c31-8f51-24db765ba19e",
  "roomCode": "849201",
  "userId": "9bc12844-3291-4c12-a108-72410a827110",
  "username": "bob",
  "eventType": "JOINED",
  "message": "bob joined the room.",
  "timestamp": "2026-10-31T20:50:02.810Z",
  "roomStatus": "OPEN",
  "isHost": false,
  "isHostOnline": true,
  "memberCount": 2
}^@
```

---

## 6. Cryptographic Security Data Flow (AES-256-GCM at Rest)

The application secures message contents at rest using Galois/Counter Mode (AES-GCM), ensuring both confidentiality and authenticity.

```mermaid
flowchart LR
    subgraph InboundWrite["Message Persistence Flow"]
        Plaintext["Plaintext Message String<br/>'Hello world!'"]
        IVGen["SecureRandom<br/>Generate 12-byte IV"]
        CipherGen["AES/GCM/NoPadding<br/>256-bit Key + 12-byte IV + 128-bit Tag"]
        Concat["Combine:<br/>IV (12 bytes) + Ciphertext + Tag (16 bytes)"]
        Encode["Base64 Encode & Prepend Prefix<br/>'enc:v1:' + Base64(...)"]
        DBCol[("PostgreSQL Column<br/>chat_messages.content")]

        Plaintext --> CipherGen
        IVGen --> CipherGen
        CipherGen --> Concat
        Concat --> Encode
        Encode --> DBCol
    end

    subgraph OutboundRead["Message Retrieval Flow"]
        DBRead[("PostgreSQL Column<br/>chat_messages.content")]
        PrefixCheck{"Check prefix<br/>'enc:v1:'"}
        Decode["Base64 Decode combined bytes"]
        ExtractIV["Extract 12-byte IV"]
        DecryptCipher["AES/GCM/NoPadding<br/>Decrypt Ciphertext & Verify 128-bit Tag"]
        DecryptedPlaintext["Decrypted Plaintext<br/>'Hello world!'"]

        DBRead --> PrefixCheck
        PrefixCheck -->|Yes| Decode
        PrefixCheck -->|No| DecryptedPlaintext
        Decode --> ExtractIV
        ExtractIV --> DecryptCipher
        DecryptCipher --> DecryptedPlaintext
    end
```

### Encryption Parameters
- **Algorithm:** Advanced Encryption Standard (`AES`)
- **Cipher Transformation:** `AES/GCM/NoPadding`
- **Key Derivation:** SHA-256 digest of secret key configured in `security.chat.encryption.secret-key`
- **IV (Initialization Vector):** 12 bytes generated cryptographically per message (`SecureRandom`)
- **Authentication Tag Length:** 128 bits
- **Storage Envelope:** `enc:v1:<Base64(IV + Ciphertext + Tag)>`

---

## 7. Client-Side Low-Level Implementation (React 19 & StompClient)

The frontend client encapsulates the WebSocket connection state machine and provides automatic reconnections without external third-party STOMP libraries.

```mermaid
stateDiagram-v2
    [*] --> DISCONNECTED

    DISCONNECTED --> CONNECTING : connect() triggered
    CONNECTING --> CONNECTED : Received STOMP CONNECTED Frame
    CONNECTING --> RECONNECT_BACKOFF : WebSocket onError / onClose

    state CONNECTED {
        [*] --> IDLE
        IDLE --> SUBSCRIBING : client.subscribe()
        SUBSCRIBING --> IDLE : Stored in Map & sent SUBSCRIBE frame
        IDLE --> SENDING : client.send()
        SENDING --> IDLE : Frame written to socket
        IDLE --> RECEIVING : onmessage event
        RECEIVING --> IDLE : Parsed frame dispatches to subscription callback
    }

    CONNECTED --> RECONNECT_BACKOFF : WebSocket onClose / network drop
    CONNECTED --> DISCONNECTED : explicit disconnect()

    state RECONNECT_BACKOFF {
        [*] --> CalculateDelay : delay = min(1000 * 1.5^attempts, 10000)
        CalculateDelay --> TimerWait : window.setTimeout(delay)
        TimerWait --> Retrying : timer expired
    }

    Retrying --> CONNECTING : attempt new WebSocket(url)
    RECONNECT_BACKOFF --> DISCONNECTED : maxReconnectAttempts (10) reached
```

### Key Client Invariants
1. **Idempotent Subscription Management:** Calling `subscribe()` with an existing destination updates the active callback rather than creating duplicate wire subscriptions.
2. **Auto-Resubscription on Reconnect:** When the WebSocket connection drops and recovers, `StompClient` automatically iterates over `subscriptions.values()` and re-issues `SUBSCRIBE` frames for all active topics.
3. **Typing Throttle:** Client typing indicators are throttled with a 1.5-second minimum interval between frames to avoid network congestion.
4. **Presence Decoupling:** Message and presence topics are segregated. Presence notifications (`JOINED`, `LEFT`, `CLOSED`) are published to `/topic/room/{roomId}/presence` and do not trigger duplicate message entries.
5. **Deduplication:** The UI applies client-side deduplication using `message.id` to prevent duplicate renders from concurrent REST history fetches and live WebSocket broadcasts.

---

## 8. Network Configuration & Production Resilience

### Nginx Gateway Configuration Summary
- **Proxy Buffering Disabled:** `proxy_buffering off;` ensures sub-millisecond WebSocket frame forwarding without proxy latency.
- **Connection Keep-Alive:** `proxy_read_timeout 86400s;` and `proxy_send_timeout 86400s;` prevent long-lived idle WebSocket sessions from being prematurely severed by the proxy.
- **Health Probing:** Nginx exposes `/healthz`, and proxies `/actuator/health` to allow the React SPA to continuously monitor backend service availability.

### Automated Room Cleanup Engine
- A Spring Scheduled task (`RoomCleanupService`) queries unclosed rooms exceeding the configured TTL (default 24h) via index `idx_chat_rooms_status_created`.
- Stale rooms are marked as `CLOSED` in batches of 100, and a final presence notification is broadcast to any active connections before the rooms are flagged for historical retention cleanup (default 15 days).

---

## 9. File & Component Cross-Reference

| Layer | File / Symbol | Description |
|---|---|---|
| **Reverse Proxy** | `nginx/nginx.conf` | Edge routing, TLS termination, WebSocket upgrade configuration |
| **Backend Config** | `backend/.../config/WebSocketConfig.java` | Registers `/chat` STOMP endpoint, enables `/topic`, `/queue` brokers |
| **Controller** | `backend/.../controller/ChatController.java` | `@MessageMapping` handlers for chat messages, typing, joins, and leaves |
| **Controller** | `backend/.../controller/RoomController.java` | REST endpoints for room creation, joining, closing, and keyset message history |
| **Service** | `backend/.../service/impl/RoomServiceImpl.java` | Core business logic, presence broadcast, room validation, membership control |
| **Security** | `backend/.../crypto/MessageCryptoService.java` | AES-256-GCM authenticated cipher implementation |
| **JPA Converter** | `backend/.../crypto/EncryptedContentConverter.java` | Automatic encryption and decryption for JPA entities |
| **Frontend Client** | `frontend/.../components/chat/StompClient.ts` | Zero-dependency STOMP 1.2 client over native WebSocket |
| **Frontend UI** | `frontend/.../components/chat/ChatWindow.tsx` | Main chat view, topic subscription hooks, message list & typing states |
| **API Client** | `frontend/.../components/chat/chatApi.ts` | Axios REST client for room management and health checks |
