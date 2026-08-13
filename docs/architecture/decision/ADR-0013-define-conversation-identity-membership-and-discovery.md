# ADR-0013: Define conversation identity, membership, and user discovery

- Status: Accepted
- Date: 2026-08-13
- Decision owners: Project maintainers

## Context

Milestone 4 introduces private direct and group conversations. Implementation
requires stable decisions for direct-conversation identity, group ownership,
membership history, target-user discovery, privacy-preserving authorization,
and list pagination. Leaving these policies to individual API or repository
methods would produce inconsistent behavior and unsafe concurrent writes.

## Decision

Adopt the Milestone 4 policies defined in
`docs/development-guide/milestone-4-conversations-step-by-step.md`:

- one direct conversation exists per unordered pair of distinct users;
- the pair is canonicalized by lexicographically ordering lowercase RFC 4122
  UUID strings, with the same binary-collated ordering enforced in SQL Server;
- every group has exactly one active owner as a transactional invariant, while
  a filtered unique index enforces at most one active owner;
- membership rows are retained and departure is represented by `left_at`;
- system administrator status does not bypass conversation membership;
- inaccessible private conversations normally return the same `404` outcome
  used for missing conversations;
- authenticated users may perform limited prefix search over active usernames
  to obtain target user IDs;
- conversation and user search lists use opaque, versioned seek cursors rather
  than offsets;
- direct creation and membership mutations use the HTTP and idempotency
  outcomes specified in the Milestone 4 guide.
- creation of one canonical direct pair is serialized by a transaction-owned
  SQL Server application lock, with the unique pair constraint retained as the
  final database invariant. This avoids missing-row range-lock conversion
  deadlocks while preserving concurrency between different pairs.

The implementation guide is the detailed contract for endpoint shapes,
authorization transitions, cursor payloads, SQL constraints, and tests.

## Alternatives considered

### Allow duplicate direct conversations

Rejected because clients would need ambiguous conversation-selection rules and
concurrent retries could fragment history between several conversations.

### Use native UUID ordering independently in Java and SQL Server

Rejected because Java and SQL Server do not share a sufficiently explicit UUID
ordering contract. Canonical string ordering is portable and testable.

### Give system administrators implicit access to every conversation

Rejected because platform administration is separate from private
conversation membership and should not silently expand message visibility.

### Delete membership rows when users leave

Rejected because historical membership is required for auditability and later
message authorization analysis.

### Require clients to know target UUIDs without user search

Rejected because normal clients begin with usernames and should not rely on
manually configured identifiers. Unbounded directory enumeration is also
rejected; Version 1 requires an authenticated, minimum-length prefix query.

### Offset pagination

Rejected because concurrent conversation updates can cause duplicates or gaps
between offset pages. Versioned seek cursors provide a stable evolution path.

## Consequences

### Positive

- Direct creation is deterministic and safe under concurrency.
- Conversation privacy follows one centralized membership model.
- Ownership transitions preserve a clear group authority chain.
- Clients can discover conversation targets without external UUID setup.
- Retained memberships support audit and later messaging semantics.
- Cursor formats can evolve through an explicit version field.

### Negative

- Direct participants cannot create separate threads with the same person in
  Version 1.
- Membership tables grow because departed rows are retained.
- User prefix search reveals limited active-account identity information to
  other authenticated users.
- Ownership requires multi-row transactional logic in addition to database
  constraints.

### Risks and mitigations

- Risk: Java and SQL canonicalization drift.
  Mitigation: shared domain code plus SQL Server integration tests using UUIDs
  that sort differently under alternative comparison strategies.
- Risk: a group temporarily or durably loses its owner.
  Mitigation: one transactional transfer path, conditional updates, and
  invariant tests; owner leave/removal is rejected before transfer.
- Risk: user search enables enumeration.
  Mitigation: authentication, a required two-character prefix, bounded result
  size, cursor pagination, and no role/status disclosure.
- Risk: cursor internals become a client dependency.
  Mitigation: Base64URL encoding, explicit versioning, and documentation that
  cursors are opaque.

## Security impact

Conversation authorization derives the actor from the authenticated session
and the actor's active membership from SQL Server. Client-supplied actor or role
values are never authoritative. Missing and inaccessible conversations share a
privacy-preserving response. User discovery exposes only active user IDs and
display usernames to authenticated users.

## Operational impact

The decision adds conversation, membership, and direct-pair migrations plus
filtered and lookup indexes. No new runtime service is required. Existing
transaction management, SQL Server, audit, Testcontainers, and Postman tooling
remain the implementation platform.

## Revisit conditions

Revisit this decision if:

- the product needs multiple direct threads per user pair;
- privacy policy no longer permits authenticated username discovery;
- groups require multiple owners or ownerless operation;
- membership history receives a defined retention/deletion requirement;
- cursor payloads need signing or server-side storage;
- system administrators receive an explicit, audited support-access workflow.
