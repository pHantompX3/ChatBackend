# Milestone X1 — Production Infrastructure and Recovery Foundation

## Status

**Status:** Pocketed for future implementation; depends on resolved production-environment decisions  
**Parent program:** [Milestone X — Production Activation](milestone-x-production-activation.md)  
**Successor increment:** [Milestone X2 — Monitoring and Production Acceptance](milestone-x2-monitoring-and-production-acceptance.md)  
**Last refined:** 2026-08-30

## 1. Purpose and boundary

Milestone X1 converts the Milestone 9 hardened local rehearsal into a secure, repeatable, and
recoverable production candidate. It owns the production host, private ingress, certificates,
secrets, immutable release process, SQL Server and RabbitMQ operations, off-host backups, rollback,
incident foundations, capacity characterization, ownership, and evidence manifest.

X1 does not approve normal production use or release version `1.0.0`. Its output is a production
candidate that can be deployed and recovered safely. Monitoring, ChatMonitor, operational alerting,
final acceptance exercises, and the production-release decision belong to X2.

## 2. Entry decisions and prerequisites

Before work begins, resolve and record the applicable decisions using the decision-record template in
the [Milestone X umbrella guide](milestone-x-production-activation.md):

1. production host hardware, physical location, operating system, patch owner, power protection,
   time synchronization, storage layout, and expected availability;
2. public Internet/router topology, ISP/CGNAT and inbound-port capability, firewall owner, DNS owner,
   certificate issuer, renewal owner, and private administrative/monitoring sources;
3. OCI registry, immutable image promotion policy, deployment approver, rollback owner, and protected
   evidence location;
4. production SQL Server placement, edition/licence, storage, maintenance owner, and supported backup
   tooling;
5. secret-delivery mechanism, privileged identity inventory, credential-rotation owner, and exposure
   response;
6. encrypted off-host backup destination, retention/immutability policy, recovery owner, RPO, and RTO;
7. release workload model, capacity limits, maintenance window, and X1 acceptance owner.

ADR-0017, ADR-0019, the Milestone 9 guide, threat model, and operations runbooks remain authoritative
for their existing decisions. X1 must document environment-specific values without committing
secrets, raw private topology, or production data.

## 3. Production Infrastructure and Recovery Workstreams

1. Provision private application, SQL Server, and RabbitMQ networks with only the authenticated
   HTTPS/WSS NGINX edge reachable from the Internet.
2. Replace rehearsal certificates with trusted, automatically renewed certificates appropriate to
   the selected network exposure.
3. Deliver runtime/operator credentials from the selected secret mechanism; keep them out of images,
   source control, deployment logs, and long-running containers that do not need them.
4. Promote reviewed immutable image digests and exercise migration-before-rollout and schema-aware
   rollback in the selected environment.
5. Schedule encrypted off-host backups, enforce retention/immutability, monitor age and transfer, and
   perform a timed isolated restore from the retrieved artifact.
6. Produce the production endpoints, evidence, and safe integration boundary required by the
   [X2 monitoring and acceptance guide](milestone-x2-monitoring-and-production-acceptance.md).
7. Run characterization and regression tests against production-representative resources and data,
   then record approved thresholds and capacity limits.
8. Complete the X1 threat-model review, recovery exercise, and production-candidate checklist; final
   incident/alert exercises and the deliberate `1.0.0` decision remain X2 responsibilities.

The detailed contracts below refine these workstreams. They extend rather than replace the accepted
single-host decision in
[`ADR-0017`](../architecture/decision/ADR-0017-harden-single-instance-deployment.md), the rehearsal
mechanics in the
[`Milestone 9 guide`](milestone-9-operational-hardening-step-by-step.md), and the canonical operations
runbooks under [`docs/operations`](../operations). When a host-specific choice is not yet available,
the contract states the required decision record, recommended default, and acceptance evidence rather
than inventing an environment value.

### 3.1 Production host and service-lifecycle contract

#### 3.1.1 Required host decision record

Before provisioning, record:

| Decision | Required detail |
|---|---|
| Hardware | CPU model/cores, RAM, system disk, data disk, expected useful life, and spare capacity |
| Platform | Host operating system, supported version, architecture, patch channel, and licence |
| Ownership | Physical location, administrator, recovery contact, and who can power or unlock it |
| Power | UPS availability, automatic shutdown behavior, restart after power restoration, and test date |
| Time | NTP source, timezone, clock-drift monitoring, and UTC handling |
| Runtime | Docker Engine and Compose versions, installation source, update owner, and support policy |
| Storage | Filesystems, mount points, encryption-at-rest decision, capacity, and failure replacement path |
| Maintenance | Patch window, reboot procedure, pre-checks, post-checks, and rollback owner |

The recommended personal-use baseline is a supported long-term-support host OS, automatic security
updates with a controlled reboot window, full-disk encryption where unattended restart requirements
permit it, a UPS when the host is expected to remain available, and at least 25% free storage after
the initial production dataset and recovery reserve are included. Final values require the actual
machine.

#### 3.1.2 Filesystem and authority layout

Production paths must be explicit, absolute on the host, owned by dedicated administrative or
service identities, and excluded from source control. The implementation guide should map the
environment's actual paths to these logical responsibilities:

```text
production-root/
├── compose/                 # reviewed Compose and non-secret deployment metadata
├── secrets/                 # service-specific mounted secrets; owner/read scope minimized
├── certificates/
│   ├── edge/                # HTTPS/WSS certificate and key
│   ├── sql-client-trust/    # trust material delivered only to SQL clients
│   └── sql-server/          # SQL Server certificate/key where host-managed
├── data/
│   ├── sql/                 # SQL data, log, and tempdb locations or volume mappings
│   ├── rabbitmq/            # broker durable state
│   └── application/         # only explicitly required application state
├── backups/
│   ├── staging/             # bounded temporary backup staging
│   └── manifests/           # checksums and transfer/verification status
├── logs/                    # host-owned service logs when not held in managed volumes
└── evidence/                # sanitized deployment, recovery, and verification records
```

Do not grant one long-running container access to the whole production root. NGINX receives only its
edge certificate material and configuration; ChatBackend receives only runtime application and SQL
trust material; SQL Server receives its data, backup staging, and SQL certificate material; RabbitMQ
receives only its broker data and credential/certificate inputs. Operator, migrator, backup, restore,
and monitor credentials remain absent from ChatBackend.

#### 3.1.3 Boot, restart, and shutdown behavior

The production service manager must:

1. start Docker before the production Compose project;
2. preserve SQL Server and RabbitMQ data through container replacement;
3. use bounded health-based dependency sequencing rather than assuming process start means ready;
4. restart failed long-running services according to an explicit policy that avoids infinite rapid
   crash loops;
5. never run bootstrap, migration, restore, or destructive maintenance automatically at every host
   boot;
6. expose restart counts and last-start time to monitoring;
7. stop accepting edge traffic before an intentional shutdown where practicable; and
8. allow SQL Server and RabbitMQ to flush durable state within bounded shutdown timeouts.

Auto-start must be tested after a cold host reboot and after an unexpected power-loss simulation that
does not endanger the hardware. Acceptance requires application readiness, WebSocket handshake,
RabbitMQ recovery, SQL consistency checks appropriate to the event, persistent data verification,
and no operator credential left in the running service environment.

#### 3.1.4 Host maintenance and capacity

Define warning and critical thresholds for system disk, SQL data/log/tempdb, RabbitMQ, backup staging,
container layers, logs, and future media storage separately. Cleanup must target known disposable
artifacts and never use an unbounded recursive deletion against a broad production path. Before OS,
Docker, firmware, or storage maintenance:

1. confirm a recent verified off-host backup and recovery key;
2. record current image digests, Flyway version, health, and monitor state;
3. pause deployments and scheduled maintenance that could overlap;
4. perform the smallest reviewed change;
5. verify the complete HTTPS/WSS and durable-data path afterward; and
6. record any new residual risk or changed capacity.

### 3.2 Network, ingress, and administrative-access contract

#### 3.2.1 Target traffic model

The default single-host traffic model remains:

```text
remote Internet and local client devices
        │ HTTPS/WSS :443
        ▼
NGINX edge
        │ private application port
        ▼
one ChatBackend instance
        ├── private SQL TLS ──> SQL Server
        └── private AMQP ─────> RabbitMQ

operator workstation
        ├── HTTPS/WSS synthetic checks ──> NGINX
        ├── approved host/container monitoring ──> production agent
        └── read-only SQL TLS ──> monitoring projection only
```

Only NGINX publishes the client-facing HTTPS/WSS port. Port 80 may exist
only for an approved ACME challenge and/or redirect path; it must not expose ChatBackend directly.
SQL Server, RabbitMQ AMQP,
RabbitMQ management, ChatBackend's internal HTTP port, and container-engine control interfaces must
not be publicly reachable.

#### 3.2.2 Firewall and reachability matrix

The implementation guide must replace placeholders with exact addresses/CIDRs and retain the tested
rule inventory:

| Source | Destination | Service | Default disposition |
|---|---|---|---|
| Internet and local client sources | NGINX | TCP 443 HTTPS/WSS | Allow through hardened edge policy |
| Any source | ChatBackend direct port | internal HTTP/WSS | Deny |
| Certificate authority, if HTTP-01 is selected | NGINX challenge handler | TCP 80 | Allow only as required |
| Internet/unapproved LAN devices | SQL Server | TCP 1433 or selected port | Deny |
| Internet/unapproved LAN devices | RabbitMQ AMQP/management | selected ports | Deny |
| ChatBackend network/identity | SQL Server | SQL TLS | Allow |
| ChatBackend network/identity | RabbitMQ | AMQP/TLS as selected | Allow |
| Operator workstation fixed address/range | monitoring SQL projection | SQL TLS | Allow privately |
| Operator workstation | monitoring agent/API | selected private port | Allow |
| Approved administrator address only | host administration | selected SSH/management path | Allow |
| Any workload | Docker socket/API | engine control | Deny unless explicitly required |

Use a DHCP reservation or static assignment for the production host and monitoring workstation where
firewall rules depend on their addresses. Administrative access should use key-based authentication,
an allowlisted source, no direct privileged login, and logged elevation. If remote access is required,
prefer a private VPN-style path over publishing administrative services.

#### 3.2.3 Accepted client-access model

Production follows ADR-0019:

- **public remote access:** native mobile, web, and other compatible clients reach only the NGINX
  HTTPS/WSS edge;
- **private internals:** ChatBackend, SQL Server, RabbitMQ, administration, monitoring SQL, and Docker
  control are not Internet-reachable;
- **initial client trust:** normal user authentication and server-side authorization are
  authoritative; the server does not attest exact client software; and
- **future required hardening:** IE-01 adds native per-installation identity and gateway mTLS; IE-02
  adds mobile-authorized browser pairing; IE-03 adds the official web companion after both backend
  foundations are accepted.

NGINX must not treat Origin, CORS, user-agent, client labels, custom headers, or an embedded shared
secret as client authentication. Public/delegated OAuth client registration remains outside X1.

#### 3.2.4 Network acceptance evidence

Retain sanitized port scans from Internet and LAN test positions, effective router and host firewall
rules, Docker published-port inspection, DNS resolution, trusted proxy configuration,
client-IP/trace forwarding checks, and proof that only NGINX TCP 443 is public while direct
SQL/RabbitMQ/ChatBackend/administrative access fails. Exercise authentication throttling, bootstrap
closure, session revocation, HTTP/WSS limits, and common malformed traffic through the public edge.
Evidence must not reveal reusable credentials or unnecessarily publish private topology.

### 3.3 DNS and certificate-lifecycle contract

#### 3.3.1 Required certificate decisions

Record separately for the NGINX edge and SQL Server transport:

| Decision | Required detail |
|---|---|
| Names | Canonical DNS names and every required subject alternative name |
| Issuer | Public CA, private CA, or other accepted trust source |
| Validation | DNS-01, HTTP-01, private issuance, and the accountable owner |
| Automation | ACME/client/tool, pinned installation source, execution identity, and schedule |
| Storage | Certificate, private key, chain, truststore, and backup/recovery locations |
| Permissions | Owner/group/mode or equivalent ACL for every consumer |
| Renewal | Attempt interval, warning thresholds, reload behavior, and failure escalation |
| Revocation | Compromise response, replacement, client trust update, and evidence |

A publicly trusted server certificate is mandatory for the remote mobile production path. DNS-01 is
preferred when inbound port 80 should remain closed; HTTP-01 may be used only with an explicitly
bounded challenge path. A private/local CA and rehearsal certificates are insufficient for general
remote clients.

#### 3.3.2 Renewal and rotation sequence

The production process must:

1. request or renew without exposing the private key to application containers;
2. validate name coverage, chain, expiry, key match, and file permissions;
3. stage material atomically so NGINX or SQL Server never reads a partial pair;
4. test configuration before reload/restart;
5. reload the smallest affected service;
6. verify HTTPS, WSS, SQL client trust, and expiry through the normal paths;
7. retain only the minimum rollback material allowed by policy; and
8. alert and fail visibly if renewal or activation does not succeed.

Exercise ordinary renewal and emergency replacement before production approval. Certificate expiry
alerts remain required at 30 and 14 days; an additional urgent seven-day alert is recommended.

#### 3.3.3 Client and Postman implications

Production Postman and future clients use the public `https://` and `wss://` endpoints and trust
the selected public issuer. Do not distribute a server private key or private-key passphrase to
clients. Browser HTTP and WebSocket origins must match the deployment allowlists. Origin checks
harden browsers but do not authenticate frontend software or replace user authorization.

### 3.4 Secret and privileged-identity lifecycle contract

#### 3.4.1 Secret inventory

The implementation guide must enumerate, owner-tag, and classify at least:

- bootstrap/operator SQL authority;
- Flyway migrator credential;
- ChatBackend runtime SQL credential;
- backup SQL credential;
- break-glass restore authority;
- ChatMonitor read-only SQL credential;
- RabbitMQ operator and ChatBackend credentials;
- bootstrap administrator application credential until bootstrap is closed;
- backup certificate/private-key recovery passphrase;
- SQL client truststore password where retained;
- edge and SQL private keys; and
- registry, deployment, alert-delivery, DNS, and off-host-storage credentials.

For each, record producer, consumers, delivery path, storage location, rotation owner, expiry/cadence,
recovery path, revocation procedure, and whether downtime is required. Do not place actual values in
this document, Git, image layers, Compose defaults, CI artifacts, logs, command histories, or
ChatMonitor browser assets.

#### 3.4.2 Delivery and storage rules

The selected host secret mechanism must support service-specific readable files or an equivalently
narrow delivery method. Environment variables may be used only when the exposure and process-inspection
tradeoff is accepted and no safer supported mount exists. Secret files must be owner-restricted,
mounted read-only into only the consumer, excluded from backups unless deliberately encrypted, and
removed from temporary staging.

No long-running service receives bootstrap, migrator, backup, or restore authority. Deployment jobs
receive migrator authority only for the migration window. Restore authority is break-glass, absent
from routine automation, and retrieved only during an approved recovery event.

#### 3.4.3 Rotation state machine

Every rotatable username/password credential should follow:

```text
PREPARED_NEW
  -> VERIFIED_NEW_WITHOUT_TRAFFIC
  -> CONSUMERS_UPDATED
  -> NORMAL_PATH_VERIFIED
  -> OLD_REVOKED
  -> EVIDENCE_RECORDED
```

Where a system cannot support overlapping credentials, document the maintenance window, stop order,
credential update, restart, verification, and rollback boundary. Rotation must fail closed before old
revocation if the new credential has not been proven. Perform one non-production rehearsal and one
production rotation exercise before final acceptance for each materially different mechanism.

#### 3.4.4 Exposure response

An exposure procedure must identify affected authority, revoke/replace it, inspect relevant access
and audit evidence, redeploy consumers, verify least privilege, check source history and artifacts,
and record whether dependent secrets also require rotation. Never solve an exposure only by deleting
the visible file while leaving the credential valid.

### 3.5 Immutable release, migration, deployment, and rollback contract

#### 3.5.1 Release identity and promotion

Every production deployment must identify:

- Git commit and signed/annotated release tag as selected by repository policy;
- application image repository, immutable digest, and human-readable version;
- migration image digest;
- NGINX, SQL Server, RabbitMQ, and monitoring image digests;
- Flyway schema version before and after deployment;
- SBOM and security evidence;
- approver, operator, start/end time, and resulting health state; and
- previous compatible application digest.

Mutable tags such as `latest` may aid discovery but cannot be the deployed authority. Production
Compose input must resolve to reviewed digests. Milestone X must replace the current production
placeholder with a deliberately triggered, environment-protected workflow or an equivalently audited
operator process; a missing required production secret must fail rather than report a skipped success.

#### 3.5.2 Deployment state machine

The production flow must implement and retain evidence for:

```text
PRECHECKED
  -> BACKUP_VERIFIED
  -> IMAGES_AND_EVIDENCE_VERIFIED
  -> MIGRATION_APPROVED
  -> MIGRATED
  -> APPLICATION_ROLLED_OUT
  -> READINESS_PROVEN
  -> HTTP_WSS_SMOKE_PROVEN
  -> OBSERVATION_WINDOW_PASSED
  -> RELEASE_ACCEPTED
```

Prechecks include host capacity, current health, backup recency, monitoring availability, image
digests, secret availability, migration review, schema compatibility, and maintenance/alert
suppression. Flyway runs once with the migrator, before application rollout, and the application never
auto-migrates with runtime authority.

The post-rollout observation window must inspect readiness/liveness, durable HTTP flows, WSS policy,
SQL/RabbitMQ state, audit persistence/degradation, error/latency trends, restarts, and disk growth.
The duration is chosen from production characterization and deployment risk; it must not be silently
skipped.

#### 3.5.3 Rollback decision tree

1. If application behavior fails and the current schema is verified backward compatible, redeploy the
   last verified compatible image digest and repeat smoke/observation checks.
2. If backward compatibility is uncertain or false, do not place an older image over the newer schema.
   Keep the current image isolated as needed and implement a reviewed forward fix.
3. Use database restore only for an approved destructive/corruption recovery event where accepted data
   loss matches the RPO; restore is not routine application rollback.
4. Never rewrite or automatically reverse an applied Flyway migration.
5. Record the trigger, decision owner, schema evidence, selected path, outcome, and follow-up.

The canonical rehearsal commands remain in the
[`hardened deployment runbook`](../operations/hardened-deployment-runbook.md); production activation
must parameterize or wrap those reviewed behaviors instead of creating an unrelated deployment path.

#### 3.5.4 Production-candidate release acceptance

The first production release becomes `1.0.0` only after all X1 and X2 exit criteria and final
evidence are accepted. Finalization includes changelog promotion from Unreleased, version alignment,
release notes, image/tag provenance, known-risk register, recovery contacts, and an explicit statement
of the selected network exposure, RPO/RTO, availability objective, and unsupported capabilities.

### 3.6 SQL Server production operations contract

#### 3.6.1 Placement and resource decision

Record SQL Server edition/licence, containerized or host-installed placement, image/build/CU, CPU and
memory limits, collation, timezone behavior, TLS certificate, and ownership. The Milestone 9
containerized topology is the proportional default for the single-host personal deployment unless
licensing, platform support, backup tooling, or measured performance justifies a different placement.

Map and size data, transaction log, `tempdb`, backup staging, and diagnostic storage separately where
the host permits it. Configure bounded autogrowth in fixed increments, maximum-size/alert behavior,
and sufficient free space for maintenance and recovery. Do not rely on unlimited percentage growth.

#### 3.6.2 Principal and transport inventory

Acceptance must prove the separate operator, migrator, runtime, backup, restore, and monitoring
authorities defined by ADR-0017 and the migration baseline. Each negative-permission test must prove
what that principal cannot read, mutate, execute, create, restore, or administer. Application,
migrator, backup, and monitor connections must validate the intended SQL Server certificate; setting
`trustServerCertificate=true` is not an accepted production shortcut.

#### 3.6.3 Database maintenance

Define and schedule:

- `DBCC CHECKDB` cadence and alerting;
- backup and restore verification from section 3.8;
- statistics maintenance based on measured need;
- index-health review without habitual blanket rebuilds;
- data/log/tempdb growth and free-space checks;
- failed-login, severe-error, and long-blocking diagnostics;
- SQL Server and host patch review;
- audit-table retention and capacity enforcement; and
- Flyway history validation before and after releases.

Maintenance must use bounded windows, record duration/outcome, and avoid competing with backups,
deployments, ChatMonitor catch-up, or normal active use. Any proposal to enable
`READ_COMMITTED_SNAPSHOT`, change recovery model, alter durability, or add the monitoring index
requires explicit migration/ADR/performance evidence appropriate to its database-wide impact.

#### 3.6.4 Recovery and corruption behavior

On suspected corruption, storage failure, or accidental destructive change: restrict writes as
appropriate, preserve logs/evidence, identify the last known good point, select forward repair or
isolated restore under the recovery owner, and never experiment against the sole production copy.
Restored data is validated through `DBCC CHECKDB`, Flyway, application readiness, durable synthetic
records, tombstones, sessions, messages, and delivery/read cursors before cutover.

### 3.7 RabbitMQ production operations contract

#### 3.7.1 Role and availability boundary

RabbitMQ transports audit records; SQL Server remains authoritative for durable messaging. Broker
failure therefore remains ready-but-degraded, not an application-readiness failure. The operator must
still restore the broker before the bounded local fallback is exhausted and must prove eventual audit
persistence afterward.

#### 3.7.2 Production configuration inventory

Record the reviewed RabbitMQ image digest/version, durable volume, node name, virtual host, exchange,
queue, routing key, DLQ, application principal, operator principal, management exposure, TLS decision,
resource limits, disk/memory alarms, and definitions-provisioning path. The application principal
must not be an administrator and must have only the configure/write/read permissions required by the
accepted topology.

The management interface is private to approved administration/monitoring sources or disabled. Default
guest/administrator credentials are absent. Durable queues and persistent messages must survive
container replacement and host restart using the selected durable volume.

#### 3.7.3 Backlog, DLQ, and outage handling

Document thresholds using queue depth, oldest-message age, publish/ack rate, disk alarm, memory alarm,
local fallback utilization, and measured audit throughput. A DLQ count above zero triggers
investigation; it does not trigger blind replay.

DLQ handling must:

1. inspect a sanitized sample and identify the failure class;
2. correct schema/configuration/consumer causes first;
3. preserve event identifiers and idempotency;
4. replay only a bounded reviewed batch;
5. verify durable SQL audit insertion and absence of repeated dead-lettering; and
6. retain evidence of discarded events and explicit justification if replay is unsafe.

Exercise broker stop, restart, persistent-volume recovery, backlog drain, DLQ handling, credential
rotation, and bounded local-fallback pressure before production acceptance. Broker data is operational
transport state, not a substitute for SQL backup.

### 3.8 Backup, off-host transfer, and disaster-recovery contract

#### 3.8.1 Recovery objectives and covered assets

The stakeholder must accept or replace the provisional **24-hour RPO** and **60-minute restore
objective** from Milestone 9. The final record must distinguish:

- database recovery point and recovery time;
- complete service recovery time;
- maximum acceptable audit loss during fail-open degradation;
- monitoring-history recovery expectations; and
- certificate/key or secret loss scenarios that can make encrypted backups unusable.

At minimum protect the encrypted SQL backup, checksum/metadata manifest, backup encryption
certificate and protected private key, required recovery instructions, production configuration,
secret-reconstruction inventory, image digests, Flyway version, and monitoring configuration. Store
the backup artifact and the material needed to decrypt it under separately controlled failure domains
where practical.

#### 3.8.2 Backup and transfer lifecycle

```text
SCHEDULED
  -> NATIVE_BACKUP_CREATED_WITH_CHECKSUM
  -> VERIFYONLY_PASSED
  -> MANIFEST_RECORDED
  -> OFF_HOST_TRANSFERRED
  -> DESTINATION_CHECKSUM_VERIFIED
  -> RETENTION_IMMUTABILITY_CONFIRMED
  -> MONITOR_HEARTBEAT_UPDATED
```

Failure at any state leaves a visible failed/overdue status and retry path. A local backup does not
satisfy disaster recovery. Staging capacity is bounded and cleanup occurs only after destination
verification and retention requirements are satisfied.

The destination decision must specify transport, encryption in transit, authentication, versioning
or immutability, capacity, retention tiers, deletion authority, alerting, and recovery when the normal
operator workstation is unavailable. For a personal deployment, physically separate encrypted local
storage can be proportional if its fire/theft/power risks are explicitly accepted; cloud storage is
not required.

#### 3.8.3 Retention and drill schedule

Final retention follows measured size and accepted RPO, with a documented grandfather-father-son or
similarly understandable schedule. As a planning baseline—not a final mandate—consider daily copies
for 14 days, weekly copies for 8 weeks, and monthly copies for 6–12 months when capacity permits.
Deletion must never remove the last known-good independently verified recovery point.

Run an isolated restore from the **retrieved off-host copy**, not the local staging file, on the
accepted schedule and after material changes to encryption, SQL version, storage, or restore tooling.
The canonical restore behavior remains in the
[`backup and restore runbook`](../operations/backup-and-restore-runbook.md). Record artifact identity,
retrieval time, restore duration, integrity results, application-level durable-state checks, achieved
RPO/RTO, cleanup, and findings.

#### 3.8.4 Total-host-loss exercise

Before activation, perform or tabletop a production-host-loss sequence detailed enough to execute:
obtain replacement capacity, install pinned prerequisites, restore configuration and trust, retrieve
images by digest, provision principals, retrieve/decrypt backup, restore into an isolated target,
validate, establish DNS/certificate routing, reconnect monitoring, and admit clients. Identify every
step that currently depends on knowledge or material available only on the failed host and remove or
accept that dependency.

### 3.9 Logs, diagnostics, incident response, and privacy contract

#### 3.9.1 Log inventory and retention

Inventory NGINX access/error, ChatBackend application/audit, container, SQL Server, RabbitMQ, host,
deployment, backup, restore, monitoring, and security-gate logs. For each define owner, location,
format, rotation, maximum storage, retention, access, backup status, sensitive fields, and deletion
method.

Keep request/trace correlation available, but do not add message bodies, tokens, credentials, raw
headers, unrestricted query values, or high-cardinality identifiers to routine logs or metrics. Host
clock synchronization is mandatory for cross-system timelines. Production logs must not grow without
bound or silently consume SQL/RabbitMQ/backup capacity.

#### 3.9.2 Diagnostic bundle

Define an operator command or checklist that collects a timestamped, sanitized bundle containing
service/image state, health output, recent bounded logs, restart counts, resource state, queue/DLQ
state, backup/restore heartbeat, certificate metadata without private keys, Flyway version, and
monitor freshness. It must redact secrets, tokens, message content, sensitive audit context, and
private keys; default to a short time window; and avoid mutating production.

#### 3.9.3 Incident lifecycle

Use a lightweight lifecycle proportional to personal operation:

```text
DETECTED -> TRIAGED -> CONTAINED -> RECOVERED -> VERIFIED -> REVIEWED
```

Record start/end time, detection source, affected capability/data, severity, actions, evidence,
recovery verification, residual risk, and follow-up. Required exercises cover application failure,
database unavailability, RabbitMQ degradation, certificate-renewal failure, disk pressure, failed
deployment, lost/rotated credential, backup overdue, and monitoring outage. Security or privacy
incidents additionally require credential scope review and evidence preservation.

### 3.10 Production characterization, objectives, and capacity contract

#### 3.10.1 Workload model

Because this is a private personal deployment, characterize the expected workload rather than an
enterprise-scale hypothetical. Record expected users, active devices, simultaneous sockets,
conversations, messages/day, typical and maximum message length, retained-history size, audit-event
rate, backup growth, and future media-storage assumptions. Use synthetic identities and content for
testing.

#### 3.10.2 Characterization sequence

1. Record host/container resource limits, image digests, database size, fixture size, and network path.
2. Run the existing characterization workload from the
   [`load-test baseline`](../operations/load-test-baseline.md) using valid production-like TLS against
   an approved non-production or maintenance-isolated target.
3. Add scenarios only for demonstrated critical paths; do not turn Milestone X into a speculative
   load-testing platform.
4. Observe API latency/error rate, WSS checks, CPU, memory, disk I/O, SQL waits/blocking, datasource
   behavior, RabbitMQ/audit behavior, restarts, and ChatMonitor collector impact.
5. Run the accepted threshold phase, verify durable state afterward, and retain sanitized results.
6. Establish normal operating ranges and alerts from evidence rather than copying laptop numbers.

#### 3.10.3 Required objectives

Before activation, the stakeholder accepts values for:

- availability expectation and planned maintenance treatment;
- API p95 and failure-rate threshold for representative operations;
- WebSocket handshake/ping success and acceptable signaling delay;
- maximum collector lag and audit backlog age;
- RPO and complete-service RTO;
- disk reserve and growth warning horizon;
- backup and restore-drill freshness; and
- maximum simultaneous client/device target for the installed host.

These are operator objectives, not public SLA promises. Failure to meet them blocks activation only
where the stakeholder marks the objective as a release gate; otherwise the deviation requires an
explicit accepted risk and follow-up.

### 3.11 Production readiness, evidence, and ownership contract

#### 3.11.1 Responsibility matrix

Even when one person fills every role, Milestone X must name the acting owner for:

| Responsibility | Required ownership |
|---|---|
| Product/release approval | Accept scope, objectives, residual risks, and `1.0.0` |
| Host/network | Hardware, OS, firewall, DNS, power, and patching |
| Deployment | Registry, image promotion, migration, rollout, rollback, and evidence |
| Database | SQL provisioning, maintenance, backup, restore, and capacity |
| Security | Secrets, certificates, scans, findings, and incident review |
| Monitoring | Uptime/host tools, ChatMonitor, alerts, retention, and workstation recovery |
| Recovery | Off-host artifacts, key recovery, drills, and disaster decision |

No requirement disappears because the same stakeholder owns multiple columns.

#### 3.11.2 Production evidence manifest

Maintain a sanitized manifest that links rather than embeds sensitive evidence. It records production
host/profile identifier, release/image digests, schema version, deployment result, smoke result,
certificate expiry/renewal exercise, principal tests, firewall verification, backup identity and
off-host verification, restore drill, load characterization, monitoring/alert exercises, unresolved
risks, approvals, and timestamps. Evidence with secrets, private topology, raw logs, or production data
remains in access-controlled operator storage, not Git.

#### 3.11.3 Readiness review

The final review must classify every entry decision and finding as resolved, explicitly accepted with
reason/owner/review date, or production-blocking. It must confirm documentation matches the deployed
state, recovery is executable without chat history, the client responsibility guide remains accurate,
and no completion claim relies only on the local Milestone 9 rehearsal.

---


---

## 4. X1 Exit Criteria

X1 is complete only when:

- the production host and private network boundary are provisioned and ownership is recorded;
- HTTPS/WSS uses the selected trusted certificate path and renewal/rotation is exercised;
- only the hardened NGINX HTTPS/WSS edge is public, while internal and administrative services remain
  unreachable from the Internet;
- SQL Server and RabbitMQ remain private and least-privilege inventories match the hardened model;
- secret delivery and rotation, immutable image promotion, migration-before-rollout, and
  schema-aware rollback are proven;
- a retrieved off-host encrypted backup restores within the accepted RTO and meets the accepted RPO;
- production-representative characterization passes the approved X1 thresholds;
- the incident, diagnostic, evidence-manifest, and responsibility foundations are usable without
  relying on chat history;
- the threat model, architecture, operations, client responsibility guide, and changelog match the
  deployed candidate; and
- every X1 risk is resolved, explicitly accepted with owner and review date, or marked as an X2/final
  production blocker.

Passing X1 means **secure and recoverable production candidate**, not production approval.

## 5. Handoff to X2

The X1 handoff must provide sanitized identifiers or protected references for the deployed release,
image digests, schema version, firewall and certificate evidence, runtime principals, backup and
restore evidence, characterization baseline, unresolved risks, and all endpoints or projections X2
needs for monitoring. X2 must not infer these values from developer-machine rehearsal defaults.
