# Manual WebSocket Integration Testing with Postman

**Status:** Active human-run validation guide  
**Bring Up:** `postman/collections/chat-backend-websocket-manual-integration.postman_collection.json`  
**Bring Down:** `postman/collections/chat-backend-websocket-participants-down.postman_collection.json`  
**Prerequisite:** ChatBackend and SQL Server are running and migrated

## 1. Goal

This journey provisions W (Wayden) and L (Lacara) as independent participants, gives each an authenticated session,
opens one live WebSocket connection per participant, triggers durable operations over REST, observes
the corresponding real-time frames, and verifies the same state directly in authoritative SQL Server
tables.

The HTTP setup is repeatable. The socket interaction is deliberately human-driven because Newman and
the Postman CLI do not execute WebSocket requests.

## 2. Variables Produced by the Setup

Run the collection folder `01 - Provision two socket participants` with the intended Postman
environment selected. It persists:

| Variable | Purpose |
| --- | --- |
| `socket_participant_1_token` | Raw bearer/session token for W (Wayden). |
| `socket_participant_2_token` | Raw bearer/session token for L (Lacara). |
| `socket_participant_1_bearer_token` | Complete `Bearer ...` header value for W's socket tab. |
| `socket_participant_2_bearer_token` | Complete `Bearer ...` header value for L's socket tab. |
| `socket_participant_1_user_id` | W's durable user ID. |
| `socket_participant_2_user_id` | L's durable user ID. |
| `socket_conversation_id` | Direct conversation shared by the participants. |
| `socket_message_id` | Most recently triggered durable message. |
| `socket_message_sequence` | That message's conversation sequence. |

The two token variables are intentionally blank in committed environment templates. Never commit
their runtime values. SQL Server stores only token hashes, so the raw tokens cannot be recovered from
the database later.

On an existing database, ensure `bootstrap_admin_username`, `bootstrap_admin_password`, and
`admin_user_id` identify the same established bootstrap administrator. On a fresh database, the first
setup request fills the required administrator ID from the bootstrap response.

## 3. Provision the Participants

1. Select the Local or DevDocker environment that matches the running backend.
2. Open `WL-Chat Socket Participants - Bring Up`.
3. Run only the folder `01 - Provision two socket participants`.
4. Confirm all nine requests pass.
5. Inspect the selected environment and confirm both `socket_participant_*_token` values and
   `socket_conversation_id` are populated.

Every run creates unique `wayden-socket-*` and `lacara-socket-*` usernames. This avoids conflicts and
ensures the two session tokens belong to distinct users while keeping their test roles obvious.

## 4. Open Two Authenticated Socket Tabs

Create two WebSocket requests in Postman Desktop using
`{{ws_base_url}}/api/v1/ws`. The committed Local environment resolves this to
`ws://localhost:8080/api/v1/ws`, DevDocker resolves it to `ws://localhost:8081/api/v1/ws`, and
Production must be configured with the deployed `wss://` host.

### W (Wayden) tab

- URL: `{{ws_base_url}}/api/v1/ws`
- Header key: `Authorization`
- Header value: `{{socket_participant_1_bearer_token}}`
- Suggested name: `Socket W - Wayden`

### L (Lacara) tab

- URL: `{{ws_base_url}}/api/v1/ws`
- Header key: `Authorization`
- Header value: `{{socket_participant_2_bearer_token}}`
- Suggested name: `Socket L - Lacara`

Connect both tabs. In each tab send:

```json
{"action":"ping"}
```

Each must receive:

```json
{"type":"pong"}
```

If Postman does not resolve environment variables in the WebSocket header, copy the current value
from the environment into the header temporarily. Do not save or export the resolved secret.

Do not put `{{socket_participant_1_token}}` or `{{socket_participant_2_token}}` directly in an
`Authorization` header: those variables contain only the raw token. A raw token can complete the HTTP
upgrade and then be closed immediately with `4401` because it lacks the required `Bearer ` scheme.

## 5. Simulate the Exchange

Keep both socket tabs visible. In the collection folder
`02 - Manual REST triggers while sockets are connected`, run each request individually—not the whole
folder—and compare the observations below.

### T01: W sends a message to L

Expected REST result: `201`. Both sockets should receive `message.created` with the same
`conversationId`, `messageId`, `clientMessageId`, and `sequenceNumber` returned by REST.

The request stores `socket_message_id` and `socket_message_sequence` for later steps.

### T02: L acknowledges delivery

Expected REST result: `204`. Both sockets should receive `delivery.updated` identifying L
and the acknowledged sequence. This frame reports a durable cursor change; frame receipt itself is
not proof of delivery.

### T03: L acknowledges read

Expected REST result: `204`. Both sockets should receive `read.updated`. Its read and delivery
positions should be at least `socket_message_sequence` because reading also advances delivery.

### T04: W edits the message

Expected REST result: `200`. Both sockets should receive `message.edited` for the original message ID
and sequence, with a non-null edit timestamp.

### T05 and T06: query authoritative receipt state

W's sender-only status query should show one applicable recipient and aggregate delivery
and read counts reflecting L. L's own-position query should show monotonic
delivery/read cursors and the current derived unread count.

### T07 and T08: delete and verify the tombstone

The delete returns `204`, and both sockets should receive `message.deleted`. L's history
query must retain the message at its original sequence with `body: null` and a non-null `deletedAt`.

## 6. Exercise WebSocket Acknowledgement Commands

To validate the bidirectional socket path instead of REST for acknowledgements, first run T01 to
create a new message. In L's socket tab send:

```json
{
  "action": "delivery.ack",
  "conversationId": "{{socket_conversation_id}}",
  "sequence": {{socket_message_sequence}}
}
```

Then send the corresponding command with `"action": "read.ack"`. Postman resolves the selected
environment's conversation and sequence variables when the message is sent. Keep the UUID variable
inside JSON quotes and the numeric sequence variable outside quotes. Verify the resulting
`delivery.updated` and `read.updated` frames on both sockets, then run T05 and T06 to prove the
commands advanced SQL-backed state.

Do not send `"conversationId": "<socket_conversation_id>"`; angle-bracket text in this guide denotes
a human-substituted SQL placeholder, not Postman variable syntax. The socket endpoint correctly
rejects that literal text as `INVALID_COMMAND` because it is not a UUID.

### Acknowledgement preflight and troubleshooting

Before sending either socket acknowledgement:

1. Run T01 and confirm it returns `201` for the currently selected `socket_conversation_id`.
2. Confirm T01 populated `socket_message_sequence`; do not replace it with a guessed or hardcoded `1`.
3. Run T06 and confirm `latestSequence` is greater than or equal to `socket_message_sequence`.
4. Send the command from L's socket, whose token belongs to an active member of that conversation.

If a valid UUID command still returns `INVALID_COMMAND`, compare the outgoing sequence with T06's
`latestSequence`. A sequence beyond the committed high-water mark is rejected and leaves the durable
cursor unchanged. Also confirm that the Bring Up runner, T01, T06, and the socket tabs all use the same
selected Postman environment; mixing sessions or conversation variables from different runs can
produce the same safe error response.

Equal or lower acknowledgement retries are valid no-ops and should not create a second advancement
event. A sequence beyond the committed high-water mark should produce a stable error frame and must
not change the database cursor.

## 7. Complete W/L Socket Capability Exchange

Perform the following steps in order. Keep a small evidence table with columns for step, sending
participant, received event type, `eventId`, conversation ID, message ID, sequence, and result.
Start from a fresh Bring Up run and do not run REST triggers T02 or T03 before the socket-command
steps below; otherwise the delivery/read cursors will already be advanced and the socket commands
will correctly become silent idempotent no-ops.

### 7.1 Connect and prove liveness

Connect `Socket W - Wayden` and `Socket L - Lacara`. From W send:

```json
{"action":"ping"}
```

W must receive exactly:

```json
{"type":"pong"}
```

Repeat from L and expect the same response on L. A pong is local to the requesting connection; the
other participant should not receive it. Record both passes before continuing.

### 7.2 Prove multiple connections for one user

Open `Socket W2 - Wayden second device` using the same URL and
`Authorization: {{socket_participant_1_bearer_token}}`. Send `{"action":"ping"}` from W2 and verify
only W2 receives its pong. Keep W, W2, and L connected for the next step.

### 7.3 W creates a durable message

Run collection request `T01 - W sends message to L`. This is an authoritative REST mutation because
the current socket protocol does not accept message-send commands. Confirm REST returns `201` and
stores `socket_message_id` and `socket_message_sequence`.

W, W2, and L must each receive one envelope shaped as follows:

```json
{
  "eventId": "<server-event-uuid>",
  "eventType": "message.created",
  "occurredAt": "<server-timestamp>",
  "conversationId": "<socket_conversation_id value>",
  "payload": {
    "conversationId": "<socket_conversation_id value>",
    "messageId": "<socket_message_id value>",
    "sequenceNumber": 1,
    "senderId": "<socket_participant_1_user_id value>",
    "clientMessageId": "<socket_client_message_id value>",
    "body": "Hello from participant 1 over the durable REST path",
    "createdAt": "<server-timestamp>"
  }
}
```

Use the actual returned sequence rather than assuming it is `1`. On each receiving client, correlate
by `messageId`, order by `sequenceNumber`, and treat duplicate copies of the same `eventId` as one UI
change. Confirm all three envelopes agree with the REST response before acknowledging.

### 7.4 L acknowledges delivery through the socket

From L send this exact Postman-variable message:

```json
{
  "action": "delivery.ack",
  "conversationId": "{{socket_conversation_id}}",
  "sequence": {{socket_message_sequence}}
}
```

W, W2, and L must each receive:

```json
{
  "eventId": "<server-event-uuid>",
  "eventType": "delivery.updated",
  "occurredAt": "<server-timestamp>",
  "conversationId": "<socket_conversation_id value>",
  "payload": {
    "conversationId": "<socket_conversation_id value>",
    "userId": "<socket_participant_2_user_id value>",
    "lastDeliveredSequence": 1,
    "updatedAt": "<server-timestamp>"
  }
}
```

Verify `userId` is L and use the actual stored sequence. W may now render its sender-visible delivery
indicator, while L records that its shared per-user delivery cursor advanced. Run T06 and confirm
`lastDeliveredSequence` equals or exceeds the acknowledged sequence.

### 7.5 Prove acknowledgement idempotency

Send the same `delivery.ack` from L again. The command is a successful no-op and does not send a
command-response frame. Wait a short observation window and confirm no additional
`delivery.updated` event appears. Run T06 again and confirm the cursor did not regress or advance.

### 7.6 L acknowledges read through the socket

From L send:

```json
{
  "action": "read.ack",
  "conversationId": "{{socket_conversation_id}}",
  "sequence": {{socket_message_sequence}}
}
```

W, W2, and L must each receive:

```json
{
  "eventId": "<server-event-uuid>",
  "eventType": "read.updated",
  "occurredAt": "<server-timestamp>",
  "conversationId": "<socket_conversation_id value>",
  "payload": {
    "conversationId": "<socket_conversation_id value>",
    "userId": "<socket_participant_2_user_id value>",
    "lastReadSequence": 1,
    "lastDeliveredSequence": 1,
    "updatedAt": "<server-timestamp>"
  }
}
```

Verify both cursor fields are at least the actual acknowledged sequence. W may render its read
indicator. L should treat the message as read and update its unread presentation. Run T05 and T06 to
compare W's aggregate sender projection with L's own durable position.

### 7.7 W edits the message

Run T04. REST must return `200`. W, W2, and L must each receive:

```json
{
  "eventId": "<server-event-uuid>",
  "eventType": "message.edited",
  "occurredAt": "<server-timestamp>",
  "conversationId": "<socket_conversation_id value>",
  "payload": {
    "conversationId": "<socket_conversation_id value>",
    "messageId": "<socket_message_id value>",
    "sequenceNumber": 1,
    "body": "Edited by participant 1",
    "editedAt": "<server-timestamp>"
  }
}
```

Each client replaces the cached body for that `messageId`, retains its original sequence/order, and
records the edit timestamp. Do not insert a second message row.

### 7.8 W deletes the message and L verifies the tombstone

Run T07. REST must return `204`. W, W2, and L must each receive:

```json
{
  "eventId": "<server-event-uuid>",
  "eventType": "message.deleted",
  "occurredAt": "<server-timestamp>",
  "conversationId": "<socket_conversation_id value>",
  "payload": {
    "conversationId": "<socket_conversation_id value>",
    "messageId": "<socket_message_id value>",
    "sequenceNumber": 1,
    "deletedAt": "<server-timestamp>"
  }
}
```

Each client removes the body from display/search but keeps a tombstone at the original sequence. Run
T08 as L and confirm REST returns that same message with `body: null` and non-null `deletedAt`.

### 7.9 Prove stable command errors

From either participant send:

```json
{"action":"not.supported"}
```

Only that socket must receive:

```json
{"type":"error","code":"UNKNOWN_COMMAND"}
```

Then send malformed JSON, for example:

```text
{
```

Only that socket must receive:

```json
{"type":"error","code":"INVALID_COMMAND"}
```

The connection must remain usable: send `{"action":"ping"}` afterward and expect pong. Do not apply
error frames as conversation events, and verify no message/cursor state changed.

### 7.10 Prove missed-frame recovery

1. Record L's last contiguous sequence and disconnect L.
2. Run T01 again as W. W and W2 receive `message.created`; disconnected L receives nothing.
3. Reconnect L and ping it.
4. As L, call message history with `afterSequence` equal to its previously recorded contiguous
   sequence.
5. Persist the returned message, advance L's local contiguous position, and only then acknowledge it.

No old WebSocket frame should replay. The successful REST recovery proves SQL Server, not the socket,
is authoritative.

### 7.11 Bring both participants down

Keep W, W2, and L open and run `WL-Chat Socket Participants - Bring Down` in order. D01 revokes W's
session, so both W and W2 should close with `4401`. D02 revokes L's session, so L should close with
`4401`. D03 and D04 prove both tokens receive `401` and clear the raw/bearer token variables. Do not
interpret disconnect timing as durable evidence; verify the `[identity].[session]` rows as described
below.

Active-membership privacy filtering cannot be fully demonstrated with this direct two-participant
fixture because direct-conversation membership lifecycle rules are not a substitute for group removal.
Use a separate group fixture with a removed third participant when explicitly validating that privacy
boundary.

## 8. Verify Authoritative SQL Server State

Run these read-only queries with the current Postman variable values substituted for the UUID
placeholders. The `UNIQUEIDENTIFIER` variables require the UUID values from
`socket_participant_1_user_id` and `socket_participant_2_user_id`; do not substitute the generated
`wayden-socket-*` or `lacara-socket-*` usernames. Use an administrative/developer query principal;
the application runtime principal is intentionally least-privileged.

The examples bracket schemas and objects because `IDENTITY` is a SQL Server keyword. `USE [wl_chat];`
is optional when the query connection is already scoped to `wl_chat`, but may be placed before the
declarations when running against another database context.

### Users and active sessions

```sql
DECLARE @participant1 UNIQUEIDENTIFIER = '<socket_participant_1_user_id>';
DECLARE @participant2 UNIQUEIDENTIFIER = '<socket_participant_2_user_id>';

SELECT id, username, normalized_username, system_role, status, created_at, updated_at
FROM [identity].[user_account]
WHERE id IN (@participant1, @participant2);

SELECT id, user_id, status, created_at, expires_at, last_seen_at, revoked_at
FROM [identity].[session]
WHERE user_id IN (@participant1, @participant2)
ORDER BY created_at DESC;
```

The raw Postman tokens will not appear because only `token_hash` is persisted.

### Conversation and membership cursors

```sql
DECLARE @conversation UNIQUEIDENTIFIER = '<socket_conversation_id>';

SELECT id, conversation_type, next_message_sequence, created_at, updated_at
FROM [messaging].[conversation]
WHERE id = @conversation;

SELECT conversation_id, user_id, conversation_role, joined_at, left_at,
       last_delivered_sequence, last_read_sequence
FROM [messaging].[conversation_member]
WHERE conversation_id = @conversation
ORDER BY joined_at;
```

After L reads the message, its `last_read_sequence` and
`last_delivered_sequence` should both reach the acknowledged sequence.

### Message lifecycle

```sql
DECLARE @conversation UNIQUEIDENTIFIER = '<socket_conversation_id>';
DECLARE @message UNIQUEIDENTIFIER = '<socket_message_id>';

SELECT id, conversation_id, sender_id, client_message_id, sequence_number,
       message_type, body, created_at, edited_at, deleted_at
FROM [messaging].[message]
WHERE conversation_id = @conversation
  AND id = @message;
```

After T04, `edited_at` is populated. After T07, the row remains, `deleted_at` is populated, and `body`
is `NULL`. This is the durable tombstone that REST reconciliation returns.

## 9. Failure and Recovery Scenarios

### Missed created-message frame

1. Disconnect L's socket.
2. Run T01.
3. Reconnect L.
4. Fetch history after its last contiguous sequence.
5. Confirm the durable message is recovered even though no frame is replayed.

### Session revocation and teardown

Run `WL-Chat Socket Participants - Bring Down` as an ordered collection while both sockets are open.
It logs W and L out, verifies each token returns `401`, and clears both raw token values from the
selected environment. Each socket should close with `4401`. Durable user, conversation, message, and
audit rows remain available for evidence review; bring-down means terminating the test sessions, not
deleting authoritative history.

### Membership privacy

Remove a third participant from a group conversation, then trigger a new group event. That participant must
receive neither the event nor REST access to the private conversation. This scenario requires a group
setup and is not performed by the direct-conversation provisioning folder.

## 10. Record the Manual Result

For a release or milestone sign-off, record:

- environment and application version/commit;
- date and tester;
- setup folder result;
- both handshake and ping/pong results;
- observed event types and matching identifiers/sequences;
- REST reconciliation results;
- SQL evidence checked, without copying message bodies or tokens into shared logs;
- any missing, duplicated, delayed, or unexpected frames.

This manual evidence complements repository automation. It does not turn WebSocket delivery into an
authoritative guarantee and must not include raw session tokens.
