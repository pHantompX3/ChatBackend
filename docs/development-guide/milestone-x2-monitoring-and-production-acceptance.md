# Milestone X2 — Monitoring Foundation

## Status

**Status:** Complete and locally verified on `codex/milestone-x2-monitoring-foundation`. Administrator-machine
deployment is in scope, while production onboarding remains X4 work after X3 deploys the production
candidate.
**Parent program:** [Milestone X — Production Activation](milestone-x-production-activation.md)  
**Required successor:** [Milestone X4 — Administrator Monitoring Onboarding and Production Acceptance](milestone-x4-admin-monitoring-and-production-acceptance.md)
**Last refined:** 2026-10-16

### Implementation ledger

| Slice | State | Verified result |
|---|---|---|
| X2-A | Implemented | Independent Node/Vite/Fastify project, loopback-only configuration, fixture shell, locked dependencies, CI, scoped agent context, and browser-safe build verification |
| X2-B | Implemented | Forward-only sanitized audit projection, projection-only `wl_chat_monitor` principal, hardened provisioning, and positive/negative migration integration tests |
| X2-C | Implemented | Checksummed SQLite migrations, fixture/SQL adapters, atomic event/cursor persistence, bounded non-overlapping catch-up, explicit recovery states, hourly/daily aggregation, and retention tests |
| X2-D | Complete | Bounded status/metric/operation/sanitized-failure APIs, freshness-first environment dashboard, Chart.js adapter, time filters, accessible semantics, security headers, and browser tests |
| X2-E | Complete | Durable alert state with confirmation, acknowledgement, bounded suppression, recovery confirmation, privacy-safe local sink, and pinned loopback-only Uptime Kuma/Beszel hub definitions |
| X2-F | Complete | Hardened persistent Compose runtime, stable lifecycle/package commands, checksum/integrity-verified SQLite backup/restore, replacement/rollback/uninstall procedure, workstation rehearsal, and explicit X4 handoff |

The candidate `(created_at,event_id)` production index remains deliberately unimplemented. X2-B
proved the bounded seek contract and least privilege; X4 must capture representative production plan,
size, lock, and audit-insert overhead evidence before an index is authorized. The X2-A through X2-F
review found no ChatBackend client-facing API, recovery, or UX contract change, so the canonical
client-integration guide requires no semantic update for these slices.

## 1. Purpose and boundary

Milestone X2 builds a production-capable monitoring foundation on the administrator's machine. It
owns ChatMonitor, its local source-neutral telemetry store, SQL-audit collection adapter, dashboard,
aggregation, retention, local alert-state engine, packaging, startup, backup/recovery, and fixture or
hardened-rehearsal verification. X4 owns connection to the X3 production candidate, live alert delivery,
operational exercises, and the `1.0.0` acceptance decision.

ChatBackend must continue operating when every X2 monitoring component is unavailable. X2 does not
add messaging-domain behavior, replicate production data, or permit monitoring tools to mutate
application state.

## 2. Entry decisions, dependency, and sequencing

Before X2 implementation begins:

1. X1 must provide stable production/rehearsal contracts and sanitized representative fixtures;
2. record the monitoring workstation OS, power/sleep and auto-start behavior, Node.js ownership,
   local data path, backup choice, maintenance owner, and permitted network path;
3. select accountable alert responders, destinations, escalation behavior, quiet-hours/maintenance
   behavior, and protected incident-evidence storage;
4. approve availability, latency, error-rate, disk, certificate, audit backlog/DLQ, backup age,
   collector freshness, and retention objectives after characterization;
5. decide whether an external or second-device heartbeat is warranted for workstation/LAN blind
   spots; and
6. confirm authoritative audit retention remains long enough for monitor catch-up and never below
   the accepted 62-day detailed-history floor without an explicit stakeholder decision.

The following implementation decisions are now frozen for X2:

| Decision | X2 value |
|---|---|
| Initial deployment host | The administrator's Apple-silicon macOS workstation |
| Runtime baseline | Installed Node.js `22.17.0` and npm `11.6.0`; `package.json` and preflight scripts enforce the supported major/runtime range |
| ChatMonitor exposure | Loopback-only `127.0.0.1:8090`; no public or LAN listener in X2 |
| Process lifecycle | Docker Desktop plus Docker Compose, with `restart: unless-stopped`, health checks, bounded Docker logs, and explicit lifecycle commands |
| Installed state root | Immutable container image plus Docker-managed `chat-monitor-data` named volume; ignored bind-mounted secret files remain outside the image and volume |
| Local persistence | SQLite in WAL mode, foreign keys enabled, bounded busy timeout, explicit migrations, and consistent backup API |
| Server/API | Fastify with explicit schemas and bounded same-origin routes |
| Source access | `mssql` driver using encrypted SQL Server connections and a dedicated projection-only principal |
| Validation | Zod at configuration, source-envelope, and internal boundary crossings; database constraints remain authoritative for persisted invariants |
| SQLite driver | `better-sqlite3`, isolated behind the persistence adapter so native-module replacement does not alter domain contracts |
| Dashboard/charting | Vite vanilla JavaScript, standards-based HTML/CSS, and Chart.js behind the documented chart adapter |
| Logging | Structured redacted server logs; no message content, credentials, raw headers, or unrestricted source metadata |
| Automated verification | Node test runner for server/domain tests, targeted DOM tests for browser-safe modules, and Playwright for the built-dashboard/accessibility smoke path |
| Availability/host tools | Uptime Kuma and Beszel remain separate bounded monitoring layers; their local hub deployment is X2, while X3 prepares any production-side agent and X4 owns enrollment plus live notification delivery |

Exact package versions must be selected from supported releases at implementation time, committed in
`package-lock.json`, licence-checked, vulnerability-scanned, and recorded in ChatMonitor's dependency
inventory. The listed products and libraries are architectural selections, not permission to float
unreviewed versions.

The unresolved stakeholder choices in items 2–5 above do not block core implementation. X2 uses
deterministic local alert sinks, fixture thresholds, a loopback deployment, and documented defaults.
X4 replaces those placeholders with live production credentials, targets, alert destinations,
characterized thresholds, and operational ownership evidence.

ChatMonitor is built and accepted in X2 against deterministic fixtures and the hardened local
rehearsal. X4 validates the X3 production SQL projection, live credentials, network path, query plans,
locking behavior, thresholds, and alert destinations against the deployed candidate.

## 3. Preferred Monitoring Topology

```text
Administrator/development workstation (initial ChatMonitor home)
├── availability and certificate monitor
├── host/container monitoring hub
├── ChatMonitor audit-metrics collector and Vite dashboard
├── local monitoring database and aggregates
└── alert delivery adapters
          │ private LAN and normal HTTPS/WSS path
          ▼
Production machine
├── NGINX HTTPS/WSS edge
├── ChatBackend
├── SQL Server
├── RabbitMQ
├── lightweight host/container monitoring agent
└── backup, restore-age, and operational heartbeat producers
```

This topology deliberately separates production service operation from monitoring history. The
workstation monitor should test both:

- the private LAN path, which identifies host/application availability; and
- the normal HTTPS/WSS application address, which covers more of the DNS, certificate, proxy, and
  application path where the local router supports it.

An inside-LAN public-URL check does not prove external reachability. A separate external probe remains
optional and is valuable only if public reachability becomes an accepted production requirement.

### 3.1 Accepted monitoring blind spots

Without an external probe or always-on second device, monitoring and alert delivery can be unavailable
when the operator workstation is powered off, asleep, restarting, disconnected, or affected by the
same LAN/Internet outage as production. The monitoring UI must expose its own last successful check
and collection times so stale data is never presented as current health.

### 3.2 Monitoring workstation lifecycle and recovery

The implementation guide must record the workstation OS/version, host name, reserved/fixed network
address where required, power/sleep policy, local account that owns monitoring, Node.js installation
and version management, container runtime where used by third-party monitors, local firewall rules,
auto-start mechanism, data paths, backup paths, and update owner.

Monitoring services should bind to loopback by default. LAN exposure is allowed only when another
approved device needs the dashboard, the bind address and firewall sources are explicit, and local
authentication/TLS requirements are resolved. No monitoring dashboard or agent control port is
publicly exposed by default.

The workstation must not sleep during an accepted monitoring window unless that blind spot is
deliberately accepted. Docker Desktop starts at login; Compose restart policies then start the
availability/host-monitoring components, ChatMonitor service, and alert adapters after the engine
and required storage are available. Repeated startup failures must remain visible through health,
container state, and bounded Docker logs rather than creating an infinite silent restart loop.

Back up proportionally:

- ChatMonitor SQLite database using its consistent backup procedure;
- ChatMonitor configuration without source credentials unless separately encrypted;
- availability-monitor definitions and notification configuration;
- host-monitor hub configuration and enrollment metadata;
- alert-routing configuration;
- locally maintained CA certificates or trust material; and
- the version manifest needed to reinstall every monitoring component.

The monitoring workstation recovery exercise must start from a clean replacement directory or test
machine, reinstall pinned/reviewed versions, restore configuration and SQLite, reissue or retrieve the
read-only SQL credential through the normal secret mechanism, reconnect the production agent, resume
from the durable cursor, and prove whether the monitor is `CURRENT`, `CATCHING_UP`, or `HISTORY_GAP`.
No recovery step may require a credential stored only inside the failed workstation's unencrypted
monitoring database.

---

## 4. Monitoring Layers

### 4.1 Availability and path monitoring

Monitor:

- production host private-network reachability;
- public/private HTTPS endpoint as applicable;
- `/q/health/live`;
- `/q/health/ready`;
- TLS certificate validity and expiry;
- a bounded WebSocket handshake and ping/pong synthetic check;
- SQL Server and RabbitMQ reachability only over permitted private paths; and
- backup, off-host transfer, and restore-drill heartbeats.

Uptime Kuma is the preferred initial free self-hosted implementation because it matches the bounded
availability and certificate-check role without requiring an enterprise metrics stack. Before
committing its production version, validate its licence, supported installation method, authenticated
dashboard behavior, data backup/restore, notification adapters, certificate checks, WebSocket
monitor behavior, update path, and workstation auto-start. If it fails a required capability, record
the evidence and select the smallest replacement rather than installing overlapping uptime products.

The availability monitor owns checks and alert state; it does not ingest raw SQL audit events or
replace ChatMonitor. Configure monitor names from stable service roles rather than private addresses,
store no application bearer tokens unless a synthetic authenticated check is explicitly approved,
and avoid checks that create durable user data. Monitoring software and dashboards must not be
exposed publicly by default.

The initial availability configuration must define:

- check target and whether it exercises private LAN or normal client path;
- interval, timeout, retry count, failure confirmation window, and recovery confirmation;
- expected status/body/TLS properties without recording sensitive responses;
- maintenance windows and dependency relationships;
- notification severity/destination;
- certificate-expiry thresholds; and
- last successful check, state transition, and tested-alert evidence.

### 4.2 Host and container monitoring

Collect:

- CPU, load, memory, and swap;
- disk capacity and I/O for application, SQL, RabbitMQ, logs, audit, backups, and media volumes;
- network throughput;
- temperature where supported;
- container state, restart count, CPU, and memory; and
- monitoring-agent health.

Beszel is the preferred initial lightweight self-hosted implementation for the workstation hub and
production agent. Before committing its production version, validate its licence, supported host and
container metrics, agent authentication, encrypted transport or accepted LAN boundary, retention,
backup/restore, resource cost, update compatibility, alert behavior, and service auto-start. The
production agent receives only the authority needed to publish/read host and container metrics; it
must not receive Docker control, application secrets, SQL authority, or arbitrary remote-command
capability unless separately threat-modeled and accepted.

The host-monitor inventory must map every reported filesystem/volume/container to an operator-facing
name and capacity threshold. Suppress high-cardinality container IDs from long-lived labels where a
stable service name exists. Retention and polling should be proportional to a personal deployment and
must not consume the storage or CPU being monitored.

Prometheus/Grafana remain optional if application-level time series later justify their operational
cost. Do not install them in parallel merely because they are common; adoption requires a concrete
metric gap, retention/capacity plan, authentication, backup, and removal of duplicated ownership.

### 4.3 Application and audit-derived monitoring

The current audit model can derive HTTP request count, server-side duration, response-status class,
operation/route distribution, and failure-code frequency. It cannot replace dedicated instrumentation
for JVM, datasource pool, WebSocket, host, SQL Server, RabbitMQ, certificate, or backup state.

If deeper live application metrics are needed, add bounded Quarkus Micrometer/Prometheus metrics
through an explicit implementation plan. Do not turn the security audit table into the sole
monitoring system.

---

## 5. Custom Audit-Metrics Monitor Contract

### 5.1 Committed ChatMonitor implementation shape

ChatMonitor will begin under the repository-root `chat-monitor/` directory and will be developed as
an independently runnable project that can later move to its own `ChatMonitor` GitHub repository
without redesigning its boundaries. While it remains in this repository, it must not contain a
nested Git repository.

The accepted initial technology shape is:

- **dashboard:** Vite using its vanilla-JavaScript template, with standards-based HTML and CSS;
- **browser code:** frameworkless JavaScript modules; React, Vue, Angular, or another component
  framework requires a later evidence-backed architecture decision;
- **charts:** a browser-compatible, framework-independent charting package selected during the
  implementation guide and recorded with its version and licence;
- **local service:** a small Node.js service responsible for scheduled collection, SQLite access,
  aggregation, retention, and the local dashboard API; and
- **local persistence:** SQLite, subject to the safety and retention requirements below.

#### 5.1.1 Recommended repository layout

The initial project should use the following structure. Names may change only where implementation
evidence demonstrates a clearer boundary; any material deviation must be reflected in the eventual
ChatMonitor README and implementation guide.

```text
chat-monitor/
├── README.md                         # setup, operation, recovery, and troubleshooting entrypoint
├── CHANGELOG.md                      # ChatMonitor-specific user and operator changes
├── Dockerfile                        # reproducible non-root immutable production image
├── compose.yaml                      # loopback publication, lifecycle, health, and durable volume
├── .dockerignore                     # excludes local state, secrets, and generated artifacts
├── package.json                      # scripts, runtime constraints, and direct dependencies
├── package-lock.json                 # committed reproducible npm dependency graph
├── vite.config.js                    # dashboard build/dev configuration only
├── .env.example                      # documented keys with safe placeholders, never credentials
├── .gitignore                        # local DB, secrets, logs, coverage, and build artifacts
├── config/
│   ├── defaults.js                   # non-secret bounded defaults and validation inputs
│   └── schema.js                     # startup validation for configuration and environment values
├── docs/
│   ├── architecture.md               # runtime boundaries, data flow, and dependency direction
│   ├── operations.md                 # install, auto-start, backup, restore, update, and rollback
│   ├── metrics-catalog.md            # metric definitions, units, windows, and interpretation
│   └── security.md                   # credentials, exposure, privacy, and workstation threat model
├── migrations/
│   └── sqlite/                       # immutable, ordered local-monitor schema migrations
├── public/
│   └── static/                       # Vite-copied icons and other non-generated public assets
├── src/
│   ├── client/
│   │   ├── index.html                # dashboard document entrypoint
│   │   ├── main.js                   # browser composition root and startup
│   │   ├── styles/
│   │   │   ├── tokens.css            # colors, spacing, typography, and status semantics
│   │   │   ├── base.css              # reset and accessible document defaults
│   │   │   ├── layout.css            # dashboard layout and responsive behavior
│   │   │   └── components.css        # cards, tables, filters, banners, and chart containers
│   │   ├── api/
│   │   │   └── monitor-api.js        # fetch calls, response checks, cancellation, and timeouts
│   │   ├── charts/
│   │   │   ├── chart-factory.js      # graphing-library boundary and shared chart defaults
│   │   │   ├── latency-chart.js      # latency percentiles and trends
│   │   │   ├── throughput-chart.js    # request-volume trends
│   │   │   └── failure-chart.js       # status/error category trends
│   │   ├── components/
│   │   │   ├── freshness-banner.js    # CURRENT/CATCHING_UP/failure/gap presentation
│   │   │   ├── metric-card.js         # accessible summary metric rendering
│   │   │   ├── filter-bar.js          # time, route, method, operation, and status filters
│   │   │   └── failure-table.js       # recent sanitized failures and investigation links
│   │   ├── pages/
│   │   │   ├── overview.js            # availability, freshness, volume, latency, and failures
│   │   │   ├── operations.js          # operation/route-level comparison and drill-down
│   │   │   └── collector-health.js    # ingestion lag, cursor, retention, and gap history
│   │   ├── state/
│   │   │   └── dashboard-state.js     # small explicit state model; no framework store
│   │   └── utils/
│   │       ├── format.js              # dates, durations, rates, and accessible labels
│   │       └── time-range.js          # validated dashboard time-window calculations
│   ├── server/
│   │   ├── main.js                    # Node process entrypoint and graceful shutdown
│   │   ├── app.js                     # local HTTP API/static application composition
│   │   ├── config.js                  # validated runtime configuration exposed to server only
│   │   ├── api/
│   │   │   ├── health-routes.js       # monitor process/readiness endpoints
│   │   │   ├── status-routes.js       # collector freshness, lag, retention, and state
│   │   │   ├── metrics-routes.js      # bounded aggregate metric queries
│   │   │   └── failure-routes.js      # bounded sanitized failure queries
│   │   ├── collector/
│   │   │   ├── collector.js           # one bounded ingestion cycle
│   │   │   ├── scheduler.js           # non-overlapping scheduled execution
│   │   │   ├── cursor.js              # (created_at,event_id) comparison and serialization
│   │   │   └── collection-state.js    # explicit state transitions and gap detection
│   │   ├── production/
│   │   │   ├── sql-server-client.js   # dedicated read-only connection and lifecycle
│   │   │   └── audit-projection.js    # parameterized bounded monitoring-view query
│   │   ├── persistence/
│   │   │   ├── sqlite.js              # connection, transactions, pragmas, and shutdown
│   │   │   ├── migration-runner.js    # ordered local migration application
│   │   │   ├── event-repository.js    # idempotent projected-event persistence
│   │   │   ├── cursor-repository.js   # cursor commit in the ingestion transaction
│   │   │   ├── aggregate-repository.js # hourly/daily aggregate storage and retrieval
│   │   │   └── state-repository.js    # failures, gaps, retention, and collector health
│   │   ├── metrics/
│   │   │   ├── aggregator.js          # deterministic local bucket calculation
│   │   │   ├── metric-queries.js      # bounded read models for the dashboard API
│   │   │   └── retention.js           # aggregate-before-detail cleanup policy
│   │   ├── security/
│   │   │   ├── local-access.js        # bind/access policy for localhost or approved LAN use
│   │   │   └── response-headers.js    # CSP and other dashboard response protections
│   │   └── observability/
│   │       └── logger.js              # structured local operational logging with redaction
│   └── shared/
│       ├── monitor-states.js           # shared finite state names and display semantics
│       └── api-contract.js             # JSON response shapes without server secrets/dependencies
├── test/
│   ├── unit/
│   │   ├── client/                    # formatting, state, filters, and component behavior
│   │   └── server/                    # cursor, state, aggregation, retention, and validation
│   ├── integration/
│   │   ├── sqlite/                    # migrations, transactions, idempotency, and retention
│   │   ├── collector/                 # paged ingestion, resume, delayed events, and gaps
│   │   └── api/                       # local API bounds, errors, and sanitized responses
│   ├── browser/                       # built-dashboard smoke, accessibility, and primary flows
│   ├── fixtures/                      # synthetic audit projections without production identities
│   └── helpers/                       # isolated temp DB and test-server lifecycle utilities
├── scripts/
│   ├── verify-config.js               # operator-safe preflight with no secret echoing
│   ├── backup-monitor-db.js           # consistent local SQLite backup
│   └── restore-monitor-db.js          # validated local restore workflow
├── secrets/
│   └── README.md                      # ignored SQL password/CA file contract
└── var/                               # runtime-only, ignored local state
    ├── data/chat-monitor.sqlite
    └── logs/
```

The project should remain one npm package initially. npm workspaces, a monorepo orchestrator, a
separate frontend package, or shared package publishing would add operational complexity without a
current need. The `src/client`, `src/server`, and `src/shared` boundaries provide sufficient
separation for this deployment size.

#### 5.1.2 Dependency and module rules

Dependency flow must remain simple and directional:

```text
browser pages/components/charts
        │
        ├──> client API adapter ──HTTP──> server API routes
        │                                  │
        │                                  ├──> local metric/read repositories ──> SQLite
        │                                  └──> collector status repositories ───> SQLite
        │
Node collector scheduler ──> collector ──> read-only SQL projection
                                  │
                                  └──> SQLite transaction: events + cursor + state
```

Rules:

1. Client modules may import only client or shared browser-safe modules.
2. Shared modules must contain plain constants, validation-free data shapes, or browser-safe helpers;
   they must never import SQL Server, SQLite, filesystem, process, or credential code.
3. Server API routes delegate to bounded query/service modules instead of embedding SQL.
4. Production SQL access exists only under `src/server/production/`.
5. SQLite access exists only under `src/server/persistence/`.
6. Collection and dashboard queries never share a long-running transaction.
7. Graphing-library calls remain behind `chart-factory.js` and chart modules so replacing the library
   does not rewrite pages or API contracts.
8. No module imports ChatBackend Java classes, build output, internal configuration, or container
   filesystem paths.
9. No dependency is added merely to avoid a small, clear standards-based JavaScript function.
10. Runtime dependencies and development-only dependencies must be classified correctly and kept to
    the smallest audited set.

#### 5.1.3 Configuration and secret boundary

`.env.example` should document names and safe examples for at least:

```text
CHAT_MONITOR_HOST=127.0.0.1
CHAT_MONITOR_DEPLOYMENT_MODE=native
CHAT_MONITOR_PORT=8090
CHAT_MONITOR_SQLITE_PATH=./var/data/chat-monitor.sqlite
CHAT_MONITOR_POLL_INTERVAL_SECONDS=60
CHAT_MONITOR_PAGE_SIZE=500
CHAT_MONITOR_DETAIL_RETENTION_DAYS=90
CHAT_MONITOR_AGGREGATE_RETENTION_DAYS=365
CHAT_MONITOR_ENVIRONMENTS_FILE=<server-only-registry-path>
CHAT_MONITOR_DEFAULT_ENVIRONMENT_ID=<legacy-single-source-only>
CHAT_MONITOR_SOURCE_HOST=<private-production-sql-host>
CHAT_MONITOR_SOURCE_PORT=1433
CHAT_MONITOR_SOURCE_DATABASE=wl_chat
CHAT_MONITOR_SOURCE_USERNAME=wl_chat_monitor
CHAT_MONITOR_SOURCE_PASSWORD=<supply-through-local-secret-mechanism>
CHAT_MONITOR_SOURCE_PASSWORD_FILE=<preferred-container-secret-path>
CHAT_MONITOR_SOURCE_ENCRYPT=true
CHAT_MONITOR_SOURCE_TRUST_SERVER_CERTIFICATE=false
```

Names are provisional until the implementation guide validates them against the chosen SQL Server
driver. The actual `.env`, database, backups, logs, coverage, and build output must be ignored. Vite
exposes variables prefixed with `VITE_` to browser code; therefore **no credential or private source
configuration may use a `VITE_` name**. The dashboard should normally use same-origin relative API
paths and require no environment-delivered secret.

Startup must fail clearly—but without printing secret values—when required configuration is absent,
invalid, unsafe, or contradictory. Page size, polling interval, retention, timeouts, host binding,
TLS validation, and file paths require explicit bounds or validation.

The Docker runtime sets `CHAT_MONITOR_DEPLOYMENT_MODE=container`, binds internally to `0.0.0.0`, and
publishes the service only to host `127.0.0.1`. Native mode rejects non-loopback binding. Compose
mounts the ignored `secrets/` directory read-only; SQL mode reads the password from a file and uses
the mounted CA through Node's verified TLS trust path. Secrets are never image layers, Compose
literals, named-volume content, or browser configuration.

The preferred multi-environment configuration is a validated server-only JSON registry mounted
read-only into the container. It defines stable IDs, labels, classifications, source modes, bounded
collector overrides, SQL coordinates, and password-file references. It never contains inline
passwords. All listed collectors run concurrently; browser switching selects a read partition and
must not start, stop, or reconfigure collectors. The browser receives only allowlisted display
metadata, never SQL coordinates, usernames, secret paths, or trust paths.

The registry may also declare a `planned` environment that has not yet been deployed or onboarded.
Such an environment remains visible so operators can understand the intended topology, but it must
not receive a collector, reuse another environment's fixtures, or display inherited history. Its
status is `SOURCE_UNAVAILABLE` with an explicit `ENVIRONMENT_NOT_DEPLOYED` reason, no freshness
claim, and zero environment events. For the X2 workstation deployment, Local and Staging are usable
rehearsal environments; Production is the gold-themed planned environment until X4 supplies and
verifies its real telemetry source.

#### 5.1.4 Proposed commands and lifecycle

The package scripts should converge on the following operator contract:

```text
npm ci                 # exact clean dependency installation from package-lock.json
npm run dev            # Node service plus Vite development server for local development only
npm run build          # immutable Vite dashboard production build
npm start              # Node service, collector, local API, and built static dashboard
npm test               # unit and integration tests
npm run test:browser   # built-dashboard browser and accessibility smoke tests
npm run lint           # JavaScript/CSS/HTML static checks selected by the implementation guide
npm run verify         # lint + tests + production build + browser-safe artifact checks
npm run container:config # validate the Compose model without starting it
npm run container:up     # build/start/health-wait for the persistent workstation service
npm run container:stop   # stop while retaining container and named-volume state
npm run container:down   # remove containers/network while retaining named-volume state
npm run container:status # inspect health and lifecycle state
npm run container:logs   # follow bounded Docker-managed logs
```

`npm start` must not invoke the Vite development server. The Node process must validate
configuration, migrate SQLite, start the local API/static server, then schedule collection. It must
support graceful shutdown: stop accepting new work, prevent another collection cycle, finish or
rollback the active local transaction within a bounded period, close SQL Server and SQLite
connections, and terminate cleanly.

Development convenience must not weaken production defaults. If `npm run dev` proxies dashboard API
calls to the Node service, the proxy is local-development configuration only and cannot contain a
production credential.

#### 5.1.5 Generated and persistent artifacts

- Vite build output belongs under `dist/client/` and is reproducible; it is not committed.
- Any server build output, if later introduced, belongs under `dist/server/` and is not committed.
- The SQLite database, write-ahead log, shared-memory file, backups, and logs belong under ignored
  operator-configurable paths, defaulting to `var/` for development.
- Coverage, browser-test traces/screenshots, temporary databases, and dependency caches are ignored.
- Local SQLite migrations are committed and immutable after release; corrections use a new ordered
  migration rather than rewriting an applied one.
- Backup scripts must use a SQLite-consistent backup mechanism rather than copying an actively
  changing database file blindly.
- Browser assets must be inspected during verification to prove that SQL credentials, source host
  secrets, and local filesystem paths were not bundled.

#### 5.1.6 Extraction into the standalone ChatMonitor repository

Before extraction, all ChatMonitor code, docs, tests, migrations, scripts, and package metadata must
reside under `chat-monitor/`. References back to this repository must be URL or documented contract
references, not relative source imports. Extraction consists of moving that directory into a new
repository, initializing its Git history according to the owner's chosen history-preservation
approach, adding standalone CI and repository instructions, and updating ChatBackend documentation
to link to the new canonical location.

Extraction must not change the SQL projection, cursor semantics, local API, SQLite schema, dashboard
behavior, or operator configuration solely because the Git repository changed. Until extraction,
ChatBackend's root CI should either invoke `chat-monitor`'s `npm run verify` or clearly document the
temporary reason it does not; after extraction, ChatMonitor owns that gate.

For the accepted simple deployment, the ChatMonitor Node.js service serves the immutable
`dist/client/` Vite build and same-origin local API. The Vite development server remains
development-only and is absent from workstation auto-start and normal operation.

### 5.2 Source-neutral telemetry and future client onboarding

ChatMonitor must not hard-code its durable model or dashboard to SQL audit rows. The SQL audit
projection is the first source adapter, not the definition of a monitorable event. Internally, every
adapter must emit a versioned normalized envelope containing at least:

```text
event_id                 globally unique, source-issued or deterministically namespaced
schema_version           normalized-envelope version
source_type              CHATBACKEND_AUDIT | CLIENT_TELEMETRY | HOST | SYNTHETIC
source_instance_id       stable pseudonymous source identity
event_name               allowlisted semantic name
occurred_at              source observation time
received_at              ChatMonitor receipt time
severity                 normalized bounded level
dimensions               allowlisted low-cardinality labels
measurements             bounded numeric values
```

The normalized envelope, repositories, aggregation engine, dashboard API, and charts must remain
independent of transport. Source-specific payloads are validated and reduced by adapters before a
local transaction persists them. A new official client must therefore require a new adapter and
allowlist, not a new monitoring database or dashboard architecture.

X2 implements the internal adapter contract and proves it with the SQL-audit adapter plus synthetic
client fixtures. It does **not** expose a public client-ingestion endpoint. A later official-client
track may enable one only after defining authentication/enrollment, abuse controls, consent, privacy,
network reachability, and operational ownership. IE-01 installation identity is the preferred future
authentication foundation for native clients; browser telemetry must never rely on an embedded shared
secret.

Future official-client telemetry must follow these guardrails:

- explicitly versioned schemas with additive compatibility and an unsupported-version response;
- allowlisted event names, dimensions, and measurements with strict size/cardinality limits;
- no message content, attachment content, credentials, session tokens, authorization headers,
  contact data, raw URLs/query strings, or arbitrary client logs;
- pseudonymous installation/source identifiers rather than usernames or account identifiers unless
  a separately approved diagnostic workflow requires them;
- client-generated idempotency keys, bounded batches, compression where useful, and safe duplicate
  handling;
- both client occurrence time and server receipt time, with clock-skew detection;
- bounded offline buffering with oldest/newest limits and explicit drop accounting;
- per-source authentication, revocation, rate/volume quotas, and rejection metrics; and
- a user/operator-visible switch for diagnostic telemetry where future client requirements warrant
  collection beyond essential health signals.

ChatMonitor initially runs on the administrator machine and binds its dashboard/API to loopback. X4
may authorize narrowly scoped LAN access. Public client ingestion is not achieved by exposing that
local Node service directly; any later remote ingestion path requires an intentionally designed edge
and threat-model update.

### 5.3 Responsibility boundary

The production table `[audit].[http_audit_event]` remains authoritative audit evidence. A custom
monitor may retain a sanitized, derived projection for metrics and graphs. The local monitoring store
is not a backup of the audit table and is never written back to production.

The minimum projected fields are:

```text
event_id
created_at
occurred_at
operation
method
route_template
response_status
response_code
duration_ms
error_code
```

Do not project actor identity, target identity, IP addresses, user agents, raw headers, detailed
failure location, or unrelated metadata unless a later monitoring requirement is explicitly approved.

### 5.4 Collection ordering

The collector must use the durable persistence cursor:

```text
(created_at, event_id)
```

It must not use only `occurred_at`. RabbitMQ or local asynchronous delivery can persist an event after
its request occurrence time; an occurrence-time-only cursor could permanently skip such a late event.

### 5.5 Incremental collection cycle

On each independently scheduled environment cycle:

1. load the last locally committed `(created_at, event_id)` cursor;
2. request the next bounded page from the production monitoring view;
3. insert projected events locally using `(environment_id,event_id)` idempotency;
4. update the cursor in the same local transaction;
5. repeat while a full page is returned;
6. calculate or update local metric buckets; and
7. record collection health, lag, and any error.

The normal polling interval should begin around 30–60 seconds and remain configurable. Catch-up uses
bounded pages and yields between failures; it must not issue one unbounded query after a long outage.

### 5.6 Restart and catch-up behavior

When the monitor restarts, it resumes from its last committed cursor and retrieves all still-retained
audit events persisted afterward. If local insertion succeeded but cursor persistence did not, the
the environment-scoped unique event key makes re-reading harmless.

The monitor must expose these states:

- `CURRENT` — source reachable and collection lag within threshold;
- `CATCHING_UP` — safe paged recovery is in progress;
- `SOURCE_UNAVAILABLE` — production SQL cannot be reached;
- `COLLECTION_FAILED` — source was reached but collection or local persistence failed; and
- `HISTORY_GAP` — the source retention boundary passed before missing events could be collected.

The monitor must never silently label incomplete history as current.

### 5.7 Local persistence and retention

SQLite is the preferred initial local store because it is transactional, supports unique constraints
and indexes, is easy to back up, and does not require another database server. This remains a
proportional default, not a permanent technology mandate.

Retention requirements:

- detailed sanitized projected events: minimum 62 days, recommended 90 days;
- hourly and daily aggregate buckets: recommended minimum 365 days;
- collection cursor and gap history: retained independently of detailed-event cleanup;
- purge detailed events only after their aggregate buckets are complete; and
- expose oldest/newest retained event time, effective retention, and storage capacity.

Retention cleanup occurs only in the local monitoring store. The monitor has no permission to purge
production audit records.

### 5.8 Useful derived metrics

The local monitoring application may present:

- requests per minute, hour, and day;
- average, minimum, maximum, median, p90, p95, and p99 server-side duration;
- latency and volume by operation, route template, method, and status class;
- 5xx server-failure rate;
- 4xx, 401/403, 404, and 429 outcomes as separate categories;
- problem/error-code frequency;
- slow-request counts;
- audit persistence delay from `response_timestamp`/`occurred_at` to `created_at` where available;
- collection lag, catch-up duration, and detected source gaps; and
- trends compared with an earlier equivalent window.

Do not label all non-2xx responses as application failures. Authentication, authorization,
validation, privacy-preserving 404, and throttling outcomes require separate interpretation.

Audit-derived throughput is an operational estimate rather than an authoritative counter because the
audit transport intentionally fails open. Audit degradation, queue-full, dead-letter, and persistence
failure signals must accompany these graphs.

---

## 6. Production Database Safety for Monitoring

### 6.1 Dedicated projection and principal

Provide a monitoring-schema view or equivalent stored query that exposes only the approved fields.
Create a dedicated principal such as `wl_chat_monitor` with `SELECT` only on that projection.

The principal must have no permission to:

- select raw audit headers, actor/target identity, session, message, or unrelated tables;
- insert, update, or delete production records;
- execute arbitrary procedures;
- alter schemas or objects; or
- administer SQL Server.

Restrict network access to the operator workstation or approved monitoring host and require SQL Server
TLS. Credentials must use the production secret-delivery and rotation process.

### 6.2 Query contract

Collector queries must:

- use the indexed `(created_at, event_id)` seek cursor;
- use a bounded page, initially 250–1,000 rows;
- run without a long explicit transaction;
- use a short command and lock timeout;
- set low deadlock priority where supported;
- stop and retry with backoff rather than compete with production;
- perform long-window grouping, graphing, and percentile calculation only on the local store; and
- never run unbounded production scans for dashboard refreshes.

Do not use `NOLOCK` as the default. Dirty, skipped, or duplicated reads can create permanent cursor
gaps. The database does not currently enable `READ_COMMITTED_SNAPSHOT`; enabling it changes the whole
database's isolation behavior and requires an explicit migration, ADR, SQL Server `tempdb` assessment,
and regression evidence.

### 6.3 Candidate monitoring index

The existing audit clustered index `(occurred_at, event_id)` supports occurrence-time investigations
but not ideal incremental collection by persistence time. Discovery should capture the actual query
plan and assess a single narrow covering index shaped like:

```sql
CREATE INDEX ix_http_audit_event_monitoring_ingest
ON [audit].[http_audit_event] (created_at, event_id)
INCLUDE (
    occurred_at,
    operation,
    method,
    route_template,
    response_status,
    response_code,
    duration_ms,
    error_code
);
```

This is a candidate, not an authorized migration. Validate index size, insert overhead, seek behavior,
locking, and representative catch-up performance before adoption. Do not add one production index per
chart.

---

## 7. Alert Inventory and Initial Threshold Direction

Final values require production characterization, but the following alerts must exist:

| Concern | Required signal | Initial direction |
|---|---|---|
| Public/private availability | HTTPS and liveness | Alert after sustained 2–5 minute failure |
| Application readiness | `/q/health/ready` | Alert after sustained 2–5 minute failure |
| Certificate | Days until expiry | Warning at 30 days; critical at 14 days |
| Disk | Host and volume utilization | Warning around 80%; critical around 90% |
| Container stability | Unexpected restarts | Alert on repetition or crash loop |
| SQL Server | Connectivity/readiness | Alert on sustained failure |
| Backup | Last successful encrypted backup | Alert when schedule plus grace is exceeded |
| Off-host transfer | Last successful transfer | Alert on failed or overdue transfer |
| Restore readiness | Last successful isolated drill | Alert when the agreed schedule is overdue |
| Audit transport | Degraded/fallback state | Alert when sustained beyond grace period |
| RabbitMQ audit queue | Depth and oldest age | Alert on stalled or growing backlog |
| Audit DLQ | Dead-letter count | Alert above zero |
| Monitor collector | Last success and lag | Alert when polling or catch-up exceeds threshold |
| Monitoring retention | Oldest retained detail | Alert if effective history falls below 62 days |
| Host resources | CPU, memory, load, temperature | Alert only on sustained abnormal use |
| Deployment | Version/readiness after rollout | Trigger rollback/incident workflow as defined |
| Security gates | Release security result | Alert/block on the accepted failure policy |

Every alert must have an owner, severity, actionable diagnostic link, suppression/maintenance rule,
and tested delivery path. Avoid alerting on short harmless spikes.

### 7.1 Alert severity and lifecycle

Use a small consistent model:

| Severity | Meaning | Expected action |
|---|---|---|
| Info | Planned transition or recovery; no action required | Retain for context without repeated notification |
| Warning | Degradation or approaching limit with recovery time available | Investigate during the accepted response window |
| Critical | Service/data/recovery protection unavailable or limit imminent | Act promptly or deliberately take the service offline |

An alert instance moves through `PENDING`, `FIRING`, `ACKNOWLEDGED` where supported, and `RECOVERED`.
The confirmation window prevents transient spikes from firing; recovery requires a successful
confirmation rather than one good sample. Repeated notifications use a bounded cadence appropriate
to severity. Related symptoms should identify a likely parent failure where the selected tool
supports dependencies, reducing a host-down event from becoming an unactionable notification storm.

### 7.2 Alert destination and delivery contract

The stakeholder must select at least one destination available when the workstation is operational.
Email, a self-hosted notification endpoint, or another free/private channel is acceptable; no cloud
service is mandatory. Record provider/tool, account owner, credential delivery, recipient, severity
routing, quiet-hours behavior, rate limits, retry policy, message privacy, and fallback.

Notifications contain service role, state, severity, first/last observation, concise action, and a
local diagnostic location. They must not contain credentials, tokens, message content, private keys,
raw audit metadata, or unrestricted internal URLs. Alert-delivery failure is itself visible in the
monitoring UI and local logs.

Because the primary alert sender lives on the workstation, it cannot reliably notify during
workstation power/network failure. This is an accepted blind spot unless the stakeholder selects an
external or second-device heartbeat. If selected, that heartbeat observes only monitor/production
freshness and does not receive application data or privileged production access.

### 7.3 Threshold approval and runbook linkage

Initial directions in the table are hypotheses. After characterization, each alert record must contain:

- exact measurement and source;
- warning/critical threshold and confirmation duration;
- recovery condition;
- expected normal range and evidence period;
- false-positive/noise review;
- owner and first diagnostic step;
- linked recovery or investigation procedure; and
- last successful delivery/recovery test.

Threshold changes are configuration changes with a reason and date. Do not silence a persistent
problem by only raising its threshold. Maintenance suppression must expire automatically or be
explicitly cleared and followed by a current-state check.

### 7.4 Alert exercises

Before activation, safely induce or simulate each required class: HTTPS/readiness failure,
certificate warning, disk warning, container restart, SQL connectivity failure, backup/transfer/drill
overdue, RabbitMQ degradation and backlog, DLQ non-zero, ChatMonitor stale/gap, monitoring-agent
failure, and failed deployment/security gate. Verify pending/firing/recovery transitions, delivery,
privacy, diagnostic usefulness, and absence of excessive duplicate notifications. Retain sanitized
evidence and schedule periodic retesting of delivery and certificate/backup paths.

---

## 8. Logs, Metrics, and Privacy Boundaries

- Keep structured application and proxy logs available for request-ID/trace-ID investigation.
- Do not copy message bodies, tokens, query values, raw headers, or audit-sensitive context into metric
  labels, local monitoring records, notifications, or dashboard URLs.
- Keep metric labels bounded; never label time series by user ID, conversation ID, message ID, request
  ID, IP address, or unnormalized path.
- The custom monitor may link an aggregate failure to the authoritative audit investigation workflow,
  but it must not become a second unrestricted audit browser by accident.
- Monitoring configuration and history reveal topology and operational behavior; protect and back them
  up proportionally.
- Define local monitoring data deletion, credential rotation, and workstation replacement behavior.

---

## 9. Monitoring Failure and Recovery Exercises

X2 demonstrates the following with deterministic fixtures and hardened rehearsal; X4 repeats every
item that depends on production, a live alert destination, or the external network:

1. production continues operating while the monitor is stopped;
2. the monitor restarts and automatically catches up all retained events;
3. a crash between local event insertion and cursor advancement creates no duplicate metrics;
4. RabbitMQ-delayed audit events are collected because the cursor uses `created_at`;
5. a production-source outage enters `SOURCE_UNAVAILABLE` and later recovers;
6. a local database failure enters `COLLECTION_FAILED` without affecting production;
7. a simulated source-retention gap is detected as `HISTORY_GAP`;
8. bounded catch-up does not cause meaningful production blocking or latency regression;
9. alert delivery works for application down, readiness down, certificate warning, disk warning,
   backup overdue, audit degradation, DLQ non-zero, and collector stale states;
10. the workstation reboot/auto-start path restores monitoring without manual data repair; and
11. monitor credentials cannot read raw sensitive audit columns or mutate production data.

---

## 10. Monitoring Acceptance Evidence

The X2 implementation must define exact commands and retain local/rehearsal evidence for:

- monitoring topology and firewall inventory;
- availability and WebSocket synthetic checks;
- host/container metric collection;
- dedicated SQL monitoring principal negative-permission tests;
- monitoring view column inventory;
- actual execution plan for incremental collection;
- candidate index size and audit-insert overhead comparison;
- lock, command-timeout, and production-latency behavior during normal and catch-up collection;
- idempotent cursor recovery and delayed-event collection;
- detailed and aggregate retention evidence;
- dashboard freshness and history-gap presentation;
- every required alert reaching the responsible person;
- monitoring data backup/restore where chosen; and
- reproducible ChatMonitor dependency installation, tests, Vite production build, Node service
  startup, and workstation auto-start evidence;
- proof that production-built dashboard assets are served without the Vite development server and
  that no SQL credential is present in browser-delivered assets; and
- accepted blind spots and residual risks.

---


---

## 11. Ordered implementation plan and verification gates

Implementation proceeds in the following bounded slices. A later slice may build on an earlier one,
but it may not conceal a failed gate. Each slice updates ChatMonitor's README/changelog and the root
changelog when it creates a notable repository capability.

### X2-A — Scaffold, contracts, and reproducible toolchain

Create the single-package `chat-monitor/` project, committed lockfile, Vite vanilla client, Node
service composition root, validated configuration, structured logging, lint/test/build scripts,
fixture format, and root CI integration. Add the ChatMonitor-specific architecture, security,
operations, and metrics-catalog documents. Add a scoped `chat-monitor/AGENTS.md`, mirrored scoped
rules for `.agents/` and `.github/`, root-agent navigation, and stable ChatMonitor nodes in both
architecture-map artifacts. Keep runtime state and credentials ignored.

**Gate:** `npm ci` and `npm run verify` pass from a clean checkout on the supported Node runtime; the
built browser assets contain no secret/configuration values; the server binds only to loopback; and a
fresh contributor can start fixture mode using only documented commands. A cold-agent walk must find
the ChatMonitor architecture, source boundaries, commands, tests, operational guide, and X4 handoff
from the root `AGENTS.md` and graph without loading the entire X2 specification.

### X2-B — Backend monitoring projection and least privilege

Add a forward-only ChatBackend Flyway migration defining the minimal monitoring projection and its
seek index only after representative plan/insert-overhead evidence justifies that index. Extend the
hardened principal tooling with `wl_chat_monitor`, granting `SELECT` only on the approved projection.
Add positive projection tests and negative tests for raw audit, identity, conversation, message,
write, execute, and schema authority. Provide sanitized deterministic projection fixtures for
ChatMonitor without copying production audit records.

**Gate:** `./mvnw clean verify` plus clean/upgrade migration harnesses pass; the monitoring principal
can page the projection by `(created_at,event_id)` and cannot read or mutate any other application
surface; the query is bounded and parameterized; and the client-integration audit records either the
verified operational impact or that no client-facing contract changed.

### X2-C — Durable local ingestion, aggregation, and retention

Implement immutable SQLite migrations, event/cursor/state repositories, one transactional bounded
collection cycle, non-overlapping scheduling, idempotency, delayed-event handling, explicit collector
states, hourly/daily aggregation, and aggregate-before-detail retention. Implement fixture and SQL
source adapters behind one normalized envelope; do not create a public client-ingestion endpoint.

**Gate:** automated tests prove crash/retry idempotency, cursor atomicity, deterministic ordering,
multi-page catch-up, delayed persistence, source outage/recovery, local persistence failure,
`HISTORY_GAP`, 62-day minimum detail retention, 365-day aggregate retention, and bounded source
queries. Stopping ChatMonitor must have no effect on ChatBackend.

### X2-D — Local API and operator dashboard

Implement bounded health, status, metrics, and sanitized-failure APIs; serve the immutable Vite build
from the Node service; and build overview, operation, and collector-health pages. Present freshness
before metrics, distinguish expected 4xx outcomes from failures, provide accessible non-colour-only
state semantics, and keep Chart.js behind the chart adapter.

**Gate:** API contract/integration tests, built-dashboard Playwright smoke tests, keyboard navigation,
accessible-name/contrast checks, empty/loading/error/stale/gap states, time-filter bounds, CSP/security
headers, and production-build secret scanning all pass. `npm start` serves no Vite development server.

### X2-E — Alert state and bounded monitoring-layer integration

Implement the local alert state machine, deterministic alert sink, maintenance/suppression expiry,
recovery confirmation, privacy-safe notification payloads, and tested fixture thresholds. Add pinned
loopback-only administrator-workstation definitions for Uptime Kuma and the Beszel hub after licence,
backup, authentication, and update checks. Do not enroll the production host or configure live
notification credentials in this slice.

**Gate:** fixtures deterministically exercise `PENDING`, `FIRING`, `ACKNOWLEDGED` where supported, and
`RECOVERED`; alert retries remain bounded; delivery failure is visible; dashboards are not public;
and Uptime Kuma/Beszel configuration can be backed up, restored, and removed without affecting
ChatBackend or ChatMonitor's authoritative local store.

### X2-F — Docker workstation deployment, recovery, and X4 handoff

Create the immutable multi-stage image and Compose definition, preserving configuration/data across
container replacement and upgrades through the named volume. Run as a non-root user with a read-only
root filesystem, host-loopback-only publication, no Docker socket, dropped capabilities, health
checks, restart policy, and bounded logs. Add preflight, start/stop/status/log commands, consistent
SQLite backup/restore, clean-host recovery, rollback, and uninstall instructions. Configure Docker
Desktop to start at login and install the accepted candidate on the administrator workstation in
fixture/rehearsal mode. Assemble sanitized evidence and the explicit X4 onboarding manifest.

**Gate:** a clean image build/install, Docker-at-login auto-start, bounded restart failure, upgrade,
rollback, SQLite
backup/restore, replacement-directory recovery, monitor-offline/catch-up exercise, and complete
uninstall rehearsal pass without losing retained data or exposing credentials. Ordinary container
replacement and `docker compose down` must preserve the named volume. The host publishes only to
loopback and reports `CURRENT`, `CATCHING_UP`, `SOURCE_UNAVAILABLE`,
`COLLECTION_FAILED`, or `HISTORY_GAP` truthfully.

### 11.1 Command contract

The implementation must converge on these stable commands:

```text
cd chat-monitor
npm ci
npm run dev
npm run build
npm test
npm run test:browser
npm run lint
npm run verify
npm run container:config
npm run container:build
npm run container:up
npm run container:stop
npm run container:down
npm run container:status
npm run container:logs
npm run package
npm run install:local
npm run backup
npm run restore -- <verified-backup>
```

The root verification path must invoke ChatMonitor verification explicitly, either from
`./mvnw clean verify` through a documented integration or from a dedicated required CI job. It must
not rely on a developer remembering to run a second project manually.

### 11.2 Definition of deployed for X2

“Deployed” in X2 means the accepted ChatMonitor image and selected monitoring hubs are installed on
the administrator workstation, Docker Desktop starts at login, Compose restores healthy services,
fixtures or the hardened rehearsal source operate correctly, the named volume retains/recover local
state, and the host exposes only loopback interfaces. It does **not** mean they have production SQL credentials, production agents,
live external probes, public dashboard reachability, or approved notification destinations. Those
connections and their evidence belong to X4.

### 11.3 Agent context and architecture-map contract

ChatMonitor is independently runnable but remains part of this workspace until extraction. Agent
context must therefore use progressive disclosure:

1. root `AGENTS.md` remains the small universal router and gains one ChatMonitor code/docs/verify
   route only after those paths exist;
2. `chat-monitor/AGENTS.md` provides scoped identity, source map, canonical documents, commands,
   migration rules, runtime-state exclusions, and extraction constraints without duplicating the X2
   specification;
3. `.agents/rules/chat-monitor.md` and `.github/instructions/chat-monitor.instructions.md` carry
   equivalent narrowly scoped implementation guardrails, following the repository parity rule;
4. `chat-monitor/README.md` is the human/operator entrypoint, while its architecture, security,
   operations, and metrics-catalog files remain the canonical detailed sources;
5. `project-architecture.excalidraw` and `project-architecture.svg` retain their existing spatial
   zones and stable identifiers while adding a clear ChatMonitor project boundary, source/API/store
   flow, SQL projection contract, monitoring-tool relationships, deployment boundary, and canonical
   documentation routes; and
6. the graph-generator skill scans both Maven/Quarkus and repository-owned companion manifests and
   source trees, excluding `node_modules`, generated assets, runtime SQLite/log data, and ordinary
   fixtures.

Every X2 slice audits whether it materially added, removed, or rewired a represented component,
dependency, deployment boundary, capability, or canonical documentation route. If so, both graph
artifacts are updated in the same change set. Internal edits that do not change navigation or
architecture do not trigger redraws. X2-F repeats the cold-agent walk and records whether a new agent
can identify both projects, their one-way integration contract, their independent verification
commands, and the X2/X4 responsibility boundary using routed context rather than broad document
loading.

---

## 12. X2 Exit Criteria

### 12.1 Closeout evidence — 2026-10-16

- `npm run verify`: 27 Node unit/integration tests, lint, Vite production build, and browser-secret
  scan passed.
- `npm run test:browser`: the built dashboard passed its Chromium environment-switching,
  freshness, operational-view, theme, keyboard-focus, empty-state, and planned-production checks.
- `npm run container:config` and `npm run tools:config`: both Compose models validated.
- `npm run container:up`: the non-root read-only ChatMonitor image rebuilt and became healthy on
  `127.0.0.1:8090` while retaining the existing named volume.
- `npm run backup` followed by `npm run restore -- <backup>`: online SQLite backup, SHA-256 and
  integrity verification, replacement, restart, and health recovery passed.
- Uptime Kuma 2.5.5 and Beszel hub 0.21.0 are pinned, isolated, and verified healthy on loopback-only
  listeners; their local first-run accounts and later production targets remain operator/X4 inputs,
  not X2 secrets.
- `./mvnw clean verify`: passed 139 ChatBackend tests with zero failures or errors, clean SpotBugs
  analysis, successful Quarkus packaging, and generated CycloneDX SBOMs.

The client-responsibility audit found no new messaging-client recovery contract: X2 adds an
administrator-only monitoring companion and does not change public HTTP/WebSocket behavior.

X2 is complete when:

- the monitoring workstation stack is installed, auto-starting, access-controlled, recoverable, and
  documented;
- fixture/rehearsal availability, host/container, audit, backup, deployment, collector-freshness,
  capacity, and security-gate conditions drive deterministic alert-state transitions;
- ChatMonitor catches up after simulated downtime, retains at least 62 days of detailed projected
  events, maintains an idempotent durable cursor, exposes data freshness/gaps, and cannot block or
  mutate its source;
- its dependency installation, automated tests, Vite production build, Node service, and clean-host
  recovery are reproducible, with no SQL credential in browser-delivered assets;
- the SQL projection/principal contract has migrations and positive/negative integration tests, while
  live query-plan and lock evidence is explicitly handed to X4;
- the source-adapter contract accepts SQL-audit and synthetic source-neutral fixtures without
  changing normalized persistence, aggregation, or dashboard APIs;
- malformed, oversized, unknown-version, duplicate, high-cardinality, or content-bearing fixture
  envelopes are rejected without contaminating retained telemetry; authentication and authorization
  for future remote client ingestion remain explicitly out of X2 scope;
- local alert-state, monitoring-outage/catch-up, ChatMonitor backup/restore, and clean-host recovery
  exercises are recorded;
- the monitoring threat model and canonical runbooks match the implemented foundation;
- all residual risks and accepted monitoring blind spots have an owner, reason, and review date;
- no unaccepted High or Critical implementation risk remains; and
- the X4 handoff identifies every live credential, network, threshold, alert-destination, production
  query-plan, and operational exercise still requiring external evidence.

Passing X2 means the monitoring foundation is independently deployable and ready for production
onboarding. It does not claim a production deployment or authorize normal production use.

## 13. Non-goals

X2 does not introduce multi-tenancy, end-to-end encryption, multi-instance realtime distribution,
new messaging features, enterprise analytics, invasive user-behavior tracking, a mandatory cloud
monitoring dependency, speculative distributed tracing, production database replication, monitoring
writes to production application data, public delegated client registration, implementation of
IE-01 through IE-03 native trust, linked-browser protocol, or official web-companion capabilities,
federation, or licensing terms for independently operated deployments.
