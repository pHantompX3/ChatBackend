# Client Responsibility and Recovery Guide

**Status:** Current implementation audit  
**Last reviewed:** 2026-08-22  
**Applies to:** ChatBackend `0.8.0-SNAPSHOT`

## 1. Purpose

This is the canonical integration guide for the first browser, mobile, or desktop client placed in
front of ChatBackend. It records the behavior that the backend guarantees and the complementary work
that a client must perform to provide a seamless user experience.

SQL Server is the durable authority. REST reads and mutations expose that authority. The WebSocket
endpoint is a low-latency, best-effort signal and has no durable per-client frame queue, replay cursor,
or frame-delivery acknowledgement. A correct client must therefore work while the socket is delayed,
disconnected, duplicated, reordered, or missing an event.

This guide supplements the generated OpenAPI contract, ADR-0014, and ADR-0016. It does not redefine
endpoint schemas or authorization policy.

### 1.1 Maintenance cycle

This guide is a living, release-controlled contract rather than a one-time client-planning artifact.

- During implementation, investigation, testing, review, or incident analysis, record every verified
  new client-facing responsibility, recovery rule, UX implication, transport limitation, security
  consideration, or backend capability gap here in the same change set that establishes the learning.
- Do not defer a known learning solely because the current milestone does not build a client. If the
  learning is tentative, label it as an open question instead of presenting it as implemented fact.
- Before declaring any milestone complete, audit its code, migrations, API/OpenAPI changes,
  WebSocket behavior, ADRs, development guide, tests, and operational findings against this guide.
- Update affected responsibility tables, workflows, acceptance checks, limitations, and canonical
  references. Remove or revise superseded workarounds when the backend gains a stronger contract.
- Add a corresponding `CHANGELOG.md` entry when the review produces a notable addition or correction.
- A milestone is not documentation-complete until this review has been performed and either the guide
  is updated or the completion report explicitly states that no client-facing responsibilities or
  recovery behavior changed.

## 2. Responsibility Boundary

| Concern | Backend guarantee | Client responsibility |
| --- | --- | --- |
| Durable state | Committed messages, tombstones, memberships, sessions, and per-user delivery/read cursors live in SQL Server. | Treat successful REST responses and later REST reads as authoritative; never make socket receipt the only copy of state. |
| Real-time signals | Post-commit events are attempted for every currently connected active member. | Expect missed, duplicated, delayed, and potentially reordered events; merge them idempotently. |
| Frame failure | A failed socket send is logged, then discarded. | Reconcile through REST after reconnect, detected gaps, app resume, and suspicious state. |
| Authentication | REST and `/api/v1/ws` validate the opaque session token; revocation closes matching sockets with `4401`. | Store tokens securely, avoid logging them, reconnect with a valid token, and return to login after `401` or socket `4401`. |
| Message submission | `clientMessageId` gives sender-scoped idempotency; first acceptance returns `201`, replay returns the same message with `200`. | Generate one UUID per user action, persist it with the pending message, and reuse it for every retry. |
| Ordering | Messages receive a monotonic sequence within each conversation. | Order by `sequenceNumber`, track the highest contiguous accepted sequence, and do not advance across gaps. |
| Delivery/read state | Explicit acknowledgements monotonically update one cursor per user and conversation; read also advances delivery. | Acknowledge only content actually accepted/read, retry safely, and remember that one device advances the shared user cursor for all devices. |
| Receipt visibility | A sender can query aggregate delivery/read status; recipient identities and raw positions are not exposed. | Render aggregate states accurately and do not imply per-device or named-recipient proof. |
| Membership privacy | Only active members can read a private conversation or receive its events. | Remove inaccessible cached content from active UI on privacy-preserving `404`, leave/removal, or account/session changes according to product policy. |
| Pagination | Conversation/member pages use opaque cursors; message history uses forward sequence pagination. | Follow returned cursors exactly, avoid inventing or decoding opaque cursors, and persist per-conversation synchronization state. |

## 3. Recommended Client State Model

Maintain three distinct layers:

1. **Durable local cache:** conversations, messages, tombstones, membership summaries, own positions,
   and the last successful synchronization markers. Mobile and installable clients should use an
   on-device database; browser clients may use IndexedDB. Memory alone is insufficient for offline
   send and process-restart recovery.
2. **Pending operation queue:** locally generated sends and other mutations whose outcome is unknown.
   Store the operation kind, target IDs, payload, creation time, retry count, and stable
   `clientMessageId` where applicable. Never store the raw session token in this queue.
3. **Ephemeral connection state:** socket status, reconnect attempt, heartbeat state, and transient UI
   indicators. This may be discarded and rebuilt from durable state.

Suggested outgoing-message UI states are:

- `pending`: retained locally but the client has no authoritative server response;
- `accepted`: REST returned `200` or `201` with the durable message and server sequence;
- `delivered`: sender status reports the applicable recipient aggregate as delivered;
- `read`: sender status reports the applicable recipient aggregate as read;
- `failed`: a non-retryable problem requires user action.

Do not map “WebSocket frame sent” or “WebSocket frame received” to durable delivery. The current
backend tracks receipts per user, not per device, and does not expose receipt timestamps.

## 4. Authentication and Connection Lifecycle

### 4.1 Login and token handling

- Authenticate with `POST /api/v1/sessions`.
- Store the opaque token using the platform's protected credential storage. Browser applications
  should minimize exposure to script and URL/history leakage.
- There is no refresh-token endpoint. An expired, revoked, or invalid session requires login again.
- Respect `429 Too Many Requests` and the `Retry-After` header. Disable or delay login retry rather
  than running a tight loop.
- Treat `401` from REST or socket close code `4401` as an authentication-state transition: stop
  automatic authenticated work, clear sensitive credentials, close the socket, and show login.
- Call `POST /api/v1/sessions/logout` when the user explicitly signs out. Local credential deletion
  should still happen if the network call cannot complete.

### 4.2 WebSocket establishment

Connect to `/api/v1/ws`. Supported token transports are `token.<token>` or `bearer.<token>` as a
WebSocket subprotocol, `?token=<token>`, or an `Authorization` header for clients that can set one.
Prefer a subprotocol over a query parameter where the client platform supports it because URLs are
more commonly retained in diagnostics and intermediary logs.

The client must maintain a connection state machine such as `offline`, `connecting`, `connected`,
`reconciling`, and `reauthentication-required`. A connected socket is not evidence that local state is
current; do not show the client as fully synchronized until reconciliation finishes.

### 4.3 Liveness and reconnect

- Respond to transport-level ping/pong behavior supplied by the WebSocket library. The application
  also accepts `{"action":"ping"}` and responds with `{"type":"pong"}`.
- Detect silent connections using a bounded pong/traffic deadline appropriate to the client platform.
- Reconnect with exponential backoff and jitter. Suspend aggressive reconnect while the device is
  offline or the app is backgrounded.
- Reset backoff after a stable connection, not merely after a TCP/WebSocket handshake.
- On every reconnect and foreground resume, reconcile durable state before relying on new frames.
- Multiple tabs or devices are supported, but every instance must independently reconcile its local
  cache. A receipt acknowledged by one instance advances the shared per-user cursor.

## 5. Incoming Event Processing

Every domain event uses an envelope containing `eventId`, `eventType`, `occurredAt`,
`conversationId`, and `payload`. Current event types are `message.created`, `message.edited`,
`message.deleted`, `delivery.updated`, and `read.updated`.

Client rules:

1. Validate the envelope and ignore unknown fields. Unknown `eventType` values must not terminate the
   connection; log bounded diagnostics and reconcile if the event might affect visible state.
2. Use `eventId` only for short-lived duplicate suppression. Event IDs are not durable replay
   offsets and cannot be used to fetch missed frames.
3. Upsert messages by server `messageId`; correlate the sender's optimistic row using
   `clientMessageId`.
4. Order messages by server `sequenceNumber`, never by arrival time or `occurredAt`.
5. If a created-message sequence is greater than the next expected sequence, buffer it, fetch the
   missing range through REST, and then apply the buffered event.
6. Apply edit/delete events only when their identifiers and conversation match known state. A delete
   produces a tombstone: retain ordering metadata and remove the body from display/local search.
7. Treat delivery/read values as monotonic. Ignore an event that would move a displayed cursor
   backward.
8. Never infer authorization from receipt of an old frame. REST remains authoritative after a
   membership change.

## 6. Message Submission and Offline Outbox

For each send action:

1. Generate a UUID `clientMessageId` once.
2. Persist the pending message and identifier locally before the first network attempt.
3. Render an optimistic pending row without assigning a server sequence.
4. Submit `POST /api/v1/conversations/{conversationId}/messages` with the same identifier on every
   retry.
5. On `201`, replace the optimistic row with the returned durable message. On `200`, do the same;
   this is successful idempotent recovery, not a duplicate message.
6. If the connection fails before a response, leave the operation pending and retry with bounded
   exponential backoff when connectivity returns.
7. Stop automatic retry for validation, authorization, or membership failures and give the user an
   actionable state. Retry transient network failures and `5xx` responses using the same identifier.

The client may retry pending sends on connectivity restoration and periodically while online, but it
should not poll every second indefinitely. Use backoff, connectivity signals, app lifecycle events,
and an explicit user retry affordance.

Other mutations do not currently expose a general client-operation idempotency key. When their HTTP
outcome is ambiguous, do not blindly create a second logical operation. Re-read the relevant resource
where possible, or present a retry that is safe for that specific endpoint.

## 7. History and Reconciliation

### 7.1 Created-message recovery

For each locally known conversation, retain the highest **contiguous** sequence stored locally.
After initial load, reconnect, foreground resume, a sequence gap, or suspected frame loss:

1. Call `GET /api/v1/conversations/{conversationId}/messages?afterSequence={lastContiguous}&limit={n}`.
2. Upsert returned messages in sequence order, including tombstones.
3. Continue using `nextAfterSequence` until it is absent.
4. Re-evaluate buffered socket events after filling the gap.
5. Advance the local contiguous marker only after local persistence succeeds.

### 7.2 Current limitation: missed edits and deletions

Forward history is keyed by the message's original sequence. If a client already stored sequence 25
and later misses an edit or deletion for that message, fetching `afterSequence=25` will not return the
changed row. There is currently no durable conversation change feed, message revision cursor, or
single-message read endpoint.

Until the backend gains such a contract, a client that needs strong mutation freshness must perform a
bounded re-fetch of previously loaded history after reconnect/foreground resume and merge rows by
`messageId`. A full refresh from sequence zero is correct but may become expensive for long
conversations. This is a known backend capability gap to resolve before promising seamless recovery
of arbitrarily old missed edits/deletions.

### 7.3 Conversation and membership recovery

Conversation creation, membership changes, role changes, ownership transfer, leaving, and user
directory changes do not currently have WebSocket event types. Refresh the conversation list after
login/reconnect/foreground resume and refresh conversation detail/member pages when entering or
administering a conversation. Follow opaque `nextCursor` values exactly.

A privacy-preserving `404` for a formerly accessible conversation can mean it no longer exists or the
actor is no longer an active member. Stop polling/acknowledging it and remove it from active UI without
trying to distinguish those cases.

## 8. Delivery, Read, and Unread Behavior

- Send delivery acknowledgement only after every message through that sequence has been accepted and
  durably stored by the client. Send read acknowledgement only after the product's actual read rule is
  satisfied (for example, visible while the conversation is foregrounded).
- Acknowledge the highest contiguous sequence, never merely the newest frame observed.
- Use either REST `PUT /delivery-position` and `PUT /read-position` or the matching WebSocket commands.
  If a WebSocket command's result is uncertain, retrying the same or lower sequence is safe because
  cursor updates are monotonic and stale/equal acknowledgements are no-ops.
- Query `GET /position` to restore the current user's authoritative `latestSequence`, delivered/read
  cursors, and derived unread count. Do not rely solely on locally incremented badges.
- Read acknowledgement also advances delivery. A client does not need to send both for the same
  sequence once it is read.
- A cursor belongs to the user across every session/device. The backend cannot express “delivered to
  all of this user's devices.” Product copy and icons must not claim that.
- For messages authored by the current user, `GET /messages/{messageId}/status` provides aggregate
  recipient counts and `allDelivered`/`allRead`. It intentionally does not reveal recipient identity.
- Receipt event frames are best-effort. If one is missed, refresh sender-visible status for messages
  whose receipt indicator is on screen or otherwise relevant; avoid polling status for every message.

## 9. HTTP Failure and Retry Policy

| Result | Client behavior |
| --- | --- |
| Network failure/timeout | Outcome may be unknown. Preserve the pending operation, reconcile, and retry only according to endpoint idempotency. |
| `400` | Treat as a client/input defect; do not automatically retry unchanged input. |
| `401` | End the authenticated workflow, close the socket, clear/reprotect credentials, and request login. |
| `403` | Show that the authenticated user lacks permission; refresh role/membership state when relevant. |
| `404` on private resources | Treat as unavailable without revealing whether it exists; remove stale access assumptions. |
| `409` | Refresh conflicting state and ask the user to retry or resolve the conflict. |
| `429` | Honor `Retry-After`; prevent automatic/manual retry storms. |
| `5xx` | Preserve user work, retry with capped exponential backoff where safe, and expose a non-destructive retry action. |

Parse `application/problem+json` by stable problem fields, retain the server request/occurrence
identifier for support, and show user-safe copy. Do not display internal diagnostics or branch business
logic on human-readable detail strings.

## 10. Platform and UX Responsibilities

- Detect OS/browser connectivity and lifecycle changes, while recognizing that “online” does not
  prove backend reachability.
- Persist drafts separately from the send outbox.
- Avoid notification duplication when multiple tabs/devices receive the same event.
- Reconcile notification taps before navigating to cached content.
- Keep local clocks out of message ordering; server timestamps are display metadata and sequences are
  ordering metadata.
- Bound cache size and remove message bodies from local indexes when tombstoned.
- Protect tokens, message bodies, and cached private content using facilities appropriate to the
  client platform. The backend does not provide end-to-end encryption.
- Provide explicit offline, reconnecting, synchronizing, pending, failed, and reauthentication UI
  states so transient transport behavior is not mistaken for data loss.
- Apply backpressure: batch reconciliation and acknowledgements, limit concurrent requests, and avoid
  per-message receipt polling.

## 11. First-Client Acceptance Checklist

A client is not ready for production integration until automated tests demonstrate:

- offline send survives process restart and reuses its original `clientMessageId`;
- a lost HTTP send response resolves to one server message;
- socket loss during new-message fan-out recovers every sequence through REST;
- duplicated and reordered socket events do not duplicate or regress UI state;
- reconnect, foreground resume, and detected gaps enter and complete reconciliation;
- missed edits/deletions follow the documented bounded-refresh workaround;
- delivery/read acknowledgements never cross a local sequence gap;
- multiple devices correctly present shared per-user receipt cursors;
- `401`, socket `4401`, logout, and administrative revocation terminate authenticated work;
- `429` honors `Retry-After` and reconnect/retry loops use jittered backoff;
- removal from a conversation stops access and clears active cached views;
- tombstones remove bodies while preserving sequence continuity;
- unknown socket event types and additional JSON fields are forward-compatible;
- secrets and message bodies do not appear in client telemetry.

## 12. Backend Gaps to Revisit Before Client Production Readiness

The audit found these areas where client workarounds are possible but not ideal:

1. **No durable mutation/change cursor:** missed edits and deletes of previously synchronized messages
   require history re-fetch rather than an incremental revision feed.
2. **No realtime conversation/membership events:** clients must refresh conversation and member lists.
3. **No server-side WebSocket replay or per-device acknowledgement:** recovery is REST-driven and
   receipt state is per user.
4. **No refresh-token flow:** expired sessions require full login.
5. **No general idempotency key for non-message mutations:** ambiguous failures need endpoint-specific
   reconciliation.
6. **No machine-readable WebSocket contract in OpenAPI:** event/command schemas are documented in the
   Milestone 8 guide and source rather than generated as an AsyncAPI artifact.

These are not evidence that the current backend loses durable data. They identify where the first
client must compensate or where a future backend milestone could simplify client behavior.

## 13. Canonical References

- HTTP schemas and response codes: `docs/api/openapi.yaml`
- Durable receipt semantics: `docs/architecture/decision/ADR-0014-use-per-user-delivery-and-read-cursors.md`
- WebSocket transport decision: `docs/architecture/decision/ADR-0016-use-websockets-next-for-realtime-signaling.md`
- Messaging behavior: `docs/development-guide/milestone-5-messaging-step-by-step.md`
- Reconnect and receipt behavior: `docs/development-guide/milestone-6-delivery-and-read-state-step-by-step.md`
- WebSocket frames and commands: `docs/development-guide/milestone-8-websockets-step-by-step.md`
- Executable REST journeys: `postman/README.md`
