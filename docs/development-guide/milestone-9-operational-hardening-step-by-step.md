# Milestone 9 Implementation Guide

## Operational Hardening, Recovery, Supply-Chain Evidence, and Load Baseline

**Project:** Private Messenger

**Milestone:** 9 - Operational hardening

**Database:** Microsoft SQL Server 2022

**Application stack:** Java 25, Quarkus 3.33 LTS, Maven, Docker Compose

**Status:** Complete — repository implementation and local hardened deployment/recovery rehearsal
verified

**Last reviewed:** 2026-08-29

**Implementation snapshot:** The repository now contains the application hardening, least-privilege
principal model, NGINX/Compose rehearsal, encrypted backup/restore tooling, SBOM/security gates, load
harness, threat model, and supporting tests described by this guide. The hardened HTTPS/WSS stack,
encrypted isolated restore, RabbitMQ outage/recovery behavior, characterization and threshold load
runs, clean/upgrade migrations, Postman contracts, privacy checks, and runtime privilege boundaries
have been exercised successfully. The canonical 138-test build, SpotBugs analysis, CycloneDX SBOM,
dependency review, image/config/secret scans, Flow Smoke, database bootstrap/migration, and Postman
CI gates pass. Netty is aligned on 4.1.137.Final to remediate CVE-2026-62380. The repository-owned
Milestone 9 implementation and local rehearsal are complete; the external production-activation
requirements in Section 16 remain intentionally outside this milestone.

Infrastructure scan status is explicit: the current RabbitMQ, unprivileged NGINX, application, and
repository-owned SQL Server-only migration images pass the High/Critical gate. Microsoft's SQL Server
2022 CU26 image still reports High findings in vendor-built Go helper binaries. The project owner
accepted that visible, narrowly scoped residual risk for the private local rehearsal through
2026-11-26; no scanner suppression was added, and production use requires a separate review.

---

## 0. Purpose and Scope

Milestone 9 turns the already functional backend into an operationally testable deployment unit. The
goal is not to claim production readiness without a selected host, domain, certificate issuer, secret
store, and off-host backup destination. The goal is to make every repository-owned part of that future
deployment reproducible and to record the exact external evidence still required.

This milestone preserves the core platform invariants:

- SQL Server remains authoritative for durable application state.
- WebSockets remain non-authoritative delivery signals.
- the application runtime never connects as `sa` or a schema owner;
- database migrations complete before application rollout;
- secrets do not enter images, source control, logs, SBOMs, or test reports;
- a backup is not accepted until it has been restored into an isolated database and exercised;
- repository scripts contain operational behavior while CI workflows remain thin orchestrators.

### 0.1 In scope

1. Harden the existing OCI application image and attach standard build metadata.
2. Add a backend-only reverse proxy that terminates TLS and correctly proxies HTTP and WebSockets.
3. Define a hardened deployment Compose overlay with private database and queue networks.
4. Verify and document separate bootstrap, migration, runtime, backup, and restore database authority
   boundaries, including clean-install and upgrade paths.
5. Automate SQL Server-native full backup, preliminary verification, isolated restore, and application
   smoke validation.
6. Generate a CycloneDX SBOM and run dependency, filesystem, image, and secret scans in CI.
7. Add a small, reproducible HTTP/WebSocket-aware load test and record the first baseline.
8. Produce a system-specific threat model and close or explicitly accept its high-priority findings.
9. Document fresh-host deployment, health verification, backup scheduling, restore, rollback, and
   evidence collection.
10. Close the known raw-query audit leak, define the hardened WebSocket authentication/Origin policy,
    and establish minimum degraded-state and operational monitoring signals.

### 0.2 Explicitly out of scope

- provisioning a production host, DNS zone, public domain, or certificate authority account;
- committing private keys, real certificates, passwords, tokens, or off-host storage credentials;
- Kubernetes, service meshes, autoscaling, or cloud-provider-specific deployment resources;
- multiple backend replicas or cross-instance WebSocket event distribution;
- the future frontend/midlayer tier and its second proxy layer;
- end-to-end message encryption, key management, push notifications, presence, or media transport;
- observability platforms such as Prometheus, Grafana, Loki, or an OpenTelemetry collector;
- asserting a production SLA before real-host monitoring and recovery drills exist.

The first production deployment remains a separate activation exercise. It must supply the external
values and evidence listed in Section 16.

---

## 1. Authoritative Inputs and Current Baseline

Implementation must remain aligned with:

- `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`, Milestone 9;
- `docs/architecture/single-host-layered-container-architecture.md`;
- `docs/operations/environment-strategy-and-rollout-plan.md`;
- `docs/database/sql-server-principals-and-permissions.md`;
- `docs/architecture/decision/ADR-0016-use-websockets-next-for-realtime-signaling.md`;
- `docs/client-integration/client-responsibility-and-recovery-guide.md`;
- `docs/development-guide/versioning-and-changelog-policy.md`.

### 1.1 Capabilities already present

- a multi-stage `Dockerfile` producing a Quarkus fast-jar image;
- `compose.devdocker.yaml` for application, SQL Server, and RabbitMQ rehearsal;
- health endpoints and a self-hosted deployment workflow;
- SQL Server bootstrap and forward-only Flyway migrations;
- a non-`sa` application principal named `wl_chat_app` with DDL-denial validation in CI;
- JSON application and HTTP-audit logs with request and trace correlation;
- REST and authenticated WebSocket integration suites;
- request limits, authentication throttling, and durable audit transport.

### 1.2 Gaps Milestone 9 closes

| Concern | Current baseline | Milestone 9 outcome |
|---|---|---|
| Image | Buildable but root-based and lightly described | Non-root, metadata-labelled, scanned, reproducible runtime image |
| Ingress | App port is reached directly | HTTPS-only public ingress with HTTP redirect and WebSocket upgrades |
| Networks | Dev services expose selected host ports | Hardened overlay exposes only the proxy and isolates data services |
| DB authority | Runtime has broad fixed-role membership; migration commonly uses `sa` | Explicit operator/migrator/runtime/backup paths with clean-install, upgrade, and negative permission assertions |
| Recovery | Persistent volume only | Encrypted native backup, verification, clean restore drill, and recorded evidence |
| Supply chain | SpotBugs and build tests | Dependency review, SBOM, filesystem/image/secret scans, retained reports |
| Capacity | No accepted baseline | Versioned scenario, thresholds, environment metadata, and baseline report |
| Security review | Security rules are distributed across docs/code | One threat model with owners, dispositions, and acceptance criteria |
| Operations | Dev scripts exist | Fresh-host, deploy, verify, rollback, backup, and restore runbooks |
| Audit privacy | Raw query capture can conflict with the no-query-logging ADR | Redacted audit/log paths with regression coverage before public ingress |
| Broker operations | Runtime topology creation and fail-open fallback are present | Least-privilege topology ownership, degraded-state evidence, DLQ/backlog checks |

---

## 2. Planning Resolutions

These decisions prevent implementation from branching into incompatible approaches.

### 2.1 Single backend instance for this milestone

Milestone 9 hardens one backend instance. The current WebSocket connection registry and committed
event dispatcher are process-local. Running multiple backend replicas before adding cross-instance
event distribution would make realtime delivery depend on which instance owns each socket. That is
not acceptable as an accidental side effect of an operations milestone.

The deployment layout for this milestone is:

```text
Client
  -> TLS reverse proxy
  -> one ChatBackend container
  -> SQL Server and RabbitMQ on private networks
```

Multi-replica application deployment remains a later architecture increment with its own ADR and
cross-instance realtime design.

### 2.2 Use NGINX for the repository-owned reverse-proxy reference

The detailed single-host architecture specifies edge NGINX and already defines its trust-boundary
responsibilities. Milestone 9 therefore uses a minimal NGINX configuration as the executable
reference. Apache APISIX remains a permissible future gateway replacement, but is not introduced in
parallel merely to exercise a second product. The operations strategy must be updated during
implementation so it no longer presents APISIX as the simultaneous preferred implementation.

This choice requires an ADR because it fixes a deployment boundary and resolves documentation drift.

### 2.3 TLS has rehearsal and production modes

- Local/CI rehearsal uses an ephemeral test CA and certificate generated outside version control.
- Production uses a publicly trusted certificate issued or mounted by the selected host process.
- TLS 1.2 and 1.3 are allowed; obsolete protocols are disabled.
- Port 80 performs only redirect and certificate-challenge duties.
- Secure WebSocket traffic uses `wss://` through the same public origin.
- Plain HTTP to the application remains private to the container network.

The repository will test configuration syntax, redirect behavior, certificate trust using the test
CA, HTTPS health access, request-ID forwarding, and a real WebSocket upgrade through NGINX. Public
certificate validity cannot be claimed until a domain exists.

### 2.4 Database provisioning is explicitly two phase

Database authority is split by operation rather than hidden behind one shared administrator:

1. **Bootstrap operator:** creates the database, server logins, and initial database users. It may be
   `sa` for an explicit local bootstrap only; it is never supplied to Flyway, ChatBackend, or a
   long-running container in the hardened deployment.
2. **Migrator:** validates and applies Flyway history inside the pre-created database. It receives the
   reviewed DDL and grant authority needed by migrations, but cannot create server logins, create
   unrelated databases, or act as the application runtime.
3. **Runtime:** performs only enumerated application DML and stored-procedure execution. It is removed
   from broad fixed database roles and cannot modify schema, security principals, or Flyway history.
4. **Backup operator:** can back up the expected application database to the controlled backup path,
   but cannot restore over an active database or perform application DML.
5. **Restore operator:** is a separately invoked break-glass/bootstrap authority used only by the
   isolated restore workflow or a deliberately authorized disaster-recovery procedure.

The operator first pre-creates the database and principals so the immutable historical migrations
that conditionally create them become no-ops on a clean hardened installation. The operator also
removes the historical runtime membership from `db_datareader` and `db_datawriter`; the migrator is
not granted role-management authority. Flyway then runs as the migrator. A new forward-only migration
refuses to proceed if broad runtime membership remains, revokes broad database-wide grants, and applies
the required object-level grants.
Previously applied Flyway migrations remain immutable.

Both a clean installation and an upgrade from the Milestone 8 permission state are mandatory test
paths. The application container receives only runtime credentials. The migration one-shot job
receives only migrator credentials. Bootstrap, backup, and restore credentials are injected only into
their short-lived operator commands and are absent from the application and migration containers.

Because the immutable `V20260808111000__grant_app_permissions.sql` migration adds the runtime user to
the historical broad roles, a clean installation has an explicit compatibility phase: the operator
pre-creates the principals and temporarily establishes the historical memberships, Flyway migrates to
the Milestone 8 target, the operator removes those memberships, and Flyway then applies the current
forward migrations. An upgrade starts at the operator-removal step. The migrator never receives role-
management authority merely to replay that historical transition.

### 2.5 SQL Server-native backups are the recovery artifact

The automated unit is a `BACKUP DATABASE ... WITH CHECKSUM` `.bak` file. `RESTORE VERIFYONLY` is a
preliminary corruption check, not proof of recovery. Acceptance requires restoration under a distinct
database name in an isolated SQL Server environment, Flyway history checks, application startup, and
representative authenticated API operations.

All staged backup artifacts are encrypted at rest using SQL Server backup encryption or an immediately
applied, tested file-encryption step. Encryption-key recovery is part of the restore drill. Repository
automation manages local staging and restore validation. Production off-host transfer, retention
enforcement, and alert routing are adapters selected when the destination is known; the guide must not
embed one storage vendor.

For repository rehearsal, use a **24-hour RPO objective** and a **60-minute restore-drill objective**.
These are validation objectives, not a production SLA. Production activation must replace or accept
them explicitly after the host, storage destination, dataset size, and operational owner are known.

### 2.6 Supply-chain tools and failure policy

- Maven dependencies: OWASP Dependency-Check, with a deliberately documented CVSS failure threshold
  and suppression file requiring justification and expiry/review metadata.
- Java component SBOM: CycloneDX Maven plugin producing JSON and XML.
- image operating-system packages, misconfiguration, and secret scanning: Trivy, pinned in CI. Do not
  add a second Java dependency gate that merely duplicates OWASP results without a distinct purpose.
- GitHub pull requests: dependency-review action where the event supports it.

Reports are uploaded even on failure. Scanner unavailability is distinguishable from a clean scan and
must not silently pass. Exact pinned action/plugin versions are selected and recorded during
implementation after checking current supported releases.

### 2.7 Load testing separates characterization from regression

k6 runs from a pinned container image against DevDocker. The scenario uses only generated test users
and synthetic content. It covers login, conversation/history reads, idempotent sends, edits or
acknowledgements where appropriate, and a bounded set of authenticated WebSocket connections.

The first run is a non-gating characterization run: correctness assertions still gate, but performance
thresholds do not yet exist. Its reviewed results establish environment-specific regression
thresholds. A second run against the same reset dataset must satisfy those committed thresholds before
the milestone is complete.

The accepted report records commit, application version, host CPU/architecture/memory, image
references, SQL Server configuration, scenario parameters, percentiles, throughput, error rate,
database pool settings, and observed resource use. A laptop result is not a production SLA.

### 2.8 Threat modelling is an entry and exit activity

Use a data-flow diagram plus STRIDE analysis across these trust boundaries:

- untrusted client to reverse proxy;
- reverse proxy to Quarkus;
- Quarkus to SQL Server and RabbitMQ;
- CI/build environment to image registry/deployment host;
- operator to secrets, backups, and restore tooling;
- WebSocket handshake, long-lived connection, and reconnect recovery paths.

Create the initial model before writing deployment configuration so public-auth, trust-boundary,
credential, backup, and logging decisions guide implementation. Re-review and close it after the
implementation exists. Each threat records asset, precondition, impact, existing control, residual
risk, disposition, owner, and verification evidence. High or critical unresolved risks block
milestone completion unless the project owner explicitly accepts them in writing.

### 2.9 Hardened WebSocket authentication and Origin policy

- The public hardened profile disables query-string token authentication. Local/DevDocker may keep it
  temporarily for compatibility, but it is deprecated and must never be used in documented production
  examples.
- Browser clients use the `token.<token>` or `bearer.<token>` WebSocket subprotocol. Non-browser
  clients may use that transport or `Authorization: Bearer <token>` when their library supports it.
- The hardened profile requires an explicit allowlist for browser `Origin` values. A present Origin
  not on the allowlist is rejected. Missing Origin is accepted for non-browser clients because Origin
  is not a reliable non-browser authentication mechanism.
- Query parameters, authorization data, subprotocol credentials, cookies, and message bodies must not
  enter proxy, application, audit, scanner, or test logs.
- Handshake authentication failures, per-source connection limits, per-user socket limits, and idle
  timeout behavior must be tested through the public proxy.

This is a profile-level compatibility change rather than a silent global removal. The client guide and
release notes must explain the hardened transport policy before production activation.

### 2.10 RabbitMQ is ready-but-degraded, not a hidden hard dependency

SQL Server remains readiness-gating. RabbitMQ audit transport remains fail-open so a broker outage does
not make messaging unavailable, but the service is operationally degraded until queued/fallback audit
delivery recovers. The hardened deployment must provide:

- an operator-owned provisioning step for the vhost, exchange, queue, binding, and dead-letter path;
- an application principal without administrator tags and only the required vhost/resource rights;
- no public AMQP or management port;
- a machine-checkable degraded signal or diagnostics command for publish/fallback failures;
- queue depth, dead-letter depth, oldest-message age, and recovery-drain checks;
- documented credential rotation and broker-unavailable recovery behavior.

### 2.11 Logging, correlation, and minimum monitoring contracts

- Fix raw-query handling before enabling public ingress: redaction must occur before both JSON logging
  and RabbitMQ/SQL durable audit publication, with tests proving tokens cannot survive either path.
- NGINX supplies or preserves `X-Trace-Id`; ChatBackend continues to generate its own `X-Request-Id` as
  defined by ADR-0015. Both identifiers remain available for investigation.
- The hardened application profile logs to stdout. A file log is permitted only when an explicitly
  owned, size-bounded volume is mounted; the read-only root filesystem remains intact.
- Minimum operational checks cover service readiness, RabbitMQ degradation, queue/DLQ depth,
  certificate expiry, backup age, last successful restore drill, disk use, restart loops, and scanner
  or deployment failures. A full metrics platform remains out of scope, but detection commands,
  thresholds, owners, and escalation actions are not.

---

## 3. Planned Repository Layout

Names may be adjusted slightly during implementation, but responsibilities must remain separate.

```text
deploy/
├── compose.hardened.yaml
├── nginx/
│   ├── nginx.conf
│   └── conf.d/chat-backend.conf
└── tls/
    └── README.md

scripts/
├── database/
│   ├── provision-hardened-principals.sh
│   └── verify-hardened-permissions.sh
├── ci/
│   ├── build-image.sh
│   ├── generate-sbom.sh
│   ├── scan.sh
│   └── run-load-test.sh
├── deploy/
│   ├── validate-environment.sh
│   ├── deploy.sh
│   ├── smoke-test.sh
│   ├── verify-websocket-policy.sh
│   └── rollback.sh
└── operations/
    ├── backup-database.sh
    ├── verify-backup.sh
    ├── restore-database.sh
    └── collect-diagnostics.sh

load-test/
├── README.md
├── chat-backend-baseline.js
└── results/.gitkeep

docs/
├── architecture/decision/ADR-0017-harden-single-instance-deployment.md
├── operations/
│   ├── fresh-host-deployment-runbook.md
│   ├── backup-and-restore-runbook.md
│   └── load-test-baseline.md
└── security/threat-model.md
```

Generated certificates, private keys, backup files, load-test raw results, scanner caches, and local
secret files must be ignored. Sanitized summary evidence may be committed where the runbook requires
it.

---

## 4. Step 1 - Establish the Security Baseline and Record Decisions

Before infrastructure implementation:

1. Create the first reviewed version of `docs/security/threat-model.md` using Section 2.8.
2. Add regression tests that fail against the current raw-query exposure, then redact query values
   before both the structured log and durable audit paths. Preserve parameter names only when useful.
3. Implement/configure the hardened WebSocket token-transport and Origin policy from Section 2.9.
4. Decide and document proxy-owned versus application-owned forwarding and correlation headers.
5. Create ADR-0017. It must record:

- the single-instance Milestone 9 boundary;
- NGINX as the reference reverse proxy;
- TLS termination, WebSocket public-auth policy, and trusted-proxy behavior;
- the two-phase database provisioning model and distinct operational identities;
- encrypted native backup/restore validation and rehearsal objectives;
- RabbitMQ ready-but-degraded behavior and topology ownership;
- why multiple backend replicas are deferred;
- what remains external to the repository.

Then update the environment strategy and single-host architecture cross-references so the current
Milestone 9 implementation and the larger future topology are distinguishable.

Acceptance:

- neither ordinary nor durable audit evidence contains raw query values or bearer credentials;
- hardened-profile WebSocket tests cover allowed and rejected Origins and token transports;
- no authoritative document simultaneously directs implementers to deploy both APISIX and NGINX;
- the future multi-layer/multi-replica architecture remains documented but is not presented as a
  Milestone 9 exit requirement;
- the initial threat model has dispositions for every high/critical finding before implementation
  proceeds.

---

## 5. Application Image Work Package

Refine the `Dockerfile` rather than adding competing production Dockerfiles.

Required properties:

1. Build from pinned Java 25 builder/runtime versions; release evidence records immutable digests.
2. Run the final image as a dedicated non-root UID/GID.
3. Copy only the Quarkus runtime artifact into the final stage.
4. Set OCI labels for title, version, revision, creation time, and source.
5. Do not bake environment secrets or local configuration into any layer.
6. The hardened profile logs to stdout and uses `tmpfs` only for required temporary data. Keep the
   general-purpose `/tmp` mount non-executable. Argon2's JNA extraction uses a separate bounded,
   executable tmpfs owned by the runtime UID; `-Djna.tmpdir` must point only to that mount, and native
   initialization must fail application startup when the mount is unusable. If file logging is
   deliberately enabled, mount one bounded directory owned by the runtime UID.
7. Support graceful container shutdown within a documented timeout.
8. Add a `.dockerignore` excluding `.git`, `target`, logs, secrets, backups, certificates, reports,
   and workstation metadata.

Automated evidence:

- build the image from a clean checkout;
- inspect labels and configured user;
- assert the runtime UID is not zero;
- assert the root filesystem is read-only, capabilities are dropped, and only declared paths are
  writable;
- start the image against DevDocker dependencies;
- verify `/q/health/live` and `/q/health/ready`;
- stop it and record clean termination;
- scan its final filesystem and configuration.

Do not add a shell-based container `HEALTHCHECK` unless the final image deliberately contains the
required client. Compose/orchestrator health probes may call the application from a probe container.

---

## 6. Hardened Services, Compose, and Network Work Package

Create an overlay or separate hardened Compose definition that reuses the proven service settings
without turning `compose.devdocker.yaml` into a production-secrets template.

Required behavior:

- pin NGINX, SQL Server, RabbitMQ, the application, migration tooling, and probe/tool images to reviewed
  versions and record release digests;
- only NGINX publishes host ports 80/443;
- application, SQL Server, and RabbitMQ publish no public host ports;
- the application and proxy share only the backend network;
- SQL Server and RabbitMQ join only networks needed by their consumers;
- secrets enter through environment-owned files or secret mounts, never Compose defaults;
- application, proxy, and queue use `no-new-privileges` where supported;
- read-only filesystems, `tmpfs`, dropped capabilities, and explicit resource limits are applied where
  compatible;
- a one-shot initializer grants fresh SQL Server named volumes, including its durable writable secrets
  directory, to the image's documented non-root `10001:10001` account and installs the generated TLS
  identity before database startup; the long-running SQL Server process remains non-root;
- SQL Server data, SQL backups, and RabbitMQ data use durable volumes;
- startup dependencies use readiness/health conditions, while the migration job is one-shot;
- restart policies do not create an endless failed-migration loop;
- RabbitMQ topology is provisioned by an operator step; the application principal has no
  administrator tag and only required vhost/resource permissions;
- SQL readiness gates application readiness, while RabbitMQ outage is surfaced as the documented
  ready-but-degraded audit condition;
- verified SQL TLS uses `encrypt=true;trustServerCertificate=false` with a mounted truststore or
  equivalent trusted certificate chain for application and migrator connections.

Validation includes `docker compose config`, secret-placeholder checks, network/port inspection, a
fresh-volume launch, host reboot/restart rehearsal where available, and a negative check that port
1433 is not reachable externally. Verification also proves that an untrusted SQL certificate fails,
the runtime RabbitMQ principal cannot administer users/vhosts, and queue/DLQ diagnostics remain
available without exposing the management port publicly.

---

## 7. HTTPS and WebSocket Proxy Work Package

The NGINX reference must:

- redirect HTTP to HTTPS;
- terminate TLS using mounted certificate/key paths;
- accept only TLS 1.2/1.3;
- proxy REST and `/api/v1/ws` to ChatBackend;
- preserve WebSocket `Upgrade`/`Connection` behavior and use suitable idle timeouts;
- generate or preserve `X-Trace-Id` and allow ChatBackend to generate its own `X-Request-Id` according
  to ADR-0015;
- overwrite untrusted forwarding headers and supply only the trusted proxy chain;
- enforce a body limit compatible with the Quarkus limit;
- hide implementation details and avoid publishing Quarkus management endpoints by default;
- emit access logs without authorization headers, tokens, query-token credentials, or message bodies.
- reject browser Origins not present in the hardened allowlist;
- reject query-string WebSocket token transport in the hardened profile;
- enforce documented handshake, source, and per-user connection limits without changing durable REST
  recovery semantics.

Set `chat.http.trusted-proxies` to the exact proxy subnet/address used by the hardened deployment. Do
not use a universal trust range.

Automated proxy tests:

1. NGINX configuration syntax passes.
2. HTTP redirects to the expected HTTPS URL.
3. HTTPS fails without trusting the test CA and succeeds with it.
4. Liveness/readiness routing follows the documented exposure policy.
5. An authenticated REST request works through HTTPS.
6. An authenticated WebSocket connects through `wss://`, exchanges ping/pong, and observes a durable
   event generated through REST.
7. Oversized requests fail at the intended boundary.
8. Spoofed forwarding/request headers do not override proxy-owned identity.
9. Allowed browser Origin plus subprotocol authentication succeeds; disallowed Origin and query-token
   authentication fail without credential leakage.
10. Proxy and application evidence contain a shared trace ID and distinct application request ID.

---

## 8. Database Provisioning and Least-Privilege Work Package

Update `docs/database/sql-server-principals-and-permissions.md` to the implemented Milestone 9 model.
Add idempotent operational provisioning/rotation scripts. Do not edit applied Flyway migrations.

Required implementation sequence:

1. The operator pre-creates `wl_chat`, the migrator/runtime/backup logins, and matching database users.
   Existing conditional historical migrations therefore remain valid and skip login/user creation on
   a clean hardened installation.
2. On a clean install, the operator temporarily pre-establishes the two historical runtime role
   memberships and Flyway migrates only through the Milestone 8 target. This is a compatibility step,
   not the final runtime authority.
3. The operator removes `wl_chat_app` from `db_datareader` and `db_datawriter`. Flyway then continues
   as the migrator against the pre-created database. Add a new forward-only
   migration that refuses to run if those memberships remain, revokes broad database-wide
   `EXECUTE`/`VIEW DEFINITION` where not required, and grants only enumerated schema/object access.
4. An upgrade begins at step 3; it does not replay or edit historical migrations.
5. Keep permission changes for future objects in the same migration change set as those objects.
6. Start ChatBackend only after migration success, with runtime credentials only.

Test two database histories independently:

- **clean install:** empty SQL Server instance -> operator provisioning -> migrator -> runtime smoke;
- **upgrade:** Milestone 8 schema and broad runtime roles -> new forward migration -> runtime smoke and
  negative privilege checks.

The verification suite must prove:

- the running application identity is the configured runtime principal;
- runtime can perform every required application operation;
- runtime cannot create, alter, or drop objects;
- runtime cannot change role membership or impersonate privileged principals;
- runtime cannot insert/update/delete Flyway history;
- runtime cannot read server-level secrets or unrelated databases;
- migrator can validate/apply Flyway history but is not used by the application container;
- migration credentials are absent from the application container environment/filesystem;
- bootstrap administrator credentials are absent after provisioning;
- each migration that introduces a new object includes or is followed by required least-privilege
  grants;
- backup can create an encrypted backup only for the expected database/path and cannot restore or
  perform application DML;
- restore authority is absent from all long-running containers and is accepted only by the isolated,
  guarded restore command;
- application and migrator reject an untrusted SQL Server certificate.

Local scripts may still support an explicit administrator bootstrap command. Normal application start
and deployment must never silently fall back to `sa`.

---

## 9. Encrypted Backup and Restore Work Package

### 9.1 Backup command

`backup-database.sh` must:

- require explicit environment/config input;
- resolve a validated, fixed backup directory and safe filename;
- execute SQL Server `BACKUP DATABASE [wl_chat] ... WITH CHECKSUM` using the backup operator;
- encrypt the backup with a recoverable key/certificate, or immediately encrypt the staged file before
  it leaves the protected SQL backup volume;
- avoid logging credentials or private data;
- write a machine-readable result containing timestamp, database, backup identifier, size, checksum
  status, and source version without containing message data;
- fail non-zero if SQL Server reports any backup error.

### 9.2 Preliminary verification

`verify-backup.sh` verifies/decrypts the artifact, executes `RESTORE VERIFYONLY ... WITH CHECKSUM`, and
checks that the file is non-empty and belongs to the expected database. Its output must say explicitly
that this is not a restore drill.

### 9.3 Restore drill

`restore-database.sh` must require an isolated target and refuse the configured active database unless
an explicit, separately guarded disaster-recovery mode is designed later. The normal drill:

1. starts a clean SQL Server target or creates a distinct restore database;
2. inspects logical file names with `RESTORE FILELISTONLY`;
3. restores with explicit `MOVE` paths;
4. checks database consistency and records the restored Flyway history;
5. runs the repository migration image as the migrator when the restored history precedes the current
   application schema;
6. starts the compatible ChatBackend image with runtime credentials against the restored target;
7. verifies health, login, conversation history, message bodies/tombstones, membership, session,
   durable audit, and delivery/read cursor state using seeded
   synthetic drill data;
8. records elapsed time and whether the 24-hour rehearsal RPO and 60-minute rehearsal restore objective
   were met;
9. destroys only the validated isolated drill target after evidence is retained.

### 9.4 Retention and off-host boundary

Repository scripts implement deterministic encrypted local retention for rehearsal. Production activation
also requires transfer to physically separate storage, least-privilege backup credentials,
retention-lock/versioning where available, failure alerting, and a documented restore retrieval path.
Those settings remain parameters until a destination is selected.

---

## 10. Dependency, Image, and Supply-Chain Work Package

Add pinned build/CI integration for:

- CycloneDX JSON and XML SBOM generation for the complete Java artifact;
- OWASP dependency vulnerability analysis;
- Trivy repository/filesystem, configuration, secret, and final-image scanning;
- pull-request dependency review.
- final-image operating-system package scanning and explicit scans of the pinned NGINX, SQL Server,
  RabbitMQ, migration, and probe/tool images used by the hardened stack.

Requirements:

- SBOMs identify application version and source revision;
- generated artifacts live under `target/` or the CI artifact directory and are not casually committed;
- CI uploads reports on both success and failure;
- severity thresholds and allowed suppressions are version controlled;
- every suppression names the vulnerability, reason, scope, owner, and review/expiry date;
- scanner database/download failures fail or explicitly mark the job inconclusive;
- the protected CI environment supplies `NVD_API_KEY` to the OWASP check; local runs without the key
  are valid but can spend substantial time downloading the public vulnerability feed;
- secrets are masked and no scanner command echoes them;
- the guide records how to reproduce scans locally.

The Maven `verify` lifecycle may generate the SBOM and run stable dependency checks when practical,
but network-dependent image databases should remain a distinct CI step so the canonical Java build is
not made nondeterministic by transient scanner services.

---

## 11. Load Characterization and Regression Work Package

### 11.1 Scenario

Use generated accounts and conversations in an isolated environment reset from a known empty
volume/snapshot for every characterization or regression run. Setup uses unique fixture names, records
seed time separately, and never relies on cleanup of production-like data. A representative profile:

- a short warm-up;
- stepped HTTP virtual users at documented small loads;
- authenticated directory/history/position reads;
- idempotent message sends plus bounded acknowledgement traffic;
- a small fixed number of authenticated WebSocket connections receiving event signals;
- a cool-down/reconciliation phase checking durable state through REST.

Do not use production identities or message content. Test setup must not rely on the bootstrap endpoint
remaining reusable after initial provisioning.

### 11.2 Characterization and regression gates

The first run is characterization and fails only on correctness, corruption, instability, or resource
leaks. Review its evidence, commit explicit environment-specific error/latency/resource thresholds,
reset the environment, and run the scenario again. The second regression run must satisfy those
thresholds. Both runs fail on:

- unexpected HTTP responses or socket protocol errors;
- any lost durable message or invalid cursor transition;
- resource exhaustion, unbounded connection-pool growth, or server instability.

The regression run additionally fails when the committed error-rate, p95/p99 latency, throughput, or
resource thresholds are violated.

### 11.3 Baseline report

Commit a sanitized summary, not a massive raw result. Record:

- date, source revision, application version, scenario revision;
- host architecture, CPU, memory, container runtime, and whether emulation was used;
- image versions/digests and relevant resource limits;
- dataset size and virtual-user/socket counts;
- throughput, error rate, p50/p95/p99, and maximum latency;
- CPU, memory, database pool, and SQL Server observations;
- RabbitMQ queue/dead-letter depth and publish/fallback observations;
- identified bottleneck and the accepted threshold for regression runs;
- a clear statement that this environment is or is not production-representative.

---

## 12. Final Threat-Model Review and Security Checklist

Create `docs/security/threat-model.md` with:

1. protected assets and security objectives;
2. deployment/data-flow diagram;
3. trust boundaries and privileged actors;
4. entry points: REST, WebSocket, proxy, SQL Server, RabbitMQ, CI, registry, backup storage, and SSH;
5. STRIDE table with existing and planned controls;
6. abuse cases for credential stuffing, token theft, authorization bypass, replay/idempotency abuse,
   socket exhaustion, spoofed forwarding headers, log injection, queue poisoning, SQL privilege
   escalation, malicious dependency/image, secret exposure, backup theft, destructive restore, and
   denial of service;
7. residual-risk register with owner and disposition;
8. verification links to tests, scripts, configuration, or runbooks.

Mandatory review checks include:

- hardened browser Origin enforcement and query-token rejection match Section 2.9;
- whether invitation creation/redeem and non-login abuse-sensitive endpoints need additional quotas;
- whether application/audit logs and backups meet retention and access-control expectations;
- RabbitMQ credentials, vhost permissions, topology, and network access are least privilege and the
  degraded-state contract is observable;
- whether dependency and base-image patch cadence has an accountable owner;
- whether rollback remains safe after forward-only database migrations.

The milestone does not require speculative controls unsupported by the threat model. It does require
closing, deferring with an owner, or explicitly accepting every identified finding.

---

## 13. Operational Runbooks and Thin CI/CD Work Package

### 13.1 Fresh-host deployment runbook

Document:

- supported x86-64 Linux and Docker/Compose prerequisites;
- firewall and port exposure;
- directory ownership and durable-volume locations;
- secret creation without example production values;
- certificate issuance/mounting;
- image retrieval by immutable tag/digest;
- bootstrap, principal provisioning, migration, and application startup order;
- health, HTTPS, WebSocket, logging, and database-access verification;
- backup schedule and first mandatory restore drill;
- rollback and diagnostics collection;
- schema-aware rollback rules: an older application image is used only when compatibility with the
  current Flyway state is proven; otherwise recover by forward fix or the guarded restore procedure;
- minimum checks, thresholds, owners, and escalation actions for certificate expiry, backup age,
  restore-drill age, disk use, container restart loops, RabbitMQ degradation, queue/DLQ depth, and
  deployment/scanner failure.

### 13.2 Repository scripts

Scripts must use strict shell behavior, validate required inputs, quote variables, avoid unsafe broad
deletion, avoid secret output, and support a dry-run or non-destructive validation mode where useful.
Deployment scripts accept image references and configuration paths rather than hard-coding one CI or
registry provider.

### 13.3 CI workflows

Add or refine workflows to orchestrate repository scripts for:

- image build and runtime smoke test;
- SBOM and security scanning;
- hardened Compose/proxy integration;
- backup/restore drill;
- manually triggered load baseline.

Heavy SQL Server/image jobs should use concurrency controls and artifact retention appropriate to CI.
Remote deployment remains gated behind an explicitly configured environment and approval. Missing
production secrets must not turn a required production deployment into a misleading success.

The existing workflows are part of this change, not an untouched parallel path:

- replace automatic remote `sa` bootstrap/migration on pushes to `main` with an explicitly approved,
  environment-protected operator/migrator workflow;
- remove `sa` and migrator credentials from application deployment/container environments;
- keep PR validation free of production secrets and use ephemeral fixtures;
- stop treating a missing required deployment secret as a successful deployment;
- deploy an immutable image digest rather than a mutable `latest` tag;
- keep the self-hosted DevDocker workflow clearly labelled as rehearsal unless it is deliberately
  converted to the hardened stack and validated as such.

---

## 14. Verification Matrix

| Area | Automated evidence | Manual/real-environment evidence |
|---|---|---|
| Java application | `./mvnw clean verify` | None beyond release smoke test |
| Image | Build, non-root assertion, labels, health, clean stop | Registry pull by digest |
| Compose/network | Config validation, exposed-port inspection, service health | Host firewall inspection |
| TLS/proxy | Config test, redirect, trusted test CA, REST and WebSocket path | Public DNS/certificate check |
| Audit privacy | Log and RabbitMQ/SQL audit redaction tests | Sanitized production evidence sample |
| WebSocket policy | Origin/token-transport positive and negative tests | Supported client-library handshake |
| DB principals | Clean/upgrade paths, runtime positive DML, negative DDL/security/Flyway tests | Production principal inventory |
| SQL transport | Untrusted-certificate rejection and trusted-chain success | Production certificate rotation check |
| RabbitMQ | Least-privilege negatives, fail-open degradation, backlog/DLQ recovery | Production broker inventory and alert routing |
| Backup | Encrypted native backup, key recovery, and `VERIFYONLY` | Encrypted off-host artifact check |
| Restore | Clean isolated restore plus application smoke journey | Timed recovery drill from off-host copy |
| Supply chain | Dependency review, SBOM, Trivy, OWASP reports | Finding disposition review |
| Load | Reproducible k6 run and durable-state assertions | Production-like baseline when host exists |
| Threat model | Required sections/findings linter where practical | Human sign-off and risk acceptance |
| Operations | Scripted certificate/backup/restore/queue/disk/restart checks | Alert delivery and operator exercise |

All automated checks must be reproducible from documented local commands. Expected negative tests may
log failures but must assert the intended outcome.

### 14.1 Canonical verification commands

Implementation may place stable argument sets behind the planned repository scripts, but the final
guide/runbooks must make these commands work from the repository root:

```bash
./mvnw clean verify
./scripts/database/validate-flyway-naming.sh
./scripts/database/verify-hardened-permissions.sh --scenario clean
./scripts/database/verify-hardened-permissions.sh --scenario upgrade-milestone-8

node --test scripts/postman/discover-postman.test.mjs
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh

WL_CHAT_TEMURIN_JDK_IMAGE=<reviewed-jdk-digest> \
WL_CHAT_TEMURIN_JRE_IMAGE=<reviewed-jre-digest> \
WL_CHAT_BUILD_IMAGE_TAG=chat-backend:milestone-9 ./scripts/ci/build-image.sh
docker inspect chat-backend:milestone-9
docker compose -f deploy/compose.hardened.yaml config --quiet
./scripts/deploy/validate-environment.sh
./scripts/deploy/smoke-test.sh
WL_CHAT_K6_IMAGE=<reviewed-k6-digest> \
WL_CHAT_LOAD_WS_BASE_URL=<wss-base-url> \
WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN=<allowed-origin> \
./scripts/deploy/verify-websocket-policy.sh

./scripts/operations/backup-database.sh --environment rehearsal
./scripts/operations/verify-backup.sh --environment rehearsal
./scripts/operations/restore-database.sh --environment rehearsal --isolated-target wl_chat_restore_drill

./scripts/ci/generate-sbom.sh
WL_CHAT_TRIVY_IMAGE=<reviewed-trivy-digest> \
WL_CHAT_SCAN_IMAGE=chat-backend:milestone-9 \
WL_CHAT_SCAN_MIGRATION_IMAGE=chat-backend-flyway:milestone-9 ./scripts/ci/scan.sh
NVD_API_KEY=<local-key> ./mvnw -DskipTests -Psecurity-scan verify

./scripts/ci/run-load-test.sh --phase characterization
./scripts/ci/run-load-test.sh --phase regression

git diff --check
git status --short
```

`run-load-test.sh` owns the reviewed k6 image digest and identical scenario mounting/network arguments
for both phases. Tests must also inspect the running application container to prove that
bootstrap/migrator/restore secrets are absent and that the effective UID is non-zero.

---

## 15. Ordered Implementation Sequence

1. Advance the active development version and record the audited planning contract.
2. Create the initial threat model; resolve public WebSocket auth/Origin, query-redaction, proxy trust,
   RabbitMQ degradation, database authority, and backup-security decisions.
3. Add audit/query-redaction regression coverage and implement the hardened WebSocket profile policy.
4. Create ADR-0017 and reconcile NGINX/APISIX, single/multi-instance, credential, backup, and degraded
   service documentation.
5. Implement two-phase database provisioning, the forward-only runtime permission migration, verified
   SQL TLS, and independent clean-install/upgrade permission tests.
6. Harden the Docker image and add `.dockerignore`.
7. Provision least-privilege RabbitMQ topology/credentials and add the hardened Compose/network layout.
8. Add NGINX TLS/HTTP/WebSocket configuration and public-boundary integration tests.
9. Implement encrypted backup, preliminary verification, key recovery, and isolated restore scripts.
10. Add SBOM generation and application/infrastructure image scanning with explicit policies.
11. Run non-gating load characterization, approve thresholds, reset the environment, and pass the
    gating regression run.
12. Complete deployment, backup/restore, rollback, monitoring, and diagnostics runbooks.
13. Replace/refine the existing migration/deployment workflows and wire repository scripts into thin,
    environment-protected CI/CD orchestration.
14. Re-review the threat model, resolve or explicitly accept residual risk, run the full verification
    matrix, update authoritative status snapshots, and audit the client integration guide.

This order makes security and authority decisions before infrastructure depends on them, proves both
database histories before deployment, and establishes performance gates only after characterization.

---

## 16. External Decisions and Evidence Required for Production Activation

The repository implementation can proceed without these values, but Milestone 9 must not claim an
actual production deployment until they are supplied:

1. x86-64 Linux host and operating-system patch policy;
2. public domain, DNS control, certificate issuer, and renewal owner;
3. OCI registry and immutable image-promotion policy;
4. production SQL Server placement and licence/edition;
5. secret store or host secret-delivery mechanism;
6. encrypted off-host backup destination and access policy;
7. production acceptance or replacement of the 24-hour rehearsal RPO and 60-minute rehearsal restore
   objective, plus backup schedule and retention;
8. alert destination and on-call/incident owner;
9. public network/firewall/SSH access policy;
10. accepted production load thresholds and maintenance window.

Until then, repository completion means **production-capable deployment rehearsal**, not a live
production environment.

---

## 17. Milestone Exit Criteria

Milestone 9 is complete only when all applicable boxes are evidenced:

### Deployment and ingress

- [x] A clean checkout builds an immutable, labelled, non-root application image.
- [x] The hardened stack starts from documented commands with no secrets in source or image layers.
- [x] Only the TLS proxy exposes application host ports; SQL Server/RabbitMQ remain private.
- [x] HTTPS REST and `wss://` signaling work through the proxy.
- [x] HTTP redirect, trusted forwarding headers, request limits, and health exposure are verified.
- [x] Hardened WebSocket Origin/token-transport rules pass positive and negative proxy tests.
- [x] Raw query values and credentials are absent from proxy, application, and durable audit evidence.

### Database and recovery

- [x] Clean-install and Milestone 8 upgrade database paths pass without editing applied migrations.
- [x] The application uses only enumerated runtime grants and automated DDL/security/Flyway-history
      negative privilege tests pass.
- [x] Migration/bootstrap credentials are not present in the running application container.
- [x] Application and migrator connections validate the SQL Server certificate and reject an untrusted
      certificate.
- [x] A native encrypted checksum backup is produced, its key is recoverable, and it passes
      `RESTORE VERIFYONLY`.
- [x] That backup is restored into an isolated clean target.
- [x] Flyway integrity, application startup, authentication, messages, and cursor state pass after
      restore.
- [x] Drill duration and RPO/RTO assessment are recorded.
- [x] The off-host/retention boundary is implemented for a selected environment or clearly
      recorded as the remaining production-activation prerequisite.

### Security and performance

- [x] Dependency scanning, image/config/secret scanning, and SBOM generation run in CI.
- [x] Findings and suppressions have documented dispositions; no unaccepted high/critical finding
      remains.
- [x] The threat model is reviewed and its blocking controls are verified.
- [x] RabbitMQ topology/runtime permissions are least privilege and ready-but-degraded recovery,
      backlog, and DLQ checks pass.
- [x] Characterization and threshold-gated load runs complete without durable-state corruption.
- [x] The environment-labelled performance baseline and regression thresholds are recorded.
- [x] Minimum operational checks cover certificates, backups, restore age, disk, restart loops,
      RabbitMQ degradation/backlog/DLQ, and CI/deployment failures.

### Documentation and validation

- [x] Fresh-host deployment, rollback, backup, restore, scanning, and load-test commands are current.
- [x] README, specification status, operations strategy, database-principal guide, CHANGELOG, and ADR
      references agree with the implementation.
- [x] Client-facing TLS, proxy, reconnect, outage, or recovery responsibilities discovered during the
      milestone are reflected in the client integration guide.
- [x] `./mvnw clean verify` passes.
- [x] Flyway naming validation, clean/upgrade migration tests, Postman discovery tests, discovery, and
      strict Postman validation pass.
- [x] Compose, proxy, security, backup/restore, and load-test validation commands pass.
- [x] `git diff --check` passes and no generated secrets, certificates, backups, reports, or build
      artifacts are tracked.

---

## 18. Definition of Done Versus Production Ready

Milestone 9 closes the roadmap's operational-hardening implementation scope. It does not automatically
make version `1.0.0` appropriate. The first production-ready release additionally requires the
external production activation evidence in Section 16, a deliberate release review, and completion of
the versioning policy's release procedure.

If Milestone 9 finishes only against Local/DevDocker/CI, describe it as a validated deployment and
recovery rehearsal. Do not state that public TLS, off-host recovery, monitoring, or production load
have been demonstrated when they have not.
