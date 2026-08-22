# ChatBackend

## Prerequisites

- JDK 25
- Docker with Docker Compose
- Git
- x86-64 Docker host for a supported SQL Server Linux container

## Current Baseline

- Java: 25
- Quarkus: 3.33.2.1
- Build: Maven Wrapper (`./mvnw`)
- Database: Microsoft SQL Server 2022
- Active local database name: `wl_chat`
- Active app login: `wl_chat_app`
- Audit transport: RabbitMQ-backed delivery is optional; if the audit transport is not configured, the app falls back to local async persistence and still boots cleanly

## Environment Model

This repository currently standardizes three environments:

1. Local
   - App runs from IDE/terminal (`./mvnw quarkus:dev`)
   - App connects to SQL Server on `localhost:1433`

2. DevDocker

- App and SQL Server run as containers on a dedicated Docker network
- SQL Server host port: `1434`
- App host port: configurable, default `8081`

3. Production
   - Future hosted target (not provisioned yet)

- Future remote machine fronted by an API gateway/load balancer, with Apache APISIX as the preferred edge layer
- Deployment automation is intentionally deferred until a persistent remote environment exists

## Shared Remote Queue Server (Docker, Optional Audit Transport)

This repository now includes a Dockerized RabbitMQ service that can act as a shared remote queue endpoint for one or more app instances. The queue is optional infrastructure for the app's audit transport and is not required for the core identity flows to work. The application now includes an optional RabbitMQ-backed audit transport with local async persistence fallback, so the service can keep running when the queue is unavailable or disabled.

- Compose service name: `queue-dev`
- Standalone compose file: `compose.queue.yaml`
- AMQP port: `5672` (configurable)
- Management UI port: `15672` (configurable)
- Persistent queue data volume: `wl-chat-devdocker-rabbitmq-data`

This is infrastructure-only setup for the optional RabbitMQ-backed audit transport. The application can still run in local mode when the queue is unavailable or disabled.

Queue topology is provisioned at broker startup from repository-managed definitions under `config/rabbitmq/`:

- Exchange: `audit.events`
- Dead-letter exchange: `audit.events.dlx`
- Queue: `audit.events`
- Dead-letter queue: `audit.events.dlq`

### Queue secrets and network settings

Set these in `scripts/config/local.secrets.env` (or via `WL_CHAT_SECRETS_FILE`):

```bash
WL_CHAT_QUEUE_USERNAME=wl_chat_queue
WL_CHAT_QUEUE_PASSWORD=replace_with_queue_password
WL_CHAT_QUEUE_VHOST=/
WL_CHAT_QUEUE_PORT=5672
WL_CHAT_QUEUE_MGMT_PORT=15672
WL_CHAT_QUEUE_HOST_IP=0.0.0.0
WL_CHAT_QUEUE_MGMT_HOST_IP=127.0.0.1

# Optional RabbitMQ-backed audit transport
WL_CHAT_AUDIT_RABBITMQ_ENABLED=true
WL_CHAT_AUDIT_RABBITMQ_HOST=127.0.0.1
WL_CHAT_AUDIT_RABBITMQ_HOST_CANDIDATES=queue-dev,127.0.0.1,host.docker.internal
WL_CHAT_AUDIT_RABBITMQ_PORT=5672
WL_CHAT_AUDIT_RABBITMQ_USERNAME=wl_chat_queue
WL_CHAT_AUDIT_RABBITMQ_PASSWORD=replace_with_audit_password
```

Notes:

- `WL_CHAT_QUEUE_HOST_IP=0.0.0.0` exposes AMQP to other hosts that can reach this machine.
- Keep `WL_CHAT_QUEUE_MGMT_HOST_IP=127.0.0.1` unless remote UI access is explicitly required.
- `WL_CHAT_AUDIT_RABBITMQ_HOST_CANDIDATES` is an optional ordered fallback list.
- With `queue-dev,127.0.0.1,host.docker.internal`, the same config works for both host-local and DevDocker runs.
- For a fully remote broker, set `WL_CHAT_AUDIT_RABBITMQ_HOST` and optionally `WL_CHAT_AUDIT_RABBITMQ_HOST_CANDIDATES` to remote-only values.

### Start/stop queue server only

```bash
./scripts/cicd/queue-up.sh
./scripts/cicd/queue-down.sh
```

By default these scripts use `compose.queue.yaml` so you can run queue infrastructure independent of app and DB.

Optional override:

```bash
export WL_CHAT_QUEUE_COMPOSE_FILE=/absolute/path/to/compose.queue.yaml
```

### Start full DevDocker stack (app + DB + shared queue)

```bash
./scripts/cicd/devdocker-up.sh
./scripts/cicd/devdocker-down.sh
```

## Current Runtime Configuration

Primary runtime defaults are defined in `src/main/resources/application.properties`:

- JDBC URL default: `jdbc:sqlserver://localhost:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=true`
- Dev Services: disabled (`quarkus.datasource.devservices.enabled=false`)
- Flyway startup migration: disabled by default (`quarkus.flyway.migrate-at-start=false` unless overridden)

The application also supports external runtime config files so values can be changed without rebuilding the jar.

- Default runtime override file: `config/application.properties`
- Optional override location: `WL_CHAT_CONFIG_FILE=/absolute/path/to/application.properties`
- Restart required: yes
- Rebuild required: no

Config precedence for local development is:

1. Environment variables
2. `scripts/config/local.secrets.env` or `WL_CHAT_SECRETS_FILE`
3. `config/application.properties` or `WL_CHAT_CONFIG_FILE`
4. `src/main/resources/application.properties`

Use the external runtime file for values such as ports, health toggles, log levels, or environment-specific runtime overrides. Keep secrets in `scripts/config/local.secrets.env` or another file pointed to by `WL_CHAT_SECRETS_FILE`.

## Database Initialization and Migration Structure

The repository uses a two-phase SQL setup model:

1. Bootstrap (admin, one-time per environment)

- Flyway versioned scripts in `scripts/database/flyway/master`
- Responsibility: create `wl_chat` database if missing

2. Flyway migrations (versioned, repeatable process)

- Bootstrap location (master DB): `scripts/database/flyway/master`
- Application schema location (wl_chat DB): `scripts/database/flyway/wl_chat`
- `V20260808110000__create_wl_chat_database.sql` (master)
- `V20260808110500__create_app_login_and_user.sql` (wl_chat)
- `V20260808111000__grant_app_permissions.sql` (wl_chat)
- `V20260808111500__create_logical_schemas.sql` (wl_chat)
- New migrations use `VYYYYMMDDHHMMSS__description_in_snake_case.sql`.

## Setup Scripts: Use Cases and Required Order

Pick one environment path at a time.

### One-Time Secrets File Setup (recommended)

Create a local secrets file once so you do not need to export passwords every run.

```bash
cp scripts/config/local.secrets.env.example scripts/config/local.secrets.env
```

Then edit `scripts/config/local.secrets.env` with your real values (at minimum `MSSQL_SA_PASSWORD`).

Notes:

- Setup scripts auto-load `scripts/config/local.secrets.env` if it exists.
- Setup scripts also support `WL_CHAT_SECRETS_FILE` for alternate file paths (used by self-hosted runner deploys).
- The application now also auto-loads `scripts/config/local.secrets.env` during startup, so both `./scripts/cicd/run-quarkus-dev.sh` and plain `./mvnw quarkus:dev` pick up the same local DB credentials.
- `scripts/config/local.secrets.env` is gitignored and should not be committed.

### Runtime Override File Setup (optional)

Use this when you want to change non-secret runtime config without rebuilding.

Default file location:

```bash
mkdir -p config
```

Edit:

```bash
config/application.properties
```

Example values:

```properties
quarkus.http.port=8080
quarkus.log.level=DEBUG
quarkus.datasource.health.enabled=true
```

Alternate file path:

```bash
export WL_CHAT_CONFIG_FILE=/absolute/path/to/application.properties
```

After editing the file, restart the application. A rebuild is not required.

### Script Reference

Use these scripts instead of remembering the full bootstrap and migration sequence.

- `./scripts/database/bootstrap-local.sh`
  - Creates the local database if it does not already exist.
- `./scripts/database/migrate-local.sh`
  - Applies schema migrations to the local `wl_chat` database.
- `./scripts/database/init-local.sh`
  - Convenience wrapper for local bootstrap + migrate.
  - Set `WL_CHAT_RESET_DB=true` to reset `wl_chat` and bootstrap Flyway history before reapplying migrations (destructive).
- `./scripts/cicd/run-quarkus-dev.sh`
  - Starts Quarkus dev mode, loads local secrets, and writes logs under `logs/chat_backend`.
- `./scripts/database/bootstrap-devdocker.sh`
  - Creates the DevDocker database if it does not already exist.
- `./scripts/database/migrate-devdocker.sh`
  - Applies schema migrations to the DevDocker database.
- `./scripts/database/init-devdocker.sh`
  - Convenience wrapper for DevDocker bootstrap + migrate.
  - Set `WL_CHAT_RESET_DB=true` to reset `wl_chat` and bootstrap Flyway history before reapplying migrations (destructive).
- `./scripts/cicd/devdocker-up.sh`
  - Starts the DevDocker stack and initializes the database.
- `./scripts/cicd/devdocker-down.sh`
  - Stops the DevDocker stack.

## Build, Test, Run, Format, and Shutdown

Build and verify:

```bash
./mvnw clean verify
```

Run tests only:

```bash
./mvnw test
```

Run application locally:

```bash
./mvnw quarkus:dev
```

Format source:

```bash
./mvnw spotless:apply
```

Shutdown local app and infrastructure:

```bash
# Stop Quarkus dev mode with Ctrl+C in its terminal
./scripts/cicd/devdocker-down.sh
```

### Local Path (app from terminal, DB on localhost:1433)

Use this for day-to-day coding when you run Quarkus directly from your machine.

Required env vars:

- `MSSQL_SA_PASSWORD` (required)
- `WL_CHAT_DB_PORT` (optional, default `1433`)
- `WL_CHAT_DB_NAME` (optional, default `wl_chat`)
- `WL_CHAT_DB_USERNAME` (optional, default `wl_chat_app`)
- `WL_CHAT_DB_PASSWORD` (required)

You can provide these via `scripts/config/local.secrets.env` instead of exporting in terminal.

Script order:

1. Start SQL Server for Local mode.
2. `./scripts/database/bootstrap-local.sh`

- One-time/admin step per environment: creates database if missing.

3. `./scripts/database/migrate-local.sh`

- Applies Flyway versioned migrations.

4. `./mvnw -q -DskipTests compile`
5. `./scripts/cicd/run-quarkus-dev.sh`

- This startup script sets `WL_CHAT_LOG_DIR` to `logs` before launching Quarkus.
- This startup script sets `WL_CHAT_LOG_DIR` to `logs/<yyyy>/<MM>` before launching Quarkus.
- Active app log: `logs/<yyyy>/<MM>/chat_backend/chatback.log`
- Active HTTP/audit transport log: `logs/<yyyy>/<MM>/chat_backend/http-audit.log`
- Rolled app logs: `logs/<yyyy>/<MM>/chat_backend/chatback.log.<yyyy-MM-dd>.gz` (intraday size rollover appends backup index)
- Rolled HTTP/audit logs: `logs/<yyyy>/<MM>/chat_backend/http-audit.log.<yyyy-MM-dd>.gz` (intraday size rollover appends backup index)

Direct startup is also supported:

- `./mvnw quarkus:dev`
  - Useful from an IDE terminal when you do not need the wrapper script.
  - Local DB credentials are still loaded from `scripts/config/local.secrets.env`.
  - Runtime overrides are also loaded from `config/application.properties` by default.
  - The wrapper script remains the preferred path when you want the log directory exported consistently.

Shortcut for steps 2 and 3:

- `./scripts/database/init-local.sh`
  - Runs bootstrap then migrate in order.

### DevDocker Path (app + DB both in Docker)

Use this when you want a remote-like local environment.

Required env vars:

- `MSSQL_SA_PASSWORD` (required)
- `WL_CHAT_APP_PORT` (optional, default `8081`)
- `WL_CHAT_DB_USERNAME` (optional, default `wl_chat_app`)
- `WL_CHAT_DB_PASSWORD` (required)

You can provide these via `scripts/config/local.secrets.env` instead of exporting in terminal.

Preferred script order:

1. `./scripts/cicd/devdocker-up.sh`

- Starts DevDocker SQL Server.
- Runs `./scripts/database/init-devdocker.sh` (bootstrap + migrate).
- Starts DevDocker app container.

Manual equivalent (if needed for troubleshooting):

1. `./scripts/database/bootstrap-devdocker.sh`
2. `./scripts/database/migrate-devdocker.sh`
3. `./scripts/database/init-devdocker.sh` (wrapper for the two above)

Stop DevDocker stack:

1. `./scripts/cicd/devdocker-down.sh`

### Local Trigger Scripts (optional)

Use these only if you want branch-based pre-push checks locally.

1. `./scripts/cicd/install-git-hooks.sh`

- Installs `.githooks/pre-push` as active hooks path.

2. `scripts/cicd/local-trigger.sh <branch>`

- For `main`: runs local DB init + compile.
- For `production`: runs production deploy placeholder.

## Health Endpoints

When app is running locally:

- `GET http://localhost:8080/q/health/live`
- `GET http://localhost:8080/q/health/ready`
- `GET http://localhost:8080/q/health`

Notes:

- `live` can be `UP` even when DB credentials are wrong.
- `ready` and aggregate `health` report DB connectivity and return `DOWN` if DB auth fails.

## CI/CD Posture (Current)

Workflows in `.github/workflows` currently include both DB validation and a self-hosted Dev deployment path:

- `db-local-bootstrap-migrate.yml`
  - Validates bootstrap + migration flow in an ephemeral SQL Server container inside GitHub Actions runner

- `db-remote-bootstrap-migrate.yml`
  - Manual workflow scaffold for remote SQL bootstrap/migration
  - Kept as deferred guidance until a persistent hosted environment is available

- `flow-smoke-gate.yml`
  - Trigger: pull requests and pushes to `main` for backend/database/postman flow changes
  - Runner: `ubuntu-latest`
  - Execution order: start DevDocker stack -> validate Postman artifacts -> run Newman `Run-all API smoke journey` -> upload Newman artifacts -> stop stack
  - Queue transport is disabled for this gate (`WL_CHAT_ENABLE_QUEUE=false`, `WL_CHAT_AUDIT_RABBITMQ_ENABLED=false`), but compose interpolation still requires `WL_CHAT_QUEUE_PASSWORD`, so the workflow sets a non-secret dummy value
  - CI Flyway bootstrap/migrate scripts use CI-safe defaults (`sqlserver-dev:1433` on `wl-chat-devdocker_default`) instead of relying on `host.docker.internal:1434`

- `dev-self-hosted-build-migrate-deploy.yml`
  - Trigger: push to `main` or manual dispatch
  - Runner: `self-hosted` (must run on this Dev machine)
  - Execution order: build app image -> start/reuse Dev SQL container -> bootstrap DB (idempotent) -> run Flyway migrations -> roll app container -> health checks
  - Default behavior preserves SQL volume and user data across deploys (migration-only updates)
  - Optional manual reset: run with `workflow_dispatch` input `reset_db=true` to wipe SQL volume before deploy (destructive)
  - Uses runner-local secrets file path from `WL_CHAT_SECRETS_FILE` (default workflow value: `/Users/x3phantonpx3/.wl-chat/local.secrets.env`)

### Self-Hosted Runner Secrets File

For GitHub-triggered Dev deploys, place a secrets file on the runner host (outside repo), for example:

```bash
/Users/x3phantonpx3/.wl-chat/local.secrets.env
```

Template file:

- `scripts/config/runner.local.secrets.env.example`

Required entries:

- `MSSQL_SA_PASSWORD`
- `WL_CHAT_DB_USERNAME` (recommended)
- `WL_CHAT_DB_PASSWORD` (recommended)
- `WL_CHAT_APP_PORT` (optional)

The deploy workflow exports `WL_CHAT_SECRETS_FILE` so scripts read this host-local file directly.

## Local Trigger Mirroring (Branch-Based)

To mirror branch-based remote triggers locally, this repository includes a Git `pre-push` hook:

- `main` push trigger:
  - Runs `./scripts/database/init-local.sh`
  - Runs `./mvnw -q -DskipTests compile`
- `production` push trigger:
  - Runs `./scripts/cicd/production-deploy-placeholder.sh`
  - Intended to be replaced later with real AWS deployment steps

Install local hooks once:

```bash
./scripts/cicd/install-git-hooks.sh
```

Key files:

- `.githooks/pre-push`
- `scripts/cicd/local-trigger.sh`
- `scripts/cicd/production-deploy-placeholder.sh`

Temporarily skip local triggers for one push:

```bash
WL_CHAT_SKIP_LOCAL_TRIGGERS=1 git push
```

## Authoritative Documentation

Current application development version: `0.8.0-SNAPSHOT`. Application releases follow Semantic
Versioning; API generations and Flyway migration versions remain independent.

Milestone 3 status snapshot (2026-08-11): session schema, login/logout/filter behavior, and the administrative revoke-all-sessions API are implemented and validated.

Milestone 4 status snapshot (2026-08-13): conversation persistence, authenticated user discovery, direct and group conversation APIs, membership authorization, SQL-backed tests, and Postman coverage are implemented and validated under ADR-0013.

Milestone 5 status snapshot (2026-08-13): durable authenticated text messaging, sender-scoped
idempotency, per-conversation sequence allocation, forward history pagination, editing, soft deletion,
safe audit metadata, SQL Server rollback guarantees, and concurrency coverage are implemented and
validated. Delivery/read acknowledgements and WebSockets remain assigned to later milestones.

Milestone 6 status snapshot (2026-08-13): explicit per-user monotonic delivery/read
acknowledgements, derived unread counts, own-position queries, sender-only aggregate status, safe
auditing, SQL Server concurrency coverage, and the executable reconnect-reconciliation journey are
implemented under ADR-0014. Transport publication remains only a signal; WebSockets and per-device
receipts remain deferred.

Milestone 7 status snapshot (2026-08-14): unified RFC 9457 problems, generated and committed
OpenAPI, OpenAPI-to-Postman operation checks, strict request and pagination limits, cursor-paginated
member traversal, replica-safe SQL-backed login throttling, trusted proxy resolution, structured
JSON logs, and validated request/trace correlation are implemented under ADR-0015.

Milestone 8 status snapshot (2026-08-22): authenticated WebSocket signaling, multi-connection
registration, active-member-only post-commit fan-out, message and delivery/read events, inbound
delivery/read acknowledgements, heartbeat support, session-revocation disconnects, and durable REST
reconciliation are implemented under ADR-0016. SQL Server remains authoritative; socket delivery is
never treated as durable delivery proof.

- Detailed implementation runbook:
  - `docs/development-guide/milestone-0-sql-server-step-by-step.md`
- Milestone 1 database foundation runbook:
  - `docs/development-guide/milestone-1-database-foundation-step-by-step.md`
- Milestone 2 identity and invitations runbook:
  - `docs/development-guide/milestone-2-identity-and-invitations-step-by-step.md`
- Milestone 3 sessions and authentication runbook:
  - `docs/development-guide/milestone-3-sessions-step-by-step.md`
- Milestone 4 conversations and membership authorization runbook:
  - `docs/development-guide/milestone-4-conversations-step-by-step.md`
- Milestone 4 conversation identity, membership, and discovery decision:
  - `docs/architecture/decision/ADR-0013-define-conversation-identity-membership-and-discovery.md`
- Milestone 5 durable messaging, history, and mutation runbook:
  - `docs/development-guide/milestone-5-messaging-step-by-step.md`
- Milestone 6 delivery/read state and reconciliation runbook:
  - `docs/development-guide/milestone-6-delivery-and-read-state-step-by-step.md`
- Milestone 6 per-user cursor and status-visibility decision:
  - `docs/architecture/decision/ADR-0014-use-per-user-delivery-and-read-cursors.md`
- Milestone 7 API hardening runbook:
  - `docs/development-guide/milestone-7-api-hardening-step-by-step.md`
- Milestone 7 HTTP contract and authentication-throttling decision:
  - `docs/architecture/decision/ADR-0015-harden-http-contracts-and-authentication-throttling.md`
- Milestone 8 WebSockets and real-time signaling runbook:
  - `docs/development-guide/milestone-8-websockets-step-by-step.md`
- Release history:
  - `CHANGELOG.md`
- Versioning and changelog policy:
  - `docs/development-guide/versioning-and-changelog-policy.md`
- System specification and architecture baseline:
  - `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`
- Environment lifecycle and rollout plan:
  - `docs/operations/environment-strategy-and-rollout-plan.md`
- SQL Server principal and permission baseline:
  - `docs/database/sql-server-principals-and-permissions.md`
- Postman artifact workflow:
  - `postman/README.md`

## Postman Workflow

Postman artifacts are version-controlled and should be updated whenever API contracts change.

Authoritative contract direction used for Postman maintenance:

- Java resources and DTOs under `src/main/java` generate `docs/api/openapi.json` and
  `docs/api/openapi.yaml`; Postman discovery and validation consume that operation inventory

Committed artifacts:

- `postman/collections/chat-backend.postman_collection.json`
- `postman/collections/chat-backend-user-flows.postman_collection.json`
- `postman/environments/local.example.postman_environment.json`
- `postman/environments/devdocker.example.postman_environment.json`
- `postman/environments/production.example.postman_environment.json`

Local-only Postman Cloud config:

- copy `postman/config.properties.example` to `postman/config.properties`
- keep `postman/config.properties` untracked/ignored

Local validation (no cloud key required):

```bash
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
```

Cloud synchronization:

```bash
./scripts/postman/inspect-postman.sh
./scripts/postman/sync-postman.sh --dry-run
./scripts/postman/sync-postman.sh
./scripts/postman/sync-postman.sh --check-drift
```

CI and manual workflow behavior:

- PR validation workflow runs local Postman artifact checks.
- PR validation workflow runs strict cloud drift checks when POSTMAN\_\* secrets are configured in GitHub.
- Manual Postman cloud workflow is guarded and runs only when explicitly dispatched.

For full setup and Native Git/Desktop guidance, see `postman/README.md`.

## Quick Local Start

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"

docker compose up -d --wait sqlserver
./scripts/database/init-local.sh

./mvnw -q -DskipTests compile
./scripts/cicd/run-quarkus-dev.sh
```

Then verify:

```bash
curl -i http://localhost:8080/q/health/live
```

Shortest local script-driven path after secrets are configured:

```bash
./scripts/database/init-local.sh
./scripts/cicd/run-quarkus-dev.sh
```

## Quick DevDocker Start

```bash
export WL_CHAT_APP_PORT=8080   # optional, default is 8081

./scripts/cicd/devdocker-up.sh
```

Then verify:

```bash
curl -i http://localhost:${WL_CHAT_APP_PORT:-8081}/q/health/live
curl -i http://localhost:${WL_CHAT_APP_PORT:-8081}/q/health/ready
```

Stop DevDocker stack:

```bash
./scripts/cicd/devdocker-down.sh
```
