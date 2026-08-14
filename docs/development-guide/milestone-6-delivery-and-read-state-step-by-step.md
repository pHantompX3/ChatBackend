# Milestone 6 Implementation Guide

## Durable Delivery, Read State, and Unread Counts

**Project:** Private Messenger

**Milestone:** 6 - Delivery and read state

**Database:** Microsoft SQL Server 2022

**Application stack:** Java 25, Quarkus 3.33 LTS, Maven

**Status:** Implemented

**Last reviewed:** 2026-08-13

**Implementation snapshot:** The delivery module now provides explicit monotonic delivery/read
acknowledgements, own-position and derived unread queries, sender-only aggregate status, stable
problem responses, bounded whole-transaction deadlock retry, safe HTTP audit enrichment, real SQL
Server tests, and the Postman reconciliation journey. The Milestone 4 cursor schema proved sufficient,
so no redundant Flyway migration was added.

---

## 0. Purpose and Scope

Milestone 6 makes recipient delivery and read state durable without introducing real-time transport.
SQL Server remains authoritative. A message becomes delivered only after an authenticated client for
that recipient explicitly acknowledges the highest contiguous conversation sequence it has accepted.
A message becomes read only after a client explicitly acknowledges that the user has visibly consumed
the conversation through that sequence.

This milestone is complete only when the backend can:

1. persist monotonic delivery and read positions for an active conversation member,
2. safely accept retries and out-of-order older acknowledgements without moving either cursor back,
3. prevent either cursor from advancing beyond the latest committed message sequence,
4. atomically advance delivery when a read acknowledgement moves beyond it,
5. return the authenticated member's current positions and unread count,
6. let a message sender distinguish server acceptance, recipient delivery, and recipient read state,
7. calculate direct and group status without exposing unnecessary per-member activity,
8. support deterministic client reconciliation after missed signals or offline periods.

Milestone 6 does **not** implement WebSockets, push notifications, presence, typing indicators,
per-device cursors, per-device registration, notification preferences, background client execution,
end-to-end encryption, per-recipient receipt timestamps, or a batch status API. It does not infer
delivery from an open connection, an HTTP response to the sender, broker publication, or server-side
fan-out.

The lifecycle remains:

```text
LOCAL_PENDING    client-owned outbox; SQL Server has not accepted the message
SERVER_ACCEPTED  message transaction committed and returned a server ID and sequence
DELIVERED        recipient's durable lastDeliveredSequence reached the message sequence
READ             recipient's durable lastReadSequence reached the message sequence
```

For groups, `DELIVERED` and `READ` are per-recipient facts. Aggregate `allDelivered` or `allRead`
means every currently active applicable recipient has reached the message sequence.

---

## 1. Deliverables and Exit Criteria

### Deliverables

- accepted ADR for Version 1 per-user cursor semantics,
- verified SQL Server constraints and least-privilege permissions for existing cursor columns,
- transactional delivery-position acknowledgement,
- transactional read-position acknowledgement that also advances delivery when required,
- authenticated current-position and unread-count query,
- sender-only aggregate delivery/read status query,
- stable delivery-state problem responses,
- safe audit metadata for cursor updates and internal failures,
- SQL Server repository, concurrency, API, privacy, and authorization tests,
- updated Postman contracts and run-all journey,
- documented reconnect and sequence-gap reconciliation contract.

### Exit criteria

- publishing or attempting to publish a message never advances a recipient cursor,
- stale delivery and read acknowledgements are idempotent successful no-ops,
- concurrent acknowledgements cannot move either position backwards,
- `lastReadSequence` never exceeds `lastDeliveredSequence`,
- neither cursor can exceed the latest committed conversation sequence,
- a read acknowledgement advances both cursors atomically when required,
- unread counts exclude the actor's own messages and deleted tombstones,
- sender status distinguishes accepted, delivered, and read state,
- group aggregates exclude the sender and use current active recipients,
- non-members and departed members cannot read or mutate delivery state,
- no client can advance or inspect another user's raw cursor,
- message bodies never enter logs or durable HTTP audit metadata,
- migration validation, Maven, Postman, and repository checks all pass.

---

## 2. Current Baseline and Gap Map

### 2.1 Baseline already present

The merged Milestone 5 baseline provides:

- authenticated `UserId` in the request context,
- durable direct and group conversations,
- retained membership rows with active membership represented by `left_at IS NULL`,
- `last_delivered_sequence` and `last_read_sequence` on `messaging.conversation_member`,
- defaults of zero and the non-negative/read-not-ahead check constraint,
- runtime `SELECT` and `UPDATE` permission on `messaging.conversation_member`,
- durable messages ordered by `(conversation_id, sequence_number)`,
- `next_message_sequence`, whose committed high-water mark is `next_message_sequence - 1`,
- deterministic forward message history,
- private-resource membership checks and message-specific safe errors,
- asynchronous HTTP auditing through RabbitMQ with SQL Server persistence,
- real SQL Server Testcontainers and Postman discovery/synchronization tooling.

Milestone 4 intentionally created the cursor columns early. Therefore, Milestone 6 must **not** add a
duplicate migration merely to satisfy older roadmap wording. A forward-only migration is required only
if implementation proves that a new index or constraint is necessary. Applied Flyway migrations remain
immutable.

### 2.2 Baseline gaps closed by Milestone 6

The implementation closes the following gaps from the merged Milestone 5 baseline:

1. application services and repositories now advance the cursors,
2. acknowledgements validate against the latest committed message in the update transaction,
3. authenticated delivery and read acknowledgement APIs are available,
4. current-position and unread-count responses are available,
5. senders can query aggregate delivery/read status,
6. delivery-specific typed errors and audit attributes are implemented,
7. cursor updates have real SQL Server concurrency coverage,
8. acknowledgement, status, and reconciliation behavior is executable through Postman.

---

## 3. Prerequisites

Before implementation, verify the merged Milestone 5 baseline:

```bash
./scripts/database/validate-flyway-naming.sh
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
./mvnw --batch-mode --no-transfer-progress clean verify
git diff --check
```

Work on the Milestone 6 feature branch. The planning change advances the development version to
`0.6.0-SNAPSHOT` because the milestone adds backward-compatible API capability; keep that version
through implementation. Do not create a release tag merely for starting or completing the milestone.

ADR-0014 is authoritative for the Version 1 per-user cursor and status-visibility decisions. Any move
to per-device state, raw group member receipts, or implicit transport acknowledgements requires a new
or superseding ADR.

---

## 4. Milestone 6 Design Decisions

### 4.1 SQL Server is authoritative

- Delivery and read positions are durable database state.
- An authenticated client acknowledgement is the only action that advances a position.
- WebSocket, broker, push, and HTTP publication are signals, not proof of client acceptance.
- A sender's successful send response means `SERVER_ACCEPTED`, never `DELIVERED` or `READ`.
- A cursor update is successful only after its SQL transaction commits.

### 4.2 Version 1 cursors are per user

Any authenticated session for a user may advance that user's membership cursors. The cursor represents
the furthest conversation position accepted/read by that user across all their clients. It does not
prove that every device has received the messages.

This keeps browser, mobile, and desktop clients interoperable without introducing device identity.
Per-device or all-device delivery semantics are future work and must not be inferred from session IDs.

### 4.3 Acknowledgements use the highest contiguous sequence

The client submits the highest sequence for which it has accepted every preceding conversation event
it needs through that point. After reconnecting, a client:

1. loads history after its locally retained sequence,
2. applies messages and tombstones in sequence order,
3. detects and recovers any gap through the REST history API,
4. acknowledges only the highest contiguous sequence it accepted.

The server can validate that a requested sequence is non-negative and no greater than the committed
high-water mark. It cannot prove what a client retained locally. Claiming continuity is the client's
contractual responsibility.

Sequence `0` is valid and represents no acknowledged messages. An older or equal acknowledgement
returns success without changing state. Gaps in the numeric sequence are not expected for committed
messages, but clients must still reconcile rather than infer delivery from a transient signal.

The acknowledgement PUT is naturally idempotent and does not accept a separate idempotency key. The
authenticated user, conversation, acknowledgement type, and requested sequence completely determine
the monotonic operation.

### 4.4 Read implies delivery

A read acknowledgement for sequence `N` atomically advances both positions to at least `N`:

```text
newDelivered = max(currentDelivered, N)
newRead      = max(currentRead, N)
```

The update occurs in one transaction and one conditional SQL statement. There must never be a visible
state where read exceeds delivery.

### 4.5 Unread-count semantics

For the authenticated active member:

```text
unreadCount = count(
  active message rows in this conversation
  where sequence_number > last_read_sequence
    and sequence_number <= latest_sequence_snapshot
    and sender_id <> authenticated user
    and deleted_at is null
)
```

Consequences:

- the actor's own messages are not unread,
- deleted tombstones preserve synchronization order but do not count as unread content,
- edits do not make an already-read message unread again,
- the count is derived, not separately persisted,
- `COUNT_BIG` maps to Java `long`,
- the current position response and unread count must come from one bounded SQL snapshot.

### 4.6 Sender-visible status is aggregate-only

Only the original sender may query delivery/read status for a message. The response exposes counts,
not a list of member identities or raw per-member cursors:

```text
recipientCount
deliveredCount
readCount
allDelivered
allRead
```

Applicable recipients are current active members other than the sender. This matches the existing
membership model, which retains one row but does not retain multiple historical join intervals.
Adding a member can increase the aggregate denominator; removing a member can decrease it.
Consequently aggregate flags can change as membership changes even though every individual cursor is
monotonic. This is intentional and must be tested rather than described as immutable message state.

For a direct conversation, the aggregate normally has one recipient and maps cleanly to familiar
single/double/read indicators. For a group, clients must display counts or accurately labelled partial
status. If `recipientCount` is zero, both `allDelivered` and `allRead` are `false`; absence of a
recipient is not proof of delivery.

Platform administrators receive no implicit access. An active non-sender may read the message through
normal history but receives `403 DELIVERY_STATUS_FORBIDDEN` for its status query. Non-members and
departed members receive the privacy-preserving inaccessible-conversation response.

### 4.7 Membership lifecycle

- Only an active membership can acknowledge or query positions.
- Removing a member preserves their last cursors for auditability but immediately denies access.
- Reactivating a membership continues the existing Milestone 4 behavior: role becomes `MEMBER`,
  `joined_at` is replaced, `left_at` is cleared, and both cursors reset to zero.
- A reactivated member can access the conversation's retained history under the current Version 1
  history policy, so unread state is recalculated from zero.
- Deploying Milestone 6 does not backfill or infer cursors for messages accepted during Milestone 5.
  Existing membership positions remain zero until an authenticated client reconciles history and
  explicitly acknowledges it.
- Acknowledgement never changes membership, role, or conversation timestamps.

### 4.8 Canonical REST routes

```text
PUT /api/v1/conversations/{conversationId}/delivery-position
PUT /api/v1/conversations/{conversationId}/read-position
GET /api/v1/conversations/{conversationId}/position
GET /api/v1/conversations/{conversationId}/messages/{messageId}/status
```

All routes require authentication. Actor identity always comes from the session. Request bodies never
contain a user ID, device ID, timestamp, or conversation ID.

### 4.9 HTTP and idempotency policy

- Successful delivery/read acknowledgement returns `204 No Content`.
- Retrying the same value returns `204`.
- Submitting an older value returns `204` without moving state backwards.
- Negative, non-integral, missing, or unknown request properties return `400`.
- A sequence beyond the committed high-water mark returns `409 DELIVERY_SEQUENCE_AHEAD`.
- Missing/inaccessible conversations use the same privacy-preserving `404` outcome.
- Sender status for a message outside the path conversation returns `404`.
- A status query by an active non-sender returns `403`.
- Internal database failures return a safe `500 DELIVERY_INTERNAL_ERROR`; privileged audit metadata
  retains bounded root-cause diagnostics under the existing redaction policy.

---

## 5. Step 1 - Verify Schema and Permissions

### 5.1 Existing schema contract

`messaging.conversation_member` already contains:

```sql
last_delivered_sequence BIGINT NOT NULL DEFAULT (0),
last_read_sequence BIGINT NOT NULL DEFAULT (0),

CONSTRAINT ck_messaging_conversation_member_positions CHECK (
    last_delivered_sequence >= 0
    AND last_read_sequence >= 0
    AND last_read_sequence <= last_delivered_sequence
)
```

Extend `SchemaVerificationTest` to assert the columns, defaults, constraint, and runtime permissions
specifically as Milestone 6 requirements. Confirm the runtime principal can `SELECT` and `UPDATE` the
membership table but cannot `DELETE` it.

Do not attempt a cross-table `CHECK` constraint for the latest committed sequence; SQL Server check
constraints cannot safely enforce that relationship. High-water validation belongs in the same
transaction as the cursor update.

No data backfill is required. In particular, do not mark historical messages delivered or read during
migration or startup. Existing zero cursors are valid and preserve the explicit-acknowledgement rule.

### 5.2 Migration decision

Start implementation without a new migration. The existing clustered message history index and active
membership indexes support the planned queries. Add a forward-only migration only if an actual query
plan or integration test demonstrates the need for another index or constraint. If added, use a version
later than `V20260813150000` and update schema-verification expectations and runtime grants.

---

## 6. Step 2 - Add the Delivery-State Domain Module

Use a focused `com.wayden.messenger.delivery` module rather than mixing cursor transactions into the
message resource or conversation membership administration service.

Suggested types:

```text
MessagePosition
  latestSequence: long
  lastDeliveredSequence: long
  lastReadSequence: long
  unreadCount: long

MessageDeliveryStatus
  messageId: MessageId
  sequence: long
  serverAccepted: boolean
  recipientCount: long
  deliveredCount: long
  readCount: long
  allDelivered: boolean
  allRead: boolean

AcknowledgementResult
  latestSequence: long
  previousDeliveredSequence: long
  currentDeliveredSequence: long
  previousReadSequence: long
  currentReadSequence: long
  outcome: ADVANCED | UNCHANGED
```

Domain invariants:

- all numeric fields are non-negative,
- read does not exceed delivered,
- delivered does not exceed latest,
- delivered/read counts do not exceed recipient count,
- read count does not exceed delivered count,
- `serverAccepted` is true for every returned persisted message status,
- `allDelivered` and `allRead` are false when recipient count is zero,
- otherwise aggregate booleans exactly match their counts.

Do not introduce abstractions for transports, devices, notifications, or presence. They have no role in
the Milestone 6 REST/SQL contract.

### 6.1 Repository contract

Keep SQL details behind a delivery-specific repository. Use this contract:

```text
acknowledgeDelivery(conversationId, actorId, sequence)
acknowledgeRead(conversationId, actorId, sequence)
findPosition(conversationId, actorId)
findSenderStatus(conversationId, messageId, actorId)
```

Acknowledgement methods return a typed outcome that distinguishes:

- advanced or idempotently unchanged, including previous/current positions for safe audit metadata,
- inactive/missing membership,
- sequence ahead of committed history.

Do not signal normal outcomes by parsing arbitrary SQL exception text.

### 6.2 Application service contract

Expose one delivery application service with these use cases:

```text
acknowledgeDelivery(actorId, conversationId, sequence)
acknowledgeRead(actorId, conversationId, sequence)
getPosition(actorId, conversationId)
getStatus(actorId, conversationId, messageId)
```

The service validates raw API values, owns deadlock retry and audit enrichment, and delegates one
transaction attempt at a time. Resources do not call JDBC repositories or the transaction-attempt bean
directly.

---

## 7. Step 3 - Implement Cursor Transactions

### 7.1 Transaction boundary and high-water mark

Each acknowledgement runs in one short transaction:

1. parse and validate identifiers and sequence before entering the repository,
2. lock/read the authenticated actor's active membership row with `UPDLOCK`,
3. return the private-resource `404` immediately if that active membership does not exist,
4. lock/read the conversation's `next_message_sequence` with `UPDLOCK`,
5. derive `latestSequence = next_message_sequence - 1`,
6. reject `requestedSequence > latestSequence`,
7. atomically update the authenticated membership row and capture previous/current positions,
8. commit before returning `204`.

Authorization must happen before high-water validation. Otherwise a non-member could distinguish
conversation activity by observing `409` versus `404` responses for chosen sequence values.

Lock membership first and conversation second, matching Milestone 5 send's established lock order.
The conversation lock serializes the high-water decision with concurrent sequence allocation without
table-wide locking. A missing conversation after locking a valid membership is an internal referential
invariant failure, not a normal client outcome. Do not perform network or audit-queue calls inside the
transaction.

Put the transaction in a dedicated `DeliveryAcknowledgementAttempt` CDI bean, with delivery and read
methods annotated `@Transactional(Transactional.TxType.REQUIRES_NEW)`. The outer service owns
validation, bounded deadlock retry/backoff, and post-commit audit enrichment. Do not self-invoke a
transactional retry method because CDI interception would be bypassed.

### 7.2 Delivery update

Use an atomic monotonic update:

```sql
UPDATE [messaging].[conversation_member]
SET last_delivered_sequence =
    CASE
        WHEN last_delivered_sequence < ? THEN ?
        ELSE last_delivered_sequence
    END
OUTPUT
  DELETED.last_delivered_sequence,
  INSERTED.last_delivered_sequence,
  DELETED.last_read_sequence,
  INSERTED.last_read_sequence
WHERE conversation_id = ?
  AND user_id = ?
  AND left_at IS NULL;
```

The active membership was already locked and authorized in this transaction. The `OUTPUT` values
produce a deterministic `ADVANCED` or `UNCHANGED` result for post-commit auditing. A zero-row update
after the locked membership matched is an internal consistency failure, not a second authorization
outcome.

### 7.3 Read update

Advance both values in one statement:

```sql
UPDATE [messaging].[conversation_member]
SET
    last_delivered_sequence =
        CASE WHEN last_delivered_sequence < ? THEN ? ELSE last_delivered_sequence END,
    last_read_sequence =
        CASE WHEN last_read_sequence < ? THEN ? ELSE last_read_sequence END
OUTPUT
  DELETED.last_delivered_sequence,
  INSERTED.last_delivered_sequence,
  DELETED.last_read_sequence,
  INSERTED.last_read_sequence
WHERE conversation_id = ?
  AND user_id = ?
  AND left_at IS NULL;
```

Bind the requested sequence consistently for all four value placeholders. The database check remains
the final invariant even if application logic regresses.

### 7.4 Concurrency and retry policy

The CASE updates make equal, older, and concurrently reordered acknowledgements idempotent. If SQL
Server selects an acknowledgement as deadlock victim (`1205`), retry the complete transaction through
a fresh transaction boundary using the same bounded policy style as message send. Never retry only the
statement inside a rollback-only transaction.

Use the same explicit three-attempt ceiling and bounded jittered backoff as message send unless an ADR
later establishes a shared configurable retry policy. Report performed retries rather than total
attempts, and distinguish an exhausted retry sequence from an active retry.

Log safe identifiers, requested sequence, retry number, and outcome. Do not log message bodies,
tokens, or SQL parameter payloads.

---

## 8. Step 4 - Implement Current Position and Unread Count

The current-position query requires active membership and returns one immutable snapshot:

```json
{
  "conversationId": "4a5cb65c-fef2-4baa-bf57-d24b222cf830",
  "latestSequence": 143,
  "lastDeliveredSequence": 143,
  "lastReadSequence": 138,
  "unreadCount": 4
}
```

Return the membership positions, committed high-water mark, and unread count from one SQL statement
that joins the active actor membership to its conversation. A transaction containing unrelated
READ-COMMITTED statements is not by itself a consistent snapshot. Bind the count to the high-water
value selected by that statement so a concurrently committed later send cannot make `unreadCount`
describe messages beyond the returned `latestSequence`.

The correlated count uses the clustered conversation/sequence access path and filters actor-owned
messages and tombstones. Use this query shape:

```sql
SELECT
    c.next_message_sequence - 1 AS latest_sequence,
    actor.last_delivered_sequence,
    actor.last_read_sequence,
    (
        SELECT COUNT_BIG(*)
        FROM [messaging].[message] m
        WHERE m.conversation_id = c.id
          AND m.sequence_number > actor.last_read_sequence
          AND m.sequence_number <= c.next_message_sequence - 1
          AND m.sender_id <> actor.user_id
          AND m.deleted_at IS NULL
    ) AS unread_count
FROM [messaging].[conversation_member] actor
JOIN [messaging].[conversation] c
  ON c.id = actor.conversation_id
WHERE actor.conversation_id = ?
  AND actor.user_id = ?
  AND actor.left_at IS NULL;
```

The lower bound is the selected `last_read_sequence`; the inclusive upper bound is the selected
`latestSequence`. If no active membership row matches, return the private-resource `404` without
running a separate unscoped message count.

If persisted cursor values violate application-level high-water invariants, fail with a typed internal
error and retain bounded audit diagnostics. Never silently clamp or repair durable state in a read API.

Do not persist unread counts or increment/decrement counters during send/edit/delete. Derivation from
the authoritative cursor and message rows avoids drift and preserves correctness after retries.

---

## 9. Step 5 - Implement Sender Aggregate Status

Resolve the message by both `conversation_id` and `message_id`. Verify that the authenticated actor is
an active conversation member and is the message sender. Then aggregate current active recipients:

```sql
SELECT
    COUNT_BIG(*) AS recipient_count,
    COALESCE(SUM(CONVERT(BIGINT,
        CASE WHEN last_delivered_sequence >= ? THEN 1 ELSE 0 END)), 0)
        AS delivered_count,
    COALESCE(SUM(CONVERT(BIGINT,
        CASE WHEN last_read_sequence >= ? THEN 1 ELSE 0 END)), 0)
        AS read_count
FROM [messaging].[conversation_member]
WHERE conversation_id = ?
  AND user_id <> ?
  AND left_at IS NULL;
```

Response:

```json
{
  "messageId": "1b44e082-1927-4701-b326-8831257d2f8c",
  "sequence": 143,
  "serverAccepted": true,
  "recipientCount": 3,
  "deliveredCount": 2,
  "readCount": 1,
  "allDelivered": false,
  "allRead": false
}
```

Soft-deleted messages retain status because their durable ID and sequence remain. Status queries never
return message bodies. An edit does not reset delivery or read state because the message retains its
sequence; Version 1 has no separate edit-delivery receipt.

Perform actor membership/message-sender resolution before aggregation. A missing or inactive actor
must not receive aggregate information. Returning `403` for an active non-sender is safe because that
member can already see the persisted message and sender through authorized history. A batch status
query and embedding status into every history row are deferred until measured client usage justifies
the additional contract and query complexity.

Hold the actor membership row with a transaction-scoped shared read lock while aggregating so a
concurrent removal cannot invalidate authorization between the two statements. Use SQL Server's
`REPEATABLEREAD` table hint for that row; `UPDLOCK` is unnecessary for this read-only use case and
would serialize otherwise compatible status queries from the same sender.

---

## 10. Step 6 - Expose Authenticated APIs

### 10.1 Acknowledgement request

Use one strict request DTO for both PUT routes. Preserve the raw JSON node at the HTTP boundary so
Jackson cannot silently coerce decimal or string values into an integer; convert it to `long` only
after verifying that it is an integral JSON number in the signed 64-bit range:

```java
public record AcknowledgePositionRequest(JsonNode sequence) {}
```

JSON:

```json
{
  "sequence": 143
}
```

Reject:

- missing body,
- missing or null `sequence`,
- negative values,
- fractional/string values,
- unknown JSON properties,
- oversized request bodies,
- malformed conversation IDs.

The API must not accept `userId`, `deviceId`, `deliveredAt`, or `readAt`. Server processing time is not
part of the durable cursor model.

### 10.2 Response records

Create immutable API records with these exact fields:

```text
MessagePositionResponse
  conversationId: UUID
  latestSequence: long
  lastDeliveredSequence: long
  lastReadSequence: long
  unreadCount: long

MessageDeliveryStatusResponse
  messageId: UUID
  sequence: long
  serverAccepted: boolean
  recipientCount: long
  deliveredCount: long
  readCount: long
  allDelivered: boolean
  allRead: boolean
```

Keep domain records free of JSON annotations and map them at the API boundary. Implement the four
routes in a delivery-owned resource rooted at `/api/v1/conversations`; do not make the existing message
resource depend on the delivery module merely because the status route contains `/messages/`.

### 10.3 HTTP outcomes

| Scenario | Outcome |
| --- | --- |
| Delivery cursor advanced, equal, or stale | `204 No Content` |
| Read cursor advanced, equal, or stale | `204 No Content` |
| Current actor position | `200 OK` |
| Sender aggregate status | `200 OK` |
| Invalid sequence/body/identifier | `400 DELIVERY_VALIDATION_FAILED` |
| Requested sequence beyond committed high-water mark | `409 DELIVERY_SEQUENCE_AHEAD` |
| Active non-sender requests status | `403 DELIVERY_STATUS_FORBIDDEN` |
| Missing/inaccessible conversation or mismatched message path | `404 DELIVERY_RESOURCE_NOT_FOUND` |
| Unexpected delivery-state failure | `500 DELIVERY_INTERNAL_ERROR` |

Authentication failures continue to use the session module's established `401` behavior.

---

## 11. Step 7 - Error Mapping, Audit, and Observability

### 11.1 Typed exceptions

Define delivery-specific exceptions and a mapper. Repository failures must remain in the delivery
exception hierarchy so another broad runtime mapper cannot misclassify them as identity or conversation
errors.

Suggested stable codes:

```text
DELIVERY_VALIDATION_FAILED
DELIVERY_SEQUENCE_AHEAD
DELIVERY_STATUS_FORBIDDEN
DELIVERY_RESOURCE_NOT_FOUND
DELIVERY_INTERNAL_ERROR
```

Client details stay safe. Internal failures retain the original cause so the existing privileged audit
path can record the bounded root-cause class, message, and first application location.

### 11.2 Safe audit attributes

Extend common audit target resolution for the new routes. Allowed attributes include:

```text
conversationId
messageId
requestedSequence
previousDeliveredSequence
currentDeliveredSequence
previousReadSequence
currentReadSequence
recipientCount
deliveredCount
readCount
operation outcome
```

The delivery application service, not the HTTP filter, records request-body-derived sequences and
repository outcomes in `RequestAuditContext` after a successful transaction. Store numeric values as
bounded decimal strings because the existing custom-attribute contract is string-valued. Use explicit
event types:

```text
delivery.position.acknowledged
read.position.acknowledged
delivery.position.queried
message.delivery-status.queried
delivery.request.failed
```

Extend `HttpAuditFilter` target resolution so `targetConversationId` becomes a durable
`target_type=conversation`/`target_id=<UUID>` when no more-specific message target exists. Message
status continues to prefer `targetMessageId`. The exception mapper must call the existing
`recordFailure(code, exception)` path so the RabbitMQ/SQL audit record retains bounded nested cause and
application locations.

Only enrich success audit attributes after the `REQUIRES_NEW` attempt returns and its transaction has
committed. Failed attempts may log safe retry telemetry, but must not emit an acknowledgement-success
event before commit.

Never include:

- message bodies,
- authorization/session tokens,
- raw request or response bodies,
- usernames merely for receipt tracking,
- device fingerprints,
- SQL text or complete driver payloads.

Useful application metrics/log fields include acknowledgement type, stale/no-op/advanced outcome,
deadlock retry count, and query latency. Do not create high-cardinality metric labels from user,
conversation, or message IDs.

---

## 12. Step 8 - Add Tests

### 12.1 Domain tests

Cover:

- valid zero and non-zero positions,
- negative value rejection,
- read-ahead-of-delivery rejection,
- aggregate count invariants,
- zero-recipient aggregate booleans,
- complete and partial delivery/read aggregates.

### 12.2 SQL Server and schema tests

Using real SQL Server, prove:

1. cursor columns/defaults/check constraint exist,
2. runtime principal can select/update but not delete memberships,
3. direct SQL cannot set negative positions,
4. direct SQL cannot set read beyond delivered,
5. delivery acknowledgement advances monotonically,
6. read acknowledgement atomically advances delivery,
7. stale values are no-ops,
8. values beyond committed history are rejected by repository behavior,
9. removed-member rows are not updated,
10. concurrent ascending/descending/random acknowledgements finish at the maximum valid value,
11. concurrent read and delivery updates preserve `read <= delivered`,
12. deadlock retries use fresh transaction boundaries,
13. unread counts exclude own messages and tombstones,
14. edited messages do not become unread again,
15. sender aggregates exclude the sender and inactive members,
16. position snapshots never count a message above their returned `latestSequence`,
17. membership changes intentionally change aggregate denominators/flags,
18. existing pre-Milestone 6 messages remain unacknowledged until an explicit update.

Use barriers/latches for concurrency tests. Do not rely on sleeps as the coordination mechanism.
Explicitly race acknowledgement against member removal. Either acknowledgement commits first and its
position is retained on the subsequently departed row, or removal commits first and acknowledgement
receives private `404`; no cursor may advance after `left_at` becomes non-null.

### 12.3 API integration tests

At minimum cover:

1. authenticated delivery acknowledgement returns `204`,
2. authenticated read acknowledgement returns `204`,
3. retry/equal/stale acknowledgement returns `204` and does not regress,
4. read advances delivery when necessary,
5. zero acknowledgement is valid,
6. sequence beyond latest returns `409`,
7. invalid sequence and unknown JSON properties return `400`,
8. current position returns latest/delivered/read/unread values,
9. unread excludes the actor's own messages,
10. unread excludes deleted tombstones,
11. sender receives direct delivery/read aggregate status,
12. sender receives partial/all group aggregates accurately,
13. active non-sender status query returns `403`,
14. non-member and departed-member routes return private `404`,
15. platform administrator status does not bypass membership,
16. message from another path conversation returns `404`,
17. deleted-message status remains queryable by its sender,
18. missing/invalid/revoked/expired sessions retain established `401` behavior,
19. oversized bodies fail without persistence,
20. audit records retain safe cursor metadata and never contain message content,
21. internal SQL failures return safe delivery codes while audit metadata retains bounded cause/location,
22. a non-member cannot infer the high-water mark by varying acknowledgement sequences,
23. acknowledgement success audit events appear only after commit.

### 12.4 Reconciliation contract tests

Model a client that misses transient events:

1. retain a local sequence before the gap,
2. retrieve history after that sequence,
3. apply results in ascending sequence order,
4. acknowledge the recovered high-water mark,
5. verify position and unread count,
6. retry the acknowledgement and verify idempotency.

No WebSocket implementation is needed for this test; the missed signal is represented by simply not
calling the backend until reconciliation.

---

## 13. Step 9 - Update Postman Contracts

After implementing routes:

```bash
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
```

Extend the run-all journey to:

1. create two authenticated members and a conversation,
2. send at least two messages with stable per-run client IDs,
3. query the recipient's initial position/unread count,
4. acknowledge delivery through the first message,
5. verify sender status shows delivered but unread,
6. acknowledge read through the first message,
7. verify sender status shows read,
8. retry an older delivery/read acknowledgement and verify no regression,
9. recover remaining history and acknowledge its highest contiguous sequence,
10. verify unread count reaches zero,
11. verify a sequence beyond the committed high-water mark returns `409`,
12. verify a non-sender cannot inspect aggregate status.

The flow must explicitly switch from the sender token to the recipient token for acknowledgements and
restore the sender token for status queries. Compute the ahead-of-history request as
`latestSequence + 1` from the position response rather than hard-coding a value. Run these steps before
message soft deletion, member removal, or session revocation so later lifecycle checks do not
invalidate the receipt and unread-count journey.

Generate message IDs and client message IDs dynamically. Store only synthetic test body text in the
collection. Never store real private content or credentials in committed environments.

Before completion:

```bash
./scripts/postman/sync-postman.sh --dry-run
./scripts/postman/sync-postman.sh
./scripts/postman/sync-postman.sh --check-drift
```

---

## 14. Local Validation Sequence

Run focused tests during implementation. Finish with:

```bash
./scripts/database/validate-flyway-naming.sh
node --test scripts/postman/discover-postman.test.mjs
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
./mvnw --batch-mode --no-transfer-progress clean verify
git diff --check
```

Run the canonical build with Docker access so Testcontainers exercises SQL Server. If a local service
already owns Quarkus test port `8081`, stop that service or explicitly use an ephemeral test port; do
not interpret a bind collision as a product test failure.

---

## 15. Common Failure Modes

1. **Treating publication as delivery**

   Only an explicit authenticated acknowledgement advances durable state.

2. **Using session or device IDs as the cursor owner**

   Version 1 cursors belong to the authenticated user membership.

3. **Trusting a user ID from JSON**

   Derive the membership row exclusively from the authenticated session.

4. **Read moving independently beyond delivery**

   Advance both values atomically in the same SQL statement.

5. **Checking high-water outside the update transaction**

   Serialize the bound check with message sequence allocation.

6. **Returning an error for stale retries**

   Older/equal valid acknowledgements are successful idempotent no-ops.

7. **Persisting unread counters**

   Derive unread count from durable messages and the read cursor to avoid drift.

8. **Counting the actor's messages or tombstones as unread**

   Exclude both explicitly.

9. **Leaking raw member activity in groups**

   Return sender-only aggregates; expose only the actor's own raw positions.

10. **Calling zero-recipient status fully delivered**

    Both aggregate booleans are false when there is no applicable recipient.

11. **Resetting receipts on edit**

    Version 1 receipts are sequence-based; edits retain the sequence.

12. **Adding a duplicate cursor migration**

    Milestone 4 already created the columns, defaults, constraints, and grants.

13. **Adding WebSocket infrastructure prematurely**

    Complete and validate REST/SQL semantics first.

14. **Logging content while diagnosing receipts**

    Record safe identifiers, positions, outcomes, and bounded failure diagnostics only.

---

## 16. Definition of Done

Milestone 6 is done when:

- ADR-0014 remains accepted and implementation matches it,
- every deliverable and exit criterion in this guide is implemented,
- delivery/read cursor updates are monotonic and bounded by committed history,
- read atomically implies delivery,
- own-position, unread-count, and sender aggregate APIs are implemented,
- privacy and authorization behavior is covered by API tests,
- SQL Server concurrency tests prove retries and reordering cannot regress state,
- reconciliation is proven without relying on a live transport,
- no implementation claims WebSocket publication is delivery,
- Postman collections and the run-all flow represent the implemented API,
- Postman Cloud targets are synchronized with no drift,
- the canonical Maven build and repository validation commands pass,
- `CHANGELOG.md` describes the delivered capability under `Unreleased`,
- README and the system specification status move from planned to implemented.

---

## 17. Recommended Implementation Order

1. Confirm the Maven development version remains `0.6.0-SNAPSHOT`.
2. Add schema/permission assertions without creating a redundant migration.
3. Add delivery domain records, typed errors, and focused unit tests.
4. Define the delivery repository and transactional high-water validation.
5. Implement monotonic delivery and atomic read acknowledgements.
6. Add real SQL Server concurrency and deadlock-retry tests.
7. Implement current-position and unread-count query.
8. Implement sender-only aggregate status query.
9. Expose strict authenticated REST records and stable problem responses.
10. Add safe audit metadata and failure-cause coverage.
11. Extend Postman discovery, contracts, and the run-all reconciliation journey.
12. Synchronize Postman Cloud targets and verify no drift.
13. Update changelog and completion-status documentation.
14. Run the complete validation sequence.

---

## 18. References

- `README.md`
- `AGENTS.md`
- `CHANGELOG.md`
- `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`
- `docs/development-guide/milestone-4-conversations-step-by-step.md`
- `docs/development-guide/milestone-5-messaging-step-by-step.md`
- `docs/development-guide/versioning-and-changelog-policy.md`
- `docs/architecture/decision/ADR-0013-define-conversation-identity-membership-and-discovery.md`
- `docs/architecture/decision/ADR-0014-use-per-user-delivery-and-read-cursors.md`
- `docs/architecture/single-host-layered-container-architecture.md`
- `docs/database/sql-server-principals-and-permissions.md`
- `postman/README.md`
