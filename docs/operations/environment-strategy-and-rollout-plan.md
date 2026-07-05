# Environment Strategy and Rollout Plan

## Purpose

This document formalizes the environment model and rollout sequence for ChatBackend so implementation and documentation stay synchronized.

## Canonical Environment Model

1. Local
   - App runtime: terminal or IDE (`./mvnw quarkus:dev`)
   - SQL Server: local instance on `localhost:1433`
   - Database: `wl_chat`

2. DevDocker (planned)
   - App runtime: containerized
   - SQL Server: containerized, isolated from Local
   - Intended host exposure for SQL Server: `localhost:1434`
   - Purpose: rehearse remote-like behavior entirely on laptop

3. Production (future)
   - Persistent hosted environment
   - API reachable by real clients
   - Deployment automation enabled only after provisioning

## Database Initialization Strategy

Two-phase model:

1. Bootstrap (admin, one-time per environment)
   - Script: `scripts/database/bootstrap/V0__create_wl_chat_database.sql`
   - Creates database `wl_chat` if missing

2. Flyway migrations (versioned, immutable)
   - Location: `src/main/resources/db/migration`
   - Current scripts:
     - `V1__create_app_login_and_user.sql`
     - `V2__grant_app_permissions.sql`

## CI Posture (Current)

1. Local validation workflow
   - File: `.github/workflows/db-local-bootstrap-migrate.yml`
   - Uses ephemeral SQL Server in GitHub runner
   - Validates bootstrap + migration execution path
   - Not a deployment workflow

2. Remote workflow scaffold
   - File: `.github/workflows/db-remote-bootstrap-migrate.yml`
   - Manual-only and deferred
   - Intended for activation when persistent hosted infrastructure exists

## Documentation Source-of-Truth

1. `README.md`
   - High-level entrypoint and quickstart

2. `docs/development-guide/milestone-0-sql-server-step-by-step.md`
   - Detailed implementation runbook
   - Section 0 is authoritative current-state guidance

3. `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`
   - Architecture and lifecycle intent

## Implementation Phases

### Phase A - Current Baseline Stabilization (in progress)

1. Keep Local environment reliable.
2. Keep Flyway scripts authoritative for DB evolution.
3. Ensure docs and runtime config match.

Exit criteria:

- `./mvnw -DskipTests compile` succeeds on Java 25.
- `/q/health/live` responds when app is running.
- Local DB initialization path is documented and reproducible.

### Phase B - DevDocker Environment

1. Add Dockerized app container configuration.
2. Add second SQL Server service mapped to host port `1434`.
3. Add environment-specific variables for DevDocker.
4. Add verification commands for health and DB connectivity.

Exit criteria:

- DevDocker stack starts with one command.
- App in container reaches SQL Server container.
- Flyway migration path is repeatable in DevDocker.

### Phase C - Production Enablement

1. Provision persistent hosted runtime and database.
2. Add secure secrets management and network policy.
3. Activate remote migration workflow with production safeguards.
4. Add deployment + post-deploy health checks.

Exit criteria:

- Merged `main` changes can migrate and deploy to persistent environment.
- Rollback and recovery procedures are documented.
- Observability and alerting are in place.

## Guardrails

1. Do not run application with `sa` as runtime login.
2. Do not edit previously applied Flyway scripts; add new versions instead.
3. Keep Local and DevDocker SQL data stores separate.
4. Keep production deployment automation disabled until persistent infrastructure exists.

## Open Decisions

1. Final hosting target for Production.
2. Whether Production database is containerized SQL Server or managed SQL.
3. Whether migration execution should be a dedicated job before app rollout.
