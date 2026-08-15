# Milestone 8 Implementation Guide

## Real-Time Event Signaling, Connection Management, and Reconnect Synchronization

**Project:** Private Messenger

**Milestone:** 8 - WebSockets

**Database:** Microsoft SQL Server 2022

**Application stack:** Java 25, Quarkus 3.33 LTS, Maven

**Status:** Planned / Ready for Implementation

**Last reviewed:** 2026-08-15

**Implementation snapshot:** This guide specifies the implementation of non-authoritative real-time signaling via Quarkus WebSockets Next (`quarkus-websockets-next`). It defines handshake authentication, an in-memory connection registry, post-commit transactional event dispatching for message and cursor updates, active-membership authorization filters, heartbeat/liveness tracking, and deterministic reconnect recovery backed by SQL Server and REST.

---

## 0. Purpose and Scope

Milestone 8 introduces low-latency, bidirectional event signaling to the platform without compromising the core architectural invariant: **SQL Server and REST remain the sole authoritative source of truth**.

WebSockets in this architecture serve purely as an ephemeral signaling and notification transport. Publication to a WebSocket is **never** proof of delivery or read receipt. If a client disconnects, experiences packet loss, or misses events, it recovers deterministically by querying the durable REST history endpoint (`GET /api/v1/conversations/{id}/messages?afterSequence={seq}`) and then explicitly acknowledging its accepted contiguous sequence.

This milestone is complete only when the backend can:

1. accept authenticated WebSocket connections authenticated via valid session tokens during the handshake,
2. maintain an in-memory connection registry mapping active user IDs and sessions to live WebSocket connections,
3. dispatch real-time events (`message.created`, `message.edited`, `message.deleted`, `delivery.updated`, `read.updated`) only **after** database transactions commit to SQL Server,
4. restrict event fan-out strictly to active conversation members (`left_at IS NULL`), preventing unauthorized data leakage,
5. support bidirectional heartbeats (ping/pong) and cleanly detect client disconnects and expired sessions,
6. deliver delivery and read cursor updates to senders only after explicit recipient acknowledgement has persisted to SQL Server,
7. demonstrate zero data loss and deterministic message recovery across client reconnects via integration tests, and
8. reject or close WebSocket connections upon session revocation, logout, or authentication expiry.

Milestone 8 does **not** implement external multi-node distributed message brokers (such as Kafka or Redis Pub/Sub for clustering), push notifications (APNs/FCM), media streaming/WebRTC signaling, client typing indicators, end-to-end encryption key exchange, or deployment-edge TLS proxying (assigned to Milestone 9).

---

## 1. Deliverables and Exit Criteria

### Deliverables

- accepted **ADR-0016** establishing the non-authoritative WebSocket architecture, post-commit fanout, and reconnect recovery contract,
- addition of `io.quarkus:quarkus-websockets-next` to `pom.xml`,
- authenticated WebSocket endpoint under `/api/v1/ws` (with `/ws` alias) supporting token authentication via query parameter (`?token=...`) or `Sec-WebSocket-Protocol` header,
- in-memory `ConnectionRegistry` tracking active user sessions, connection instances, and subscription metadata,
- post-commit domain event listeners (`@Observes(during = TransactionPhase.AFTER_SUCCESS)`) integrated with `MessageServiceImpl` and `DeliveryServiceImpl`,
- typed event envelopes and JSON frame serializers for:
  * `message.created` (conversationId, sequence, senderId, clientMessageId, body, createdAt),
  * `message.edited` (conversationId, sequence, body, editedAt),
  * `message.deleted` (conversationId, sequence, deletedAt),
  * `delivery.updated` (conversationId, userId, lastDeliveredSequence, updatedAt),
  * `read.updated` (conversationId, userId, lastReadSequence, updatedAt),
- active membership authorization resolver ensuring non-members and departed members never receive conversation events,
- heartbeat protocol and idle connection watchdog configuration,
- integration test suite verifying handshake authentication, post-commit dispatch, membership isolation, multi-device delivery, session revocation disconnects, and reconnect recovery via REST,
- updated `CHANGELOG.md`, `README.md`, OpenAPI/documentation references, and Postman user journey validation.

### Exit criteria

- unauthenticated or invalidly tokenized WebSocket handshakes are rejected immediately with HTTP 401 or close frame `4401 (Unauthorized)`,
- events are published to WebSockets **only after** SQL Server commits the transaction; rolled-back transactions never emit events,
- non-members and departed members (`left_at IS NOT NULL`) cannot receive events for conversations they do not belong to,
- publishing an event to a WebSocket connection is never treated as delivery proof; delivery state changes only upon explicit recipient ACK,
- sender delivery indicators reflect durable cursor state stored in `messaging.conversation_member`,
- revoking a session (`POST /api/v1/sessions/revoke-all` or `DELETE /api/v1/sessions/current`) terminates all corresponding active WebSocket connections immediately,
- clients disconnected during message transmission can reconnect and fully recover missed messages in order via `GET /api/v1/conversations/{id}/messages?afterSequence={seq}`,
- all automated unit and integration tests pass with `./mvnw clean verify`, and Spotless code formatting passes.

---

## 2. Current Baseline and Gap Map

### 2.1 Baseline already present

From Milestones 0 through 7:

- **Identity & Sessions:** Argon2id password hashing, single-use invitations, secure hashed sessions in `identity.session`, and replica-safe SQL-backed login rate limiting.
- **Conversations & Membership:** Direct 1:1 conversation invariant (`user_one_id < user_two_id`), group conversation roles (`OWNER`, `ADMIN`, `MEMBER`), membership history tracking with soft-departure (`left_at`), seek pagination for conversation lists and members.
- **Durable Messaging:** Per-conversation monotonic sequence allocation, sender-scoped idempotency (`client_message_id`), message history forward retrieval (`afterSequence`), sender message editing, and tombstone soft-deletion (`deleted_at`).
- **Delivery & Read State:** Explicit per-user monotonic delivery (`last_delivered_sequence`) and read (`last_read_sequence`) cursors, atomic read-implies-delivery, derived unread counts, and sender-only aggregate delivery queries.
- **Hardened Contracts:** RFC 9457 Problem Details for all HTTP errors, generated OpenAPI 3.1 contracts, 32 KiB request limits, trusted proxy IP resolution, and ECS structured JSON logging with request/trace ID propagation.

### 2.2 Gaps closed by Milestone 8

| Concern | Milestone 7 Baseline | Milestone 8 Solution |
| :--- | :--- | :--- |
| **Client Notification** | Polling REST endpoints (`GET /messages?afterSequence=...`) | Ephemeral, low-latency WebSocket event push immediately upon database commit. |
| **Transport** | Quarkus REST HTTP/1.1 & HTTP/2 only | Quarkus WebSockets Next bidirectional connection endpoint (`/api/v1/ws`). |
| **Connection Tracking** | Stateless HTTP request cycle | In-memory `ConnectionRegistry` associating authenticated user IDs and sessions with active sockets. |
| **Event Fan-out** | None (audit log/queue sink only) | Transactional post-commit event dispatcher filtering recipients by active conversation membership. |
| **Live Cursor Signaling** | Manual HTTP poll to see if recipient read message | Real-time `delivery.updated` and `read.updated` push to message senders and fellow members. |
| **Liveness & Reconnection** | Standard HTTP keep-alive | WebSocket ping/pong frames, idle connection timeouts, and deterministic REST-based reconnect recovery protocol. |

---

## 3. Prerequisites

Before implementing Milestone 8:

1. The working branch is `antigravity/milestone-8` based on the latest `main` commit.
2. Docker environment with SQL Server 2022 is running and healthy:
   ```bash
   docker compose up -d sqlserver
   ```
3. All existing Milestone 0–7 unit and integration tests pass cleanly:
   ```bash
   ./mvnw clean verify
   ```
4. Code formatting standards are enforced via Spotless:
   ```bash
   ./mvnw spotless:check
   ```

---

## 4. Milestone 8 Design Decisions

### 4.1 Non-Authoritative Transport (ADR-0016)

WebSockets are strictly a **non-authoritative notification pipe**. The application architecture guarantees that:

1. All state mutations (sending messages, editing messages, deleting messages, acknowledging delivery, acknowledging read) originate via authenticated REST requests or authenticated WebSocket commands, execute within strict ACID database transactions in SQL Server, and assign durable monotonic sequences.
2. WebSocket frame emission occurs strictly **after** transaction commit (`AFTER_SUCCESS`).
3. If an event frame is dropped in transit, delayed, or lost due to client disconnect, the backend state remains intact. The client detects sequence gaps or reconnects, fetches missed items via `GET /api/v1/conversations/{id}/messages?afterSequence={lastSeenSequence}`, and continues seamlessly.

```
+---------------+              +----------------+              +--------------------+
|  Sender       |              |  ChatBackend   |              | SQL Server 2022    |
+---------------+              +----------------+              +--------------------+
        |                              |                                 |
        | 1. POST /messages            |                                 |
        |----------------------------->|                                 |
        |                              | 2. BEGIN TRANSACTION            |
        |                              |    Allocate Monotonic Sequence  |
        |                              |    Insert message row           |
        |                              |    COMMIT TRANSACTION           |
        |                              |-------------------------------->|
        |                              |<--------------------------------|
        | 3. 201 Created (HTTP)        |                                 |
        |<-----------------------------|                                 |
        |                              |                                 |
        |                              | 4. Post-Commit Event Fanout     |
        |                              |    Resolve Active Members       |
        |                              |    Dispatch WebSocket Frame     |
        |                              |----+                            |
        |                              |    |                            |
        |                              |<---+                            |
        |                              |                                 |
+---------------+                      |                                 |
|  Recipient    |                      |                                 |
+---------------+                      |                                 |
        |   5. WS: message.created     |                                 |
        |<-----------------------------|                                 |
        |                              |                                 |
        | 6. POST /delivery/acknowledgements (or WS ACK command)         |
        |----------------------------->|                                 |
        |                              | 7. UPDATE last_delivered_seq    |
        |                              |    COMMIT TRANSACTION           |
        |                              |-------------------------------->|
        |                              |<--------------------------------|
        | 8. 204 No Content            |                                 |
        |<-----------------------------|                                 |
        |                              |                                 |
        |                              | 9. WS: delivery.updated         |
        |<-----------------------------+-------------------------------->| (To Sender)
```

### 4.2 Handshake Authentication

WebSocket handshakes do not support standard HTTP custom request headers in native browser JavaScript `WebSocket` APIs. To accommodate browsers, desktop, mobile, and server clients securely, the WebSocket handshake supports two authentication mechanisms:

1. **Subprotocol negotiation (`Sec-WebSocket-Protocol`)**: Preferred for modern clients. Client passes `token.<raw_token>` or `bearer.<raw_token>` in the subprotocol list. Server validates the token and echoes the accepted subprotocol.
2. **Query Parameter (`?token=<raw_token>`)**: Supported for browser `WebSocket` APIs where subprotocol manipulation is inconvenient.

**Security Guardrails during Handshake:**
- The raw token is immediately hashed with SHA-256 (`token_hash = SHA256(raw_token)`).
- The session is queried against `identity.session` and `identity.user_account`.
- The handshake is rejected if:
  * Token is missing or malformed -> Handshake rejected / HTTP 401.
  * Session does not exist, is expired (`expires_at <= SYSUTCDATETIME()`), or is revoked (`revoked_at IS NOT NULL`) -> HTTP 401.
  * User account is disabled (`status != 'ACTIVE'`) -> HTTP 403.
- Successful handshake attaches the authenticated `UserId`, `Username`, `SessionId`, and connection timestamp to the WebSocket connection context.

### 4.3 In-Memory Connection Registry

A thread-safe in-memory `ConnectionRegistry` tracks all live WebSocket connections:

- **Data structures:**
  * `ConcurrentHashMap<UUID, Set<WebSocketConnection>> userConnections`: Maps each `UserId` to a set of active connections (supporting multiple tabs, devices, or desktop/mobile clients for the same user).
  * `ConcurrentHashMap<String, WebSocketConnection> sessionConnections`: Maps `connectionId` to connection metadata.
- **Lifecycle Events:**
  * `@OnOpen`: Validates session, registers connection into `userConnections`.
  * `@OnClose`: Removes connection from `userConnections`; if user has no remaining connections, cleans up user entry.
  * `@OnError`: Logs failure with connection context, ensures cleanup upon socket termination.
  * **Session Revocation Interceptor / Listener:** When `SessionService.revokeAllSessions(userId)` or `revokeSession(sessionId)` executes, the registry is notified to immediately close all corresponding live sockets with close code `4401 (Session Revoked)`.

### 4.4 Real-Time Event Envelopes and Frame Schemas

All frames emitted to clients are structured JSON objects adhering to a standard envelope:

```json
{
  "eventId": "e9b2c8f1-3c4a-4b9a-8a1e-7f6d5c4b3a21",
  "eventType": "message.created",
  "occurredAt": "2026-08-15T14:30:00.123456Z",
  "conversationId": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
  "payload": { ... }
}
```

#### Event Payloads

1. **`message.created`**
   ```json
   {
     "conversationId": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
     "sequenceNumber": 42,
     "senderId": "u1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
     "clientMessageId": "msg-001-uuid",
     "body": "Hello world",
     "createdAt": "2026-08-15T14:30:00.123456Z"
   }
   ```
2. **`message.edited`**
   ```json
   {
     "conversationId": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
     "sequenceNumber": 42,
     "body": "Hello world (edited)",
     "editedAt": "2026-08-15T14:35:00.123456Z"
   }
   ```
3. **`message.deleted`**
   ```json
   {
     "conversationId": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
     "sequenceNumber": 42,
     "deletedAt": "2026-08-15T14:40:00.123456Z"
   }
   ```
4. **`delivery.updated`**
   ```json
   {
     "conversationId": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
     "userId": "u2a3b4c5-d6e7-8f9a-0b1c-2d3e4f5a6b7c",
     "lastDeliveredSequence": 42,
     "updatedAt": "2026-08-15T14:30:05.123456Z"
   }
   ```
5. **`read.updated`**
   ```json
   {
     "conversationId": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
     "userId": "u2a3b4c5-d6e7-8f9a-0b1c-2d3e4f5a6b7c",
     "lastReadSequence": 42,
     "lastDeliveredSequence": 42,
     "updatedAt": "2026-08-15T14:31:00.123456Z"
   }
   ```

### 4.5 Active Membership Authorization & Privacy Filter

To prevent unauthorized message interception or event leakage:

1. When a domain event fires for a conversation (`conversationId`), the dispatcher queries the conversation's active member IDs:
   ```sql
   SELECT user_id
   FROM messaging.conversation_member
   WHERE conversation_id = @conversation_id
     AND left_at IS NULL
   ```
2. The dispatcher matches active `user_id`s against the `ConnectionRegistry`.
3. Only connections belonging to active members receive the event frame.
4. Users who have left the conversation (`left_at IS NOT NULL`) or who were never members are excluded immediately.
5. In direct conversations, if one participant has left or deleted the conversation, events are emitted only to remaining active participants.

### 4.6 Client WebSocket Commands (Inbound Messaging / Acknowledgements)

While REST remains fully supported and authoritative, clients connected over WebSocket may optionally issue light commands over the WebSocket connection to reduce HTTP connection overhead:

1. **Ping Command**:
   ```json
   { "action": "ping", "clientTimestamp": 1723732200000 }
   ```
   Server responds immediately with `pong` frame:
   ```json
   { "action": "pong", "clientTimestamp": 1723732200000, "serverTimestamp": 1723732200010 }
   ```
2. **Acknowledge Delivery Command (`delivery.ack`)**:
   ```json
   {
     "action": "delivery.ack",
     "conversationId": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
     "sequenceNumber": 42
   }
   ```
   Server delegates directly to `DeliveryService.acknowledgeDelivery(...)`, updating SQL Server and triggering post-commit fan-out.
3. **Acknowledge Read Command (`read.ack`)**:
   ```json
   {
     "action": "read.ack",
     "conversationId": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
     "sequenceNumber": 42
   }
   ```
   Server delegates directly to `DeliveryService.acknowledgeRead(...)`, updating SQL Server (with read-implies-delivery monotonic updates) and triggering post-commit fan-out.

---

## 5. Step 1 - Add Quarkus WebSockets Next Dependency

Add `quarkus-websockets-next` to `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-websockets-next</artifactId>
</dependency>
```

Configure runtime WebSocket properties in `src/main/resources/application.properties`:

```properties
# WebSocket Next Configuration
quarkus.websockets-next.path=/api/v1/ws
quarkus.websockets-next.server.max-frame-size=65536
quarkus.websockets-next.server.auto-ping-interval=30s
quarkus.websockets-next.server.timeout=60s
```

---

## 6. Step 2 - Establish WebSocket Handshake Authentication & Connection Registry

### 6.1 Package Layout: `com.wayden.messenger.realtime`

```
com.wayden.messenger.realtime
├── api
│   ├── ChatWebSocketEndpoint.java           // WebSockets Next @WebSocket endpoint
│   ├── WebSocketFrameDecoder.java           // Inbound JSON command decoder
│   └── WebSocketFrameEncoder.java           // Outbound event frame encoder
├── application
│   ├── ConnectionRegistry.java              // In-memory user/session/socket manager
│   ├── RealtimeEventDispatcher.java         // Event fan-out with membership check
│   ├── RealtimeEventPublisher.java          // CDI post-commit event publisher
│   └── WebSocketSessionAuthenticator.java   // Handshake token validation
└── domain
    ├── RealtimeEvent.java                   // Domain event envelope record
    ├── RealtimeEventType.java               // Event type enum
    └── WebSocketCommand.java                // Inbound client command records
```

### 6.2 `WebSocketSessionAuthenticator` Implementation

Validates session tokens during the handshake using `SessionRepository` and `UserAccountRepository`:

```java
@ApplicationScoped
public class WebSocketSessionAuthenticator {

    private final SessionRepository sessionRepository;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    @Inject
    public WebSocketSessionAuthenticator(
            SessionRepository sessionRepository,
            TokenHasher tokenHasher,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    public AuthenticatedSession authenticateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Authentication token is required");
        }

        byte[] tokenHash = tokenHasher.sha256(rawToken.trim());
        Instant now = clock.instant();

        SessionRecord session = sessionRepository.findActiveByTokenHash(tokenHash, now)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired session token"));

        return new AuthenticatedSession(session.userId(), session.id(), session.expiresAt());
    }
}
```

### 6.3 `ConnectionRegistry` Implementation

```java
@ApplicationScoped
public class ConnectionRegistry {

    private final ConcurrentHashMap<UUID, Set<WebSocketConnection>> userSockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConnectionMetadata> connectionIndex = new ConcurrentHashMap<>();

    public void register(UUID userId, WebSocketConnection connection, ConnectionMetadata metadata) {
        userSockets.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(connection);
        connectionIndex.put(connection.id(), metadata);
    }

    public void unregister(WebSocketConnection connection) {
        ConnectionMetadata metadata = connectionIndex.remove(connection.id());
        if (metadata != null) {
            Set<WebSocketConnection> sockets = userSockets.get(metadata.userId());
            if (sockets != null) {
                sockets.remove(connection);
                if (sockets.isEmpty()) {
                    userSockets.remove(metadata.userId(), Collections.emptySet());
                }
            }
        }
    }

    public Set<WebSocketConnection> getConnectionsForUser(UUID userId) {
        Set<WebSocketConnection> sockets = userSockets.get(userId);
        return sockets != null ? Collections.unmodifiableSet(sockets) : Collections.emptySet();
    }

    public void closeSessionsForUser(UUID userId, int closeCode, String reason) {
        Set<WebSocketConnection> sockets = userSockets.remove(userId);
        if (sockets != null) {
            for (WebSocketConnection socket : sockets) {
                connectionIndex.remove(socket.id());
                socket.close(new CloseReason(closeCode, reason));
            }
        }
    }
}
```

---

## 7. Step 3 - Real-Time Domain Events and CDI Post-Commit Observers

### 7.1 Domain Event Types

```java
public enum RealtimeEventType {
    MESSAGE_CREATED("message.created"),
    MESSAGE_EDITED("message.edited"),
    MESSAGE_DELETED("message.deleted"),
    DELIVERY_UPDATED("delivery.updated"),
    READ_UPDATED("read.updated");

    private final String typeName;

    RealtimeEventType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }
}
```

### 7.2 Post-Commit Event Observer

Domain services fire standard CDI events during business operations. A dedicated post-commit observer listens only for successful commits:

```java
@ApplicationScoped
public class RealtimePostCommitObserver {

    private final RealtimeEventDispatcher dispatcher;

    @Inject
    public RealtimePostCommitObserver(RealtimeEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void onMessageCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) MessageCreatedEvent event) {
        dispatcher.dispatchMessageCreated(event);
    }

    public void onMessageEdited(@Observes(during = TransactionPhase.AFTER_SUCCESS) MessageEditedEvent event) {
        dispatcher.dispatchMessageEdited(event);
    }

    public void onMessageDeleted(@Observes(during = TransactionPhase.AFTER_SUCCESS) MessageDeletedEvent event) {
        dispatcher.dispatchMessageDeleted(event);
    }

    public void onDeliveryAcknowledged(@Observes(during = TransactionPhase.AFTER_SUCCESS) DeliveryAcknowledgedEvent event) {
        dispatcher.dispatchDeliveryUpdated(event);
    }

    public void onReadAcknowledged(@Observes(during = TransactionPhase.AFTER_SUCCESS) ReadAcknowledgedEvent event) {
        dispatcher.dispatchReadUpdated(event);
    }
}
```

---

## 8. Step 4 - Membership Resolution and Event Fan-Out

### 8.1 Event Dispatcher Flow

When `RealtimeEventDispatcher` receives a committed event:

1. Query active member IDs for `conversationId`:
   ```java
   List<UUID> memberIds = conversationRepository.findActiveMemberUserIds(event.conversationId());
   ```
2. For each active `memberId`:
   * Retrieve all live `WebSocketConnection`s from `ConnectionRegistry.getConnectionsForUser(memberId)`.
   * Serialize payload frame to JSON.
   * Send text frame asynchronously to each connection.
3. Catch and log socket I/O errors individually without disrupting fan-out to other connected members.

```java
@ApplicationScoped
public class RealtimeEventDispatcher {

    private final ConnectionRegistry connectionRegistry;
    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private static final Logger LOG = Logger.getLogger(RealtimeEventDispatcher.class);

    @Inject
    public RealtimeEventDispatcher(
            ConnectionRegistry connectionRegistry,
            ConversationRepository conversationRepository,
            ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
    }

    public void dispatchMessageCreated(MessageCreatedEvent event) {
        List<UUID> activeMemberIds = conversationRepository.findActiveMemberUserIds(event.conversationId());
        RealtimeEventEnvelope envelope = new RealtimeEventEnvelope(
                UUID.randomUUID(),
                RealtimeEventType.MESSAGE_CREATED.getTypeName(),
                event.createdAt(),
                event.conversationId(),
                new MessageCreatedPayload(
                        event.conversationId(),
                        event.sequenceNumber(),
                        event.senderId(),
                        event.clientMessageId(),
                        event.body(),
                        event.createdAt()
                )
        );

        broadcastToUsers(activeMemberIds, envelope);
    }

    private void broadcastToUsers(List<UUID> userIds, RealtimeEventEnvelope envelope) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(envelope);
            for (UUID userId : userIds) {
                Set<WebSocketConnection> sockets = connectionRegistry.getConnectionsForUser(userId);
                for (WebSocketConnection socket : sockets) {
                    socket.sendText(jsonPayload).subscribe().with(
                            success -> {},
                            failure -> LOG.warnf("Failed to send WebSocket event to connection %s: %s",
                                    socket.id(), failure.getMessage())
                    );
                }
            }
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize realtime event envelope", e);
        }
    }
}
```

---

## 9. Step 5 - WebSockets Next Endpoint Implementation

```java
@WebSocket(path = "/api/v1/ws")
public class ChatWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(ChatWebSocketEndpoint.class);

    @Inject
    WebSocketSessionAuthenticator authenticator;

    @Inject
    ConnectionRegistry connectionRegistry;

    @Inject
    DeliveryService deliveryService;

    @Inject
    ObjectMapper objectMapper;

    @OnOpen
    public void onOpen(WebSocketConnection connection, @HandshakeRequest HandshakeRequest handshake) {
        String token = extractToken(handshake);
        try {
            AuthenticatedSession session = authenticator.authenticateToken(token);
            connectionRegistry.register(session.userId(), connection, new ConnectionMetadata(
                    session.userId(), session.sessionId(), Instant.now()
            ));
            LOG.infof("WebSocket opened for user=%s connection=%s", session.userId(), connection.id());
        } catch (Exception e) {
            LOG.warnf("WebSocket authentication rejected for connection=%s: %s", connection.id(), e.getMessage());
            connection.close(new CloseReason(4401, "Unauthorized"));
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        connectionRegistry.unregister(connection);
        LOG.debugf("WebSocket closed connection=%s", connection.id());
    }

    @OnTextMessage
    public void onMessage(String messageText, WebSocketConnection connection) {
        try {
            JsonNode root = objectMapper.readTree(messageText);
            String action = root.path("action").asText();

            switch (action) {
                case "ping" -> handlePing(root, connection);
                case "delivery.ack" -> handleDeliveryAck(root, connection);
                case "read.ack" -> handleReadAck(root, connection);
                default -> connection.sendText("{\"error\":\"UNKNOWN_ACTION\"}");
            }
        } catch (Exception e) {
            LOG.warnf("Failed to process inbound WebSocket frame: %s", e.getMessage());
        }
    }

    private void handlePing(JsonNode root, WebSocketConnection connection) {
        long clientTs = root.path("clientTimestamp").asLong();
        connection.sendText(String.format("{\"action\":\"pong\",\"clientTimestamp\":%d,\"serverTimestamp\":%d}",
                clientTs, System.currentTimeMillis()));
    }

    private void handleDeliveryAck(JsonNode root, WebSocketConnection connection) {
        // Parse conversationId and sequenceNumber, delegate to deliveryService
    }

    private void handleReadAck(JsonNode root, WebSocketConnection connection) {
        // Parse conversationId and sequenceNumber, delegate to deliveryService
    }

    private String extractToken(HandshakeRequest handshake) {
        // Extract from Sec-WebSocket-Protocol or query parameter ?token=
        String tokenParam = handshake.queryParam("token");
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }
        String subprotocol = handshake.header("Sec-WebSocket-Protocol");
        if (subprotocol != null && subprotocol.startsWith("bearer.")) {
            return subprotocol.substring("bearer.".length()).trim();
        }
        return null;
    }
}
```

---

## 10. Step 6 - Reconnection & Gap Recovery Protocol

When a client loses its WebSocket connection or restarts:

```
+---------------+                                   +----------------+
|  Client       |                                   |  ChatBackend   |
+---------------+                                   +----------------+
        |                                                   |
        | [1. WebSocket drops / Reconnects]                 |
        |                                                   |
        | 2. GET /api/v1/conversations/{id}/messages?afterSequence=42
        |-------------------------------------------------->|
        |                                                   |
        | 3. Returns messages [43, 44, 45]                  |
        |<--------------------------------------------------|
        |                                                   |
        | 4. Client applies messages locally in order       |
        |                                                   |
        | 5. POST /api/v1/conversations/{id}/delivery/acknowledgements
        |    { "sequenceNumber": 45 }                       |
        |-------------------------------------------------->|
        |                                                   |
        | 6. 204 No Content                                 |
        |<--------------------------------------------------|
        |                                                   |
        | 7. WS: delivery.updated broadcast to members      |
        |<==================================================|
```

**Invariants Enforced:**
- The client must **never** acknowledge sequences across a gap that has not been fetched via REST.
- Senders that reconnect query `GET /api/v1/conversations/{id}/delivery/status` to synchronize aggregate recipient delivery indicators without depending on missed transient events.

---

## 11. Step 7 - Test Matrix

Milestone 8 verification requires comprehensive Quarkus integration tests:

### 11.1 Authentication & Lifecycle Tests (`WebSocketAuthenticationIntegrationTest`)
- Connection rejected with `4401` when token is missing, malformed, expired, or revoked.
- Connection accepted with valid session token via query param and `Sec-WebSocket-Protocol`.
- Connection closed immediately when user session is revoked via `POST /api/v1/sessions/revoke-all`.
- Disabled user connection rejected.

### 11.2 Real-Time Event Fan-Out Tests (`WebSocketMessagingIntegrationTest`)
- Two connected users in a direct conversation: User A sends message via REST -> User B receives `message.created` frame over WebSocket within 200ms.
- Message editing: User A edits message -> User B receives `message.edited` with updated body.
- Message soft-delete: User A deletes message -> User B receives `message.deleted` with sequence number.
- Multi-client fan-out: User A connected on 2 devices receives self-event with matching `clientMessageId`.

### 11.3 Authorization & Isolation Tests (`WebSocketPrivacyIntegrationTest`)
- User C (not a member of conversation) is connected to WebSocket. User A sends message in conversation (A+B). User C receives **zero** frames.
- User B leaves group conversation (`POST /leave`). User A sends message. User B receives **zero** frames.

### 11.4 Delivery & Read Cursor Synchronization Tests (`WebSocketDeliveryIntegrationTest`)
- User B sends `delivery.ack` (or HTTP ACK) for sequence 42 -> User A receives `delivery.updated` frame over WebSocket.
- User B sends `read.ack` for sequence 42 -> User A receives `read.updated` frame with `lastReadSequence=42` and `lastDeliveredSequence=42`.

### 11.5 Reconnect & Sequence Recovery Tests (`WebSocketReconnectRecoveryIntegrationTest`)
- User B connects, receives messages 1..5.
- User B disconnects WebSocket.
- User A sends messages 6..10.
- User B reconnects WebSocket, queries REST `GET /messages?afterSequence=5`, recovers 6..10.
- User B sends ACK for sequence 10 -> User A receives delivery notification.

---

## 12. Step 8 - Architecture Decision Record: ADR-0016

Document the design decisions in `docs/architecture/decision/ADR-0016-use-websockets-next-for-realtime-signaling.md`:

- **Title:** ADR-0016: Use WebSockets Next for Non-Authoritative Realtime Signaling
- **Status:** Accepted
- **Context:** Need low-latency message and delivery signaling for connected clients without compromising data durability or REST contract authority.
- **Decision:**
  * Adopt Quarkus WebSockets Next for HTTP/1.1 and HTTP/2 WebSocket transport.
  * WebSockets are strictly non-authoritative; SQL Server and REST remain the sole source of record.
  * Handshake authenticates against `identity.session` with SHA-256 token hashing.
  * Event publication occurs post-commit via CDI transactional event observers.
  * Active membership filter is enforced before frame emission.
  * Reconnection recovery is performed deterministically via REST seek pagination (`afterSequence`).

---

## 13. Documentation, OpenAPI, and Postman Maintenance

1. **`README.md`**: Update milestone progress matrix to reflect Milestone 8 implementation.
2. **`CHANGELOG.md`**: Add Milestone 8 deliverables under `[Unreleased]`.
3. **OpenAPI & Postman**:
   * Document WebSocket endpoint `/api/v1/ws` in OpenAPI specifications.
   * Add Postman reconnect-reconciliation user flow in `postman/collections/chat-backend-user-flows.postman_collection.json`.
   * Run `./scripts/postman/validate-postman.sh`.

---

## 14. Local Validation Sequence

Run the complete validation pipeline in order:

```bash
# 1. Flyway naming validation
./scripts/database/validate-flyway-naming.sh

# 2. OpenAPI validation
./scripts/openapi/validate-openapi.sh

# 3. Postman collection validation
./scripts/postman/validate-postman.sh

# 4. Spotless code formatting check
./mvnw spotless:check

# 5. Full test suite execution with testcontainers
./mvnw --batch-mode --no-transfer-progress clean verify

# 6. Working tree inspection
git diff --check
git status --short
```

---

## 15. Common Failure Modes and Anti-Patterns

1. **Publishing events inside active transactions**
   * *Anti-Pattern:* Emitting WebSocket frames before transaction commit. If SQL Server aborts or rolls back the transaction, clients receive phantom messages that do not exist in the database.
   * *Solution:* Always use `@Observes(during = TransactionPhase.AFTER_SUCCESS)` or explicit post-commit dispatchers.
2. **Treating WebSocket publication as proof of delivery**
   * *Anti-Pattern:* Marking `last_delivered_sequence` when a socket frame is written.
   * *Solution:* Delivery state advances **only** when the recipient explicitly sends an acknowledgement command or REST request.
3. **Missing active membership checks during event fan-out**
   * *Anti-Pattern:* Broadcasting to all connected subscribers who requested a conversation ID without checking SQL Server membership.
   * *Solution:* Resolve active member user IDs (`left_at IS NULL`) for every event and only send to matching authenticated connections in `ConnectionRegistry`.
4. **Leaking sensitive data in connection logs**
   * *Anti-Pattern:* Logging raw session tokens or message text in WebSocket access/error logs.
   * *Solution:* Log only connection ID, authenticated `userId`, and event metadata; never log raw token values or decrypted bodies.
5. **Failing to close sockets upon session revocation**
   * *Anti-Pattern:* Letting existing WebSocket connections stay alive after user logs out or admin revokes sessions.
   * *Solution:* Hook session revocation events into `ConnectionRegistry.closeSessionsForUser(...)` to immediately close active sockets with code `4401`.
