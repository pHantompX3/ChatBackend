# ChatBackend Single-Instance Deployment Threat Model

**Status:** Initial Milestone 9 model; final control review pending

**Last reviewed:** 2026-08-26

**Scope:** One NGINX edge, one ChatBackend instance, SQL Server, RabbitMQ audit transport, CI/CD,
operator credentials, and backup/restore tooling.

## 1. Security objectives and protected assets

- Preserve confidentiality and integrity of message bodies, membership, sessions, identities, audit
  records, credentials, certificates, backup artifacts, and encryption keys.
- Preserve authorization at every REST and WebSocket boundary.
- Keep SQL Server authoritative; WebSocket delivery never changes durable state without an
  authenticated command reaching the application service.
- Keep privileged database and broker identities out of the long-running application.
- Detect degraded audit delivery, failed backups, stale restore evidence, and public-edge failures.

## 2. Data flow and trust boundaries

```text
Untrusted client
  | HTTPS / WSS
  v
NGINX edge
  | private HTTP / WebSocket upgrade
  v
ChatBackend runtime
  | JDBC with verified TLS              | AMQP on private network
  v                                     v
SQL Server authoritative state          RabbitMQ audit transport
  ^                                     |
  | operator/migrator/backup/restore    v
Protected operator boundary             SQL audit sink / dead-letter path

CI/build boundary -> immutable image/SBOM/scans -> protected deployment environment
Backup operator -> encrypted backup -> isolated restore / future off-host storage
```

Trust boundaries exist between client/NGINX, NGINX/ChatBackend, ChatBackend/SQL Server,
ChatBackend/RabbitMQ, CI/deployment, operator/secrets, and backup/restore storage.

## 3. Entry points and privileged actors

Entry points are REST, `/api/v1/ws`, ports 80/443, SQL/TDS, AMQP, RabbitMQ management access,
container runtime access, CI workflows, image registry, SSH/host administration, secret files/mounts,
and backup/restore commands.

Privileged actors are the host operator, database bootstrap/restore operator, migrator, backup
operator, RabbitMQ topology operator, CI environment approver, and certificate/secret owner. The
ChatBackend and RabbitMQ runtime users are not privileged operators.

## 4. STRIDE and abuse-case register

| ID | Category | Threat and impact | Required control and evidence | Owner | Status |
|---|---|---|---|---|---|
| T01 | Spoofing | Stolen or replayed session token permits REST/socket access | Opaque token hashing, session expiry/revocation, TLS, no credential logging, socket close `4401`, integration tests | Backend | Partly implemented |
| T02 | Spoofing | Cross-site browser opens an authenticated socket | Hardened Origin allowlist; reject unknown present Origin with `4403`; proxy/endpoint tests | Backend/Operations | Endpoint implemented; proxy pending |
| T03 | Information disclosure | Query token appears in logs, audit queue, SQL audit, or intermediaries | Redact all non-empty query strings before both audit paths; hardened profile rejects query tokens; proxy logs `$uri` only | Backend/Operations | Backend implemented; proxy pending |
| T04 | Tampering | Untrusted forwarded headers spoof source/trace identity | Exact trusted-proxy range, overwrite forwarded headers, preserve/generate `X-Trace-Id`, retain app-owned `X-Request-Id`, negative tests | Backend/Operations | Pending |
| T05 | Elevation | Application or migrator uses `sa` or broad fixed roles | Two-phase provisioning, forward-only permission migration, container secret inspection, clean/upgrade negative tests | Database | Clean/upgrade and negative tests verified; live container inspection pending |
| T06 | Information disclosure | SQL TLS encryption accepts an attacker certificate | Trusted SQL certificate and `trustServerCertificate=false`; positive/negative connection tests | Database/Operations | Pending; completion blocker |
| T07 | Elevation | RabbitMQ runtime account administers broker or unrelated resources | Operator-provisioned vhost/topology, no administrator tag, regex-scoped permissions, private ports, negative tests | Operations | Pending |
| T08 | Repudiation | Broker outage silently loses durable audit evidence | Ready-but-degraded signal, local fallback diagnostics, queue/DLQ/oldest-age/drain checks and alerts | Backend/Operations | Pending |
| T09 | Information disclosure | Backup theft reveals messages, sessions, or identity data | Encrypted backup, restrictive ownership, recoverable key held separately, future off-host policy | Operations | Pending; completion blocker |
| T10 | Tampering/DoS | Restore overwrites the active database or starts incompatible schema/image | Isolated target requirement, explicit `MOVE`, Flyway validation/migration, compatibility check, guarded DR mode | Operations/Database | Pending |
| T11 | DoS | Login abuse, oversized requests, socket exhaustion, or reconnect storms exhaust resources | Existing SQL throttling/body/frame limits plus edge rate/connection limits and bounded idle timeouts | Backend/Operations | Partly implemented |
| T12 | Tampering | Malicious/compromised dependency, action, base image, or mutable tag enters deployment | Pinned dependencies/actions/images, SBOM, dependency/image/config/secret scans, immutable digest deployment | CI/Operations | Application, RabbitMQ, NGINX, and migration images verified; SQL Server vendor-image residual risk accepted through 2026-11-26 |
| T13 | Information disclosure | Local ignored secrets are sent to a remote image builder | `.dockerignore`, narrow build context, clean-checkout image test, secret scan | CI | Repository-owned build context and Git-filtered secret scan verified locally; hosted CI evidence pending |
| T14 | DoS | Disk fills with logs, backups, SQL/Rabbit data, or DLQ records | Stdout logging/read-only root, bounded backup retention, disk/queue thresholds, diagnostics and runbook | Operations | Pending |
| T15 | Tampering | Concurrent deployment or failed migration leaves incompatible schema/application | One-shot migrator, environment concurrency, migration-before-rollout, immutable image, schema-aware rollback | CI/Database | Pending |
| T16 | Information disclosure | Error/audit diagnostics expose message content or credentials | Safe client problems, bounded diagnostic fields, query/header/body redaction, access control and retention | Backend/Operations | Partly implemented |
| T17 | DoS | One slow/failing socket delays recipients or request completion | Existing independent asynchronous fan-out; bounded connection limits; REST recovery remains authoritative | Backend | Implemented |
| T18 | Repudiation | Missed/duplicated/reordered socket frame produces incorrect client state | Client idempotency/gap detection and REST reconciliation; canonical client guide and transport tests | Client/Backend | Implemented contract |

## 5. Required security verification

Milestone completion requires evidence for every pending control above and no unaccepted high or
critical residual risk. At minimum:

1. inspect proxy/application/audit records after requests containing sentinel query/header tokens;
2. exercise allowed/disallowed/missing Origin and every supported hardened token transport;
3. run clean-install and Milestone 8 upgrade permission suites;
4. prove untrusted SQL certificates fail;
5. prove runtime database and broker accounts cannot administer their services;
6. interrupt RabbitMQ and verify API availability plus visible degradation and eventual drain;
7. create, verify, decrypt, restore, migrate, and smoke-test a representative encrypted backup;
8. scan source, SBOM, application image, and every pinned infrastructure image;
9. exercise request/socket limits and load regression without durable-state corruption;
10. inspect running containers for privileged credentials, root UID, unexpected writable paths, and
    public ports.

## 6. Residual risks and production activation

The repository rehearsal does not eliminate single-host failure, supply a public domain/certificate,
select an off-host backup service, prove alert delivery, establish a production patch owner, or prove a
production SLA. These are explicit production-activation prerequisites rather than reasons to weaken
repository-owned controls.

Any accepted high/critical residual risk must identify the accepting owner, date, rationale, review
date, affected environment, and compensating control. Silence or a passing-but-skipped CI job is not
risk acceptance.

## 7. Final review cycle

Re-run this review after the hardened stack, database permissions, recovery tooling, scanning, load
baseline, and CI changes are complete. Update statuses with direct evidence links and record new
client-facing behavior in the canonical client integration and recovery guide before declaring the
milestone complete.

## 8. Reviewed scanner dispositions

| Finding | Scope | Disposition and evidence | Owner | Reviewed / expires |
|---|---|---|---|---|
| CVE-2025-59250 | `com.microsoft.sqlserver:mssql-jdbc` in the application image | Trivy reads the JAR's internal `Bundle-Version` as `13.2.1`, but the packaged filename, `quarkus-app-dependencies.txt`, and CycloneDX PURL all resolve `13.2.1.jre11`; Trivy lists that exact artifact as fixed. The PURL-scoped suppression is therefore a scanner-normalization disposition, not acceptance of a vulnerable driver. | Backend/Supply Chain | 2026-08-26 / 2026-11-26 |

The version-controlled `.trivyignore.yaml` is the executable policy. Any renewal must reverify the
resolved artifact, scanner database, and vendor advisory; broad or expiry-free vulnerability
suppressions are prohibited.

### 8.1 Time-limited SQL Server infrastructure acceptance

The 2026-08-26 scan of Microsoft's SQL Server 2022 CU26 Ubuntu image reported 20 distinct High Go
standard-library findings repeated across three Microsoft-built helper binaries
(`/opt/mssql-extensibility/bin/launchpad`, `/opt/mssql/bin/launchpadd`, and
`/opt/mssql/bin/setnetbr`). RabbitMQ 4.3.4, current stable unprivileged NGINX, and the repository-owned
SQL Server-only Flyway 13.3.0 migration image produced no High/Critical findings after patching their
base packages.

The project owner accepted this narrowly scoped residual risk on 2026-08-26 through 2026-11-26 for
the Milestone 9 local hardened rehearsal. This is a risk acceptance, not a scanner suppression: the
findings remain visible in retained scan evidence. Compensating controls require the digest-pinned SQL
Server container to remain exclusively on the internal data network, publish no host port, accept
traffic only from the application/migration/authorized operator paths, and be replaced with a patched
reviewed image as soon as Microsoft publishes one. The acceptance expires automatically on the review
date; renewal requires a fresh vendor advisory and image scan. It does not authorize public SQL Server
exposure or silently extend to a production deployment.
