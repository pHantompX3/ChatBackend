# ChatMonitor Operations

## Development lifecycle

Install and validate from the project directory:

```bash
npm ci
cp .env.example .env
npm run verify:config
npm run verify
```

For development, run `npm run dev` and browse `http://127.0.0.1:5173`. Native `npm start` is a
production-build smoke path, not the durable workstation lifecycle.

## Docker-managed workstation lifecycle

```bash
npm run container:config
npm run container:up
npm run container:status
curl --fail --silent http://127.0.0.1:8090/api/v1/health/ready
```

Compose publishes `8090` only to host loopback. Inside the isolated container the process binds to
`0.0.0.0:8090`, which is required for Docker port forwarding and is permitted only when
`CHAT_MONITOR_DEPLOYMENT_MODE=container`. The image runs as UID/GID 10001, drops capabilities,
prevents privilege escalation, uses a read-only root filesystem and bounded JSON logs, and writes
only to the `chat-monitor-data` named volume.

Use Docker lifecycle controls rather than killing the Node process:

```bash
npm run container:logs
npm run container:stop
npm run container:up
npm run container:down
```

`container:stop`, image replacement, and `container:down` preserve the named volume. Never run
`docker compose down --volumes` or remove `chat-monitor-data` unless intentionally destroying all
monitor history after a verified backup.

Confirm binding from another terminal:

```bash
lsof -nP -iTCP:8090 -sTCP:LISTEN
curl --fail --silent http://127.0.0.1:8090/api/v1/health/ready
```

The listener must show `127.0.0.1` (or explicit IPv6 loopback), never `0.0.0.0`, `::`, a LAN
address, or a public interface.

## Environment registry and switching

Use `environments/environments.example.json` as the shape reference and keep the active
`environments/environments.json` ignored. Configure one stable lowercase ID per source, one
human-readable label, an explicit `local`, `development`, `test`, `staging`, or `production`
classification, and either a fixture or SQL adapter. A known but undeployed target may instead use
`mode: "planned"` with a bounded operator-facing reason. SQL profiles may reference only password
files; do not place passwords in the JSON document.

The container reads the registry at startup. After editing it, run `npm run container:up` to recreate
the service. Removal or renaming does not delete prior SQLite data, but a renamed environment starts
with a new cursor and history partition. All configured environments collect concurrently. The UX
selector and optional `?environment=<id>` URL choose a view only, and the browser remembers the last
valid selection locally. Production is visibly classified in the UX to reduce context mistakes.
An undeployed planned environment remains selectable for context but reports `SOURCE_UNAVAILABLE`,
retains no fabricated event count, and starts no collector until its profile is replaced by a real
SQL source.

When environments use different private SQL authorities, combine their public CA certificates into
the PEM bundle referenced by `NODE_EXTRA_CA_CERTS`; never add CA private keys. Confirm the registry
API exposes no host, database, username, password path, or secret before onboarding a source.

## Failure behavior

Invalid configuration exits before listening and reports only field paths/messages, never values.
If `dist/client/` is absent, health/status APIs remain inspectable while `/` returns a bounded 503
that instructs the operator to build. One source outage changes only that environment to
`SOURCE_UNAVAILABLE`; other collectors and views remain available. A
local transaction/validation failure changes it to `COLLECTION_FAILED`; a cursor older than the
available source boundary changes it to `HISTORY_GAP`. SIGINT/SIGTERM closes HTTP, stops scheduling,
waits for every active bounded cycle, then closes all source and SQLite resources.

## State, backup, and service installation

Native development writes ignored state beneath `var/data/`; the accepted service writes durable
state to the Docker-managed `chat-monitor-data` volume. SQLite runs in WAL mode with full synchronous
commits, foreign keys, a bounded busy timeout, and checked immutable migrations. Never copy the live
database or volume directory directly. Use the online SQLite backup API and checksum sidecar:

```bash
npm run backup
npm run restore -- var/backups/chat-monitor-<timestamp>.sqlite
```

Restore refuses paths outside the ignored `var/backups/` directory, verifies both SHA-256 and SQLite
integrity, stops the service, keeps the displaced database as a `pre-restore` recovery copy, restores
the verified file, and waits for health. Copy backup plus sidecar off this workstation periodically;
the local backup directory is recovery staging, not an off-host backup boundary.

For an upgrade, run `npm ci`, `npm run verify`, `npm run package`, and `npm run container:up`. The
named volume survives image/container replacement. Roll back by checking out the last accepted
source or unpacking its verified package and rerunning `npm run container:up`; immutable SQLite
migrations are forward-compatible and are not reversed. Restore a pre-upgrade backup only when the
operator has explicitly accepted losing newer monitor history.

To uninstall the runtime without destroying history, run `npm run container:down`. Remove the image
separately if desired. Delete `chat-monitor-data` only after a verified backup and an explicit
decision to destroy retained history.

Docker Desktop starts at login and Compose `restart: unless-stopped` restores all accepted services
after the engine starts. On this administrator workstation the accepted candidate was built,
installed, restarted, backed up, restored, and returned healthy on 2026-10-16.

## Auxiliary monitoring hubs

`compose.monitoring-tools.yaml` pins Uptime Kuma 2.5.5 and Beszel hub 0.21.0. Both publish only to
loopback (`3001` and `8091`), use independent named volumes, bounded logs, no Docker socket, and
`restart: unless-stopped`. Manage them independently:

```bash
npm run tools:config
npm run tools:up
npm run tools:status
npm run tools:down
```

Their first-run administrator accounts remain local operator actions. X2 does not enroll production
agents or configure notification secrets. Back up their named-volume application data using each
product's documented export/backup procedure before upgrade; removing either stack cannot affect
ChatMonitor's authoritative `chat-monitor-data` volume.

## X4 handoff

Real production SQL credentials, connectivity, query-plan/lock evidence, production agents,
availability targets, live notifications, and production threshold approval belong to X4 after X3
delivers the independently accepted production candidate. Fixture or
hardened rehearsal success must not be described as production monitoring acceptance.
