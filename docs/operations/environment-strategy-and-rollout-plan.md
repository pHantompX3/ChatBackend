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
   - Persistent Windows 11 Pro host at reserved LAN address `192.168.0.199`
   - Docker Desktop with WSL2; one single-host Compose deployment
   - API reachable by remote clients only through the authenticated public HTTPS/WSS edge under
     ADR-0019
   - Fronted by the Milestone 9 NGINX HTTPS/WSS reference on the remote host
   - Apache APISIX remains an optional later replacement if multi-service gateway or load-balancing
     requirements justify the added component
   - One free private Docker Hub repository, with artifacts selected by annotated release tag and
     immutable digest
   - Production receives deployment artifacts and metadata, not a repository checkout
   - Deployment automation enabled only after X1 host, release, recovery, and approval controls pass

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
4. Route public HTTPS/WSS traffic through NGINX and keep ChatBackend and every data/control service
   on internal-only ports.
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
8. Expose only the ADR-0019 public NGINX HTTPS/WSS edge. Do not publish ChatBackend, SQL Server,
   RabbitMQ, administration, monitoring SQL, or Docker control; do not infer frontend authenticity
   from Origin, CORS, client labels, or embedded shared secrets.

## Implementation discovery and later decisions

1. IPv4 CGNAT is confirmed. ADR-0020 selects outbound-only Cloudflare Tunnel for the current
   text/API/WebSocket workload; no WAN forwarding or public origin port is used. Future on-premises
   media delivery requires a separate compliant ingress decision.
2. Preserve and verify every existing Hostinger DNS record, then onboard the zone to Cloudflare DNS
   and create a dedicated Tunnel hostname without exposing an origin address.
3. Use Cloudflare-managed public TLS and authenticated private TLS from `cloudflared` to NGINX; open
   no ACME or application ingress port on the production host.
4. Use SQL Server 2022 Express with its documented capacity limits, uncompressed checksum backups,
   and external archive compression/encryption; no paid SQL edition or SQL-native at-rest encryption
   enters X1.
5. Discover the router network-drive protocol. Prefer SMB 2/3, prohibit SMB1, compress and encrypt
   packages before transfer, and record the accepted lack of immutability/snapshots.
6. Whether future multi-service or multi-instance requirements justify replacing NGINX with APISIX or
   another gateway through a later ADR.
7. When client foundations are mature enough to begin required Infrastructure Evolution Track IE-01.

X1 has now supplied proportional initial recovery objectives: 24-hour database RPO, 60-minute
isolated-database restore after recovery capacity is ready, two-hour ordinary software/configuration
service RTO, and eight-hour total-host-loss service RTO excluding external replacement-hardware delay.
They remain subject to measured X1 acceptance rather than rehearsal timing alone.

X1 has no remaining planning blocker. Environment-specific WAN, router, DNS, and share facts are
collected during X1-A before the related control is enabled. Monitoring/alert decisions remain X2
scope.
