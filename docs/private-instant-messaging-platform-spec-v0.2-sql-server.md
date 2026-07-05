# Private Instant Messaging Platform

## System Specification, Development Plan, and Coding Guide

**Document version:** 0.2  
**Status:** Draft baseline  
**Date:** 2026-07-05  
**Architecture style:** API-first modular monolith  
**Primary deployment target:** Local development workstation  
**Secondary deployment target:** DevDocker rehearsal environment on local workstation  
**Future deployment target:** Hosted production environment

## Revision 0.2 summary

This revision replaces the PostgreSQL and jOOQ baseline with:

- Microsoft SQL Server running in Docker
- the Microsoft SQL Server JDBC driver through Quarkus Agroal
- Flyway SQL Server migrations written in T-SQL
- explicit JDBC repository implementations
- SQL Server Testcontainers for integration and migration testing
- SQL Server-specific constraints, indexes, concurrency handling, backup guidance, and deployment restrictions

jOOQ remains an optional future enhancement if an appropriate commercial licence is available. It is not required by the baseline implementation.

---

# Part I — System Specification

## 1. Purpose

This project is a learning-focused implementation of a private, invite-only instant messaging platform. It shall be engineered using production-grade practices even though its expected deployment size is small.

The system shall initially expose all durable functionality through an HTTP API so that it can be exercised without a graphical client. A browser or mobile client may be introduced later without changing the core domain rules or data ownership model.

The project is intended to teach and demonstrate:

- API design
- authentication and authorization
- relational data modelling
- database migration discipline
- concurrency control
- transactional consistency
- idempotency
- real-time communication
- secure secret and session handling
- automated testing
- observability
- deployment and operational practices

## 2. Goals

The system shall:

1. Support invite-only account creation.
2. Authenticate users securely.
3. Support direct and group conversations.
4. authorize every conversation and message operation.
5. Persist an ordered, durable message history.
6. prevent duplicate messages when clients retry requests.
7. track per-user read positions.
8. support message editing and deletion policies.
9. expose a versioned REST API.
10. support complete functional testing through Postman or another HTTP client.
11. add WebSocket-based live delivery after the REST model is correct.
12. run locally through Docker Compose.
13. be deployable to a small Linux server without redesign.
14. follow immutable Flyway migration practices.
15. provide structured logs, health checks, metrics, and traces.
16. enforce quality through automated tests and static analysis.

## 2.1 Environment Lifecycle Strategy

The delivery lifecycle is currently organized into three environments:

1. **Local**
   - Application runs from IDE or terminal.
   - SQL Server is available on `localhost:1433`.
   - Primary use: feature development and fast debugging.

2. **DevDocker**
   - Application and SQL Server run in Docker as a remote-like rehearsal stage.
   - Planned SQL Server host mapping: `localhost:1434`.
   - Primary use: validate containerized runtime behavior before hosted deployment.

3. **Production (future)**
   - Persistent hosted environment with externally reachable API.
   - Activated only after infrastructure provisioning and operational controls are in place.

### Current automation boundary

- Current CI workflows focus on validation of bootstrap/migration behavior.
- Automatic production deployment is intentionally deferred until persistent hosting exists.
- Remote migration workflow artifacts may exist as scaffolding but are not treated as active production rollout.

## 3. Non-goals for Version 1

Version 1 shall not include:

- end-to-end encryption
- voice or video calling
- federation between servers
- public registration
- public channels
- message bots
- microservices
- Kafka or another external event broker
- Kubernetes
- multi-region deployment
- horizontal database sharding
- attachment storage
- disappearing messages
- full-text message search

These features may be considered only after the core system is demonstrably correct.

## 4. Quality Attributes

The design shall prioritize the following attributes, in order:

1. **Correctness**
2. **Security**
3. **Recoverability**
4. **Maintainability**
5. **Observability**
6. **Performance**
7. **Deployment simplicity**

Raw benchmark throughput shall not override correctness, understandable code, or secure defaults.

## 5. Technology Baseline

### 5.1 Required stack

| Area                | Selection                                                                |
| ------------------- | ------------------------------------------------------------------------ |
| Language            | Java 25                                                                  |
| Runtime framework   | Quarkus 3.33 LTS                                                         |
| Build               | Maven Wrapper                                                            |
| HTTP                | Quarkus REST                                                             |
| Concurrency model   | Imperative services on virtual threads where appropriate                 |
| Database            | Microsoft SQL Server in Docker; project image pinned to a tested version |
| Database access     | Explicit JDBC repositories using the Microsoft JDBC driver               |
| Connection pool     | Quarkus Agroal                                                           |
| Migrations          | Flyway                                                                   |
| JSON                | Jackson                                                                  |
| Validation          | Jakarta Bean Validation                                                  |
| API description     | OpenAPI                                                                  |
| Real-time transport | Quarkus WebSockets Next, introduced later                                |
| Testing             | JUnit, REST Assured, and SQL Server Testcontainers                       |
| Telemetry           | OpenTelemetry and Micrometer integration                                 |
| Local orchestration | Docker Compose                                                           |
| Source control      | Git                                                                      |

### 5.2 Why this stack

Java and Quarkus are selected as the balanced high-performance option for this project. The stack provides:

- a high-performance Vert.x-based HTTP foundation
- virtual-thread support for readable imperative code
- mature transaction and security tooling
- first-class Flyway integration
- supported Microsoft SQL Server JDBC integration through Quarkus Agroal
- native-image capability if startup time or memory later becomes important
- mature testing, profiling, observability, and build ecosystems
- direct exposure to T-SQL, JDBC, SQL Server transactions, locking, and execution behaviour

Rust could deliver lower-level control and potentially lower runtime overhead, but it would move a large portion of the learning exercise toward memory ownership, ecosystem integration, and custom infrastructure. For this project, system correctness, security, database semantics, and enterprise development practices are more valuable than maximizing synthetic HTTP benchmark scores.

### 5.3 Runtime mode

The application shall initially run in **JVM mode**.

Native compilation shall be treated as a later optimization and shall not be introduced until:

- the JVM implementation is functionally complete
- automated tests are comprehensive
- startup time or memory consumption is measured as a real limitation
- all dependencies are confirmed compatible with native compilation

## 6. Architectural Style

The application shall begin as a **modular monolith**.

```text
Postman / Browser / Mobile Client
                |
          HTTPS / WebSocket
                |
       Quarkus Application
     ┌──────────────────────┐
     │ Identity             │
     │ Invitations          │
     │ Sessions             │
     │ Conversations        │
     │ Messaging            │
     │ Realtime             │
     │ Audit                │
     └──────────────────────┘
                |
      Microsoft SQL Server
```

A modular monolith is one deployable application with explicit internal module boundaries. Modules shall communicate through application-level interfaces rather than directly reaching into one another's persistence implementation.

The system shall not be split into microservices unless independent deployment, scaling, ownership, or failure isolation becomes an actual requirement.

## 7. Source Layout

The repository shall use package-by-feature organization.

```text
private-messenger/
├── .github/
│   └── workflows/
├── docs/
│   ├── architecture/
│   │   ├── decisions/
│   │   ├── diagrams/
│   │   └── threat-model.md
│   ├── api/
│   ├── database/
│   └── operations/
├── postman/
├── scripts/
├── src/
│   ├── main/
│   │   ├── java/com/example/messenger/
│   │   │   ├── bootstrap/
│   │   │   ├── common/
│   │   │   ├── identity/
│   │   │   │   ├── api/
│   │   │   │   ├── application/
│   │   │   │   ├── domain/
│   │   │   │   └── infrastructure/
│   │   │   ├── invitation/
│   │   │   ├── session/
│   │   │   ├── conversation/
│   │   │   ├── message/
│   │   │   ├── realtime/
│   │   │   └── audit/
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/
│   └── test/
│       └── java/com/example/messenger/
├── compose.yaml
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

Within a feature:

```text
message/
├── api/
│   ├── MessageResource.java
│   ├── SendMessageRequest.java
│   └── MessageResponse.java
├── application/
│   ├── SendMessageCommand.java
│   ├── SendMessageService.java
│   └── MessageQueryService.java
├── domain/
│   ├── Message.java
│   ├── MessageId.java
│   ├── MessageRepository.java
│   └── MessagePolicy.java
└── infrastructure/
    ├── JdbcMessageRepository.java
    └── MessageRowMapper.java
```

## 8. Layer Responsibilities

### API layer

The API layer shall:

- decode HTTP requests
- perform syntactic validation
- obtain the authenticated principal
- call one application use case
- translate results into response DTOs
- translate exceptions into RFC 9457 problem responses

The API layer shall not:

- contain SQL
- allocate message sequence numbers
- implement authorization policy
- manage transactions
- return JDBC `ResultSet`, driver-specific values, or persistence records directly

### Application layer

The application layer shall:

- implement use cases
- define transaction boundaries
- coordinate domain policies and repositories
- enforce authorization
- emit application events after successful state changes

### Domain layer

The domain layer shall:

- model business concepts and invariants
- contain no Quarkus, HTTP, JDBC, or database annotations
- use immutable objects wherever practical
- reject invalid state at construction time

### Infrastructure layer

The infrastructure layer shall:

- implement repository interfaces
- contain explicit parameterized T-SQL and JDBC mapping code
- integrate with SQL Server, Flyway, Agroal, telemetry, clocks, token generators, and cryptographic libraries
- map persistence records to domain objects

## 9. Core Domain Model

### 9.1 User

```text
User
- id
- username
- normalizedUsername
- passwordHash
- systemRole
- status
- createdAt
- updatedAt
```

Roles:

```text
ADMIN
USER
```

Statuses:

```text
ACTIVE
DISABLED
```

### 9.2 Invitation

```text
Invitation
- id
- tokenHash
- createdBy
- expiresAt
- redeemedAt
- redeemedBy
- revokedAt
- createdAt
```

Invitation rules:

- the raw token shall be returned only once
- only the token hash shall be stored
- an invitation shall be single-use
- a revoked, expired, or redeemed invitation shall be rejected
- invitation redemption and user creation shall occur in one transaction

### 9.3 Authentication session

```text
Session
- id
- userId
- tokenHash
- createdAt
- expiresAt
- lastSeenAt
- revokedAt
- userAgent
- sourceAddress
```

Session rules:

- session tokens shall be cryptographically random
- raw tokens shall never be persisted
- logout shall revoke the session immediately
- disabling a user shall invalidate all active sessions
- authorization shall not rely on client-supplied user identifiers

### 9.4 Conversation

```text
Conversation
- id
- type
- title
- createdBy
- nextMessageSequence
- createdAt
- updatedAt
```

Types:

```text
DIRECT
GROUP
```

### 9.5 Conversation member

```text
ConversationMember
- conversationId
- userId
- role
- joinedAt
- leftAt
- lastReadSequence
```

Roles:

```text
OWNER
ADMIN
MEMBER
```

A membership row shall be retained after a user leaves so that historical membership can be audited.

### 9.6 Message

```text
Message
- id
- conversationId
- senderId
- clientMessageId
- sequenceNumber
- type
- body
- createdAt
- editedAt
- deletedAt
```

Message types for Version 1:

```text
TEXT
SYSTEM
```

## 10. Database Architecture

Microsoft SQL Server shall be the authoritative durable store.

The development database shall run in Docker using an image pinned by the project. The exact image tag or digest shall be recorded in the repository and changed only through a reviewed dependency upgrade.

The database shall use logical schemas:

```text
platform
identity
messaging
audit
```

Suggested ownership:

```text
platform  - Flyway history and platform metadata
identity  - users, invitations, sessions
messaging - conversations, memberships, messages
audit     - security and administrative audit events
```

### 10.1 SQL Server deployment constraint

The official SQL Server Linux container is supported on Intel and AMD x86-64 hosts. Therefore:

- the complete application and database stack shall target an x86-64 workstation, server, or mini PC
- a Raspberry Pi shall not be considered a supported all-in-one SQL Server host
- a Raspberry Pi may host only the Quarkus application if SQL Server runs on a separate supported machine
- changing to an ARM-compatible database would require a separately reviewed architecture decision

### 10.2 Database principals and least privilege

The deployment model shall use separate SQL Server logins and database users:

```text
messenger_migrator
messenger_runtime
```

`messenger_migrator` shall receive only the DDL permissions needed to create and alter the project's schemas and objects.

`messenger_runtime` shall receive only the DML and execution permissions required by the application. It shall not be a member of `db_owner`, `db_ddladmin`, or `sysadmin`.

The application shall never connect as `sa`.

Local development may initially use a simplified account, but migration and runtime credentials shall be separated before any network deployment.

### 10.3 Identifier policy

Public and domain identifiers shall use Java `UUID` values stored as SQL Server `UNIQUEIDENTIFIER`.

Identifier generation shall be centralized behind application interfaces such as:

```text
UserIdGenerator
ConversationIdGenerator
MessageIdGenerator
```

The baseline shall use cryptographically strong application-generated UUIDs. A move to time-ordered identifiers shall require measurement and an ADR because SQL Server's `UNIQUEIDENTIFIER` comparison order and clustered-index behaviour must be considered explicitly.

High-write tables shall not automatically use a random `UNIQUEIDENTIFIER` primary key as the clustered index. Index clustering shall follow the dominant access path.

For `messaging.message`:

```text
PRIMARY KEY NONCLUSTERED (id)
UNIQUE CLUSTERED (conversation_id, sequence_number)
```

This aligns physical ordering with conversation history retrieval and prevents random message IDs from becoming the clustered insertion key.

### 10.4 Time policy

- persisted application timestamps shall use `DATETIME2(7)`
- every persisted application timestamp shall represent UTC
- SQL defaults shall use `SYSUTCDATETIME()`
- the application shall use `Instant` internally
- API timestamps shall use RFC 3339 / ISO-8601 UTC form
- tests shall inject a controllable `Clock`
- local server time and client-supplied timestamps shall not be authoritative

`DATETIMEOFFSET` may be used only where preserving the original external offset is itself a domain requirement.

### 10.5 Text and binary policy

- user-facing Unicode text shall use `NVARCHAR`
- bounded values shall use bounded columns such as `NVARCHAR(4000)`
- `NVARCHAR(MAX)` shall be reserved for genuinely unbounded content
- cryptographic hashes should be stored as `VARBINARY` where practical
- IP addresses shall be stored in a representation supporting IPv4 and IPv6, such as `VARCHAR(45)`, unless a stronger model is introduced

### 10.6 Constraints

Database constraints shall duplicate critical domain invariants.

Examples:

- unique normalized username
- unique `(sender_id, client_message_id)`
- unique `(conversation_id, sequence_number)`
- non-negative read sequence
- valid status values
- valid conversation membership foreign keys
- non-empty non-deleted message body
- invitation redemption fields set consistently

The application shall not rely solely on pre-insert checks for uniqueness because concurrent requests can pass the same check.

Constraint names shall be explicit and stable. Repository code may use known constraint names and SQL Server error numbers to translate expected conflicts into typed application errors.

### 10.7 Transaction and locking baseline

The baseline transaction isolation level shall be SQL Server `READ COMMITTED`.

The implementation shall:

- prefer atomic `UPDATE`, `INSERT`, and constraint-based coordination
- avoid broad table locks
- keep transactions short
- never perform remote network calls inside database transactions
- measure and log deadlock retries
- define a bounded retry policy for SQL Server deadlock victim error `1205`
- consider `READ_COMMITTED_SNAPSHOT` only through an explicit migration and ADR

No isolation-level change shall be made merely to hide incorrect concurrency logic.

## 11. Flyway Migration Standard

### 11.1 Dependencies

The project shall use:

```text
io.quarkus:quarkus-flyway
org.flywaydb:flyway-sqlserver
io.quarkus:quarkus-jdbc-mssql
```

SQL Server support is a separate Flyway database dependency and shall be present explicitly in the build.

Flyway shall be configured to use `platform` as its default schema and to keep its history table at:

```text
platform.flyway_schema_history
```

Flyway schema creation or bootstrap SQL shall ensure that the `platform` schema exists before the history table is initialized.

### 11.2 Migration directory

```text
src/main/resources/db/migration
```

### 11.3 Version naming

Use UTC timestamp versions:

```text
VYYYYMMDDHHMMSS__description_in_snake_case.sql
```

Examples:

```text
V20260625090000__create_platform_schemas.sql
V20260625090500__create_identity_user.sql
V20260625091000__create_identity_invitation.sql
V20260625091500__create_identity_session.sql
V20260625092000__create_messaging_conversation.sql
V20260625092500__create_messaging_member.sql
V20260625093000__create_messaging_message.sql
V20260625093500__create_initial_indexes.sql
```

Repeatable migrations shall use:

```text
R__description_in_snake_case.sql
```

Repeatable migrations may be used only for objects whose complete definition can safely be reapplied, such as:

- views
- stored procedures
- functions
- controlled reference-data synchronization

Repeatable migrations shall not be used as a substitute for versioned table changes.

### 11.4 Migration rules

1. Applied versioned migrations are immutable.
2. A correction shall be made through a new forward migration.
3. Each migration shall have one coherent purpose.
4. Destructive changes require an explicit data migration and recovery plan.
5. Table creation and initial indexes may be separated when that improves reviewability.
6. Production data fixes shall be versioned and idempotent where practical.
7. Migrations shall not depend on application code.
8. Migrations shall execute successfully against an empty SQL Server database.
9. Migrations shall be tested against the same SQL Server major version used by the project.
10. `flyway clean` shall be disabled outside disposable test databases.
11. `validateOnMigrate` shall be enabled.
12. `outOfOrder` shall be disabled.
13. `baselineOnMigrate` shall be disabled for this greenfield project.
14. Production migrations shall run as a deployment step before application startup.
15. Local development may run migrations automatically at startup.
16. Every schema and object name shall be explicitly schema-qualified.
17. Migration scripts shall use T-SQL accepted by the pinned SQL Server version.
18. `GO` shall be used only where Flyway's SQL Server parser requires or safely supports the batch boundary.
19. A migration shall not assume that the runtime login owns the target schema.
20. SQL Server session settings required by persisted objects shall be declared explicitly when relevant.

### 11.5 Initial schema migration example

```sql
IF SCHEMA_ID(N'platform') IS NULL
    EXEC(N'CREATE SCHEMA platform AUTHORIZATION dbo');

IF SCHEMA_ID(N'identity') IS NULL
    EXEC(N'CREATE SCHEMA identity AUTHORIZATION dbo');

IF SCHEMA_ID(N'messaging') IS NULL
    EXEC(N'CREATE SCHEMA messaging AUTHORIZATION dbo');

IF SCHEMA_ID(N'audit') IS NULL
    EXEC(N'CREATE SCHEMA audit AUTHORIZATION dbo');
```

### 11.6 Message table migration example

```sql
CREATE TABLE messaging.message (
    id UNIQUEIDENTIFIER NOT NULL,
    conversation_id UNIQUEIDENTIFIER NOT NULL,
    sender_id UNIQUEIDENTIFIER NOT NULL,
    client_message_id UNIQUEIDENTIFIER NOT NULL,
    sequence_number BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    body NVARCHAR(4000) NULL,
    created_at DATETIME2(7) NOT NULL
        CONSTRAINT df_message_created_at DEFAULT SYSUTCDATETIME(),
    edited_at DATETIME2(7) NULL,
    deleted_at DATETIME2(7) NULL,

    CONSTRAINT pk_message
        PRIMARY KEY NONCLUSTERED (id),

    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES messaging.conversation(id),

    CONSTRAINT fk_message_sender_membership
        FOREIGN KEY (conversation_id, sender_id)
        REFERENCES messaging.conversation_member(conversation_id, user_id),

    CONSTRAINT uq_message_client_id
        UNIQUE NONCLUSTERED (sender_id, client_message_id),

    CONSTRAINT uq_message_sequence
        UNIQUE CLUSTERED (conversation_id, sequence_number),

    CONSTRAINT ck_message_sequence_positive
        CHECK (sequence_number > 0),

    CONSTRAINT ck_message_type
        CHECK (message_type IN ('TEXT', 'SYSTEM')),

    CONSTRAINT ck_message_body
        CHECK (
            deleted_at IS NOT NULL
            OR (
                body IS NOT NULL
                AND LEN(TRIM(body)) BETWEEN 1 AND 4000
            )
        )
);
```

### 11.7 SQL Server migration review checklist

Every migration review shall consider:

- lock duration on existing tables
- transaction-log growth
- index build cost
- whether an operation is metadata-only or rewrites data
- default constraints and their explicit names
- nullable-to-not-null conversion strategy
- data backfill batching
- online-operation edition requirements
- rollback through restore or a forward corrective migration
- compatibility with the pinned SQL Server edition and version

## 12. JDBC Persistence Standard

The baseline implementation shall use explicit JDBC repositories through the Quarkus-managed Agroal datasource.

jOOQ shall not be required by the project because SQL Server support belongs to jOOQ's commercial distributions. It may be adopted later through a separate ADR if an appropriate licence is available and the benefit justifies the additional dependency.

### 12.1 Repository responsibilities

JDBC repositories shall:

- expose domain-oriented operations rather than generic CRUD
- use prepared statements for every parameter
- use explicit selected column lists
- map result sets into domain objects inside infrastructure code
- close statements and result sets using try-with-resources
- obtain connections from the injected Agroal datasource
- participate in the application service transaction
- avoid manual `commit`, `rollback`, or autocommit changes on container-managed transactions
- translate expected SQL Server conflicts into typed persistence exceptions
- never expose `Connection`, `PreparedStatement`, `ResultSet`, or driver types outside infrastructure code

### 12.2 SQL organization

Short, single-purpose statements may remain as Java text blocks beside the repository operation.

Longer statements shall be stored in dedicated infrastructure classes or classpath SQL resources when doing so improves readability and review.

SQL shall always be:

- schema-qualified
- parameterized
- formatted
- covered by SQL Server integration tests
- reviewed for indexes and expected execution shape

`SELECT *` is prohibited in application repositories.

### 12.3 Mapping standard

Each aggregate or result shape shall have an explicit mapper.

```java
final class MessageRowMapper {

    Message map(ResultSet resultSet) throws SQLException {
        return Message.rehydrate(
                new MessageId(resultSet.getObject("id", UUID.class)),
                new ConversationId(
                        resultSet.getObject("conversation_id", UUID.class)),
                new UserId(resultSet.getObject("sender_id", UUID.class)),
                new ClientMessageId(
                        resultSet.getObject("client_message_id", UUID.class)),
                resultSet.getLong("sequence_number"),
                MessageType.valueOf(resultSet.getString("message_type")),
                resultSet.getString("body"),
                resultSet.getTimestamp("created_at")
                        .toLocalDateTime()
                        .toInstant(ZoneOffset.UTC),
                nullableInstant(resultSet, "edited_at"),
                nullableInstant(resultSet, "deleted_at"));
    }
}
```

Time conversion shall be centralized and tested so UTC handling does not vary between repositories.

### 12.4 SQL Server error translation

Repository infrastructure shall recognize at minimum:

```text
2601  duplicate key on a unique index
2627  violation of a UNIQUE constraint or primary key
1205  transaction chosen as deadlock victim
547   foreign-key or check-constraint violation
```

Application behaviour shall not depend only on brittle parsing of localized driver messages. Known constraint names and SQL Server error numbers shall be used where available.

### 12.5 Test database rule

Repository, migration, and API integration tests shall use a real disposable SQL Server container. H2, Derby, or an in-memory compatibility mode shall not substitute for SQL Server because T-SQL, locking, types, error codes, and index behaviour differ materially.

## 13. API Standard

### 13.1 Base path

```text
/api/v1
```

### 13.2 Media types

```text
application/json
application/problem+json
```

### 13.3 API groups

```text
/api/v1/auth
/api/v1/invitations
/api/v1/users
/api/v1/conversations
/api/v1/conversations/{conversationId}/members
/api/v1/conversations/{conversationId}/messages
/api/v1/conversations/{conversationId}/read-position
```

### 13.4 Request rules

- unknown JSON properties should be rejected
- request bodies shall have explicit maximum sizes
- syntactic validation shall occur at the API boundary
- identifiers shall be parsed before reaching application services
- authenticated user identity shall come from the session
- clients shall not supply server timestamps, sender identity, roles, or sequence numbers

### 13.5 Response rules

- resource DTOs shall be immutable Java records
- response fields shall be stable within an API version
- null and absent fields shall have documented semantics
- list endpoints shall use cursor pagination
- internal database or exception details shall never be returned

### 13.6 Error format

Errors shall use RFC 9457 Problem Details.

```json
{
  "type": "https://messenger.local/problems/conversation-access-denied",
  "title": "Conversation access denied",
  "status": 404,
  "detail": "The conversation does not exist or is not accessible.",
  "instance": "/api/v1/conversations/...",
  "code": "CONVERSATION_ACCESS_DENIED",
  "traceId": "8ac4e1e41b0e4c86"
}
```

The `detail` field shall be safe for clients and shall not expose stack traces, SQL, table names, token values, or security-sensitive state.

### 13.7 Pagination

Message history shall use a sequence cursor.

```http
GET /api/v1/conversations/{id}/messages?afterSequence=120&limit=50
```

Offset-based pagination shall not be used for message synchronization.

## 14. Authentication Specification

### 14.1 Passwords

Passwords shall be hashed using Argon2id through a maintained cryptographic library.

Password hashing parameters shall be configurable and recorded alongside the hash format so that hashes can be upgraded on successful login.

The system shall never:

- store plaintext passwords
- log passwords
- return password hashes
- use plain SHA-256 or another general-purpose digest as password storage
- expose whether a username exists through materially different login responses

### 14.2 Session tokens

Session tokens shall:

- contain at least 256 bits of cryptographically secure randomness
- be encoded using a URL-safe representation
- be returned only at creation
- be stored only as a secure hash
- have an expiry
- be revocable
- be associated with a user
- support revoking all sessions for one user

For browser clients, the preferred final transport is a `Secure`, `HttpOnly`, appropriately configured cookie. For Postman and early development, a bearer token may be used.

### 14.3 Bootstrap administrator

The first administrator shall be created through one controlled bootstrap mechanism, such as:

- a one-time command-line operation, or
- a startup secret that is accepted only when no users exist

The application shall never expose a permanent unauthenticated “create admin” endpoint.

## 15. Authorization Specification

Every protected operation shall verify authorization server-side.

Examples:

| Operation                  | Required authorization             |
| -------------------------- | ---------------------------------- |
| Create invitation          | System administrator               |
| Create direct conversation | Active user                        |
| Create group               | Active user                        |
| Add group member           | Conversation owner/admin           |
| Remove group member        | Conversation owner/admin           |
| Read history               | Active conversation member         |
| Send message               | Active conversation member         |
| Edit message               | Original sender, subject to policy |
| Delete message             | Original sender or permitted admin |
| Advance read position      | The authenticated member only      |

An inaccessible conversation should normally return `404` rather than confirming the existence of a private resource.

## 16. Message Send Semantics

A send request shall contain:

```json
{
  "clientMessageId": "aa96bf40-1a96-449d-a7a6-997eb72ef403",
  "body": "Hello"
}
```

The server shall derive:

- sender
- creation time
- conversation sequence
- server message identifier
- authorization decision

### 16.1 Transaction algorithm

```text
1. Authenticate request.
2. Validate request syntax.
3. Begin transaction.
4. Verify active membership.
5. Query by sender ID and client message ID.
6. If found, return existing message.
7. Atomically increment conversation next-message sequence.
8. Insert message with allocated sequence.
9. Insert required audit metadata.
10. Commit transaction.
11. Publish an in-process committed-message event.
12. Return the persisted message.
```

### 16.2 Sequence allocation

```sql
UPDATE messaging.conversation
SET
    next_message_sequence = next_message_sequence + 1,
    updated_at = SYSUTCDATETIME()
OUTPUT INSERTED.next_message_sequence - 1
WHERE id = ?;
```

The update serializes sequence allocation per conversation without globally locking message creation.

### 16.3 Idempotency

The unique constraint below shall be authoritative:

```text
UNIQUE(sender_id, client_message_id)
```

If two identical requests race, one insert may lose the unique constraint race. The service shall then read and return the already-created message rather than return an internal error.

## 17. Read Position Semantics

Read state shall be stored per conversation member.

```text
last_read_sequence
```

The update shall be monotonic:

```sql
UPDATE messaging.conversation_member
SET last_read_sequence =
    CASE
        WHEN last_read_sequence < ? THEN ?
        ELSE last_read_sequence
    END
WHERE conversation_id = ?
  AND user_id = ?
  AND left_at IS NULL;
```

The server shall reject a read sequence beyond the latest known message sequence.

Opening a UI window alone does not define read state. A future client shall update the read position only after messages are visibly presented to the user.

## 18. Message Editing and Deletion

### 18.1 Edit

Version 1 policy:

- only the original sender may edit
- deleted messages may not be edited
- system messages may not be edited
- edit updates `body` and `edited_at`
- the original body is not retained in Version 1

An edit-history table may be added later.

### 18.2 Delete for everyone

Version 1 deletion shall be a soft delete:

```text
deleted_at = current time
body = null
```

The message row and sequence position shall remain so that ordering and references are preserved.

### 18.3 Delete for self

Delete-for-self is out of scope for Version 1. It requires a per-user visibility record and is distinct from deleting a shared message.

## 19. Real-time Delivery

REST and SQL Server shall remain authoritative.

WebSocket delivery shall be added only after REST functionality is complete.

### 19.1 WebSocket responsibilities

WebSocket shall:

- notify connected users of committed changes
- carry message-created, message-edited, message-deleted, and read-position events
- authenticate the connection
- enforce authorization for subscriptions
- support heartbeat and disconnect detection

WebSocket shall not:

- replace database persistence
- become the only path for message retrieval
- be trusted as proof that a client has persisted or read a message
- make missed events unrecoverable

### 19.2 Reconnection

After reconnecting, the client shall call the REST history endpoint using its last received sequence. This provides deterministic recovery from missed WebSocket events.

## 20. Audit Model

Audit events shall be distinct from general application logs.

Minimum security audit events:

- administrator bootstrap
- invitation created
- invitation revoked
- invitation redeemed
- login succeeded
- login failed
- logout
- session revoked
- user disabled
- user enabled
- member added
- member removed
- authorization denied
- message administratively deleted

Audit records shall include:

```text
audit_event_id
event_type
actor_user_id
target_type
target_id
occurred_at
trace_id
source_address
metadata
```

Private message bodies, raw passwords, raw session tokens, and raw invitation tokens shall not be written to audit records.

## 21. Observability

### 21.1 Logs

Logs shall be structured JSON outside local developer mode.

Every request shall include or receive:

```text
traceId
requestId
```

Log fields should include:

```text
timestamp
level
service
event
traceId
requestId
userId when safe
conversationId when relevant
durationMs
outcome
```

Message bodies shall not be logged by default.

### 21.2 Metrics

Initial metrics:

- HTTP request count
- HTTP request duration
- active sessions
- login failures
- messages created
- message-send duration
- authorization failures
- active WebSocket connections
- database pool usage
- Flyway migration status

### 21.3 Health

Expose separate health concepts:

- liveness: process is functioning
- readiness: database and required dependencies are usable
- startup: initialization and migrations have completed

## 22. Testing Strategy

### 22.1 Test layers

#### Unit tests

Test:

- domain value objects
- policies
- command validation
- authorization decisions
- token and time boundary logic

Unit tests shall not start Quarkus or SQL Server unless required.

#### Repository integration tests

Use a real Microsoft SQL Server Testcontainer.

Test:

- SQL mapping
- constraints
- transaction behavior
- concurrency behavior
- sequence allocation
- idempotency
- cursor queries

#### API integration tests

Start the Quarkus application against a disposable SQL Server container.

Test:

- status codes
- validation
- authentication
- authorization
- RFC 9457 errors
- serialization
- OpenAPI consistency

#### Migration tests

CI shall:

1. start an empty SQL Server container
2. run Flyway validation
3. run all migrations
4. verify expected schemas and critical constraints
5. optionally migrate a seeded prior-version database to current

#### Security negative tests

Every protected endpoint shall have tests for:

- missing token
- invalid token
- expired token
- revoked token
- disabled user
- non-member
- removed member
- wrong role
- malformed identifier
- oversized request
- duplicate request

#### Concurrency tests

At minimum:

- concurrent sends to one conversation produce unique contiguous sequences
- concurrent duplicate requests produce one logical message
- concurrent read updates never move backwards
- invitation redemption succeeds once
- username registration succeeds once

### 22.2 Test data

Tests shall create their own data and shall not depend on execution order.

Random values shall be reproducible when a seed is useful.

Tests shall not share one mutable database state across unrelated test classes.

## 23. Performance Engineering

Performance shall be measured, not assumed.

The project shall establish a reproducible benchmark profile containing:

- hardware description
- JVM version and options
- Quarkus version
- SQL Server edition, version, cumulative update, and configuration
- database location
- number of users
- number of conversations
- message size distribution
- concurrent client count
- warm-up period
- test duration

Primary scenarios:

1. login
2. list conversations
3. fetch message history
4. send message to one conversation
5. concurrent message sends
6. WebSocket fan-out

Track:

- throughput
- p50 latency
- p95 latency
- p99 latency
- error rate
- CPU
- memory
- garbage collection
- database connections
- database lock waits

No optimization shall be merged without an observed bottleneck and a before/after measurement.

## 24. Deployment Model

### 24.1 Local development

```text
Quarkus dev mode on host
SQL Server in Docker Compose
Optional telemetry stack in Docker Compose
```

### 24.2 Full local container deployment

```text
reverse proxy
application container
SQL Server container
persistent database volume
```

### 24.3 Home-server deployment

Recommended final topology:

```text
Home LAN / VPN
      |
Caddy or equivalent TLS reverse proxy
      |
Quarkus application
      |
SQL Server on a supported x86-64 Linux host
```

The complete all-in-one deployment host shall be x86-64. A Raspberry Pi is not a supported SQL Server container host. An ARM device may host only the application tier while connecting to SQL Server on a separate supported x86-64 machine.

The service shall not be exposed directly to the public Internet until:

- TLS is configured
- administrative bootstrap is closed
- rate limits exist
- backup and restore are tested
- default credentials are absent
- security headers and CORS policy are configured
- dependency scanning is enabled
- logs are reviewed for secret leakage

## 25. Backup and Recovery

Before remote use, the project shall define:

- scheduled full SQL Server backups
- differential and transaction-log backup policy if point-in-time recovery is required
- recovery model selection
- backup retention
- encrypted backup storage
- `RESTORE VERIFYONLY` as a preliminary check
- full restore testing into a separate SQL Server instance
- recovery-time and recovery-point expectations
- cumulative-update and major-version upgrade procedures

A backup that has never been restored in a test shall not be considered a valid recovery strategy.

---

# Part II — Development Plan

## Milestone 0 — Repository and standards

Deliver:

- Git repository
- Maven Wrapper
- Quarkus skeleton
- coding formatter
- static analysis
- CI build
- architecture decision record template
- Docker Compose SQL Server
- `/q/health` or equivalent health endpoints
- initial README

Exit criteria:

- clean checkout builds
- tests run
- application starts
- database is reachable
- CI passes

## Milestone 1 — Database foundation

Deliver:

- Flyway integration
- logical schemas
- migration naming rules
- migration validation
- SQL Server Testcontainer migration harness
- initial database roles documentation

Exit criteria:

- an empty SQL Server database migrates to the latest version
- the application can connect through the runtime datasource after migration
- migration and schema-verification tests run in CI
- Flyway history is inspectable in `platform.flyway_schema_history`
- the runtime database principal cannot perform unauthorized DDL

## Milestone 2 — Identity and invitations

Deliver:

- administrator bootstrap
- user table
- invitation table
- Argon2id password hashing
- invitation create, revoke, and redeem
- normalized username policy
- security audit events

Exit criteria:

- public registration is impossible
- invitation is single-use
- concurrent redemption succeeds once
- duplicate username succeeds once
- raw token values are not stored

## Milestone 3 — Sessions

Deliver:

- login
- opaque session token creation
- authenticated request filter
- logout
- session expiry
- user disable and revoke-all-sessions

Exit criteria:

- invalid, expired, and revoked tokens fail
- disabled users cannot continue using existing sessions
- no raw token appears in logs or database

## Milestone 4 — Conversations

Deliver:

- direct conversation creation
- group creation
- conversation listing
- conversation details
- member add/remove
- owner/admin/member roles
- authorization policy tests

Exit criteria:

- non-members cannot enumerate or inspect conversations
- removed members lose access
- role transitions are validated
- direct-conversation duplication policy is defined and tested

## Milestone 5 — Messaging

Deliver:

- send text message
- sequence allocation
- idempotency
- message history
- edit
- soft delete
- message audit metadata
- concurrency tests

Exit criteria:

- repeated `clientMessageId` produces one message
- concurrent sends produce unique ordered sequences
- history pagination is deterministic
- unauthorized send and read operations fail

## Milestone 6 — Read state

Deliver:

- advance read position
- unread-count query
- member read positions where permitted
- monotonic update enforcement

Exit criteria:

- old requests cannot move read position backwards
- read position cannot exceed latest sequence
- unread calculation is covered by tests

## Milestone 7 — API hardening

Deliver:

- RFC 9457 problem mapper
- OpenAPI generation
- request-size limits
- pagination limits
- rate limits for authentication
- structured JSON logs
- trace IDs
- Postman collection and environment

Exit criteria:

- every endpoint is represented in OpenAPI
- every endpoint has positive and negative Postman tests
- no internal exceptions leak through responses

## Milestone 8 — WebSockets

Deliver:

- authenticated WebSocket connection
- connection registry
- committed message events
- edit/delete events
- read-position events
- heartbeat
- reconnect synchronization test

Exit criteria:

- disconnecting does not lose durable data
- missed events are recovered through REST
- non-members cannot subscribe to conversation events
- expired sessions cause connection closure or reauthentication

## Milestone 9 — Operational hardening

Deliver:

- container image
- reverse proxy configuration
- TLS
- runtime database role
- automated backup script
- restore procedure
- dependency scanning
- SBOM
- basic load test
- threat model review

Exit criteria:

- fresh server deployment is documented
- backup restoration is demonstrated
- security checklist passes
- load-test baseline is recorded

---

# Part III — Coding Guide

## 26. General Java Rules

1. Use Java records for immutable commands and DTOs.
2. Use final fields by default.
3. Do not return `null` collections.
4. Do not use `Optional` for fields or request DTO members.
5. Do not catch `Exception` unless translating at a system boundary.
6. Do not throw raw persistence exceptions from application services.
7. Inject `Clock`, token generators, and ID generators.
8. Keep methods focused on one abstraction level.
9. Prefer explicit names over abbreviations.
10. Avoid static mutable state.
11. Avoid framework annotations in domain objects.
12. Treat compiler warnings as build failures where practical.

## 27. Naming

Examples:

```text
SendMessageRequest       HTTP input
SendMessageCommand       application input
SendMessageService       use-case implementation
Message                  domain object
MessageRepository        domain-facing persistence contract
JdbcMessageRepository    infrastructure implementation
MessageResponse          HTTP output
```

Do not use vague names such as:

```text
Manager
Helper
Util
Processor
CommonService
DataObject
```

unless the class has a precise, defensible responsibility.

## 28. API Resource Example

```java
@Path("/api/v1/conversations/{conversationId}/messages")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class MessageResource {

    private final SendMessageService sendMessageService;
    private final CurrentUser currentUser;

    public MessageResource(
            SendMessageService sendMessageService,
            CurrentUser currentUser) {
        this.sendMessageService = sendMessageService;
        this.currentUser = currentUser;
    }

    @POST
    public Response send(
            @PathParam("conversationId") UUID conversationId,
            @Valid SendMessageRequest request) {

        var command = new SendMessageCommand(
                currentUser.requireUserId(),
                new ConversationId(conversationId),
                new ClientMessageId(request.clientMessageId()),
                request.body());

        Message message = sendMessageService.execute(command);

        return Response
                .status(Response.Status.CREATED)
                .entity(MessageResponse.from(message))
                .build();
    }
}
```

The resource maps transport input to an application command. It does not perform SQL, sequence allocation, or membership authorization.

## 29. Application Service Example

```java
@ApplicationScoped
public final class SendMessageService {

    private final ConversationMembershipRepository memberships;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final Clock clock;
    private final MessageIdGenerator idGenerator;

    public SendMessageService(
            ConversationMembershipRepository memberships,
            ConversationRepository conversations,
            MessageRepository messages,
            Clock clock,
            MessageIdGenerator idGenerator) {
        this.memberships = memberships;
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public Message execute(SendMessageCommand command) {
        return messages
                .findBySenderAndClientMessageId(
                        command.senderId(),
                        command.clientMessageId())
                .orElseGet(() -> createMessage(command));
    }

    private Message createMessage(SendMessageCommand command) {
        memberships.requireActiveMembership(
                command.conversationId(),
                command.senderId());

        long sequence =
                conversations.allocateNextMessageSequence(
                        command.conversationId());

        Message message = Message.createText(
                idGenerator.next(),
                command.conversationId(),
                command.senderId(),
                command.clientMessageId(),
                sequence,
                command.body(),
                clock.instant());

        try {
            messages.insert(message);
            return message;
        } catch (DuplicateClientMessageException duplicate) {
            return messages.requireBySenderAndClientMessageId(
                    command.senderId(),
                    command.clientMessageId());
        }
    }
}
```

The database uniqueness constraint remains the final arbiter when concurrent requests race.

## 30. Repository Rules

Repositories shall:

- represent domain operations, not table CRUD
- return domain objects
- use explicit transactions controlled by application services
- use explicit selected columns
- map known database constraint names to typed application exceptions
- avoid hidden N+1 queries
- expose pagination explicitly
- avoid generic `save(Object)` interfaces

Good:

```java
long allocateNextMessageSequence(ConversationId conversationId);
Optional<Message> findBySenderAndClientMessageId(
        UserId senderId,
        ClientMessageId clientMessageId);
```

Avoid:

```java
Object save(Object value);
List<Object> findAll();
```

## 31. Transaction Rules

1. One application use case should normally own one transaction.
2. Network calls shall not occur inside database transactions.
3. WebSocket publication shall occur only after commit.
4. Read-modify-write operations shall be protected through atomic SQL, locking, or constraints.
5. Transaction isolation assumptions shall be documented for concurrency-sensitive code.
6. Transaction retries shall be bounded and observable.
7. Authorization checks and mutations that depend on them should occur in the same transaction when race conditions matter.

## 32. Exception Rules

Define typed application exceptions:

```text
AuthenticationRequiredException
InvalidSessionException
ConversationNotFoundException
ConversationAccessDeniedException
DuplicateUsernameException
InvitationExpiredException
InvitationAlreadyRedeemedException
MessageNotFoundException
MessageEditNotAllowedException
```

The API exception mapper shall convert these to stable problem codes.

Do not expose:

- SQL Server error numbers, SQLState values, or driver diagnostics
- constraint implementation details
- stack traces
- internal class names
- raw exception messages from libraries

## 33. Logging Rules

Good:

```java
log.infof(
    "event=message_created conversationId=%s messageId=%s sequence=%d",
    conversationId,
    messageId,
    sequence);
```

Bad:

```java
log.info("User sent message: " + body);
log.debug("Session token: " + token);
```

Do not log private message contents by default.

## 34. Configuration Rules

Configuration shall be separated by profile:

```text
application.properties
%dev
%test
%prod
```

Secrets shall come from environment variables, mounted secret files, or a secret manager. Secrets shall not be committed to Git.

Every configuration property shall have:

- a clear name
- documented purpose
- safe default where possible
- validation at startup

## 35. API Documentation Rules

OpenAPI shall be treated as a build artifact and reviewed contract.

Each endpoint shall document:

- purpose
- authorization requirement
- request schema
- response schema
- status codes
- problem codes
- pagination behavior
- idempotency behavior

The generated OpenAPI document should be stored or diffed in CI so accidental contract changes are visible.

## 36. Definition of Done

A feature is complete only when:

- requirements are documented
- authorization rules are explicit
- database migrations exist
- migration from an empty database passes
- implementation is complete
- unit tests pass
- repository integration tests pass
- API positive tests pass
- API negative/security tests pass
- OpenAPI is updated
- Postman collection is updated
- logs and metrics are considered
- no secret or message body is unintentionally logged
- code format and static analysis pass
- architecture documentation is updated when boundaries change

## 37. Architecture Decision Records

Significant decisions shall receive an ADR.

Initial ADRs:

```text
ADR-0001 Use a modular monolith
ADR-0002 Use Java 25 and Quarkus LTS
ADR-0003 Use Microsoft SQL Server as the durable source of truth
ADR-0004 Use Flyway migrations as the schema source of truth
ADR-0005 Use explicit JDBC repositories instead of an ORM
ADR-0006 Use opaque revocable sessions instead of JWT access tokens
ADR-0007 Use per-conversation sequence numbers
ADR-0008 Use client message IDs for idempotency
ADR-0009 Keep WebSocket delivery non-authoritative
ADR-0010 Defer end-to-end encryption
ADR-0011 Require x86-64 for an all-in-one SQL Server deployment
ADR-0012 Keep jOOQ optional because SQL Server support is commercial
```

ADR template:

```text
Title
Status
Date
Context
Decision
Alternatives considered
Consequences
Security impact
Operational impact
Revisit conditions
```

## 38. Immediate First Sprint

The first implementation sprint shall contain only:

1. repository initialization
2. Maven Wrapper
3. Quarkus 3.33 LTS on Java 25
4. Docker Compose SQL Server using a pinned project image
5. Microsoft SQL Server JDBC extension and Agroal datasource
6. SQL Server readiness health verification
7. health endpoint tests
8. coding formatter
9. static analysis
10. RFC 9457 error skeleton
11. CI build
12. README startup instructions
13. ADR-0001 through ADR-0005

No user, session, conversation, or message endpoint should be implemented until this foundation is stable.

---

# References

- OpenJDK 25 project: https://openjdk.org/projects/jdk/25/
- Quarkus releases and LTS policy: https://quarkus.io/releases/
- Quarkus Java 25 support: https://quarkus.io/blog/quarkus-3-31-released/
- Quarkus REST: https://quarkus.io/guides/rest
- Quarkus virtual threads: https://quarkus.io/guides/virtual-threads
- Quarkus Flyway guide: https://quarkus.io/guides/flyway
- Quarkus WebSockets Next: https://quarkus.io/guides/websockets-next-reference
- Quarkus OpenAPI: https://quarkus.io/guides/openapi-swaggerui
- Flyway versioned migrations: https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations
- Flyway repeatable migrations: https://documentation.red-gate.com/fd/repeatable-migrations-273973335.html
- Quarkus Microsoft SQL Server JDBC extension: https://quarkus.io/extensions/io.quarkus/quarkus-jdbc-mssql/
- Quarkus datasource guide: https://quarkus.io/guides/datasource
- SQL Server Linux container quickstart: https://learn.microsoft.com/sql/linux/quickstart-install-connect-docker
- SQL Server Linux container configuration: https://learn.microsoft.com/sql/linux/sql-server-linux-docker-container-configure
- Flyway SQL Server database support: https://documentation.red-gate.com/fd/sql-server-database-277579330.html
- Testcontainers SQL Server module: https://java.testcontainers.org/modules/databases/mssqlserver/
- Microsoft JDBC Driver documentation: https://learn.microsoft.com/sql/connect/jdbc/microsoft-jdbc-driver-for-sql-server
- jOOQ editions and database support: https://www.jooq.org/download/
- RFC 9457 Problem Details: https://www.rfc-editor.org/rfc/rfc9457.html
