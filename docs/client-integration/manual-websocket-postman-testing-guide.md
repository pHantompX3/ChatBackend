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

Create two WebSocket requests in Postman Desktop. For Local use
`ws://localhost:8080/api/v1/ws`; for DevDocker use `ws://localhost:8081/api/v1/ws`, unless the
environment was configured with a different host port.

### W (Wayden) tab

- URL: the applicable `/api/v1/ws` URL
- Header: `Authorization: Bearer {{socket_participant_1_token}}`
- Suggested name: `Socket W - Wayden`

### L (Lacara) tab

- URL: the same `/api/v1/ws` URL
- Header: `Authorization: Bearer {{socket_participant_2_token}}`
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
  "conversationId": "<socket_conversation_id>",
  "sequence": 1
}
```

Then send the corresponding `read.ack`. Replace the conversation and sequence placeholders with the
current environment values. Verify the resulting `delivery.updated` and `read.updated` frames on both
sockets, then run T05 and T06 to prove the commands advanced SQL-backed state.

Equal or lower acknowledgement retries are valid no-ops and should not create a second advancement
event. A sequence beyond the committed high-water mark should produce a stable error frame and must
not change the database cursor.

## 7. Complete W/L Socket Capability Exchange

Use this order when performing a full manual pass:

1. **Authentication and liveness:** connect W and L with their own bearer tokens and verify
   application `ping`/`pong` on both tabs.
2. **Multi-connection fan-out:** open a second W socket with W's token. T01 must reach W's two sockets
   and L's socket, proving one user may have multiple live clients.
3. **Created-message fan-out:** run T01 and correlate the REST response with all
   `message.created` envelopes.
4. **Bidirectional delivery command:** send `delivery.ack` from L's socket, then run T05/T06 to prove
   the cursor is durable.
5. **Idempotent acknowledgement:** repeat the same `delivery.ack`. Durable position must remain equal
   and no second `delivery.updated` advancement event should be emitted.
6. **Bidirectional read command:** send `read.ack` from L's socket. Both participants should receive
   `read.updated`, and L's delivery cursor must advance with its read cursor.
7. **Edit fan-out:** run T04 and verify `message.edited` retains the original message ID and sequence.
8. **Receipt projection:** run T05 and T06 to compare sender aggregate status with L's own durable
   position.
9. **Delete fan-out and tombstone:** run T07/T08 and verify the `message.deleted` signal agrees with
   the retained SQL/REST tombstone.
10. **Unknown command:** send `{"action":"not.supported"}` and expect
    `{"type":"error","code":"UNKNOWN_COMMAND"}` without disconnecting.
11. **Invalid command:** send malformed JSON or an acknowledgement missing required fields and expect
    `INVALID_COMMAND` without changing SQL state.
12. **Gap/reconnect recovery:** disconnect L, create another W message, reconnect L, and recover it
    through REST because the missed frame is not replayed.
13. **Session revocation:** run the Bring Down collection while both sockets remain open. W and L
    should each close with `4401`; both revoked tokens must subsequently receive `401`.

Active-membership privacy filtering cannot be fully demonstrated with this direct two-participant
fixture because direct-conversation membership lifecycle rules are not a substitute for group removal.
Use a separate group fixture with a removed third participant when explicitly validating that privacy
boundary.

## 8. Verify Authoritative SQL Server State

Run these read-only queries with the current Postman variable values substituted for the UUID
placeholders. Use an administrative/developer query principal; the application runtime principal is
intentionally least-privileged.

### Users and active sessions

```sql
DECLARE @participant1 UNIQUEIDENTIFIER = '<socket_participant_1_user_id>';
DECLARE @participant2 UNIQUEIDENTIFIER = '<socket_participant_2_user_id>';

SELECT id, username, enabled, created_at
FROM identity.user_account
WHERE id IN (@participant1, @participant2);

SELECT id, user_id, status, created_at, expires_at, last_seen_at, revoked_at
FROM identity.session
WHERE user_id IN (@participant1, @participant2)
ORDER BY created_at DESC;
```

The raw Postman tokens will not appear because only `token_hash` is persisted.

### Conversation and membership cursors

```sql
DECLARE @conversation UNIQUEIDENTIFIER = '<socket_conversation_id>';

SELECT id, conversation_type, next_message_sequence, created_at, updated_at
FROM messaging.conversation
WHERE id = @conversation;

SELECT conversation_id, user_id, conversation_role, joined_at, left_at,
       last_delivered_sequence, last_read_sequence
FROM messaging.conversation_member
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
FROM messaging.message
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
