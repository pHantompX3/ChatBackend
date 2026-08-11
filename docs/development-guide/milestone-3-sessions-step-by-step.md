# Milestone 3 Implementation Guide

## Sessions and Authentication

**Project:** Private Messenger  
**Milestone:** 3 - Sessions and authentication  
**Database:** Microsoft SQL Server 2022  
**Application stack:** Java 25, Quarkus 3.33 LTS, Maven  
**Status:** Implemented baseline; keep updated as behavior evolves
**Last reviewed:** 2026-08-11

---

## 0. Purpose and Scope

Milestone 3 introduces the first authenticated user experience: a durable, revocable session model that lets the backend identify the caller and protect privileged operations.

This milestone is complete only when the project can:

1. create opaque, revocable sessions for authenticated users,
2. authenticate requests through an HTTP filter or equivalent middleware,
3. reject invalid, revoked, or expired sessions with a consistent problem response,
4. revoke sessions on logout and when a user is disabled,
5. keep raw session tokens out of logs, traces, and persistent storage,
6. preserve audit actor identity from the authenticated request context rather than request bodies.

Milestone 3 does not include conversations or messaging behavior. It focuses on the authentication boundary that later milestones build upon.

Current implementation status:

- Milestone 2 identity and invitation flows are present.
- Session schema, login/logout endpoints, and an authentication filter are now in place.
- Disabled users are rejected on login and authenticated request resolution.
- A dedicated administrative revoke-all-sessions API is not yet exposed; treat it as follow-on hardening work.
- This runbook remains the operational reference for expected behavior and follow-on hardening.

---

## 1. Deliverables and Exit Criteria

### Deliverables

- login endpoint
- opaque session token creation
- authenticated request filter
- logout endpoint
- session expiry handling
- user-disable enforcement and follow-on revoke-all-sessions hardening
- session-aware audit actor context

### Exit criteria

- invalid, expired, and revoked tokens fail consistently,
- disabled users cannot continue using existing sessions,
- no raw token appears in logs or database,
- authenticated requests resolve the actor identity from the request context.

---

## 2. Current Baseline and Gap Map

### 2.1 Baseline already present

The repository already contains:

- the identity and invitation domain/services from milestone 2,
- Argon2id password hashing from the identity layer,
- HTTP audit infrastructure and request-context support,
- a shared problem-response pattern for API errors.

### 2.2 Gaps Milestone 3 must close

Milestone 3 must add:

1. a durable session table and supporting indexes/constraints,
2. a token generator and token hasher abstraction,
3. a login service that validates credentials and creates a session,
4. a logout pathway, with optional revoke-all-sessions administrative capability,
5. an authentication filter that resolves the session from an HTTP header,
6. integration tests for success and failure paths,
7. Postman flows for login/logout and protected endpoint access.

---

## 3. Prerequisites

Before implementing Milestone 3, verify the current baseline:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
./scripts/database/validate-flyway-naming.sh
```

If you need a local secrets file for devdocker or local runtime overrides, create it first:

```bash
cp -n scripts/config/local.secrets.env.example scripts/config/local.secrets.env
```

Work on a feature branch. Do not push directly to `main`.

---

## 4. Milestone 3 Design Decisions

Apply these decisions consistently in code, SQL, and tests.

1. **Opaque revocable sessions**
   - Use random, opaque session tokens.
   - Store only a hash of the token, never the raw token.

2. **Bearer token transport for early development**
   - For local development and Postman, support `Authorization: Bearer <token>`.
   - A future browser-cookie model may be introduced later, but it is not required for this milestone.

3. **Authentication from the filter chain**
   - The authenticated user must be resolved in the HTTP/authentication layer, not from request bodies or service parameters.
   - The auth filter must populate `actorUserId`, `actorUsername`, and `actorAuthType` for audit emission.

4. **Session revocation as the source of truth**
   - A revoked, expired, or disabled session must be rejected even if the token is syntactically valid.

5. **No token leakage**
   - Never log raw session tokens.
   - Never return the raw token in a later read path.
   - Never persist raw tokens in the database or audit payloads.

---

## 5. Step 1 - Add Session Schema Migrations

Create immutable Flyway migrations under:

```text
scripts/database/flyway/wl_chat
```

Use the timestamp naming pattern:

```text
VYYYYMMDDHHMMSS__description_in_snake_case.sql
```

Recommended migration sequence:

1. `VYYYYMMDDHHMMSS__create_identity_session.sql`
2. `VYYYYMMDDHHMMSS__create_identity_session_indexes.sql`

### 5.1 Session table requirements

Create an `identity.session` table with at least:

- UUID primary key (`id`)
- `user_id` FK to `identity.user_account`
- `token_hash` (`VARBINARY(64)` or equivalent)
- `created_at`, `expires_at`, `last_seen_at`, `revoked_at`
- `user_agent`, `source_address`
- `status` or equivalent state indicator (`ACTIVE`, `REVOKED`, `EXPIRED`)

Required constraints:

- token hash uniqueness
- non-null user id
- valid expiry semantics (`expires_at` must be after `created_at`)
- coherent revoke semantics (`revoked_at` must be null until the session is revoked)

### 5.2 Recommended SQL DDL

Use the following as the starting point for the primary session-table migration.
Place indexes in the dedicated `__create_identity_session_indexes.sql` migration so table creation and indexing remain independently deployable.

```sql
IF SCHEMA_ID(N'identity') IS NULL
  EXEC(N'CREATE SCHEMA identity AUTHORIZATION dbo');

CREATE TABLE identity.session (
  id UNIQUEIDENTIFIER NOT NULL,
  user_id UNIQUEIDENTIFIER NOT NULL,
  token_hash VARBINARY(64) NOT NULL,
  created_at DATETIME2(7) NOT NULL,
  expires_at DATETIME2(7) NOT NULL,
  last_seen_at DATETIME2(7) NULL,
  revoked_at DATETIME2(7) NULL,
  user_agent NVARCHAR(1024) NULL,
  source_address VARCHAR(45) NULL,
  status NVARCHAR(20) NOT NULL,

  CONSTRAINT pk_identity_session
    PRIMARY KEY NONCLUSTERED (id),

  CONSTRAINT fk_identity_session_user
    FOREIGN KEY (user_id)
    REFERENCES identity.user_account(id),

  CONSTRAINT ck_identity_session_status
    CHECK (status IN (N'ACTIVE', N'REVOKED', N'EXPIRED')),

  CONSTRAINT ck_identity_session_expiry
    CHECK (expires_at > created_at)
);

```

Recommended index migration snippet:

```sql
CREATE UNIQUE INDEX ux_identity_session_token_hash
  ON identity.session (token_hash);

CREATE INDEX ix_identity_session_user_active
  ON identity.session (user_id, status, expires_at);

CREATE INDEX ix_identity_session_expires_at
  ON identity.session (expires_at);
```

### 5.3 Least-privilege grants

Apply runtime and migrator permissions after table creation:

```sql
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'messenger_migrator')
    CREATE USER [messenger_migrator] FOR LOGIN [messenger_migrator];

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'wl_chat_app')
    CREATE USER [wl_chat_app] FOR LOGIN [wl_chat_app];

GRANT SELECT, INSERT, UPDATE ON identity.session TO [wl_chat_app];
DENY DELETE ON identity.session TO [wl_chat_app];
GRANT SELECT ON identity.session TO [messenger_migrator];
```

### 5.4 Implementation alignment notes

- Keep the table append-oriented for runtime usage; do not grant delete to the runtime principal.
- Consider a `revoked_at` timestamp rather than a hard delete so the session can be audited and inspected if needed.
- Use `token_hash` rather than a plain token column.

---

## 6. Step 2 - Implement Session Domain and Application Services

Create a new session module under the existing feature structure, for example:

```text
src/main/java/com/wayden/messenger/session/
  api/
  application/
  domain/
  infrastructure/
```

### 6.1 Domain responsibilities

Introduce a `Session` value object or entity with the key properties:

- `id`
- `userId`
- `tokenHash`
- `createdAt`
- `expiresAt`
- `lastSeenAt`
- `revokedAt`
- `status`

### 6.2 Application service responsibilities

Implement an application service that supports:

- `login(username, password)`
- `logout(sessionId)` (resolved from the authenticated request context)
- `revokeAllSessionsForUser(userId)`
- `findActiveSessionByTokenHash(tokenHash)`
- `touchSession(sessionId)` for last-seen updates

### 6.3 Token handling policy

Use a token generator that creates a cryptographically random opaque string. Do not store it raw.

Recommended approach:

1. generate a random token,
2. hash it with a strong one-way algorithm,
3. persist the hash plus metadata,
4. return the raw token once in the login response.

### 6.4 Repository contract

Create a repository interface with methods for:

- insert new session,
- find by token hash,
- mark a session revoked,
- optional: revoke all sessions for a user,
- update last-seen timestamp.

Where possible, use database-level updates to keep the revocation logic race-safe.

### 6.5 Suggested API contract for login

Response example:

```json
{
  "sessionId": "...",
  "token": "<opaque-token>",
  "expiresAt": "2026-08-08T12:00:00Z"
}
```

The response should return the raw token only once, at login time.

---

## 7. Step 3 - Implement Request Authentication Filter

Add a Quarkus/JAX-RS filter that runs early in the request pipeline.

### 7.1 Recommended filter behavior

- Read the `Authorization` header.
- Expect `Bearer <token>`.
- Resolve the session from the token hash.
- Reject the request if:
  - the header is missing,
  - the scheme is not `Bearer`,
  - the session is not found,
  - the session is expired,
  - the session is revoked,
  - the user is disabled.

### 7.2 Required context wiring

Populate the request audit context with:

- `actorUserId`
- `actorUsername`
- `actorAuthType = "session"`

This allows the HTTP audit layer to keep actor identity consistent with the authenticated request.

### 7.3 Protected endpoints

Protect the endpoints that should require a signed-in user, including:

- invitation creation and revocation,
- future conversation and message endpoints,
- any admin-only operation that requires an authenticated principal.

Leave bootstrap admin and invitation redemption public unless you explicitly want them to require authentication in a later refinement.

### 7.4 Failure response contract

Use the same RFC 9457-style problem response pattern already used elsewhere in the API:

- `401 Unauthorized` for missing/invalid token,
- `403 Forbidden` for authenticated-but-not-authorized requests,
- `409 Conflict` or `400 Bad Request` for domain rule violations where appropriate.

Do not leak token values or stack traces in the response payload.

---

## 8. Step 4 - Expose Login and Logout APIs

Add an authentication resource with at least:

- `POST /api/v1/sessions`
- `POST /api/v1/sessions/logout`

### 8.1 Login endpoint

Request body:

```json
{
  "username": "admin-user",
  "password": "<set_at_runtime>"
}
```

Behavior:

1. locate the user by normalized username,
2. verify the supplied password against the Argon2id hash,
3. create a new active session,
4. return the raw token once.

### 8.2 Logout endpoint

A logout request should:

1. resolve the session from the authenticated request,
2. mark that session revoked,
3. return `204 No Content`.

### 8.3 Optional me endpoint

A lightweight `GET /api/v1/auth/me` endpoint can be useful for local smoke testing and future client work. It returns the current authenticated user's identity.

---

## 9. Step 5 - Enforce User Disable (Revoke-All-Sessions Optional)

When a user is disabled:

- subsequent requests using those tokens must fail with an authentication error,
- existing session records should remain auditable,
- optional hardening can revoke all active sessions for that user proactively.

Implement this as a domain invariant instead of relying on ad hoc checks in each endpoint.

Recommended flow:

1. disable the user,
2. enforce disabled-user rejection in login and authenticated session resolution,
3. optionally revoke all sessions for that user,
4. emit an audit event reflecting the disable action.

---

## 10. Step 6 - Add Integration Tests

Write tests that cover the core session lifecycle.

### Minimum test matrix

1. login success creates a session and returns a token,
2. invalid password fails with a safe problem response,
3. logout revokes the current session,
4. revoked session fails on subsequent authenticated request,
5. expired session fails,
6. disabled user cannot continue using existing sessions,
7. optional: revoke-all-sessions removes access for all sessions of a user when this capability is implemented.

Where possible, use SQL Server Testcontainers and real database interactions rather than mocking the repository layer.

### Suggested test names

- `login_creates_active_session_for_valid_credentials`
- `login_rejects_unknown_user`
- `logout_revokes_current_session`
- `revoked_session_cannot_access_protected_resource`
- `disabled_user_sessions_are_rejected`

---

## 11. Step 7 - Extend Postman Flows

Add Postman requests for the new auth lifecycle:

- login request
- logout request
- protected resource request using the saved bearer token
- negative test for revoked/expired token

Recommended environment variables:

- `auth_username`
- `auth_password`
- `auth_token`
- `auth_session_id`

Use the existing local environment and keep secrets out of the committed file. Prefer placeholders such as `<set_after_login>` for values that are created at runtime.

---

## 12. Definition of Done

Milestone 3 is complete when all of the following are true:

- the session table exists and migrations are verified,
- login/logout work end to end,
- authenticated requests resolve actor identity correctly,
- invalid and revoked sessions fail as intended,
- disabled users are forced out of the system,
- Postman flows and integration tests pass,
- no raw session tokens are stored or logged.

---

## 13. Recommended Implementation Order

1. add the schema migration,
2. implement session repository and service,
3. add login/logout endpoints,
4. add the auth filter and request context propagation,
5. protect the first privileged endpoints,
6. add integration tests and Postman coverage.
