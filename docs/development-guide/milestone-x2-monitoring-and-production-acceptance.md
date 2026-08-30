# Milestone X2 — Monitoring, Operational Validation, and Production Acceptance

## Status

**Status:** Pocketed for future implementation; production integration depends on X1  
**Parent program:** [Milestone X — Production Activation](milestone-x-production-activation.md)  
**Required predecessor:** [Milestone X1 — Production Infrastructure and Recovery Foundation](milestone-x1-production-infrastructure-and-recovery.md)  
**Last refined:** 2026-08-30

## 1. Purpose and boundary

Milestone X2 makes the X1 production candidate observable, operationally testable, and eligible for a
deliberate production-release decision. It owns the monitoring workstation, availability and
host/container monitoring, ChatMonitor, the safe audit projection and local telemetry store, alert
delivery, monitoring recovery, operational exercises, final evidence review, and the `1.0.0`
acceptance decision.

ChatBackend must continue operating when every X2 monitoring component is unavailable. X2 does not
add messaging-domain behavior, replicate production data, or permit monitoring tools to mutate
application state.

## 2. Entry decisions, dependency, and sequencing

Before X2 production integration begins:

1. X1 must provide a secure and recoverable production candidate plus its evidence handoff;
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

ChatMonitor may be built and tested against fixtures before X1 completes. Its production SQL
projection, least-privilege principal, query plan, locking behavior, and alert thresholds cannot be
accepted until exercised against the X1 candidate.

## 3. Preferred Monitoring Topology

```text
Operator/development workstation
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
deliberately accepted. Its service lifecycle must start the availability/host-monitoring components,
ChatMonitor Node service, and alert adapters after networking and required storage are available.
Repeated startup failures must remain visible rather than creating an infinite silent restart loop.

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
CHAT_MONITOR_PORT=8090
CHAT_MONITOR_SQLITE_PATH=./var/data/chat-monitor.sqlite
CHAT_MONITOR_POLL_INTERVAL_SECONDS=60
CHAT_MONITOR_PAGE_SIZE=500
CHAT_MONITOR_DETAIL_RETENTION_DAYS=90
CHAT_MONITOR_AGGREGATE_RETENTION_DAYS=365
CHAT_MONITOR_SOURCE_HOST=<private-production-sql-host>
CHAT_MONITOR_SOURCE_PORT=1433
CHAT_MONITOR_SOURCE_DATABASE=wl_chat
CHAT_MONITOR_SOURCE_USERNAME=wl_chat_monitor
CHAT_MONITOR_SOURCE_PASSWORD=<supply-through-local-secret-mechanism>
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

### 5.2 Responsibility boundary

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

### 5.3 Collection ordering

The collector must use the durable persistence cursor:

```text
(created_at, event_id)
```

It must not use only `occurred_at`. RabbitMQ or local asynchronous delivery can persist an event after
its request occurrence time; an occurrence-time-only cursor could permanently skip such a late event.

### 5.4 Incremental collection cycle

On each scheduled cycle:

1. load the last locally committed `(created_at, event_id)` cursor;
2. request the next bounded page from the production monitoring view;
3. insert projected events locally using unique `event_id` idempotency;
4. update the cursor in the same local transaction;
5. repeat while a full page is returned;
6. calculate or update local metric buckets; and
7. record collection health, lag, and any error.

The normal polling interval should begin around 30–60 seconds and remain configurable. Catch-up uses
bounded pages and yields between failures; it must not issue one unbounded query after a long outage.

### 5.5 Restart and catch-up behavior

When the monitor restarts, it resumes from its last committed cursor and retrieves all still-retained
audit events persisted afterward. If local insertion succeeded but cursor persistence did not, the
unique `event_id` makes re-reading harmless.

The monitor must expose these states:

- `CURRENT` — source reachable and collection lag within threshold;
- `CATCHING_UP` — safe paged recovery is in progress;
- `SOURCE_UNAVAILABLE` — production SQL cannot be reached;
- `COLLECTION_FAILED` — source was reached but collection or local persistence failed; and
- `HISTORY_GAP` — the source retention boundary passed before missing events could be collected.

The monitor must never silently label incomplete history as current.

### 5.6 Local persistence and retention

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

### 5.7 Useful derived metrics

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

Before production approval, demonstrate:

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

The X2 implementation must define exact commands and retain evidence for:

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

## 11. X2 Exit Criteria and Production Decision

X2 and the parent Milestone X program are complete only when:

- the monitoring workstation stack is installed, auto-starting, access-controlled, recoverable, and
  documented;
- availability, certificate, host/container, audit degradation/backlog/DLQ, backup age, deployment,
  collector freshness, capacity, and security-gate alerts reach the assigned responder;
- ChatMonitor catches up after downtime, retains at least 62 days of detailed projected events,
  maintains an idempotent durable cursor, exposes data freshness/gaps, and cannot block or mutate
  production;
- its dependency installation, automated tests, Vite production build, Node service, and clean-host
  recovery are reproducible, with no SQL credential in browser-delivered assets;
- the dedicated SQL projection/principal passes positive and negative permission tests, query plans
  and candidate indexes are accepted, and normal/catch-up collection stays within locking and
  latency budgets;
- production-representative thresholds and capacity assumptions are approved;
- certificate renewal, alert delivery, monitoring outage/catch-up, backup restore, incident, and
  rollback exercises are recorded;
- the production threat model and every canonical runbook match the deployed state;
- all residual risks and accepted monitoring blind spots have an owner, reason, and review date;
- no unaccepted High or Critical risk remains; and
- the primary stakeholder records the deliberate production approval and `1.0.0` release decision.

Passing X2 means the monitored production candidate has been operationally validated and accepted for
normal use. Public network reachability does not turn the personal deployment into a commercial/open
service or promise an external SLA.

## 12. Non-goals

X2 does not introduce multi-tenancy, end-to-end encryption, multi-instance realtime distribution,
new messaging features, enterprise analytics, invasive user-behavior tracking, a mandatory cloud
monitoring dependency, speculative distributed tracing, production database replication, monitoring
writes to production application data, public delegated client registration, implementation of
IE-01 through IE-03 native trust, linked-browser protocol, or official web-companion capabilities,
federation, or licensing terms for independently operated deployments.
