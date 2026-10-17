# ChatMonitor Architecture

## Runtime boundary

ChatMonitor is a companion system, not a ChatBackend module. Its only ChatBackend dependency
is the read-only, bounded `audit.monitoring_http_event` SQL projection created in X2-B. ChatBackend never calls ChatMonitor and does
not wait for it.

```text
server-only environment registry
        |
        +--> environment A source -> independent collector --+
        +--> environment B source -> independent collector --+--> environment-partitioned SQLite
                                                             +--> bounded local API
                                                             +--> switchable Vite dashboard
```

X2 implements the process, configuration, API/static boundary, projection-only SQL principal,
source adapters, SQLite store, transactional collectors, aggregation/retention, bounded operational
read APIs, dashboard, collector-cycle-driven alert state, and recovery commands. Dashboard reads do
not advance alert confirmation state.

Each configured environment has a stable ID, classification, source adapter, scheduler, cursor, and
collector state. All schedulers remain active concurrently. The dashboard selector is a read-model
choice, not a runtime source switch. Projected-event uniqueness and aggregate keys include the
environment ID, preventing cloned databases or repeated event IDs from mixing environments.

The accepted workstation runtime is one non-root container managed by Docker Compose. The container
has a read-only root filesystem and writes only SQLite state to the named `chat-monitor-data` volume.
Compose publishes the internal listener exclusively as `127.0.0.1:8090` on the host. ChatMonitor
does not mount the Docker socket and cannot control ChatBackend or other containers.

## Project boundaries

- `src/client/` contains browser-only modules and same-origin API calls.
- `src/server/` owns configuration, source credentials, HTTP serving, collection, and persistence.
- `src/shared/` contains JSON-safe contract constants and shapes only; it cannot import server or
  browser implementations.
- `config/` validates global process input and the server-only environment registry before
  composition.
- `migrations/sqlite/` contains ordered immutable local-store migrations; the runner records and
  verifies SHA-256 checksums before startup.
- `test/fixtures/` contains synthetic, identity-free telemetry.

There are no relative source imports into the Java application. Moving `chat-monitor/` into a future
standalone repository must not change SQL projection, normalized-envelope, API, SQLite, or dashboard
contracts.

## Dependency direction

The dashboard calls only same-origin `/api/v1/*` routes. It discovers safe environment metadata from
`GET /api/v1/environments` and reads an explicit partition from
`GET /api/v1/environments/{environmentId}/status`, plus bounded overview, metrics, operations,
failures, and alert routes under the same environment path. The legacy `GET /api/v1/status` alias reads the
configured default environment. The server may use shared wire contracts;
the client may use browser-safe shared wire contracts. Source adapters emit the normalized envelope
and never leak driver-specific rows into repositories or APIs. Persistence is authoritative for
monitor history; charts are replaceable presentation adapters.

## Lifecycle

The container entrypoint validates configuration and every environment profile, migrates SQLite,
composes the server, serves `dist/client`, completes one collection cycle per environment, and
schedules later non-overlapping cycles independently. It listens
on all interfaces only inside the container so Docker can forward the port; the host publication is
loopback-only. SIGINT/SIGTERM closes HTTP, prevents new collection, waits for the active cycle,
closes every source and SQLite, and exits. Docker Compose owns health, restart, logs, replacement, and
persistent-volume attachment. Native `npm start` remains a loopback-only development smoke path.

Uptime Kuma and the Beszel hub are optional adjacent workstation tools in an independent Compose
project. They do not share ChatMonitor's database or network, and neither receives a Docker socket.
Production enrollment and live destinations remain an X4 concern.
