# Milestone 4 Implementation Guide

## Conversations and Membership Authorization

**Project:** Private Messenger  
**Milestone:** 4 - Conversations  
**Database:** Microsoft SQL Server 2022  
**Application stack:** Java 25, Quarkus 3.33 LTS, Maven  
**Status:** Planned implementation baseline  
**Last reviewed:** 2026-08-13

---

## 0. Purpose and Scope

Milestone 4 introduces durable direct and group conversations plus the membership rules that protect them. It is the authorization foundation for message persistence in Milestone 5.

This milestone is complete only when the project can:

1. create or resolve a direct conversation between two active users,
2. create a group conversation with one owner and optional initial members,
3. list only conversations visible to the authenticated user,
4. return conversation details only to active members,
5. add, remove, leave, promote, demote, and transfer ownership according to server-side policy,
6. retain departed membership rows for auditability,
7. reject enumeration attempts without revealing whether a private conversation exists,
8. enforce direct-conversation uniqueness under concurrent requests.

Milestone 4 does not implement message persistence, message sequencing, delivery acknowledgements, read positions, or WebSockets. The conversation schema includes the fields those later milestones require, but Milestone 4 APIs must not pretend that message delivery exists yet.

Current implementation status:

- Milestones 0 through 3 are implemented and validated.
- Authenticated actor identity and system roles are available from the session filter.
- The current `message` package is a stub and is not a conversation implementation baseline.
- No durable conversation or membership schema exists yet.

---

## 1. Deliverables and Exit Criteria

### Deliverables

- durable `messaging.conversation` table,
- durable `messaging.conversation_member` table,
- direct-conversation pair uniqueness table or equivalent database constraint,
- authenticated active-user prefix search,
- conversation and membership domain objects,
- JDBC repositories and application services,
- authenticated conversation APIs,
- centralized conversation authorization policy,
- concurrency and authorization integration tests,
- Postman examples and an extended run-all flow.

### Exit criteria

- non-members cannot enumerate or inspect conversations,
- removed members immediately lose access,
- owner, admin, and member permissions are enforced,
- ownership transfer and role transitions preserve a valid group owner,
- the same unordered user pair cannot create duplicate direct conversations,
- actor identity is always derived from the authenticated session,
- all new migrations, APIs, tests, documentation, and Postman artifacts pass repository validation.

---

## 2. Current Baseline and Gap Map

### 2.1 Baseline already present

The repository already contains:

- invite-only users and `UserId`,
- opaque sessions and authenticated request context,
- system-level `ADMIN` and `MEMBER` roles,
- shared request auditing and RFC 9457-style problem responses,
- SQL Server Flyway and Testcontainers infrastructure,
- Postman discovery, validation, user-flow, and cloud-sync tooling.

System roles and conversation roles are different concepts:

- `SystemRole.ADMIN` controls platform-wide administrative operations.
- `ConversationRole.OWNER`, `ADMIN`, and `MEMBER` control one group conversation.
- A system administrator is not automatically a member of every conversation and must not bypass private-conversation membership checks.

### 2.2 Gaps Milestone 4 must close

Milestone 4 must add:

1. conversation and membership migrations,
2. a concurrency-safe direct-conversation identity policy,
3. authenticated target-user discovery,
4. domain invariants for direct and group conversations,
5. repositories that query only active memberships where access is required,
6. application services that derive the actor from authenticated context,
7. APIs for creation, listing, details, and membership management,
8. tests for privacy, role transitions, removals, and races,
9. Postman contracts for the complete lifecycle.

---

## 3. Prerequisites

Before implementing Milestone 4, verify the merged Milestone 3 baseline:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
./scripts/database/validate-flyway-naming.sh
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
```

Work on a Milestone 4 feature branch. Do not rewrite an applied Flyway migration and do not use SQL Server `sa` as the application runtime principal.

---

## 4. Milestone 4 Design Decisions

Apply these decisions consistently in SQL, domain code, APIs, tests, audit metadata, and Postman examples.

### 4.1 Direct-conversation uniqueness

There shall be at most one direct conversation for an unordered pair of distinct users.

- Creating the same pair in either order returns the existing conversation.
- Concurrent create requests may race, but the database uniqueness constraint is authoritative.
- The application canonicalizes the pair before insertion and translates a known unique-constraint conflict into a read of the existing conversation.
- A direct conversation is not deleted or duplicated when a participant leaves. Version 1 does not expose leave, add, remove, or role-management operations for direct conversations.

Use `messaging.direct_conversation_pair` with canonical `participant_low_id` and `participant_high_id` columns and a unique constraint across the pair.

The canonical order is the ascending lexicographic order of each UUID's lowercase 36-character RFC 4122 string form. Java must compare `UUID.toString()` values. SQL Server must enforce the same representation with a binary collation:

```sql
CONSTRAINT ck_messaging_direct_pair_canonical_order CHECK (
    CONVERT(CHAR(36), participant_low_id) COLLATE Latin1_General_100_BIN2
    < CONVERT(CHAR(36), participant_high_id) COLLATE Latin1_General_100_BIN2
)
```

Do not use `UUID.compareTo` or SQL Server's native `uniqueidentifier` ordering for canonicalization because their ordering rules are not the contract. Unit and database tests must use pairs whose ordering differs under common UUID comparison strategies so an accidental implementation change is detected.

### 4.2 Group ownership

A group has exactly one active `OWNER`.

- The authenticated creator becomes the owner.
- New members are added as `MEMBER`.
- An owner may promote a member to `ADMIN` or demote an admin to `MEMBER`.
- Ownership changes only through a transactional transfer operation.
- The current owner cannot leave or be removed until ownership is transferred.
- A group admin cannot promote another user to owner or remove/demote the owner.

SQL Server must enforce **at most one** active owner with a filtered unique index:

```sql
CREATE UNIQUE INDEX ux_messaging_conversation_member_active_owner
    ON messaging.conversation_member (conversation_id)
    WHERE left_at IS NULL AND conversation_role = 'OWNER';
```

The database cannot express **at least one active owner** with this index. Group creation, ownership transfer, owner removal, and owner leave therefore remain transactional application invariants. Direct conversations have two active `MEMBER` rows and no owner.

### 4.3 Membership lifecycle

Membership rows are retained after departure.

- Active membership means `left_at IS NULL`.
- Removal or leave sets `left_at`; it does not delete the row.
- Re-adding a former member reactivates the retained row, resets its group role to `MEMBER`, and sets a new `joined_at`.
- Delivery and read cursors begin at zero and are not exposed or advanced until the appropriate later milestone.

### 4.4 Private-resource behavior

An inaccessible conversation normally returns `404 CONVERSATION_ACCESS_DENIED`, whether it is missing or the actor is not an active member. This prevents resource enumeration.

Use `403` only when the actor may know the conversation exists but lacks permission for a requested membership action, such as an active `MEMBER` attempting to add another member.

### 4.5 Authenticated actor is authoritative

The request must never accept an actor user ID, actor role, creator ID, or membership role as authoritative input. The session identifies the actor and the server evaluates the actor's active conversation membership.

### 4.6 Authenticated user discovery

Milestone 4 includes a limited authenticated user-directory search because clients cannot create conversations from usernames alone and must not invent or manually configure target UUIDs.

- `GET /api/v1/users?query=<prefix>&cursor=<opaque>&limit=20` requires authentication.
- `query` is required, is normalized with the existing username normalization rules, and must contain between 2 and 64 characters after trimming.
- Search is a case-insensitive prefix match against `normalized_username` and returns only `ACTIVE` users.
- The authenticated caller is excluded from results.
- Results expose only `userId` and display `username`; system role, status, password data, sessions, and invitation data are not exposed.
- The default limit is `20` and the maximum is `50`.
- An empty or one-character query is rejected with `400 USER_SEARCH_VALIDATION_FAILED`; Version 1 does not provide an endpoint that enumerates the entire user directory.

This visibility policy is accepted for the invite-only Version 1 product. If future privacy requirements restrict user discovery, record that change in an ADR before changing the endpoint semantics.

---

## 5. Step 1 - Add Conversation Schema Migrations

Create forward-only migrations under:

```text
scripts/database/flyway/wl_chat
```

Recommended sequence:

```text
VYYYYMMDDHHMMSS__create_messaging_conversation.sql
VYYYYMMDDHHMMSS__create_messaging_conversation_member.sql
VYYYYMMDDHHMMSS__create_messaging_direct_conversation_pair.sql
VYYYYMMDDHHMMSS__create_messaging_conversation_indexes.sql
VYYYYMMDDHHMMSS__grant_messaging_conversation_permissions.sql
```

### 5.1 Conversation table

Create `messaging.conversation` with at least:

- `id UNIQUEIDENTIFIER` primary key,
- `conversation_type` constrained to `DIRECT` or `GROUP`,
- nullable `title`,
- `created_by` FK to `identity.user_account`,
- `next_message_sequence BIGINT` initialized to `1`,
- `created_at DATETIME2(7)`,
- `updated_at DATETIME2(7)`.

Required invariants:

- direct conversations have no client-managed title in Version 1,
- group titles are normalized, non-blank, and length-limited,
- `next_message_sequence` is positive even though allocation begins in Milestone 5,
- timestamps represent UTC.

Suggested shape:

```sql
CREATE TABLE messaging.conversation (
    id UNIQUEIDENTIFIER NOT NULL,
    conversation_type VARCHAR(20) NOT NULL,
    title NVARCHAR(200) NULL,
    created_by UNIQUEIDENTIFIER NOT NULL,
    next_message_sequence BIGINT NOT NULL
        CONSTRAINT df_messaging_conversation_next_sequence DEFAULT (1),
    created_at DATETIME2(7) NOT NULL,
    updated_at DATETIME2(7) NOT NULL,

    CONSTRAINT pk_messaging_conversation PRIMARY KEY NONCLUSTERED (id),
    CONSTRAINT fk_messaging_conversation_created_by
        FOREIGN KEY (created_by) REFERENCES identity.user_account(id),
    CONSTRAINT ck_messaging_conversation_type
        CHECK (conversation_type IN ('DIRECT', 'GROUP')),
    CONSTRAINT ck_messaging_conversation_next_sequence
        CHECK (next_message_sequence > 0)
);
```

### 5.2 Conversation member table

Create `messaging.conversation_member` with:

- `conversation_id` FK,
- `user_id` FK,
- `conversation_role` constrained to `OWNER`, `ADMIN`, or `MEMBER`,
- `joined_at`,
- nullable `left_at`,
- `last_delivered_sequence` defaulting to `0`,
- `last_read_sequence` defaulting to `0`,
- composite primary key `(conversation_id, user_id)`.

Database constraints must require non-negative cursors and `last_read_sequence <= last_delivered_sequence`. The foreign-key and cursor shape must remain compatible with the message and acknowledgement milestones.

### 5.3 Direct pair table

Create `messaging.direct_conversation_pair` with:

- `conversation_id` primary key and FK to `messaging.conversation`,
- `participant_low_id` and `participant_high_id` user FKs,
- a unique constraint on `(participant_low_id, participant_high_id)`,
- the binary-collated canonical-order check defined in Section 4.1, which also proves the participants differ.

The service must insert the conversation, its two active `MEMBER` rows, and the pair row in one transaction.

### 5.4 Indexes and permissions

Add indexes for:

- active conversation listing by user: `(user_id, left_at, conversation_id)`,
- membership lookup: `(conversation_id, user_id, left_at)`,
- group member listing: `(conversation_id, left_at, joined_at)`,
- direct pair lookup: `(participant_low_id, participant_high_id)` through the unique constraint,
- at most one active owner through the filtered unique index from Section 4.2.

Grant the runtime principal only the required `SELECT`, `INSERT`, and `UPDATE` permissions. Do not grant runtime `DELETE` on conversation or membership tables. Keep migration ownership and runtime access aligned with the existing SQL Server principal baseline.

---

## 6. Step 2 - Implement the Conversation Domain

Create a top-level `conversation` module beside `identity`, `session`, and `message`:

```text
src/main/java/com/wayden/messenger/conversation/
├── api/
├── application/
├── domain/
└── infrastructure/
```

### 6.1 Domain types

Add focused types such as:

- `ConversationId`,
- `Conversation`,
- `ConversationType`,
- `ConversationMember`,
- `ConversationRole`,
- `ConversationTitle`,
- `DirectParticipantPair`.

The domain layer owns:

- direct versus group invariants,
- title normalization and validation,
- canonical direct-pair ordering,
- active-membership semantics,
- allowed role transitions,
- ownership-transfer preconditions.

Do not put JDBC, JAX-RS, JSON, or request-context concerns in domain classes.

### 6.2 Repository contracts

Define application-owned repository interfaces supporting:

- insert a conversation and initial memberships transactionally,
- find a direct conversation by canonical participant pair,
- find an accessible conversation by conversation ID and actor ID,
- list accessible conversations for an actor with cursor pagination,
- find active or retained membership,
- add or reactivate a member,
- mark a member as left,
- change a group role,
- transfer ownership atomically,
- list active members.

Multi-table mutations must have one clear transactional boundary. Do not compose several auto-committed repository calls and assume the aggregate remains valid after a partial failure.

---

## 7. Step 3 - Implement Application Services and Authorization

Application use cases should include:

- `searchActiveUsers(actor, query, cursor, limit)`,
- `createDirectConversation(actor, targetUserId)`,
- `createGroupConversation(actor, title, initialMemberIds)`,
- `listConversations(actor, cursor, limit)`,
- `getConversation(actor, conversationId)`,
- `addMember(actor, conversationId, targetUserId)`,
- `removeMember(actor, conversationId, targetUserId)`,
- `leaveGroup(actor, conversationId)`,
- `promoteMember(actor, conversationId, targetUserId)`,
- `demoteAdmin(actor, conversationId, targetUserId)`,
- `transferOwnership(actor, conversationId, targetUserId)`.

Here, `actor` represents server-owned authenticated context. API request DTOs do not contain it.

### 7.1 Authorization matrix

| Operation | Owner | Admin | Member | Non-member |
| --- | --- | --- | --- | --- |
| List/view conversation | yes | yes | yes | no |
| List members | yes | yes | yes | no |
| Add member | yes | yes | no | no |
| Remove member | yes | yes, except owner/admin | no | no |
| Promote member to admin | yes | no | no | no |
| Demote admin to member | yes | no | no | no |
| Transfer ownership | yes | no | no | no |
| Leave group | after transfer | yes | yes | no |

An admin may remove a `MEMBER` but not another `ADMIN` or the `OWNER`. The owner may remove admins or members. Self-removal uses the leave operation so its policy remains explicit.

### 7.2 Target validation

- Creation and membership changes require existing, active target users.
- A user cannot create a direct conversation with themselves.
- Duplicate initial group member IDs are normalized before persistence.
- The creator is never duplicated in the initial member set.
- Direct conversations always contain exactly the two canonical participants.

### 7.3 Error mapping

Use these safe problem codes:

- `CONVERSATION_ACCESS_DENIED` (`404`),
- `CONVERSATION_VALIDATION_FAILED` (`400`),
- `CONVERSATION_ROLE_FORBIDDEN` (`403`),
- `CONVERSATION_MEMBER_NOT_FOUND` (`404` only when disclosure is safe),
- `CONVERSATION_OWNERSHIP_REQUIRED` (`409`),
- `USER_NOT_FOUND` (`404`),
- `USER_SEARCH_VALIDATION_FAILED` (`400`).

Known constraint violations must map to typed application outcomes. Unexpected SQL errors remain internal and must not expose schema details.

---

## 8. Step 4 - Expose Authenticated APIs

The Version 1 endpoints are:

```text
GET    /api/v1/users
POST   /api/v1/conversations/direct
POST   /api/v1/conversations/groups
GET    /api/v1/conversations
GET    /api/v1/conversations/{conversationId}
GET    /api/v1/conversations/{conversationId}/members
PUT    /api/v1/conversations/{conversationId}/members/{userId}
DELETE /api/v1/conversations/{conversationId}/members/{userId}
POST   /api/v1/conversations/{conversationId}/leave
PUT    /api/v1/conversations/{conversationId}/members/{userId}/role
POST   /api/v1/conversations/{conversationId}/members/{userId}/transfer-ownership
```

Every endpoint requires authentication. None accepts an actor ID in the request body.

### 8.1 Creation requests

Direct conversation:

```json
{
  "targetUserId": "11111111-1111-1111-1111-111111111111"
}
```

Group conversation:

```json
{
  "title": "Project room",
  "initialMemberIds": [
    "11111111-1111-1111-1111-111111111111"
  ]
}
```

Role change:

```json
{
  "role": "ADMIN"
}
```

The role endpoint accepts only `ADMIN` and `MEMBER`. Ownership is changed only through the dedicated transfer endpoint. Clients do not supply `createdBy`, the actor's role, timestamps, or message sequence values.

### 8.2 HTTP outcomes and idempotency

Use these exact outcomes:

| Operation | Outcome |
| --- | --- |
| Create a new direct conversation | `201 Created`, response body, and `Location` header |
| Resolve an existing direct conversation | `200 OK` with the same response shape |
| Create a group | `201 Created`, response body, and `Location` header |
| Add/reactivate an eligible group member | `204 No Content` |
| Add an already-active member | `204 No Content` without resetting the existing role |
| Remove an active member | `204 No Content` |
| Repeat removal for an absent/departed target | `204 No Content` after actor authorization succeeds |
| Set an eligible member to their current `ADMIN`/`MEMBER` role | `204 No Content` |
| Leave a group | `204 No Content` |
| Transfer ownership | `204 No Content` |

Idempotent membership retries must still authorize the actor before returning success. Owner protections and role restrictions take precedence over idempotent success. A repeated request must never bypass `CONVERSATION_ACCESS_DENIED`, `CONVERSATION_ROLE_FORBIDDEN`, or `CONVERSATION_OWNERSHIP_REQUIRED`.

### 8.3 List pagination

Conversation listing is ordered by `updated_at DESC, id DESC` and uses those values as the seek cursor. The default limit is `50`; the maximum is `100`.

```http
GET /api/v1/conversations?cursor=<opaque>&limit=50
```

The response envelope is:

```json
{
  "items": [],
  "nextCursor": null
}
```

`nextCursor` is `null` when no later page exists. Otherwise it is an unpadded Base64URL encoding of a UTF-8 JSON object containing exactly:

```json
{
  "v": 1,
  "updatedAt": "2026-08-13T14:30:00.1234567Z",
  "conversationId": "11111111-1111-1111-1111-111111111111"
}
```

The server treats the cursor as opaque, validates the version and fields, and returns `400 INVALID_CURSOR` for malformed, unsupported, or inconsistent values. The SQL seek predicate must match the declared ordering exactly. Fetch `limit + 1` rows to determine whether a next cursor exists; do not use offset pagination.

Seek pagination does not promise a database snapshot across requests. A conversation updated while a client is paging may move in the ordering; clients refresh from the first page to reconcile concurrent changes. Tests for non-overlapping pages use an unchanged dataset.

User search uses the same envelope shape, ordered by `normalized_username ASC, id ASC`. Its cursor contains `v`, `query`, `normalizedUsername`, and `userId`; `query` is the normalized prefix. Using a cursor with a different query returns `400 INVALID_CURSOR`.

### 8.4 Response shape

Conversation responses should include:

- conversation ID and type,
- group title when applicable,
- authenticated member's conversation role,
- active member summary appropriate to the endpoint,
- created and updated timestamps.

Do not expose `nextMessageSequence` as a promise of message availability.

---

## 9. Step 5 - Implement JDBC Transactions and Concurrency Handling

### 9.1 Direct create algorithm

```text
1. Authenticate and validate both active users.
2. Canonicalize the unordered participant pair using the lowercase UUID-string ordering from Section 4.1.
3. Read an existing direct conversation by that pair.
4. If present, return it.
5. Begin a transaction.
6. Insert the DIRECT conversation.
7. Insert both MEMBER rows.
8. Insert the canonical pair row.
9. Commit and return the conversation.
10. If the pair uniqueness constraint loses a race, roll back and read the winner.
```

The pre-read is an optimization; the unique constraint provides correctness.

### 9.2 Group create algorithm

Insert the group, owner membership, and normalized initial member set in one transaction. Any failure rolls back the entire aggregate.

### 9.3 Ownership transfer algorithm

In one transaction:

1. lock or conditionally update the current owner row,
2. verify the target is an active group member,
3. demote the current owner to `ADMIN`,
4. promote the target to `OWNER`,
5. update the conversation timestamp,
6. commit only if exactly one active owner remains.

Use conditional SQL and affected-row checks so concurrent role changes cannot silently violate ownership.

---

## 10. Step 6 - Add Tests

### 10.1 Domain tests

Cover:

- direct pair canonicalization in both input orders,
- self-direct rejection,
- group title validation,
- allowed and forbidden role transitions,
- active versus departed membership semantics.

### 10.2 SQL Server repository tests

Use SQL Server Testcontainers for:

- schema constraints and FK behavior,
- Java and SQL Server direct-pair ordering agreement,
- transactional group creation rollback,
- retained member rows after leave/removal,
- reactivation of a departed member,
- concurrent direct creation producing one conversation,
- concurrent ownership operations preserving one owner,
- filtered owner uniqueness rejecting a second active owner.

### 10.3 API integration tests

Minimum matrix:

1. authenticated prefix search returns active users without sensitive fields,
2. user search rejects an empty or one-character query,
3. authenticated user creates a direct conversation with `201`,
4. reverse-order direct creation returns the same ID with `200`,
5. parallel direct creation produces one durable conversation,
6. user creates a group and becomes owner,
7. active member lists and views the group,
8. conversation pagination returns stable, non-overlapping seek pages,
9. malformed or query-mismatched cursors return `400 INVALID_CURSOR`,
10. non-member cannot list or inspect the group,
11. owner and admin add permitted members,
12. repeated member add/remove operations obey the idempotency table,
13. ordinary member cannot manage membership,
14. removed member immediately receives access denial,
15. owner promotes and demotes a member through valid transitions,
16. ownership transfer succeeds atomically,
17. owner cannot leave before transfer,
18. system administrator without membership receives no private-conversation bypass.

Suggested test names:

- `direct_creation_is_idempotent_for_unordered_pair`
- `authenticated_user_search_returns_only_active_public_identity`
- `concurrent_direct_creation_persists_one_conversation`
- `conversation_cursor_pages_without_duplicates_or_gaps`
- `non_member_cannot_discover_private_conversation`
- `removed_member_loses_access_immediately`
- `member_cannot_manage_group_membership`
- `ownership_transfer_preserves_exactly_one_owner`
- `system_admin_requires_conversation_membership`

---

## 11. Step 7 - Extend Postman Contracts

After adding each API route:

```bash
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
```

Add explicit examples for success and expected problem responses. Extend the run-all journey to:

1. bootstrap and authenticate an admin user,
2. create at least two invited member users and sessions,
3. find a target through authenticated user search and capture the returned UUID,
4. create a direct conversation and verify reverse-order idempotency where practical,
5. create a group,
6. add a member,
7. verify member visibility,
8. verify a member cannot perform an owner-only transition,
9. remove the member,
10. verify the removed member receives `404 CONVERSATION_ACCESS_DENIED`,
11. log out active sessions.

Every run-all request must retain an `Expected:` status assertion. New identity values created during the flow should be captured dynamically rather than requiring fixed globals.

Synchronize only after local discovery and validation pass:

```bash
./scripts/postman/sync-postman.sh --dry-run
./scripts/postman/sync-postman.sh
```

---

## 12. Step 8 - Audit and Observability

Record operation names and safe identifiers for:

- direct/group creation,
- member add/remove/leave/reactivation,
- promotion/demotion,
- ownership transfer,
- authorization denial.

The authenticated request context supplies the actor. Audit attributes may include conversation ID, target user ID, previous role, new role, and outcome, but must not include session tokens or private message content.

Authorization failures should remain observable through request IDs and audit events without disclosing the private resource to the caller.

---

## 13. Local Validation Sequence

Run the narrowest relevant tests while developing, then finish with:

```bash
./scripts/database/validate-flyway-naming.sh
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
./mvnw --batch-mode --no-transfer-progress clean verify
git diff --check
```

If discovery changes the authoritative collection, commit the generated collection update with the API change.

---

## 14. Common Failure Modes

1. **Using system admin as a conversation superuser**  
   Keep system and conversation authorization separate.

2. **Checking for an existing direct conversation only in Java**  
   Two requests can pass the check concurrently; enforce the canonical pair in SQL Server.

3. **Trusting actor or role fields from JSON**  
   Derive the actor and their active role from the authenticated session and database membership.

4. **Deleting membership rows**  
   Set `left_at` so departure remains auditable.

5. **Returning `403` for every inaccessible conversation**  
   Prefer the same `404 CONVERSATION_ACCESS_DENIED` result for missing and non-visible private resources.

6. **Allowing the owner to leave directly**  
   Require a successful ownership transfer first.

7. **Splitting aggregate creation across auto-committed calls**  
   Conversation and initial memberships must commit or roll back together.

8. **Building message behavior into this milestone**  
   Preserve schema compatibility, but leave message persistence and delivery semantics to later milestones.

---

## 15. Definition of Done

Milestone 4 is done when:

- all deliverables and exit criteria in this guide are implemented,
- direct and group invariants are enforced in both application code and SQL where practical,
- private-resource authorization is tested through real HTTP and SQL Server interactions,
- concurrency tests prove direct-pair uniqueness,
- no membership operation trusts client-supplied actor identity,
- user discovery, HTTP outcomes, idempotency, and cursor behavior match the explicit Version 1 contracts,
- migrations and least-privilege grants are forward-only and validated,
- Postman collections and user flows represent the implemented API,
- the canonical build and all repository validation commands pass,
- README and the system specification status are updated from planned to implemented.

---

## 16. Recommended Implementation Order

1. Keep ADR-0013 synchronized with any policy changes that differ from this runbook.
2. Add conversation, membership, direct-pair, index, and grant migrations.
3. Add domain identifiers, enums, aggregates, and invariant tests.
4. Add repository contracts and JDBC transactional implementations.
5. Add application services and centralized authorization policy.
6. Add direct/group creation APIs.
7. Add list/details APIs with privacy-preserving access behavior.
8. Add membership and ownership APIs.
9. Add concurrency and end-to-end integration tests.
10. Run Postman discovery, extend the run-all flow, validate, and synchronize.
11. Run the full Maven verification and update completion status documentation.

---

## 17. References

- `README.md`
- `AGENTS.md`
- `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`
- `docs/development-guide/milestone-3-sessions-step-by-step.md`
- `docs/database/sql-server-principals-and-permissions.md`
- `docs/architecture/decision/ADR-0001-use-modular-monolith.md`
- `docs/architecture/decision/ADR-0013-define-conversation-identity-membership-and-discovery.md`
- `postman/README.md`
