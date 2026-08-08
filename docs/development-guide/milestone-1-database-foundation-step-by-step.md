# Milestone 1 Implementation Guide

## Database Foundation

**Project:** Private Messenger  
**Milestone:** 1 - Database foundation  
**Database:** Microsoft SQL Server 2022  
**Application stack:** Java 25, Quarkus 3.33 LTS, Maven  
**Status:** Implementation guide  
**Last reviewed:** 2026-08-05

---

## 0. Purpose and Scope

Milestone 1 establishes the database foundation that all feature milestones depend on.

This milestone is complete only when the project can:

1. migrate an empty SQL Server instance to latest,
2. validate migrations automatically,
3. run migration and schema checks in CI,
4. expose inspectable Flyway history under a stable schema/table model,
5. enforce a least-privilege runtime principal model.

This milestone does not implement identity, sessions, conversations, or messaging features.

---

## 1. Deliverables

Milestone 1 deliverables from the platform spec:

- Flyway integration
- logical schemas
- migration naming rules
- migration validation
- SQL Server Testcontainer migration harness
- initial database roles documentation

Milestone 1 exit criteria from the platform spec:

- an empty SQL Server database migrates to the latest version
- the application can connect through the runtime datasource after migration
- migration and schema-verification tests run in CI
- Flyway history is inspectable in `platform.flyway_schema_history`
- the runtime database principal cannot perform unauthorized DDL

---

## 2. Current Baseline and Gap Map

### 2.1 Current baseline already present

The repository already contains:

- Flyway execution through scripts under `scripts/database/flyway/master` and `scripts/database/flyway/wl_chat`
- local and DevDocker bootstrap/migrate wrappers
- CI bootstrap and migration workflow (`.github/workflows/db-local-bootstrap-migrate.yml`)
- runtime datasource wired to SQL Server and health checks

### 2.2 Gaps to close in Milestone 1

The following items must be explicitly implemented and verified in this milestone:

1. Logical schema foundation migration (`platform`, `identity`, `messaging`, `audit`) for `wl_chat`.
2. Explicit Flyway history placement under `platform.flyway_schema_history`.
3. Migration filename policy transition to UTC timestamped versions.
4. Migration validation and schema verification tests using SQL Server Testcontainers.
5. Database principal/permission documentation and enforcement checks.

---

## 3. Prerequisites

Before executing this guide, verify:

```bash
java --version
docker version
docker compose version
./mvnw --version
```

Expected:

- Java 25
- Maven Wrapper 3.9.16 (or current pinned wrapper version in repo)
- Docker and Compose available locally

Also ensure local secrets are configured:

```bash
cp -n scripts/config/local.secrets.env.example scripts/config/local.secrets.env
```

---

## 4. Milestone Architecture Decisions

Milestone 1 assumes these decisions:

1. Flyway SQL files are source-of-truth and live under `scripts/database/flyway/**`.
2. `master` scripts create/bootstrap the target database only.
3. `wl_chat` scripts define application schemas and objects.
4. Runtime application login (`wl_chat_app`) is least-privilege and not equivalent to migration/admin user.
5. Flyway history ownership is explicit (`platform.flyway_schema_history`) and inspectable.

If any of these decisions change, add/update ADRs under `docs/architecture/decision`.

---

## 5. Step 1 - Establish Schema Foundation Migration

### 5.1 Create a new `wl_chat` migration

Add a timestamped migration file in:

```text
scripts/database/flyway/wl_chat/
```

Filename pattern:

```text
VYYYYMMDDHHMMSS__description_in_snake_case.sql
```

Recommended first schema-foundation migration example:

```text
V20260805010000__create_logical_schemas.sql
```

Recommended content skeleton:

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

### 5.2 Keep existing migrations immutable

Do not modify already-applied versioned migrations.
Use forward-only new migrations for all corrections.

---

## 6. Step 2 - Place Flyway History in Platform Schema

### 6.1 Configure Flyway history placement

Set application-level Flyway schema/table settings so history is stored as:

```text
platform.flyway_schema_history
```

Typical properties to set in runtime configuration:

```properties
quarkus.flyway.default-schema=platform
quarkus.flyway.schemas=platform
quarkus.flyway.table=flyway_schema_history
```

If migration execution remains script-driven, ensure those settings are also reflected in the Flyway CLI invocation.

### 6.2 Verify history table location

After migration, run:

```sql
SELECT s.name AS schema_name, t.name AS table_name
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE t.name = 'flyway_schema_history';
```

Expected:

- one row
- `schema_name = platform`

---

## 7. Step 3 - Formalize Migration Naming Rules

### 7.1 Rule for new migrations

All new migrations after Milestone 1 must use timestamp naming:

```text
VYYYYMMDDHHMMSS__description_in_snake_case.sql
```

### 7.2 Compatibility note

Existing `V1`, `V2` files remain valid historical baseline. Do not rename applied files.

### 7.3 Optional policy enforcement

Add a lightweight CI check script that fails if new SQL files under `scripts/database/flyway/wl_chat` do not match the naming regex.

---

## 8. Step 4 - Add SQL Server Testcontainer Migration Harness

### 8.1 Add integration test dependencies (if missing)

Add Testcontainers dependencies in `pom.xml` (test scope):

- `org.testcontainers:junit-jupiter`
- `org.testcontainers:mssqlserver`

### 8.2 Create migration harness test

Create:

```text
src/test/java/com/wayden/messenger/bootstrap/MigrationHarnessTest.java
```

Test responsibilities:

1. Start disposable SQL Server container.
2. Execute `master` and `wl_chat` migrations against container DB.
3. Assert logical schemas exist.
4. Assert Flyway history exists at `platform.flyway_schema_history`.

### 8.3 Create schema verification test

Create:

```text
src/test/java/com/wayden/messenger/bootstrap/SchemaVerificationTest.java
```

Verify at minimum:

- `wl_chat` database exists
- expected schemas exist
- runtime login can connect
- runtime login cannot perform DDL (negative test)

---

## 9. Step 5 - Document Database Principals and Permissions

Create/update:

```text
docs/database/sql-server-principals-and-permissions.md
```

The document must include:

1. `messenger_migrator` intended permissions
2. `messenger_runtime` intended permissions
3. explicit statement that runtime principal is not `db_owner`, `db_ddladmin`, or `sysadmin`
4. grant/revoke examples
5. verification SQL snippets

Note: Current repo uses `wl_chat_app` as runtime principal baseline. This document should map current state and planned transition clearly.

---

## 10. Step 6 - CI Migration and Schema Verification

### 10.1 Extend CI workflow

Update `.github/workflows/db-local-bootstrap-migrate.yml` (or add a dedicated migration CI workflow) to include:

1. migration validation
2. schema verification assertions
3. runtime principal DDL-denial check
4. `./mvnw clean verify`

### 10.2 CI failure expectations

CI must fail when:

- migration scripts are invalid
- schema foundation missing
- history table location is wrong
- runtime principal can execute unauthorized DDL

---

## 11. Step 7 - Local Verification Sequence

Run in this order:

```bash
# 1) reset local SQL container state if needed
./scripts/cicd/devdocker-down.sh

# 2) bring up local SQL path (or devdocker path, choose one canonical path)
./scripts/database/init-local.sh

# 3) run full verification
./mvnw spotless:apply
./mvnw clean verify
```

Then manually verify health:

```bash
./mvnw quarkus:dev
curl -s http://localhost:8080/q/health/live
curl -s http://localhost:8080/q/health/ready
```

---

## 12. Exit-Criteria Validation Checklist

### Milestone 1 checklist

- [x] Empty SQL Server migrates to latest from `scripts/database/flyway/**`.
- [x] App connects with runtime datasource after migration.
- [ ] Migration + schema verification tests run in CI.
- [x] Flyway history is in `platform.flyway_schema_history`.
- [x] Runtime principal cannot perform unauthorized DDL.

### Evidence capture checklist

- [ ] Save command output for migration run from empty DB.
- [ ] Save test output for migration harness and schema verification tests.
- [ ] Save CI run URLs for passing migration workflow.
- [x] Save SQL output proving history table schema and runtime DDL denial.

---

## 13. Common Failure Modes and Fixes

1. **Flyway history in wrong schema**

- Cause: missing Flyway schema/table config.
- Fix: set Flyway default schema and table explicitly and re-verify.

2. **Runtime principal can create tables**

- Cause: excessive grants or role membership.
- Fix: remove `db_owner`/DDL privileges and re-run negative test.

3. **Migration naming drift**

- Cause: new files not following timestamp convention.
- Fix: enforce naming in CI and reject non-conforming files before merge.

4. **Container-specific migration differences**

- Cause: mismatch between local SQL version and CI SQL version.
- Fix: pin SQL image versions in scripts/workflows and keep them aligned.

---

## 14. Milestone 1 Done Definition

Milestone 1 is done when:

1. the five Milestone 1 checklist items in Section 12 are all checked,
2. CI proves migration + schema verification in repeatable runs,
3. docs and scripts are aligned with the same migration model,
4. team can bootstrap from clean clone and reproduce results without manual DB patching.

---

## 15. References

- `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md` (Milestone 1 definition)
- `docs/development-guide/milestone-0-sql-server-step-by-step.md` (completed baseline)
- `scripts/database/flyway/master`
- `scripts/database/flyway/wl_chat`
- `.github/workflows/db-local-bootstrap-migrate.yml`
