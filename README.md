# ChatBackend

## Current Baseline

- Java: 25
- Quarkus: 3.33.2.1
- Build: Maven Wrapper (`./mvnw`)
- Database: Microsoft SQL Server 2022
- Active local database name: `wl_chat`
- Active app login: `wl_chat_app`

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
   - Deployment automation is intentionally deferred until a persistent remote environment exists

## Current Runtime Configuration

Primary runtime defaults are defined in `src/main/resources/application.properties`:

- JDBC URL default: `jdbc:sqlserver://localhost:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=true`
- Dev Services: disabled (`quarkus.datasource.devservices.enabled=false`)
- Flyway startup migration: disabled by default (`quarkus.flyway.migrate-at-start=false` unless overridden)

## Database Initialization and Migration Structure

The repository uses a two-phase SQL setup model:

1. Bootstrap (admin, one-time per environment)
   - Script: `scripts/database/bootstrap/V0__create_wl_chat_database.sql`
   - Responsibility: create `wl_chat` database if missing

2. Flyway migrations (versioned, repeatable process)
   - Location: `src/main/resources/db/migration`
   - `V1__create_app_login_and_user.sql`
   - `V2__grant_app_permissions.sql`

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
- `scripts/config/local.secrets.env` is gitignored and should not be committed.

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
5. `./mvnw quarkus:dev`

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

- Detailed implementation runbook:
  - `docs/development-guide/milestone-0-sql-server-step-by-step.md`
- System specification and architecture baseline:
  - `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`
- Environment lifecycle and rollout plan:
  - `docs/operations/environment-strategy-and-rollout-plan.md`

## Quick Local Start

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"

docker compose up -d --wait sqlserver
./scripts/database/init-local.sh

./mvnw -q -DskipTests compile
./mvnw quarkus:dev
```

Then verify:

```bash
curl -i http://localhost:8080/q/health/live
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
