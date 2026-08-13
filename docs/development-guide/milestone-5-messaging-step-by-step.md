# Milestone 5 Implementation Guide

## Durable Messaging, History, and Mutation

**Project:** Private Messenger  
**Milestone:** 5 - Messaging  
**Database:** Microsoft SQL Server 2022  
**Application stack:** Java 25, Quarkus 3.33 LTS, Maven  
**Status:** Implemented and validated

**Last reviewed:** 2026-08-13

**Implementation snapshot:** The message schema, least-privilege grants, domain and persistence
modules, authenticated nested APIs, retry-safe send transaction, forward history, edit, soft-delete,
audit metadata, SQL Server integration tests, and Postman journeys described by this guide are
implemented. Delivery/read acknowledgements and WebSockets remain intentionally deferred.

---

## 0. Purpose and Scope

Milestone 5 replaces the current message stubs with durable, authenticated messaging. SQL Server is
the authoritative source for accepted messages, their order within a conversation, edits, and shared
soft-deletion state.

This milestone is complete only when the project can:

1. accept a text message from an active conversation member,
2. return the same durable message when a client safely retries the same submission,
3. allocate unique, contiguous sequence numbers under concurrent sends,
4. recover conversation history deterministically by sequence number,
5. edit an eligible message without losing its identity or sequence,
6. soft-delete a message while retaining its durable tombstone,
7. prevent non-members and departed members from sending or reading,
8. retain safe message-operation audit metadata without recording private message bodies.

Milestone 5 does not implement delivery acknowledgements, read acknowledgements, unread counts,
per-device state, WebSockets, push notifications, attachments, reactions, replies, edit history, or
delete-for-self. Those features must not be simulated by message persistence or HTTP publication.

The client/server submission boundary is:

```text
LOCAL_PENDING   - client-owned outbox state; SQL Server has not acknowledged the message
SERVER_ACCEPTED - the message has committed to SQL Server and has a server ID and sequence
```

A successful HTTP response is an acknowledgement of server persistence. It is not proof that any
recipient device has received or read the message. Milestone 6 adds those durable recipient cursors.

---

## 1. Deliverables and Exit Criteria

### Deliverables

- forward-only `messaging.message` migration,
- least-privilege runtime grants for message persistence,
- message identifiers, types, body rules, and aggregate invariants,
- transactional send service with sequence allocation and idempotency,
- authenticated sequence-based history API,
- sender-only edit behavior,
- sender and group-moderator soft-delete behavior,
- stable message-specific problem responses,
- safe request-audit metadata for create, edit, delete, and failures,
- SQL Server concurrency and API authorization tests,
- updated Postman contracts and run-all journey.

### Exit criteria

- repeating a committed `(sender_id, client_message_id)` submission returns one logical message,
- concurrent sends in one conversation receive unique contiguous sequences,
- a failed transaction does not consume a sequence number,
- history pages are strictly ordered and do not overlap on an unchanged dataset,
- non-members and departed members cannot send, list, edit, or delete messages,
- edit and delete authorization follows the Version 1 policy in this guide,
- deleted messages retain their row, identity, sender, sequence, and timestamps while returning a
  `null` body,
- message bodies never appear in normal application logs or durable HTTP audit metadata,
- migration, Maven, Postman, and repository validation all pass.

---

## 2. Current Baseline and Gap Map

### 2.1 Baseline already present

The merged Milestone 4 baseline provides:

- authenticated `UserId` through the session filter and request context,
- durable direct and group conversations,
- retained conversation memberships with active membership defined by `left_at IS NULL`,
- `messaging.conversation.next_message_sequence`, initialized to `1`,
- server-side conversation privacy and role evaluation,
- SQL Server Flyway and Testcontainers infrastructure,
- RFC 9457-style problem responses,
- asynchronous HTTP auditing through RabbitMQ with SQL Server persistence,
- Postman discovery, validation, user-flow, and synchronization scripts.

The current `message` package is deliberately a stub:

- `POST /api/v1/messages` accepts client-supplied conversation and sender strings,
- `GET /api/v1/conversations/{conversationId}/messages` accepts only a limit,
- both operations return `501 Not Implemented`,
- response fields and paths do not yet match the authoritative Version 1 contract.

The stub is scaffolding, not a compatibility promise. Milestone 5 replaces it rather than adapting
its client-supplied sender contract.

### 2.2 Gaps Milestone 5 must close

Milestone 5 must add:

1. the message table, constraints, indexes, and permissions,
2. domain-owned message validation and mutation rules,
3. an atomic per-conversation sequence allocator,
4. sender-scoped idempotency that remains correct under races,
5. active-membership checks within mutation transactions,
6. deterministic forward history pagination,
7. edit and soft-delete policies with race-safe conditional updates,
8. message-specific exception translation and safe auditing,
9. real SQL Server concurrency tests,
10. implemented Postman requests replacing the expected `501` examples.

---

## 3. Prerequisites

Before implementing Milestone 5, verify the merged Milestone 4 baseline:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
./scripts/database/validate-flyway-naming.sh
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
```

Work on the Milestone 5 feature branch. Do not rewrite
`V20260813130000__create_messaging_conversations.sql`; add a later migration. The application must
continue to run as the least-privilege runtime principal and must never connect as SQL Server `sa`.

---

## 4. Milestone 5 Design Decisions

Apply these decisions consistently in SQL, Java, APIs, tests, audit metadata, and Postman examples.

### 4.1 SQL Server remains authoritative

- A message is server-accepted only after its database transaction commits.
- The server allocates the message ID, sender ID, sequence number, type, and timestamps.
- Clients supply only `clientMessageId` and `body` for a send.
- Client clocks, provisional IDs, and local ordering are never authoritative.
- No broker or WebSocket event may be published as though a message exists before commit.

Client outboxes should retain one stable `clientMessageId` across retries. Durable browser or native
storage is a client concern; the server contract makes that design safe but does not implement it.

### 4.2 Canonical nested routes

All message operations are scoped beneath their conversation:

```text
POST   /api/v1/conversations/{conversationId}/messages
GET    /api/v1/conversations/{conversationId}/messages
PUT    /api/v1/conversations/{conversationId}/messages/{messageId}
DELETE /api/v1/conversations/{conversationId}/messages/{messageId}
```

Remove the stub `POST /api/v1/messages` route. The conversation path is part of the authorization
boundary and prevents sender or conversation identity from being trusted from JSON.

### 4.3 Message identity and idempotency scope

`clientMessageId` is a client-generated UUID whose uniqueness scope is one authenticated sender
across all conversations:

```text
UNIQUE(sender_id, client_message_id)
```

Rules:

- a new key that commits returns `201 Created`, the message body, and a `Location` header,
- a retry of the same key for the same conversation returns `200 OK` and the existing message,
- the existing message is returned even if its body was subsequently edited or deleted,
- reusing the same key for another conversation returns `409 MESSAGE_IDEMPOTENCY_CONFLICT`,
- a retry never allocates a second sequence number or rewrites the accepted message,
- the database unique constraint, not a pre-insert query, is the concurrency authority.

Version 1 does not retain an immutable request-payload fingerprint. After a key has been accepted,
the key identifies that create operation; a later request body is not allowed to mutate it. Clients
must use the edit endpoint for intentional content changes.

### 4.4 Sequence allocation and transaction boundaries

Sequences are positive `BIGINT` values scoped to one conversation. Allocation uses one atomic update
to `messaging.conversation`:

```sql
UPDATE [messaging].[conversation]
SET
    next_message_sequence = next_message_sequence + 1,
    updated_at = ?
OUTPUT INSERTED.next_message_sequence - 1
WHERE id = ?;
```

The active-membership check, existing-idempotency lookup, sequence allocation, and message insert
belong to one transaction. Rollback restores `next_message_sequence`, so failed sends do not create
gaps. Sends to different conversations do not contend on one global sequence resource.

Mutation authorization must be linearizable with membership removal and role changes. Send, edit,
and delete transactions read the actor's retained membership row with `UPDLOCK` and require
`left_at IS NULL`; delete also reads the actor's current conversation role from that locked row. The
lock is retained until commit, so a concurrent removal or demotion is ordered either before or after
the message mutation rather than slipping between authorization and persistence. Do not use a
missing-row range lock: a non-member can fail immediately, and later membership does not retroactively
authorize the failed request.

Acquire locks in this consistent order:

```text
actor membership -> conversation sequence row when needed -> message row when needed
```

Keep the transaction short and perform no network calls while locks are held.

The SQL Server baseline remains `READ COMMITTED`. Do not change database isolation to solve a local
send race.

### 4.5 History is forward synchronization

History uses a numeric sequence cursor and strict ascending order:

```http
GET /api/v1/conversations/{conversationId}/messages?afterSequence=120&limit=50
```

- `afterSequence` is optional and defaults to `0`.
- The predicate is `sequence_number > afterSequence`.
- Ordering is `sequence_number ASC`.
- The default limit is `50`; the maximum is `200`.
- The repository fetches `limit + 1` rows to determine whether another page exists.
- Offset pagination is prohibited.
- Soft-deleted rows remain in results as tombstones, so sequence recovery never gains holes from
  deletion.

The response envelope is:

```json
{
  "items": [],
  "nextAfterSequence": null
}
```

When another page exists, `nextAfterSequence` is the last returned item's sequence. It is `null`
when the current page reached the end visible at query time. A later send can still appear in a
subsequent request using the client's highest accepted sequence.

### 4.6 Edit policy

Version 1 edit rules are:

- the actor must be an active member of the conversation,
- only the original sender may edit,
- only `TEXT` messages may be edited,
- a deleted message may not be edited,
- the body must pass the same validation as send,
- the original body is not retained,
- an actual content change sets `edited_at` to the injected server clock,
- submitting the current body is an idempotent no-op and returns the current representation.

An edit returns `200 OK` with the updated `MessageResponse`. Editing never changes message identity,
sender, client message ID, sequence, creation timestamp, or conversation sequence state.

### 4.7 Soft-delete policy

Delete-for-everyone is a soft delete:

```text
body       = null
deleted_at = server time
```

Authorization is:

| Conversation | Original sender | Owner | Admin | Other member | Non-member |
| --- | --- | --- | --- | --- | --- |
| Direct | yes | n/a | n/a | no | no |
| Group, own message | yes | yes | yes | n/a | no |
| Group, another sender | n/a | yes | yes | no | no |

System-level `ADMIN` does not bypass conversation membership. A group owner/admin deleting another
sender's message is an administrative deletion and must emit a security audit event. Repeating an
authorized delete returns `204 No Content` without changing `deleted_at` again.

Delete-for-self and hard deletion are out of scope.

### 4.8 Private-resource and actor behavior

- Every route requires authentication.
- Actor identity comes only from the authenticated session.
- Active conversation membership is required for every message operation, including history.
- A missing conversation, non-member, departed member, conversation/message path mismatch, or
  otherwise inaccessible message returns the same `404 MESSAGE_ACCESS_DENIED` response.
- An active member attempting a known but unauthorized mutation may receive a specific `403`.
- System administrators have no private-message superuser access.
- An active member may read the full retained conversation history, including messages created before
  they joined or were re-added. Version 1 does not filter history by membership intervals.

### 4.9 Message types

The schema supports `TEXT` and `SYSTEM` so later server-owned events do not require a table rewrite.
Milestone 5 exposes only client-created `TEXT` messages. No public request may choose `messageType`
or create a `SYSTEM` message.

### 4.10 Body handling

- `body` is required for a non-deleted `TEXT` message.
- Its submitted value must be at most 4000 UTF-16 code units and contain at least one non-whitespace
  character according to Java's Unicode-aware blank check.
- The server preserves the submitted body rather than trimming user-visible whitespace.
- JSON `null`, blank-only input, and values longer than 4000 are rejected.
- A deleted message has `body: null`; null means deleted, not omitted or unavailable.
- Message bodies are never placed in normal logs, request-audit metadata, metric tags, or problem
  details.

SQL Server's check constraint provides a second line of defense for null and ordinary space-only
values. The Java domain check remains authoritative for the broader Unicode definition of blank.

### 4.11 Conversation ordering

- Accepting a new message updates `conversation.updated_at` through sequence allocation.
- Resolving an idempotent retry does not update conversation ordering.
- Editing or deleting an existing message does not move an old conversation to the top of the list
  in Version 1.

This keeps conversation ordering tied to newly accepted message activity rather than retrospective
mutation of an older sequence.

---

## 5. Step 1 - Add the Message Migration

Add one forward-only migration under:

```text
scripts/database/flyway/wl_chat
```

Recommended name:

```text
VYYYYMMDDHHMMSS__create_messaging_messages.sql
```

### 5.1 Message table

Create `messaging.message` with:

- application-generated `id UNIQUEIDENTIFIER`,
- `conversation_id` and `sender_id`,
- client-generated `client_message_id UNIQUEIDENTIFIER`,
- positive `sequence_number BIGINT`,
- constrained `message_type`,
- nullable `body NVARCHAR(4000)` for tombstones,
- UTC `created_at`, nullable `edited_at`, and nullable `deleted_at`.

Required shape:

```sql
CREATE TABLE [messaging].[message] (
    id UNIQUEIDENTIFIER NOT NULL,
    conversation_id UNIQUEIDENTIFIER NOT NULL,
    sender_id UNIQUEIDENTIFIER NOT NULL,
    client_message_id UNIQUEIDENTIFIER NOT NULL,
    sequence_number BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    body NVARCHAR(4000) NULL,
    created_at DATETIME2(7) NOT NULL,
    edited_at DATETIME2(7) NULL,
    deleted_at DATETIME2(7) NULL,

    CONSTRAINT pk_messaging_message
        PRIMARY KEY NONCLUSTERED (id),

    CONSTRAINT fk_messaging_message_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES [messaging].[conversation](id),

    CONSTRAINT fk_messaging_message_sender_membership
        FOREIGN KEY (conversation_id, sender_id)
        REFERENCES [messaging].[conversation_member](conversation_id, user_id),

    CONSTRAINT uq_messaging_message_client_id
        UNIQUE NONCLUSTERED (sender_id, client_message_id),

    CONSTRAINT uq_messaging_message_sequence
        UNIQUE CLUSTERED (conversation_id, sequence_number),

    CONSTRAINT ck_messaging_message_sequence_positive
        CHECK (sequence_number > 0),

    CONSTRAINT ck_messaging_message_type
        CHECK (message_type IN ('TEXT', 'SYSTEM')),

    CONSTRAINT ck_messaging_message_body
        CHECK (
            (deleted_at IS NOT NULL AND body IS NULL)
            OR (
                deleted_at IS NULL
                AND
                body IS NOT NULL
                AND LEN(TRIM(body)) BETWEEN 1 AND 4000
            )
        ),

    CONSTRAINT ck_messaging_message_timestamps
        CHECK (
            (edited_at IS NULL OR edited_at >= created_at)
            AND
            (deleted_at IS NULL OR deleted_at >= COALESCE(edited_at, created_at))
        )
);
```

The sender-membership foreign key proves that the sender has a retained membership row. It does not
prove current active membership; the transactional application check remains required.

### 5.2 Physical ordering and indexes

The unique clustered index on `(conversation_id, sequence_number)` is intentional. Conversation
history is the dominant high-write access path, while random message UUIDs remain a nonclustered
primary key.

Do not add speculative indexes. The required constraints already support:

- message lookup by ID through the primary key,
- idempotency lookup by sender and client key,
- history seek and ordering by conversation and sequence.

### 5.3 Runtime permissions

Grant the runtime principal only:

```text
SELECT, INSERT, UPDATE on messaging.message
```

Explicitly deny `DELETE`. The runtime already has the required `SELECT` and `UPDATE` permissions on
`messaging.conversation` for sequence allocation and conversation lookup. Do not broaden schema-wide
permissions.

Update schema-verification tests to assert the table, named constraints, clustered index shape, and
least-privilege behavior.

---

## 6. Step 2 - Replace the Message Stub with a Domain Module

Keep the established modular-monolith layout:

```text
src/main/java/com/wayden/messenger/message/
├── api/
├── application/
├── domain/
└── infrastructure/
```

Remove `MessageServiceStub` after its real implementation is wired. Do not leave a competing `501`
resource or preserve the unsafe client-supplied sender DTO.

### 6.1 Domain types

Add focused types such as:

- `MessageId`,
- `ClientMessageId`,
- `Message`,
- `MessageType`,
- `MessageBody`.

`Message` should carry exactly:

```text
id
conversationId
senderId
clientMessageId
sequenceNumber
type
body
createdAt
editedAt
deletedAt
```

The domain owns:

- UUID presence and identity rules,
- positive sequence validation,
- body validity while active,
- tombstone validity after deletion,
- timestamp ordering,
- edit eligibility independent of transport concerns.

Use injected `Clock` and an application-owned `MessageIdGenerator`; do not call the system clock or
random generator from persistence mapping.

### 6.2 Repository contracts

Use operation-oriented contracts rather than generic CRUD. The application-facing repository should
support:

- find by `(senderId, clientMessageId)`,
- find an accessible message by `(conversationId, messageId, actorId)`,
- allocate the next sequence after proving active membership,
- insert a message,
- list messages after a sequence with an explicit limit,
- conditionally edit an existing non-deleted text message,
- conditionally soft-delete a message.

Repository return values must distinguish a successful mutation from a zero-row conditional update,
so the application can re-read and produce the correct idempotent or forbidden outcome.

Translate SQL Server failures by error number and known constraint name:

- `2601`/`2627` on `uq_messaging_message_client_id` means a duplicate idempotency contender,
- `2601`/`2627` on `uq_messaging_message_sequence` is an internal sequencing invariant failure,
- `1205` is the retryable deadlock-victim outcome,
- unexpected `547` or other SQL failures become a message-specific internal exception.

Do not classify every unique violation as idempotency and do not parse localized driver text as the
only signal.

### 6.3 Mapping

Use explicit selected columns and one tested mapper. Convert SQL Server `DATETIME2(7)` through the
same UTC conversion convention as the conversation repository. Never use `SELECT *`.

---

## 7. Step 3 - Implement Send and Idempotency

### 7.1 Application command

The API maps transport input to a typed command containing:

```text
authenticated sender ID
conversation ID from the path
client message ID from JSON
message body from JSON
```

The request does not contain sender ID, sequence, message type, server ID, or timestamp.

### 7.2 Transaction algorithm

```text
1. Authenticate and parse the request.
2. Validate clientMessageId and body.
3. Begin the send transaction.
4. Require the sender's active conversation membership.
5. Look up (senderId, clientMessageId).
6. If found for this conversation, return the existing message as not-created.
7. If found for another conversation, return MESSAGE_IDEMPOTENCY_CONFLICT.
8. Atomically allocate the next conversation sequence and update conversation.updated_at.
9. Insert the TEXT message using the allocated sequence.
10. Commit.
11. Return the persisted representation as created.
```

Authorization is required even for an idempotent retry. A departed sender may not use the retry
endpoint to retrieve a formerly accepted message after losing conversation access.

### 7.3 Duplicate race recovery

Two requests can both miss the initial lookup. The unique client-key constraint chooses one winner.
The losing attempt must roll back its entire transaction, including sequence allocation, and then
reauthorize and read the winning message in a new transaction.

Do not swallow the unique violation and continue using a rollback-only transaction. Keep the
transactional attempt separate from the outer retry/recovery coordinator so duplicate and deadlock
recovery starts with a valid transaction.

Implement that boundary explicitly rather than relying on self-invocation of a `@Transactional`
method, which CDI interceptors do not apply. Use two CDI collaborators:

- a non-transactional send coordinator that owns duplicate/deadlock recovery and backoff,
- a send-attempt bean whose public entry point uses `@Transactional(REQUIRES_NEW)`.

Each attempt either commits a complete send or returns/throws after its transaction has completed.
The coordinator may then reauthorize and resolve the winning idempotent message through another
fresh transactional call. Unit tests should verify attempt classification and retry limits without
starting Quarkus; SQL-backed tests verify the real interceptor and rollback behavior.

### 7.4 Deadlock policy

Translate SQL Server error `1205` into a typed retryable persistence outcome. Retry the complete send
transaction, never an individual statement, with:

- at most two retries after the initial attempt,
- short bounded jittered backoff outside the transaction,
- a warning log containing request ID, conversation ID, attempt, and outcome,
- no message body in the log,
- a safe request-audit attribute containing the retry count.

Do not retry validation, authorization, idempotency conflict, or other constraint failures. If all
attempts lose as deadlock victims, return a safe `MESSAGE_INTERNAL_ERROR` and retain the root cause in
the privileged request audit record.

### 7.5 After-commit boundary

Milestone 5 does not need a message broker or WebSocket publisher. If a committed-message application
event is introduced for later consumers, it must be observed only after successful transaction
completion. An event is a delivery signal, not the authoritative message record.

---

## 8. Step 4 - Implement History

History requires one active-membership authorization check and one seek query within a read
transaction.

Suggested SQL shape:

```sql
SELECT TOP (?)
    m.id,
    m.conversation_id,
    m.sender_id,
    m.client_message_id,
    m.sequence_number,
    m.message_type,
    m.body,
    m.created_at,
    m.edited_at,
    m.deleted_at
FROM [messaging].[message] m
JOIN [messaging].[conversation_member] actor
    ON actor.conversation_id = m.conversation_id
   AND actor.user_id = ?
   AND actor.left_at IS NULL
WHERE m.conversation_id = ?
  AND m.sequence_number > ?
ORDER BY m.sequence_number ASC;
```

Bind `limit + 1` for `TOP (?)`. An empty result must not by itself distinguish an inaccessible
conversation from a valid conversation with no later messages; separately require accessible active
membership and use the same private-resource failure.

History is not a snapshot across requests. New higher sequences may appear while paging, but already
returned sequences do not move or repeat. Edits and deletions can change the representation at an
existing sequence; future real-time synchronization will signal those mutations, while a full REST
refresh remains authoritative.

---

## 9. Step 5 - Implement Edit and Soft Delete

### 9.1 Edit algorithm

In one transaction:

1. require active access to the conversation,
2. load the message using both conversation and message IDs,
3. require the actor to be the original sender,
4. require `TEXT` and not deleted,
5. return the current message if the body is unchanged,
6. conditionally update `body` and `edited_at` where `deleted_at IS NULL`,
7. if zero rows changed, re-read to classify a concurrent delete safely,
8. return the updated representation.

Concurrent edits use normal last-committed-write semantics in Version 1. Optimistic edit versions
and retained edit history are out of scope. A concurrent delete wins permanently because the edit
predicate excludes deleted rows.

### 9.2 Delete algorithm

In one transaction:

1. require active access to the conversation,
2. load the message using both path IDs,
3. authorize sender deletion or group owner/admin moderation,
4. if already deleted, return success without changing its timestamp,
5. conditionally set `body = NULL` and `deleted_at = ?`,
6. retain every other identity, ordering, and audit field,
7. attach safe audit identifiers and commit.

An administrative deletion records actor user ID, conversation ID, message ID, original sender ID,
and operation outcome through the existing audit flow. It must never record the deleted body.

---

## 10. Step 6 - Expose Authenticated APIs

### 10.1 Requests

Send:

```json
{
  "clientMessageId": "aa96bf40-1a96-449d-a7a6-997eb72ef403",
  "body": "Hello"
}
```

Edit:

```json
{
  "body": "Corrected text"
}
```

Reject unknown JSON properties. Parse UUID path and body identifiers before invoking application
services. Apply Bean Validation to request records and configure or enforce a bounded request entity
size appropriate to a 4000-character message plus small JSON overhead. Oversized transport payloads
must fail before persistence and must not be copied into audit metadata.

### 10.2 Message response

Use one stable representation for send, history, and edit:

```json
{
  "messageId": "11111111-1111-1111-1111-111111111111",
  "conversationId": "22222222-2222-2222-2222-222222222222",
  "senderId": "33333333-3333-3333-3333-333333333333",
  "clientMessageId": "aa96bf40-1a96-449d-a7a6-997eb72ef403",
  "sequenceNumber": 121,
  "type": "TEXT",
  "body": "Hello",
  "createdAt": "2026-08-13T18:30:00Z",
  "editedAt": null,
  "deletedAt": null
}
```

For a tombstone, `body` is `null` and `deletedAt` is non-null. Do not return a fabricated deletion
string as message content.

### 10.3 HTTP outcomes

| Operation | Outcome |
| --- | --- |
| New message accepted | `201 Created`, body, `Location` |
| Same accepted client key retried in the same conversation | `200 OK`, existing body |
| Client key reused in another conversation | `409 MESSAGE_IDEMPOTENCY_CONFLICT` |
| History page | `200 OK`, page envelope |
| Eligible edit | `200 OK`, current message body |
| Eligible or repeated soft delete | `204 No Content` |
| Missing/non-visible conversation or message | `404 MESSAGE_ACCESS_DENIED` |
| Active member forbidden to edit | `403 MESSAGE_EDIT_FORBIDDEN` |
| Active member forbidden to delete | `403 MESSAGE_DELETE_FORBIDDEN` |
| Invalid body, UUID, sequence, or limit | `400 MESSAGE_VALIDATION_FAILED` |

The `Location` header is:

```text
/api/v1/conversations/{conversationId}/messages/{messageId}
```

No standalone `GET` message endpoint is required in Milestone 5; the location remains the canonical
resource identity for mutation and future expansion.

---

## 11. Step 7 - Error Mapping and Audit Safety

### 11.1 Stable message problem codes

Define message-owned exceptions and one message exception mapper:

- `MESSAGE_VALIDATION_FAILED` (`400`),
- `MESSAGE_ACCESS_DENIED` (`404`),
- `MESSAGE_EDIT_FORBIDDEN` (`403`),
- `MESSAGE_DELETE_FORBIDDEN` (`403`),
- `MESSAGE_IDEMPOTENCY_CONFLICT` (`409`),
- `MESSAGE_INTERNAL_ERROR` (`500`).

Unexpected JDBC failures must become a message-specific internal exception so they cannot fall
through an identity or generic runtime mapper. The client detail remains generic. The full exception
is logged server-side, and bounded root-cause diagnostics are attached to `RequestAuditContext` for
the privileged audit record.

### 11.2 Audit metadata

Use operation names such as:

```text
message.send
message.history.list
message.edit
message.delete
```

Safe metadata may include:

- actor user ID from authenticated context,
- conversation ID,
- message ID,
- sender ID,
- client message ID,
- sequence number,
- message type,
- whether send resolved an idempotent retry,
- whether deletion was administrative,
- stable failure code and bounded diagnostic fields.

Never include body text. Extend target resolution in the common audit filter so `targetMessageId`
can produce `target_type = message` and `target_id = <messageId>` without breaking the existing user
and invitation targets. Prefer a generic audit event-type attribute for new work while preserving
compatibility with existing identity/conversation event producers.

The standard HTTP audit record is sufficient for sends, history, sender edits, and sender deletes.
For group moderation of another user's message, classify that same durable request audit record with
the security event type `message.administratively.deleted`; do not emit a second duplicate record.

---

## 12. Step 8 - Add Tests

### 12.1 Domain tests

Cover:

- valid text-message construction,
- blank and oversized body rejection,
- positive sequence enforcement,
- timestamp ordering,
- deleted tombstone invariants,
- editing a deleted or system message is rejected,
- submitted body whitespace is preserved after validation.

### 12.2 SQL Server repository and migration tests

Use the real SQL Server Testcontainer for:

- all message columns, named constraints, and index clustering,
- runtime `SELECT`, `INSERT`, and `UPDATE` permissions,
- runtime hard-delete denial,
- duplicate `(sender_id, client_message_id)` rejection,
- duplicate `(conversation_id, sequence_number)` rejection,
- sender membership FK behavior,
- atomic sequence allocation,
- rollback restoring the sequence counter,
- history seek ordering and `limit + 1`,
- membership removal serializing correctly with an in-flight send,
- role demotion serializing correctly with an administrative delete,
- conditional edit versus delete races,
- timestamp and body check constraints.

### 12.3 API integration tests

Minimum matrix:

1. active member sends text and receives `201`, location, ID, and sequence,
2. request sender identity comes from the session rather than JSON,
3. same client key retry returns the original message with `200`,
4. same client key in another conversation returns `409`,
5. concurrent duplicate sends persist one message and consume one sequence,
6. concurrent distinct sends produce unique contiguous sequences,
7. a forced failed send does not consume a sequence,
8. history returns strictly ascending non-overlapping pages,
9. history includes deleted tombstones,
10. malformed or negative `afterSequence` and invalid limits return `400`,
11. non-member cannot send or list history,
12. removed member immediately loses send and history access,
13. sender edits an active text message,
14. another ordinary member cannot edit it,
15. deleted message cannot be edited,
16. sender soft-deletes and repeated delete remains idempotent,
17. group owner/admin can moderate another member's message,
18. direct recipient and ordinary group member cannot delete another sender's message,
19. system administrator without membership has no message access,
20. concurrent removal and send have one explainable transaction order with no post-removal bypass,
21. concurrent moderator demotion and delete use the role from the winning transaction order,
22. oversized request bodies fail without persistence or body leakage into audit metadata,
23. unexpected message failure returns safe detail and retains bounded audit diagnostics.

Use latches or barriers so concurrency tests actually overlap. A test that merely submits two tasks
without synchronizing their start is not sufficient evidence.

Suggested test names:

- `duplicate_client_message_id_returns_one_durable_message`
- `concurrent_sends_allocate_contiguous_conversation_sequences`
- `rolled_back_send_does_not_consume_sequence`
- `history_pages_forward_without_duplicates`
- `removed_member_cannot_send_or_read_history`
- `sender_can_edit_but_other_member_cannot`
- `group_manager_can_soft_delete_another_members_message`
- `deleted_message_remains_as_sequence_tombstone`

---

## 13. Step 9 - Update Postman Contracts

After implementing routes:

```bash
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
```

Replace the Milestone 4 expected-`501` message requests with implemented contracts. The run-all
journey should:

1. establish authenticated admin and member sessions,
2. create a conversation and retain its ID,
3. send a message with a newly generated client message UUID and expect `201`,
4. retain message ID and sequence from the response,
5. retry the same send and expect `200` with the same IDs,
6. list history after sequence `0` and find the message,
7. edit the message and verify `editedAt`,
8. soft-delete it and expect `204`,
9. list history again and verify its tombstone,
10. verify a non-member or removed member receives the private-resource failure.

Generate per-run `clientMessageId` values dynamically so cloud and local runs do not depend on stale
globals. Do not store real private message content in committed environment files; use an explicitly
synthetic test value.

Before synchronization:

```bash
./scripts/postman/sync-postman.sh --dry-run
./scripts/postman/sync-postman.sh
```

---

## 14. Local Validation Sequence

Run focused unit and integration tests during implementation. Finish with:

```bash
./scripts/database/validate-flyway-naming.sh
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
./mvnw --batch-mode --no-transfer-progress clean verify
git diff --check
```

If Postman discovery changes the authoritative collection, commit that generated update with the API
change. Use Docker access for the canonical build so Testcontainers exercises SQL Server rather than
silently substituting another database.

---

## 15. Common Failure Modes

1. **Trusting `senderUserId` or `conversationId` from JSON**  
   Derive the sender from authentication and conversation from the nested route.

2. **Allocating a sequence before the transaction that inserts the message**  
   Allocation must roll back with a failed insert.

3. **Relying only on an idempotency pre-check**  
   Concurrent requests can both miss it; retain the database unique constraint and recover the
   winner in a new transaction.

4. **Continuing after a constraint failure in a rollback-only transaction**  
   Exit the failed attempt and perform recovery through a fresh transaction boundary.

5. **Using offset pagination for history**  
   Seek by the immutable per-conversation sequence.

6. **Filtering deleted messages out of history**  
   Return tombstones so durable sequence recovery remains coherent.

7. **Hard-deleting message rows**  
   Clear the body and set `deleted_at`; retain identity and ordering metadata.

8. **Allowing system admins to read private messages**  
   Platform roles do not bypass active conversation membership.

9. **Publishing before commit**  
   A signal for a rolled-back row creates a phantom message.

10. **Logging message bodies for diagnostics**  
    Log safe identifiers and retain bounded exception diagnostics only.

11. **Treating send success as recipient delivery**  
    `201`/`200` proves server acceptance only; delivery and read state belong to Milestone 6.

12. **Overbuilding future real-time infrastructure**  
    Complete REST and SQL semantics first. WebSockets must remain recoverable signals over durable
    state.

---

## 16. Definition of Done

Milestone 5 is done when:

- every deliverable and exit criterion in this guide is implemented,
- the unsafe stub DTO and `POST /api/v1/messages` route are removed,
- SQL Server constraints enforce message identity, ordering, membership linkage, and tombstones,
- transactional tests prove idempotency, contiguous sequences, and rollback behavior,
- authorization tests prove private history and mutation rules,
- audit tests prove safe identifiers are retained and message bodies are absent,
- no implementation claims delivery or read completion,
- Postman collections and the run-all flow represent the implemented API,
- the canonical Maven build and all repository validation commands pass,
- `CHANGELOG.md` describes the delivered capability under `Unreleased`,
- README and the system specification status are updated from planned to implemented.

---

## 17. Recommended Implementation Order

1. Add the message migration, constraints, permissions, and schema-verification assertions.
2. Add message domain types and focused unit tests.
3. Define repository contracts, row mapping, and SQL error translation.
4. Implement sequence allocation and transactional send attempt.
5. Add duplicate-race recovery and bounded deadlock retry at the outer transaction boundary.
6. Replace the stub send API and add idempotency integration tests.
7. Implement forward history pagination and privacy tests.
8. Implement sender edit and race-safe soft delete.
9. Add group moderation and safe audit metadata.
10. Run Postman discovery, replace expected-`501` examples, validate, and synchronize.
11. Update `CHANGELOG.md` and completion-status documentation.
12. Run the complete verification suite.

---

## 18. References

- `README.md`
- `AGENTS.md`
- `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`
- `docs/development-guide/milestone-4-conversations-step-by-step.md`
- `docs/development-guide/versioning-and-changelog-policy.md`
- `docs/database/sql-server-principals-and-permissions.md`
- `docs/architecture/decision/ADR-0001-use-modular-monolith.md`
- `docs/architecture/decision/ADR-0013-define-conversation-identity-membership-and-discovery.md`
- `postman/README.md`
