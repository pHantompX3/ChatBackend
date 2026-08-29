# ADR-0016: Use Quarkus WebSockets Next for Non-Authoritative Real-Time Signaling

**Status:** Accepted

**Date:** 2026-08-15

**Deciders:** Engineering Team

---

## Context

ChatBackend is a private instant-messaging platform. After Milestone 7, the REST API provides full
CRUD for messages, delivery acknowledgement, and read receipts. However, connected clients must
poll to discover new messages, delivery updates, and other state changes — increasing latency and
server load.

We need a low-latency, bidirectional signaling channel that notifies connected clients of mutations
as they happen. The key constraints are:

1. **SQL Server is the authoritative source of truth.** No WebSocket frame may be treated as proof
   of delivery or read receipt. Clients that miss frames must recover via REST history endpoints.
2. **Quarkus 3.x is already in use.** The `quarkus-websockets-next` extension provides a modern,
   reactive-friendly WebSocket API that integrates natively with the CDI event bus.
3. **Security.** Unauthenticated connections must be rejected at the handshake level.
4. **Membership privacy.** Real-time events for a conversation must only reach current active
   members (`left_at IS NULL`).
5. **Session revocation must be enforced in real time.** When a session is revoked, its WebSocket
   connections must be closed immediately.

---

## Decision

We adopt **Quarkus WebSockets Next** (`io.quarkus:quarkus-websockets-next`) as the WebSocket
transport layer, operating as a **non-authoritative signaling plane** only.

### Key design choices

#### 1. Non-Authoritative Transport

WebSocket frames are ephemeral, one-way push notifications. They carry the same data visible via
REST but are never considered a durable delivery acknowledgement. The sequence number carried in
each `message.created` frame allows clients to detect gaps and recover via:

```
GET /api/v1/conversations/{id}/messages?afterSequence={lastSeq}
```

#### 2. Post-Commit CDI Event Dispatching

Real-time frames are only dispatched after the Narayana JTA transaction commits. This is enforced
via `@Observes(during = TransactionPhase.AFTER_SUCCESS)` in `RealtimePostCommitObserver`. If the
transaction rolls back, no frame is sent.

#### 3. Handshake Authentication

The `ChatWebSocketEndpoint` (`@WebSocket(path = "/api/v1/ws")`) rejects unauthenticated handshakes
immediately with close code `4401`. Tokens are accepted via:

- Query parameter `?token=<raw>`
- `Sec-WebSocket-Protocol: bearer.<raw>` (browser-compatible)
- `Authorization: Bearer <raw>` (non-browser clients)

Milestone 9 narrows the public hardened profile without removing local compatibility: query-token
authentication is disabled at the public boundary, browser Origins must match an explicit allowlist,
and browsers use the subprotocol transport. Non-browser clients may omit Origin and use the
subprotocol or Authorization header. See ADR-0017.

The `WebSocketSessionAuthenticator` SHA-256-hashes the raw token and validates it against
`identity.session`, matching the mechanism used in the HTTP auth filter.

#### 4. Active Membership Isolation

`RealtimeEventDispatcher` resolves active conversation members at dispatch time via:

```sql
SELECT user_id
FROM messaging.conversation_member
WHERE conversation_id = ?
  AND left_at IS NULL
ORDER BY joined_at ASC, user_id ASC
```

Only users with matching connections in the `ConnectionRegistry` receive a frame.

#### 5. In-Memory ConnectionRegistry

An `@ApplicationScoped` `ConnectionRegistry` maintains `ConcurrentHashMap<UUID, Set<WebSocketConnection>>`
keyed by `userId`. It is intentionally non-persistent and is cleared on JVM restart (clients
reconnect and resync via REST).

#### 6. Session Revocation Teardown

When `SessionServiceImpl.logout()` or `revokeAllSessionsForUser()` commits, the post-commit
observer invokes `ConnectionRegistry.closeConnectionsForSession(...)` with close code `4401` and
reason `"Session revoked"` (or `"All sessions revoked"`).

#### 7. Reconnect Recovery

Clients that disconnect or miss frames recover by:

1. Noting the last `sequenceNumber` received.
2. After reconnect: `GET /api/v1/conversations/{id}/messages?afterSequence={lastSeq}`.
3. Processing missed messages in order.
4. Sending delivery ACK for the contiguous high-water mark.

---

## Consequences

### Positive

- Low-latency notifications without polling.
- Clean separation: REST = source of truth, WebSocket = best-effort signaling.
- Minimal attack surface: unauthenticated, revoked, or expired sessions never receive frames.
- Privacy guarantee: departed members never receive frames.
- Post-commit dispatch eliminates partial-commit notification hazard.

### Negative / Trade-offs

- In-memory registry is not replicated: horizontal scaling requires sticky sessions or a
  distributed pub/sub layer (deferred to a future ADR).
- `findActiveMemberUserIds` is called once per dispatched event. For conversations with many
  concurrent messages, this adds DB round-trips (acceptable for current scale; can be cached later).
- Browser clients must use `Sec-WebSocket-Protocol` for token delivery because the WebSocket API
  does not allow custom headers in browser environments.

---

## Alternatives Considered

| Alternative | Reason rejected |
|---|---|
| **Server-Sent Events (SSE)** | One-way only; no client commands (delivery/read ACK) |
| **Long polling** | Higher latency, heavier on connection management |
| **Quarkus Reactive Messaging + Kafka** | Adds significant infrastructure complexity for current single-host deployment |
| **Original `quarkus-websocket` (JSR 356)** | Deprecated; WebSockets Next is the strategic replacement |

---

## References

- [Quarkus WebSockets Next Guide](https://quarkus.io/guides/websockets-next-reference)
- [ADR-0001 — Modular Monolith](ADR-0001-use-modular-monolith.md)
- [ADR-0014 — Per-User Delivery and Read Cursors](ADR-0014-use-per-user-delivery-and-read-cursors.md)
- [Milestone 8 Development Guide](../../development-guide/milestone-8-websockets-step-by-step.md)
- [ADR-0017 — Harden One ChatBackend Instance Behind NGINX](ADR-0017-harden-single-instance-deployment.md)
