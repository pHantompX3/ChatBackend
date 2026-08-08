# Milestone 2 Implementation Guide

## Identity and Invitations

**Project:** Private Messenger  
**Milestone:** 2 - Identity and invitations  
**Database:** Microsoft SQL Server 2022  
**Application stack:** Java 25, Quarkus 3.33 LTS, Maven  
**Status:** Planned implementation guide  
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

- [ ] Public registration path does not exist.
- [ ] Invitation create/revoke/redeem endpoints and services are implemented.
- [ ] Invitation token raw value is never persisted.
- [ ] Invitation token hash is unique and race-safe.
- [ ] Username normalization uniqueness is race-safe.
- [ ] Argon2id hash and verify paths are covered by tests.
- [ ] Concurrency tests prove single success for redemption and duplicate username races.
- [ ] Security audit events are persisted/emitted with safe metadata.
- [ ] Postman artifacts are updated and validated.
- [ ] `./mvnw clean verify` passes on Milestone 2 branch.

Evidence capture checklist:

- [ ] Save verification command output.
- [ ] Save test output for concurrency cases.
- [ ] Save SQL proof that invitation token hashes, not raw tokens, are stored.
- [ ] Save CI run URL(s) for passing Milestone 2 PR checks.

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
