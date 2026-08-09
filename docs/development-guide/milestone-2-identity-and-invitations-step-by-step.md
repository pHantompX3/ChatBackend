# Milestone 2 Implementation Guide

## Identity and Invitations

**Project:** Private Messenger  
**Milestone:** 2 - Identity and invitations  
**Database:** Microsoft SQL Server 2022  
**Application stack:** Java 25, Quarkus 3.33 LTS, Maven  
**Status:** Completed and verified in CI  
**Last reviewed:** 2026-08-08

---

## 0. Purpose and Scope

Milestone 2 introduces the first real domain capability: controlled identity onboarding through administrator-issued invitations.

This milestone is complete only when the project can:

1. bootstrap an initial administrator safely,
2. create, revoke, and redeem invitations,
3. enforce single-use invitation redemption under concurrency,
4. enforce normalized-username uniqueness under concurrency,
5. hash passwords with Argon2id,
6. emit security audit events without leaking sensitive values.

Milestone 2 does not include session token lifecycle (Milestone 3), conversations (Milestone 4), or messaging behavior (Milestone 5).

Current implementation status:

- The milestone 2 feature set is implemented and verified in CI.
- Quarkus now starts successfully even when the RabbitMQ-backed audit transport is not configured with a password; the dispatcher falls back to local async persistence instead of aborting startup.
- The GitHub Actions workflow validates startup, Flyway migration, and health/integration test behavior end to end.

Additional milestone 2 implementation notes:

- Invitation creation and redemption now validate request payloads explicitly and return consistent problem+json responses for bad input and domain rule violations.
- Identity validation failures are mapped to user-facing 400-style responses instead of falling through to generic server errors.
- The HTTP audit pipeline now supports a resilient dual path: events are persisted locally in async mode and can also be forwarded to RabbitMQ when the broker is available, with background retry behavior that does not block startup.
- Local development configuration now enables the RabbitMQ-backed audit path by default in devdocker while preserving fail-open behavior if the queue is unavailable.

Milestone 3 handoff note:

- When session/token authentication is introduced, actor identity for audit events must move out of service-layer request payload handling and into the authentication filter chain.
- The auth filter that resolves the authenticated token/session to a user must populate authoritative actor audit context fields (`actorUserId`, `actorUsername`, `actorAuthType`) for downstream audit emission.
- Service-layer manual actor audit writes added in Milestone 2 are transitional and should be reduced or removed in Milestone 3 once authenticated actor identity is available from the request/auth context.
- Failure classification that can be derived from operation + HTTP status + mapped problem code should remain centralized in HTTP/filter-layer audit assembly rather than repeated across services.

---

## 1. Deliverables and Exit Criteria

Milestone 2 deliverables from the platform spec:

- administrator bootstrap
- user table
- invitation table
- Argon2id password hashing
- invitation create, revoke, and redeem
- normalized username policy
- security audit events

Milestone 2 exit criteria from the platform spec:

- public registration is impossible
- invitation is single-use
- concurrent redemption succeeds once
- duplicate username succeeds once
- raw token values are not stored

---

## 2. Current Baseline and Gap Map

### 2.1 Baseline already present

The repository already contains:

- SQL Server bootstrap and migration scripts
- logical schemas (`platform`, `identity`, `messaging`, `audit`)
- migration harness and schema verification tests
- Quarkus HTTP baseline and shared request/audit support

### 2.2 Gaps Milestone 2 must close

Milestone 2 must add:

1. identity domain model and application services,
2. SQL tables and constraints for users and invitations,
3. secure hashing/token abstractions,
4. admin bootstrap flow with one-time guard,
5. invitation API endpoints and problem mappings,
6. integration tests for race conditions and negative auth cases.

---

## 3. Prerequisites

Before implementing Milestone 2, verify foundation behavior:

```bash
java --version
./mvnw --version
./scripts/database/validate-flyway-naming.sh
./mvnw --batch-mode --no-transfer-progress clean verify
```

Prepare local secrets if needed:

```bash
cp -n scripts/config/local.secrets.env.example scripts/config/local.secrets.env
```

For queue-enabled runs, define the audit transport settings in local secrets when you want RabbitMQ-backed delivery:

```dotenv
WL_CHAT_AUDIT_RABBITMQ_ENABLED=true
WL_CHAT_AUDIT_RABBITMQ_HOST=queue-dev
WL_CHAT_AUDIT_RABBITMQ_PORT=5672
WL_CHAT_AUDIT_RABBITMQ_USERNAME=wl_chat_queue
WL_CHAT_AUDIT_RABBITMQ_PASSWORD=<strong-password>
```

If the password is omitted or blank, the application still starts and falls back to local async persistence instead of failing boot.

Work only on a feature branch. Do not push directly to `main`.

---

## 4. Milestone 2 Design Decisions

Apply these decisions consistently in code, SQL, and tests.

1. **No public sign-up endpoint**
   - User creation occurs only through invitation redemption and bootstrap admin creation.

2. **Username normalization policy**
   - Canonical normalized username is lowercase and trimmed.
   - Uniqueness constraint is enforced on normalized value, not display casing.

3. **Token handling policy**
   - Invitation token raw value is returned once at create time.
   - Persist only hash bytes (never raw token).

4. **Password policy**
   - Passwords are hashed with Argon2id.
   - Hash algorithm metadata is embedded in hash output format for future upgrades.

5. **Race-condition authority**
   - SQL constraints and atomic updates are final arbiters.
   - Service logic translates expected conflicts into typed application exceptions.

---

## 5. Step 1 - Add Identity Schema Migrations

Create new immutable Flyway migrations under:

```text
scripts/database/flyway/wl_chat
```

Use timestamp naming:

```text
VYYYYMMDDHHMMSS__description_in_snake_case.sql
```

Recommended migration sequence:

1. `V20260808120000__create_identity_user_account.sql`
2. `V20260808120500__create_identity_invitation.sql`
3. `V20260808121000__create_audit_security_event.sql`

### 5.1 User table requirements

Create an identity user table with at least:

- UUID primary key (`UNIQUEIDENTIFIER`)
- `username` and `normalized_username`
- `password_hash`
- `system_role` (`ADMIN`, `USER`)
- `status` (`ACTIVE`, `DISABLED`)
- `created_at`, `updated_at` (`DATETIME2(7)` UTC)

Required constraints:

- unique normalized username
- valid system role values
- valid status values
- non-empty username

### 5.2 Invitation table requirements

Create invitation table with at least:

- UUID primary key
- token hash (`VARBINARY` preferred)
- `created_by` FK to identity user
- `expires_at`, `redeemed_at`, `revoked_at`, `created_at`
- `redeemed_by` nullable FK to identity user

Required constraints:

- token hash uniqueness
- coherent redemption state (for example, `redeemed_at` and `redeemed_by` set together)
- coherent revoke/redeem state (define precedence and enforce it)

### 5.3 Audit table requirements

Add baseline audit table for security events in `audit` schema with:

- immutable event id
- event type
- actor user id nullable for pre-auth events
- target type/id
- timestamp
- trace id and source address
- structured metadata payload

Do not persist raw passwords, raw invitation tokens, or full private message bodies.

### 5.4 Exact SQL definition for Milestone 2 audit table

Use this as the baseline DDL for the audit migration file (`V20260808121000__create_audit_security_event.sql`).

```sql
IF SCHEMA_ID(N'audit') IS NULL
  EXEC(N'CREATE SCHEMA audit AUTHORIZATION dbo');

CREATE TABLE audit.http_audit_event (
  event_id UNIQUEIDENTIFIER NOT NULL,
  schema_version NVARCHAR(16) NOT NULL,
  event_type NVARCHAR(120) NOT NULL,
  occurred_at DATETIME2(7) NOT NULL,

  request_id NVARCHAR(80) NOT NULL,
  trace_id NVARCHAR(80) NULL,
  operation NVARCHAR(200) NOT NULL,
  method VARCHAR(12) NOT NULL,
  route_template NVARCHAR(300) NULL,
  path NVARCHAR(2048) NOT NULL,
  query NVARCHAR(2048) NULL,

  response_status INT NOT NULL,
  response_code NVARCHAR(120) NULL,
  duration_ms BIGINT NOT NULL,
  request_timestamp DATETIME2(7) NOT NULL,
  response_timestamp DATETIME2(7) NOT NULL,

  actor_user_id UNIQUEIDENTIFIER NULL,
  actor_username NVARCHAR(160) NULL,
  actor_auth_type NVARCHAR(40) NULL,

  target_type NVARCHAR(120) NULL,
  target_id NVARCHAR(120) NULL,

  source_ip VARCHAR(45) NULL,
  remote_ip VARCHAR(45) NULL,
  ip_resolution_source NVARCHAR(40) NULL,

  user_agent NVARCHAR(1024) NULL,
  device_type NVARCHAR(40) NULL,
  device_platform NVARCHAR(80) NULL,
  device_model NVARCHAR(120) NULL,
  os_family NVARCHAR(80) NULL,
  browser_family NVARCHAR(80) NULL,

  request_headers NVARCHAR(MAX) NULL,
  response_headers NVARCHAR(MAX) NULL,

  error_code NVARCHAR(120) NULL,
  error_message NVARCHAR(256) NULL,

  metadata NVARCHAR(MAX) NULL,
  record_hash VARBINARY(32) NOT NULL,

  created_at DATETIME2(7) NOT NULL
    CONSTRAINT df_http_audit_event_created_at DEFAULT SYSUTCDATETIME(),

  CONSTRAINT pk_http_audit_event
    PRIMARY KEY NONCLUSTERED (event_id),

  CONSTRAINT ck_http_audit_event_schema_version
    CHECK (LEN(TRIM(schema_version)) > 0),

  CONSTRAINT ck_http_audit_event_event_type
    CHECK (LEN(TRIM(event_type)) > 0),

  CONSTRAINT ck_http_audit_event_method
    CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),

  CONSTRAINT ck_http_audit_event_response_status
    CHECK (response_status BETWEEN 100 AND 599),

  CONSTRAINT ck_http_audit_event_duration_ms
    CHECK (duration_ms >= 0),

  CONSTRAINT ck_http_audit_event_response_time
    CHECK (response_timestamp >= request_timestamp),

  CONSTRAINT ck_http_audit_event_request_headers_json
    CHECK (request_headers IS NULL OR ISJSON(request_headers) = 1),

  CONSTRAINT ck_http_audit_event_response_headers_json
    CHECK (response_headers IS NULL OR ISJSON(response_headers) = 1),

  CONSTRAINT ck_http_audit_event_metadata_json
    CHECK (metadata IS NULL OR ISJSON(metadata) = 1),

  CONSTRAINT fk_http_audit_event_actor_user
    FOREIGN KEY (actor_user_id)
    REFERENCES identity.user_account(id)
);

CREATE UNIQUE CLUSTERED INDEX cix_http_audit_event_occurred_at
  ON audit.http_audit_event (occurred_at, event_id);

CREATE INDEX ix_http_audit_event_request_id
  ON audit.http_audit_event (request_id);

CREATE INDEX ix_http_audit_event_trace_id
  ON audit.http_audit_event (trace_id)
  WHERE trace_id IS NOT NULL;

CREATE INDEX ix_http_audit_event_actor_user
  ON audit.http_audit_event (actor_user_id, occurred_at)
  WHERE actor_user_id IS NOT NULL;

CREATE INDEX ix_http_audit_event_event_type
  ON audit.http_audit_event (event_type, occurred_at);

CREATE INDEX ix_http_audit_event_source_ip
  ON audit.http_audit_event (source_ip, occurred_at)
  WHERE source_ip IS NOT NULL;
```

### 5.5 Exact companion GRANT statements (migrator + runtime)

Use the following SQL after table creation to apply least-privilege permissions for migrator and runtime users.

```sql
/*
  Replace login/user names if your environment uses different principal names.
  Defaults in this guide:
    migrator: messenger_migrator
    runtime : wl_chat_app
*/

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'messenger_migrator')
    CREATE USER [messenger_migrator] FOR LOGIN [messenger_migrator];

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'wl_chat_app')
    CREATE USER [wl_chat_app] FOR LOGIN [wl_chat_app];

/* Runtime permissions: insert/read only, append-only enforced */
GRANT INSERT ON audit.http_audit_event TO [wl_chat_app];
GRANT SELECT ON audit.http_audit_event TO [wl_chat_app];
DENY UPDATE ON audit.http_audit_event TO [wl_chat_app];
DENY DELETE ON audit.http_audit_event TO [wl_chat_app];

/* Migrator permissions: read-only verification on target table */
GRANT SELECT ON audit.http_audit_event TO [messenger_migrator];

/* Optional hardening for runtime role scope within audit schema */
DENY ALTER ON SCHEMA::audit TO [wl_chat_app];
DENY CONTROL ON SCHEMA::audit TO [wl_chat_app];
```

Permission verification query:

```sql
SELECT
    pr.name AS principal_name,
    pe.state_desc,
    pe.permission_name,
    OBJECT_SCHEMA_NAME(pe.major_id) AS schema_name,
    OBJECT_NAME(pe.major_id) AS object_name
FROM sys.database_permissions pe
JOIN sys.database_principals pr ON pe.grantee_principal_id = pr.principal_id
WHERE pr.name IN (N'messenger_migrator', N'wl_chat_app')
  AND (
      OBJECT_NAME(pe.major_id) = N'http_audit_event'
      OR pe.class_desc = 'SCHEMA'
  )
ORDER BY pr.name, pe.permission_name;
```

Notes for implementation alignment:

- Persist `request_headers`, `response_headers`, and `metadata` as sanitized JSON text.
- Keep `error_message` sanitized and bounded to 256 characters.
- Compute `record_hash` from a canonicalized event payload before insert.
- Keep the table append-only for application roles (no update/delete grants).
- If your user table name differs from `identity.user_account`, update `fk_http_audit_event_actor_user` to match the final user table name from migration step 5.1.

---

## 6. Step 2 - Implement Identity Domain and Application Services

Create module packages under `src/main/java/com/wayden/messenger/identity`.

Recommended structure:

```text
identity/
  api/
  application/
  domain/
  infrastructure/
```

### 6.1 Core domain objects

Implement value objects/entities for:

- `UserId`
- `InvitationId`
- `NormalizedUsername`
- `PasswordHash`
- `InvitationTokenHash`
- `User`
- `Invitation`

### 6.2 Core domain interfaces

Define repository contracts for:

- user lookup by id and normalized username
- invitation lookup by id and token hash
- invitation save/update with redeem/revoke semantics

Define infrastructure abstractions for:

- `PasswordHasher` (Argon2id)
- `InvitationTokenGenerator`
- `InvitationTokenHasher`
- `Clock`
- ID generators

### 6.3 Application use cases

Use a practical two-service split for Milestone 2:

1. `AdminService`
2. `InvitationService`

Recommended ownership:

- `AdminService`: bootstrap-first-admin flow and one-time guard.
- `InvitationService`: create, revoke, and redeem invitation flows.

Keep use-case boundaries explicit inside these services by modeling each action as a dedicated method or internal command handler. Split into additional top-level services later only if class size or complexity warrants it.

Implementation style rule for Milestone 2:

- Each action should have its own function.
- That function should either:
  - implement the full logic for that action, or
  - compose reusable helper methods plus action-specific logic.
- Avoid monolithic methods that mix multiple unrelated actions.

All write use cases must execute in transactions.

---

## 7. Step 3 - Implement JDBC Repositories

Create JDBC repository implementations in `identity/infrastructure`.

Repository rules:

- prepared statements only
- explicit column lists
- schema-qualified SQL
- no `SELECT *`
- explicit error translation for SQL Server conflict codes (`2601`, `2627`, `547`, `1205`)

Suggested conflict translations:

- duplicate normalized username -> `DuplicateUsernameException`
- duplicate invitation token hash -> infrastructure retry or regenerate token
- invalid invitation state transition -> typed invitation lifecycle exception

---

## 8. Step 4 - Implement API Endpoints and Error Mapping

Add identity API resources under `identity/api`.

Use resource grouping aligned to path/context:

1. `AdminResource` for bootstrap endpoints.
2. `InvitationResource` for invitation lifecycle endpoints.

Recommended endpoints:

1. `POST /api/v1/bootstrap/admin` (guarded: allowed only when no users exist)
2. `POST /api/v1/invitations`
3. `POST /api/v1/invitations/{invitationId}/revoke`
4. `POST /api/v1/invitations/redeem`

API rules:

- request DTO validation at resource boundary
- no persistence logic in resources
- sanitize error details through RFC 9457 problem responses
- return raw invitation token only on create response

Problem codes to add:

- `INVITATION_EXPIRED`
- `INVITATION_REVOKED`
- `INVITATION_ALREADY_REDEEMED`
- `DUPLICATE_USERNAME`
- `BOOTSTRAP_ALREADY_COMPLETED`

---

## 9. Step 5 - Add Security Audit Emission

Emit audit events from application services after successful state changes.

Minimum events for Milestone 2:

- bootstrap admin created
- invitation created
- invitation revoked
- invitation redeemed
- invitation redemption failed (policy-relevant failures)

Audit payload rules:

- include actor, target, trace id, and event time
- include safe metadata only
- never include raw secret/token/password values

### 9.1 Step 5A - Add Asynchronous Audit Delivery

Implement asynchronous delivery for request and response audit events so user-facing latency is not coupled to audit persistence availability.

Tool decision for Milestone 2:

- Choose RabbitMQ as the message queue broker.
- Rationale: lighter operational footprint than IBM MQ, fast local startup in Docker, and simple environment isolation with virtual hosts.

Deployment shape:

- Run a single RabbitMQ server container.
- Use separate virtual hosts instead of separate broker instances:
  - `/local` for local app runs
  - `/devdocker` for devdocker app runs
- Use dedicated exchanges and queues per environment namespace.

Queue design baseline:

- Exchange: `audit.events`
- Primary queue: `audit.events.primary`
- Dead-letter queue: `audit.events.dlq`
- Message envelope should include request id, trace id, operation, method, path, status, duration, and event time.

Producer behavior (application side):

- The HTTP audit filter publishes non-blocking, best-effort telemetry messages.
- Application use cases publish security/business audit events after successful state changes.
- If publish fails, do not fail user requests; log structured fallback entries and increment a metric.

Consumer behavior (background listener):

- Listener processes messages and writes audit rows to the audit schema.
- Use bounded retries and send poison messages to the dead-letter queue.
- Keep consumer idempotent for duplicate message delivery.

Implementation notes:

- Keep authoritative security audit semantics in application services.
- Treat filter-emitted events as transport telemetry unless explicitly promoted.
- If strict durability is later required for business audit events, add an outbox table and outbox processor.

### 9.2 Docker Compose snippets (single broker, two virtual hosts)

Use one RabbitMQ container and isolate environments with virtual hosts.

Compose service snippet:

```yaml
services:
  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: wl-chat-rabbitmq
    ports:
      - "127.0.0.1:5672:5672"
      - "127.0.0.1:15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_ADMIN_PASSWORD:?RABBITMQ_ADMIN_PASSWORD is required}
      RABBITMQ_DEFAULT_VHOST: /local
      RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS: >-
        -rabbitmq_management load_definitions "/etc/rabbitmq/definitions.json"
    volumes:
      - wl-chat-rabbitmq-data:/var/lib/rabbitmq
      - ./config/rabbitmq/definitions.json:/etc/rabbitmq/definitions.json:ro
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
    restart: unless-stopped

volumes:
  wl-chat-rabbitmq-data:
```

RabbitMQ definitions snippet (`config/rabbitmq/definitions.json`):

```json
{
  "vhosts": [{ "name": "/local" }, { "name": "/devdocker" }],
  "users": [
    { "name": "audit_local", "password": "change-me-local", "tags": "" },
    { "name": "audit_devdocker", "password": "change-me-devdocker", "tags": "" }
  ],
  "permissions": [
    {
      "user": "audit_local",
      "vhost": "/local",
      "configure": "^audit\\..*",
      "write": "^audit\\..*",
      "read": "^audit\\..*"
    },
    {
      "user": "audit_devdocker",
      "vhost": "/devdocker",
      "configure": "^audit\\..*",
      "write": "^audit\\..*",
      "read": "^audit\\..*"
    }
  ],
  "exchanges": [
    {
      "name": "audit.events",
      "vhost": "/local",
      "type": "topic",
      "durable": true,
      "auto_delete": false,
      "internal": false,
      "arguments": {}
    },
    {
      "name": "audit.events",
      "vhost": "/devdocker",
      "type": "topic",
      "durable": true,
      "auto_delete": false,
      "internal": false,
      "arguments": {}
    },
    {
      "name": "audit.events.dlx",
      "vhost": "/local",
      "type": "topic",
      "durable": true,
      "auto_delete": false,
      "internal": false,
      "arguments": {}
    },
    {
      "name": "audit.events.dlx",
      "vhost": "/devdocker",
      "type": "topic",
      "durable": true,
      "auto_delete": false,
      "internal": false,
      "arguments": {}
    }
  ],
  "queues": [
    {
      "name": "audit.events.primary",
      "vhost": "/local",
      "durable": true,
      "auto_delete": false,
      "arguments": {
        "x-dead-letter-exchange": "audit.events.dlx",
        "x-dead-letter-routing-key": "audit.dead"
      }
    },
    {
      "name": "audit.events.primary",
      "vhost": "/devdocker",
      "durable": true,
      "auto_delete": false,
      "arguments": {
        "x-dead-letter-exchange": "audit.events.dlx",
        "x-dead-letter-routing-key": "audit.dead"
      }
    },
    {
      "name": "audit.events.dlq",
      "vhost": "/local",
      "durable": true,
      "auto_delete": false,
      "arguments": {}
    },
    {
      "name": "audit.events.dlq",
      "vhost": "/devdocker",
      "durable": true,
      "auto_delete": false,
      "arguments": {}
    }
  ],
  "bindings": [
    {
      "vhost": "/local",
      "source": "audit.events",
      "destination": "audit.events.primary",
      "destination_type": "queue",
      "routing_key": "audit.#",
      "arguments": {}
    },
    {
      "vhost": "/devdocker",
      "source": "audit.events",
      "destination": "audit.events.primary",
      "destination_type": "queue",
      "routing_key": "audit.#",
      "arguments": {}
    },
    {
      "vhost": "/local",
      "source": "audit.events.dlx",
      "destination": "audit.events.dlq",
      "destination_type": "queue",
      "routing_key": "audit.dead",
      "arguments": {}
    },
    {
      "vhost": "/devdocker",
      "source": "audit.events.dlx",
      "destination": "audit.events.dlq",
      "destination_type": "queue",
      "routing_key": "audit.dead",
      "arguments": {}
    }
  ]
}
```

Application environment snippet:

```dotenv
# local app
WL_CHAT_AUDIT_AMQP_HOST=localhost
WL_CHAT_AUDIT_AMQP_PORT=5672
WL_CHAT_AUDIT_AMQP_VHOST=/local
WL_CHAT_AUDIT_AMQP_USERNAME=audit_local
WL_CHAT_AUDIT_AMQP_PASSWORD=<local-audit-password>

# devdocker app
WL_CHAT_AUDIT_AMQP_VHOST=/devdocker
WL_CHAT_AUDIT_AMQP_USERNAME=audit_devdocker
WL_CHAT_AUDIT_AMQP_PASSWORD=<devdocker-audit-password>
```

### 9.3 Startup checks

After startup, verify:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
curl -u admin:$RABBITMQ_ADMIN_PASSWORD http://localhost:15672/api/vhosts
curl -u admin:$RABBITMQ_ADMIN_PASSWORD http://localhost:15672/api/queues/%2Flocal
curl -u admin:$RABBITMQ_ADMIN_PASSWORD http://localhost:15672/api/queues/%2Fdevdocker
```

### 9.4 Audit event contract and delivery policy

Define a versioned audit message contract before implementation.

Required message fields:

- `schemaVersion`
- `eventId`
- `eventType`
- `occurredAt`
- `requestId`
- `traceId`
- `operation`
- `method`
- `path`
- `query`
- `responseStatus`
- `responseCode` (application-level code when available)
- `durationMs`
- `requestTimestamp`
- `responseTimestamp`
- `actorUserId` (nullable)
- `actorUsername` (nullable)
- `actorAuthType` (nullable, for example `session`, `api-key`, `anonymous`)
- `targetType` (nullable)
- `targetId` (nullable)
- `sourceIp` (derived from trusted forwarding headers)
- `remoteIp` (socket/transport endpoint)
- `ipResolutionSource` (for example `x-forwarded-for`, `x-real-ip`, `forwarded`, `remote-address`)
- `userAgent`
- `deviceType`
- `devicePlatform`
- `deviceModel`
- `osFamily`
- `browserFamily`
- `requestHeaders` (sanitized subset only)
- `responseHeaders` (sanitized subset only)
- `errorCode` (nullable)
- `errorMessage` (nullable, sanitized)
- `metadata` (sanitized key-value map)

HTTP request and response capture requirements:

- Capture full request context (method, route/path template, path, query, selected headers, timing, actor, source/remote IP, and device fingerprint hints).
- Capture full response context (status, application code, selected headers, timing, error code/message when applicable).
- Capture both successful and failed requests, including mapped problem responses.
- Preserve correlation identifiers (`requestId`, `traceId`) for cross-system investigation.

Error capture requirements:

- Store stable application error code for handled failures.
- Store sanitized error message suitable for compliance/audit review.
- Do not store stack traces or internal SQL/driver exception payloads in audit records.

Sensitive data and compliance controls:

- Never persist secrets, tokens, passwords, raw authorization headers, cookies, or sensitive PII in audit records.
- Redact or hash sensitive query parameters and sensitive header values.
- For request/response bodies, default to storing metadata only (content type, size, optional body hash), not raw payload.
- Record retention window, access controls, and tamper-evidence requirements must be defined before production rollout.

Delivery policy for Milestone 2:

- API request handling is fail-open relative to queue availability.
- If publish fails, continue request processing, emit structured fallback logs, and increment a publish-failure metric.
- Consumer persistence failures use bounded retries, then route to dead-letter queue.

### 9.5 Pre-implementation decision checklist

The following decisions are now locked for Milestone 2:

- [x] Final event schema version and required fields.
      Decision: `schemaVersion=1.0` with required fields from section 9.4.
- [x] Sensitive-field redaction rules for `metadata`.
      Decision: `metadata` is allowlist-first. Allowed keys must be explicitly declared per event type. Any key matching case-insensitive patterns `password|passphrase|secret|token|authorization|cookie|session|key|credential|ssn|dob|email|phone|address` is redacted.
- [x] Sensitive-field redaction rules for headers, query parameters, and error messages.
      Decision: request/response header capture uses an allowlist only: `x-request-id`, `x-correlation-id`, `content-type`, `content-length`, `accept`, `user-agent`, `x-forwarded-for`, `x-real-ip`, `forwarded`. Never persist `authorization`, `cookie`, `set-cookie`, or custom auth headers. Query parameter values are redacted when parameter names match the sensitive pattern list above. Error messages are sanitized to remove stack traces, SQL statements, and secrets, and truncated to 256 chars.
- [x] Queue publish timeout and retry policy.
      Decision: publish timeout `200ms`, one retry with jittered backoff `50-100ms`, then fail-open (do not fail API request).
- [x] Consumer retry count and backoff strategy.
      Decision: max 5 retries with exponential backoff `1s, 2s, 4s, 8s, 16s`; then route to `audit.events.dlq`.
- [x] Dead-letter triage process (who inspects and how often).
      Decision: primary owner is backend maintainer. Inspect DLQ at least daily on working days and immediately when alert triggers. Triage records include root cause, replay decision, and fix ticket reference.
- [x] Metric names and alert thresholds for publish and consumer failures.
      Decision metrics:
  - `audit_publish_attempt_total`
  - `audit_publish_failure_total`
  - `audit_publish_latency_ms`
  - `audit_consumer_processed_total`
  - `audit_consumer_failure_total`
  - `audit_dlq_depth`
    Decision thresholds:
  - warning: publish failure rate >1% over 5 minutes
  - critical: publish failure rate >5% over 5 minutes
  - warning: DLQ depth >0 for 10 minutes
  - critical: DLQ depth >50 at any time
- [x] Local and devdocker credential rotation process for queue users.
      Decision: rotate `audit_local`, `audit_devdocker`, and `admin` credentials every 90 days and immediately after suspected exposure. Rotation steps: update secrets, update broker definitions or user passwords, restart/reload broker config, then run startup checks in section 9.3.
- [x] Audit retention, access-control, and tamper-evidence policy.
      Decision: retain audit records for 365 days in non-production environments unless storage pressure requires archival. Write access is restricted to the audit consumer service account. Read access is restricted to backend maintainers and approved compliance/security reviewers. Audit table is append-only for application roles (no update/delete grants). Each record stores a deterministic `record_hash` (SHA-256 over canonical event payload) for tamper detection.

---

## 10. Step 6 - Tests for Functional and Concurrency Behavior

Add tests under `src/test/java/com/wayden/messenger/identity`.

### 10.1 Unit tests

Cover:

- username normalization
- invitation lifecycle transitions
- token/hash helper behavior
- password hash verification workflow

### 10.2 Repository integration tests (SQL Server Testcontainers)

Cover:

- unique normalized username constraint
- invitation hash uniqueness
- redeem/revoke transition constraints
- expected SQL error translation

### 10.3 API integration tests

Cover positive and negative cases:

- bootstrap allowed exactly once
- unauthenticated invitation create denied (except bootstrap path if policy allows)
- expired/revoked/redeemed invitation redemption failures
- duplicate username race handling

### 10.4 Required concurrency tests

At minimum:

1. concurrent invitation redemption with same token -> one success
2. concurrent redemption for same username -> one success
3. concurrent bootstrap attempts -> one success

---

## 11. Step 7 - Postman and API Contract Maintenance

Because Milestone 2 adds/changes API endpoints, update:

- `postman/collections/chat-backend.postman_collection.json`
- `postman/environments/local.example.postman_environment.json` (if variables change)

Then run:

```bash
./scripts/postman/validate-postman.sh
```

Also ensure OpenAPI output reflects new endpoints and error schemas.

---

## 12. Step 8 - Local Validation Sequence

Use this verification sequence before opening a PR:

```bash
# optional clean reset when schema drift exists
WL_CHAT_RESET_DB=true ./scripts/database/init-local.sh

# migration naming and migration/application verification
./scripts/database/validate-flyway-naming.sh
./mvnw --batch-mode --no-transfer-progress clean verify

# optional focused tests while iterating
./mvnw --batch-mode --no-transfer-progress -Dtest='*Identity*Test,*Invitation*Test' test

# manual smoke
./mvnw quarkus:dev
curl -s http://localhost:8080/q/health/live
curl -s http://localhost:8080/q/health/ready
```

---

## 13. Exit-Criteria Validation Checklist

Mark complete only when all checks are true:

- [x] Public registration path does not exist.
- [x] Invitation create/revoke/redeem endpoints and services are implemented.
- [x] Invitation token raw value is never persisted.
- [x] Invitation token hash is unique and race-safe.
- [x] Username normalization uniqueness is race-safe.
- [x] Argon2id hash and verify paths are covered by tests.
- [x] Concurrency tests prove single success for redemption and duplicate username races.
- [x] Security audit events are persisted/emitted with safe metadata.
- [x] Asynchronous audit queue is configured with environment isolation and dead-letter handling.
- [x] Audit message schema version is documented and validated in tests.
- [x] Fail-open request behavior and dead-letter flow are covered by tests.
- [x] HTTP request/response, error, actor, network, and device audit fields are captured and validated.
- [x] Postman artifacts are updated and validated.
- [x] `./mvnw clean verify` passes on Milestone 2 branch.

Evidence capture checklist:

- [x] Save verification command output.
- [x] Save test output for concurrency cases.
- [x] Save SQL proof that invitation token hashes, not raw tokens, are stored.
- [ ] Save CI run URL(s) for passing Milestone 2 PR checks.

Evidence captured locally:

- `./mvnw clean verify` completed with `Tests run: 40, Failures: 0, Errors: 0`, `BugInstance size is 0`, and `BUILD SUCCESS`.
- `./mvnw -Dtest=IdentityApiConcurrencyIntegrationTest test` completed with `Tests run: 3, Failures: 0, Errors: 0`, and `BUILD SUCCESS`.
- SQL schema proof for `identity.invitation` confirms only hashed token storage is present: columns are `id`, `token_hash`, `created_by`, `expires_at`, `redeemed_at`, `redeemed_by`, `revoked_at`, `created_at`.
- CI run URL capture remains manual/pipeline-side and was not available from the local workspace session.

---

## 14. Common Failure Modes and Fixes

1. **Duplicate usernames still appear**
   - Cause: uniqueness enforced on display username instead of normalized username.
   - Fix: add unique constraint on normalized value and normalize before persistence.

2. **Invitation can be redeemed twice under race**
   - Cause: check-then-update logic without atomic state transition or constraints.
   - Fix: use single update/insert transition with guarded predicate and verify affected-row count.

3. **Raw invitation token appears in logs**
   - Cause: request/response debug logging of full payload.
   - Fix: redact or suppress sensitive fields and add regression test around logging policy.

4. **Bootstrap endpoint remains open after first admin**
   - Cause: missing one-time guard at service layer.
   - Fix: guard on authoritative user count inside transaction and return typed error.

---

## 15. Milestone 2 Done Definition

Milestone 2 is done when:

1. all Milestone 2 checklist items in Section 13 are complete,
2. CI proves migration + identity test behavior is repeatable,
3. docs, OpenAPI, and Postman are synchronized with implementation,
4. PR review confirms no secret/token leakage in logs, DB, or API errors.

---

## 16. References

- `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md` (Milestone 2 definition)
- `docs/development-guide/milestone-1-database-foundation-step-by-step.md`
- `scripts/database/flyway/wl_chat`
- `src/main/java/com/wayden/messenger`
- `src/test/java/com/wayden/messenger`
- `postman/collections/chat-backend.postman_collection.json`
