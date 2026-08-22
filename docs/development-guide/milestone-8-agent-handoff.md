# Milestone 8 Agent Handoff

## Current status

Milestone 8 is implemented and its realtime transport is proven to start under the Quarkus test
runtime. This document supersedes the earlier implementation-review snapshot that recorded a failed
development-mode launch before the realtime validation work was completed.

The authoritative implementation plan and protocol contract remain in
`docs/development-guide/milestone-8-websockets-step-by-step.md`. Client recovery and responsibility
rules remain in `docs/client-integration/client-responsibility-and-recovery-guide.md`.

## Implemented boundary

- SQL Server remains authoritative; WebSocket frames are non-authoritative delivery signals.
- `/api/v1/ws` authenticates session tokens supplied by supported query, subprotocol, or
  non-browser `Authorization` transports.
- Connections are tracked per user and session, including multiple sockets for one session.
- Message and delivery/read events fan out only after transaction success and only to active
  conversation members.
- Message send, edit, and delete remain authoritative REST operations. Socket commands are limited
  to liveness and optional delivery/read acknowledgements.
- Logout and administrative session revocation close matching sockets with code `4401`.
- Reconnect recovery uses durable REST history and sequence/cursor reconciliation; frames are not
  replayed by the socket transport.

## Review hardening completed

- Registry registration/removal is coordinated atomically and revoke-close failures are isolated per
  connection.
- Fan-out uses independent asynchronous sends so a slow or failed socket does not serialize later
  recipients.
- Session expiry is retained in connection metadata; expired sockets cannot issue commands or
  receive application events.
- The handshake immediately revalidates after registration, closing the authenticate/register race
  with committed revocation.
- Unregistered command traffic closes with `4401` rather than being reported as a command-format
  error.
- WebSocket acknowledgements accept only signed-64-bit JSON integers and cannot silently coerce
  strings, fractions, or oversized values.
- Bearer header scheme casing and token trimming match the REST authentication path.

## Automated evidence

Focused callback tests instantiate `ChatWebSocketEndpoint` and cover bearer parsing, registration,
stale-session rejection, close code `4401`, command validation, and acknowledgement delegation.
Registry and dispatcher tests cover multi-connection tracking plus independent close/send failures.

`WebSocketTransportIntegrationTest` starts the Quarkus application with migrated SQL Server and uses
a real WebSocket client connection to prove:

- case-insensitive bearer authentication and ping/pong;
- committed CDI events produce frames while rolled-back events do not;
- two sockets backed by one session both close with `4401` after logout.

Manual human validation remains documented in
`docs/client-integration/manual-websocket-postman-testing-guide.md`, including W/L setup, exact
commands and expected frames, reconnect recovery, and authoritative SQL evidence.

## Validation commands

Run before merge:

```bash
./mvnw clean verify
node --test scripts/postman/test/*.test.mjs
node scripts/postman/validate-postman.mjs
```

The transport integration test requires Docker because it uses the repository's SQL Server
Testcontainers resource.
