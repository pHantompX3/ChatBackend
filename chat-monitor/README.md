# ChatMonitor

ChatMonitor is the local monitoring companion for ChatBackend. It is intentionally independent from
the messaging runtime: ChatBackend must remain available if ChatMonitor is stopped, broken, or
removed. X2 builds and installs this project on the administrator's macOS workstation; X4 supplies
the real production source credential, network path, targets, thresholds, and alert destinations
after X3 deploys the production candidate.

## Current implementation status

X2 is implemented and locally verified. The reproducible monitoring foundation provides:

- Vite vanilla-JavaScript dashboard;
- loopback-only Fastify API and static-file server;
- validated fixture/SQL configuration with verified-TLS enforcement;
- structured redacted logs and response security headers;
- a forward-only, least-privilege SQL projection and `wl_chat_monitor` principal;
- source-neutral validated synthetic and SQL adapters;
- immutable checksummed SQLite migrations in WAL mode;
- transactional projected-event, cursor, state, gap, and hourly/daily aggregate persistence;
- a server-managed multi-environment registry with concurrent independent collectors, durable
  per-environment data partitioning, and direct dashboard switching;
- bounded non-overlapping collection with restart catch-up, idempotency, explicit outage/failure/gap
  states, and aggregate-before-detail retention;
- Node, Fastify-injection, and Playwright tests;
- production-browser bundle secret scanning; and
- dedicated CI and agent-navigation contracts;
- bounded request, latency, expected-4xx, server-failure, operation, and sanitized-failure APIs;
- an environment-themed operator dashboard with freshness-first status, time filters, Chart.js
  trends, operational tables, and explicit empty/unavailable states;
- durable local alert states with confirmation, acknowledgement, bounded suppression, recovery
  confirmation, and privacy-safe local delivery;
- checksum-verified SQLite backup and restore commands; and
- pinned loopback-only Uptime Kuma and Beszel hub definitions with independent named volumes.

Production-source credentials, production host enrollment, live notification destinations, and
final production characterization remain X4 responsibilities after X3 deploys production.

The accepted operational runtime is Docker Compose. Native npm commands remain available for
development and automated tests, but they are not the durable workstation service lifecycle.

## Prerequisites

- Node.js `22.13.0` or newer within major version 22 (validated with `22.17.0`)
- npm major version 11 (validated with `11.6.0`)
- Docker Desktop with Docker Compose v2 for the persistent workstation runtime

## First run in fixture mode

Fixture mode is the safe default and needs no SQL Server or credential.

```bash
cd chat-monitor
npm ci
cp .env.example .env
npm run verify:config
npm run dev
```

Open `http://127.0.0.1:5173`. The Vite server proxies only local `/api` calls to the Fastify service
on `127.0.0.1:8090`.

## Persistent Docker runtime

Build and start the production-shaped fixture service:

```bash
npm run container:config
npm run container:up
npm run container:status
```

Open `http://127.0.0.1:8090`. Compose publishes the container only on host loopback. The container
uses `restart: unless-stopped`, a health check, bounded Docker logs, a read-only root filesystem, and
the named `chat-monitor-data` volume. `docker compose down` replaces no state; only an explicit
volume deletion destroys the SQLite history.

```bash
npm run container:logs
npm run container:stop
npm run container:down
```

Create and restore a consistent verified backup:

```bash
npm run backup
npm run restore -- var/backups/chat-monitor-<timestamp>.sqlite
```

The restore command stops ChatMonitor, verifies the SHA-256 sidecar and SQLite integrity, preserves
the prior database as a pre-restore recovery copy, restores into the named volume, and waits for the
service to become healthy.

The optional monitoring hubs are independently managed and loopback-only:

```bash
npm run tools:config
npm run tools:up
npm run tools:status
npm run tools:down
```

`npm start` remains the native production-build smoke command and never launches Vite.

The first collection cycle runs immediately. Native fixture development persists to
`var/data/chat-monitor.sqlite`; the Docker runtime persists `/var/lib/chat-monitor` in the named
volume. SQL mode additionally requires the projection-only principal, password and CA files under
the ignored `secrets/` directory, and the verified TLS settings documented in `.env.example` and
`secrets/README.md`.

## Multiple environments

ChatMonitor can collect local, test, staging, and production sources concurrently. Copy
`environments/environments.example.json` to the ignored `environments/environments.json`, keep each
SQL password in a separate ignored file under `secrets/`, and set:

```text
CHAT_MONITOR_ENVIRONMENTS_FILE=/run/environments/environments.json
```

Then recreate the Compose service. The dashboard obtains the available environments from the local
API and presents a selector. Switching the selector changes only the displayed partition; it does
not stop, reconfigure, or delay any collector. IDs are durable keys for cursors, state, projected
events, and aggregates, so they must remain stable. The browser receives only ID, label,
classification, source mode, and collection state—not SQL coordinates, usernames, file paths, or
credentials. The dashboard palette follows the selected classification: local is green,
development/test/staging are blue, and production is gold.

An environment declared with `mode: "planned"` is a visible, intentionally unavailable target. It
starts no collector and cannot present fixture data as operational evidence; replace it with a
verified SQL profile only after that deployment exists.

## Verification

```bash
npm ci
npm run verify
npx playwright install chromium
npm run test:browser
```

`npm run verify` performs JavaScript linting, Node unit/integration tests, the immutable Vite build,
and a scan proving that server-side SQL configuration is absent from browser assets.

## Documentation routes

- [Architecture](docs/architecture.md)
- [Security model](docs/security.md)
- [Metrics catalog](docs/metrics-catalog.md)
- [Operations](docs/operations.md)
- [Agent instructions](AGENTS.md)

Do not use the complete X2 plan as the everyday operator guide; these project-local documents are
the progressive-disclosure entrypoints for implementation and operation.
