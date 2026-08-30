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
   - API reachable by real clients only through owner-approved LAN/private-VPN paths under ADR-0018
   - Fronted by the Milestone 9 NGINX HTTPS/WSS reference on the remote host
   - Apache APISIX remains an optional later replacement if multi-service gateway or load-balancing
     requirements justify the added component
   - Deployment automation enabled only after provisioning

## Database Initialization Strategy

Two-phase model:

1. Bootstrap (admin, one-time per environment)
   - Script: `scripts/database/flyway/master/V20260808110000__create_wl_chat_database.sql`
   - Creates database `wl_chat` if missing

2. Flyway migrations (versioned, immutable)
   - Location: `scripts/database/flyway/wl_chat`
   - Current scripts:
     - `V20260808110500__create_app_login_and_user.sql`
     - `V20260808111000__grant_app_permissions.sql`
     - `V20260808111500__create_logical_schemas.sql`

## CI Posture (Current)

1. Local validation workflow
   - File: `.github/workflows/db-local-bootstrap-migrate.yml`
   - Uses ephemeral SQL Server in GitHub runner
   - Validates bootstrap + migration execution path
   - Verifies Quarkus startup, health endpoints, and integration behavior in CI
   - Not a deployment workflow

2. Remote workflow scaffold
   - File: `.github/workflows/db-remote-bootstrap-migrate.yml`
   - Manual, environment-protected migration only
   - Uses a dedicated migrator URL/credential and refuses missing configuration
   - Does not bootstrap a server, receive `sa`, or run automatically on pushes
   - It remains disabled in practice until a persistent environment and approval policy exist

## Documentation Source-of-Truth

1. `README.md`
   - High-level entrypoint and quickstart

2. `docs/development-guide/milestone-0-sql-server-step-by-step.md`
   - Detailed implementation runbook
   - Section 0 is authoritative current-state guidance

3. `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`
   - Architecture and lifecycle intent

## Implementation Phases

### Phase A - Current Baseline Stabilization (completed)

1. Keep Local environment reliable.
2. Keep Flyway scripts authoritative for DB evolution.
3. Ensure docs and runtime config match.

Exit criteria:

- `./mvnw -DskipTests compile` succeeds on Java 25.
- `/q/health/live` responds when app is running.
- Local DB initialization path is documented and reproducible.
- Milestone 1 database-foundation exit criteria are completed and documented in `docs/development-guide/milestone-1-database-foundation-step-by-step.md`.

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
3. Add the Milestone 9 NGINX reference on the remote machine as the TLS reverse proxy in front of the
   single ChatBackend instance.
4. Route source-restricted HTTPS/WSS traffic through NGINX and keep the application on an
   internal-only port.
5. Activate remote migration workflow with production safeguards.
6. Add deployment + post-deploy health checks.

Exit criteria:

- Merged `main` changes can migrate and deploy to persistent environment.
- Rollback and recovery procedures are documented.
- Observability and alerting are in place.
- NGINX handles TLS termination, edge routing, safe forwarding, and WebSocket upgrades before traffic
  reaches the single application instance.

## Guardrails

1. Do not run application with `sa` as runtime login.
2. Do not edit previously applied Flyway scripts; add new versions instead.
3. Keep Local and DevDocker SQL data stores separate.
4. Keep production deployment automation disabled until persistent infrastructure exists.
5. Leave `chat.http.trusted-proxies` empty when clients connect directly. Configure only the exact
   immediate proxy IP addresses or CIDRs that are permitted to supply forwarding headers.
6. Keep login throttling enabled outside isolated tests. Initial defaults are 10 account attempts
   per five minutes and 30 source attempts per minute, with shared state in SQL Server.
7. Console, application-file, and HTTP-audit logs are JSON Lines outside tests. Preserve
   `X-Request-Id` and `X-Trace-Id` when correlating support and audit investigations.
8. Keep production client ingress restricted to the ADR-0018 trusted-network boundary. Do not expose
   a general Internet API, embed frontend secrets, or infer frontend authenticity from Origin/client
   labels.

## Open Decisions

1. Final hosting target for Production.
2. Whether Production database is containerized SQL Server or managed SQL.
3. Production acceptance or replacement of the Milestone 9 rehearsal RPO/RTO objectives.
4. Whether future multi-service or multi-instance requirements justify replacing NGINX with APISIX or
   another gateway through a later ADR.
5. Exact LAN/private-VPN implementation, source ranges, peer onboarding/revocation, and whether a
   second-device heartbeat monitors the private remote-access endpoint.
