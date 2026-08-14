# ADR-0014: Use per-user delivery and read cursors

- Status: Accepted
- Date: 2026-08-13
- Decision owners: Project maintainers

## Context

Milestone 6 introduces durable delivery and read state. A user may access the backend concurrently
from browser, mobile, and desktop clients, but the current identity model has users and sessions rather
than registered devices. The implementation also needs clear rules for explicit acknowledgement,
retries, sequence gaps, unread counts, group status visibility, and the boundary between durable state
and future WebSocket signals.

Leaving these choices to individual endpoints would create incompatible client behavior and could
incorrectly describe publication or one connected device as universal delivery.

## Decision

Adopt the Milestone 6 policies defined in
`docs/development-guide/milestone-6-delivery-and-read-state-step-by-step.md`:

- delivery/read cursors belong to a conversation membership and therefore to a user, not a session or
  device;
- any authenticated session for that user may monotonically advance the user's cursors;
- only explicit authenticated client acknowledgement advances delivery or read state;
- WebSocket, broker, push, or HTTP publication alone is never proof of delivery;
- clients acknowledge the highest contiguous conversation sequence they accepted;
- stale/equal acknowledgements are successful idempotent no-ops;
- read acknowledgement atomically advances delivery when necessary;
- acknowledgements beyond the latest committed message sequence are rejected;
- active membership is authorized before high-water validation so inaccessible users cannot infer
  conversation activity from acknowledgement errors;
- the authenticated member may query only their own raw positions and derived unread count;
- unread count excludes the user's own messages and deleted tombstones;
- only the original sender may query message delivery/read status;
- sender status is aggregate-only and uses current active recipients other than the sender;
- no-recipient aggregates are not described as fully delivered or read;
- current Version 1 membership reactivation resets both cursors to zero;
- deploying receipt APIs does not backfill delivery or read positions for existing messages;
- WebSockets and per-device receipt state remain outside Milestone 6.

## Alternatives considered

### Per-device cursors

Deferred because the platform does not yet have a durable device identity, device lifecycle, or a
product requirement for all-device delivery. Inferring devices from sessions would be unstable and
would conflate authentication credentials with installed clients.

### Advance delivery when the server publishes an event

Rejected because publication proves only that the server attempted transport. It does not prove that
a recipient client received, accepted, or persisted the event.

### Expose every group member's raw positions

Rejected because it creates unnecessary behavioral surveillance and a larger privacy-sensitive API.
Aggregate sender status satisfies delivery feedback without exposing individual reading activity.

### Persist unread counters

Rejected because counters can drift across retries, edits, deletes, membership changes, and failed
transactions. The message table and read cursor already provide authoritative inputs for derivation.

### Treat an empty recipient set as fully delivered

Rejected because vacuous truth is misleading in a user-facing delivery indicator. With zero
applicable recipients, `allDelivered` and `allRead` are false.

## Consequences

### Positive

- Every client type can share one simple, durable reconciliation contract.
- Retried and reordered acknowledgements remain safe.
- Delivery state survives reconnects and lost transient signals.
- Sender feedback can be reconstructed entirely from SQL Server.
- Group responses avoid unnecessary per-member receipt disclosure.
- The design does not require a client application or WebSocket transport to validate the backend.

### Negative

- Acknowledgement by one client advances the user's state for every client.
- The backend cannot claim that all of a user's devices received a message.
- Group aggregate denominators can change when active membership changes.
- The server cannot prove that a client honestly retained every sequence it acknowledges.
- Version 1 records sequence positions but not per-message delivery/read timestamps.

### Risks and mitigations

- Risk: a buggy client acknowledges across a local gap.
  Mitigation: document highest-contiguous semantics, provide deterministic REST history recovery, and
  test the reconciliation journey.
- Risk: cursor updates race with message creation.
  Mitigation: validate the committed high-water mark in the same transaction using locking compatible
  with message sequence allocation.
- Risk: acknowledgement errors reveal private conversation activity.
  Mitigation: authorize and lock the active membership before reading or validating the conversation
  high-water mark.
- Risk: group status is mislabelled as universal delivery.
  Mitigation: return explicit counts and require every current active recipient before setting an
  aggregate all-status flag.
- Risk: status endpoints leak private membership activity.
  Mitigation: active-membership authorization, sender-only access, aggregate-only responses, and no
  platform-admin bypass.

## Security impact

Actor identity comes from the authenticated session, and only the actor's active membership row may be
updated or returned as raw cursor state. Sender aggregate status does not reveal recipient identities,
timestamps, or message content. Non-members and departed members receive privacy-preserving resource
outcomes. Audit metadata retains safe identifiers and numeric positions but never message bodies or
tokens.

## Operational impact

No new runtime service is required. SQL Server remains the durable authority and existing membership
columns are reused. WebSocket infrastructure, device registries, and notification services are not
introduced. Cursor and aggregate query latency should be monitored without high-cardinality user or
conversation metric labels.

## Revisit conditions

Revisit this decision if:

- product requirements need per-device or all-device delivery/read state;
- group members must see named per-recipient receipts;
- membership history gains durable multiple join/leave intervals;
- edit delivery requires a separate revision receipt;
- privacy policy changes sender visibility;
- a future transport introduces acknowledged commands but cannot preserve the same durable cursor
  transaction.
