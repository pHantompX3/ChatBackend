# ChatBackend Single-Instance Deployment Threat Model

**Status:** Milestone 9 local hardened-rehearsal review complete; production activation review pending

**Last reviewed:** 2026-08-30

**Scope:** One NGINX edge, one ChatBackend instance, SQL Server, RabbitMQ audit transport, CI/CD,
operator credentials, and backup/restore tooling.

## 1. Security objectives and protected assets

- Preserve confidentiality and integrity of message bodies, membership, sessions, identities, audit
  records, credentials, certificates, backup artifacts, and encryption keys.
- Preserve authorization at every REST and WebSocket boundary.
- Keep SQL Server authoritative; WebSocket delivery never changes durable state without an
  authenticated command reaching the application service.
- Keep privileged database and broker identities out of the long-running application.
- Detect degraded audit delivery, failed backups, stale restore evidence, and trusted-edge failures.

## 2. Data flow and trust boundaries

```text
Internet or local client
  | HTTPS / WSS
  v
Router / host firewall
  | Cloudflare public HTTPS/WSS edge
  v
NGINX public edge
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

Trust boundaries exist at the Internet/router/firewall, client/NGINX, NGINX/ChatBackend, ChatBackend/SQL Server,
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
| T01 | Spoofing | Stolen or replayed session token permits REST/socket access | Opaque token hashing, session expiry/revocation, TLS, no credential logging, socket close `4401`, integration tests | Backend | Verified for local rehearsal |
| T02 | Spoofing | Cross-site browser opens an authenticated socket | Hardened Origin allowlist; reject unknown present Origin with `4403`; proxy/endpoint tests | Backend/Operations | Verified through TLS proxy and endpoint tests |
| T03 | Information disclosure | Query token appears in logs, audit queue, SQL audit, or intermediaries | Redact all non-empty query strings before both audit paths; hardened profile rejects query tokens; proxy logs `$uri` only | Backend/Operations | Verified with sentinel inspection |
| T04 | Tampering | Untrusted forwarded headers spoof source/trace identity | Exact trusted-proxy range, overwrite forwarded headers, preserve/generate `X-Trace-Id`, retain app-owned `X-Request-Id`, negative tests | Backend/Operations | Verified through proxy and durable audit |
| T05 | Elevation | Application or migrator uses `sa` or broad fixed roles | Two-phase provisioning, forward-only permission migration, container secret inspection, clean/upgrade negative tests | Database | Verified for clean/upgrade and live containers |
| T06 | Information disclosure | SQL TLS encryption accepts an attacker certificate | Trusted SQL certificate and `trustServerCertificate=false`; positive/negative connection tests | Database/Operations | Verified; untrusted certificate rejected |
| T07 | Elevation | RabbitMQ runtime account administers broker or unrelated resources | Operator-provisioned vhost/topology, no administrator tag, regex-scoped permissions, private ports, negative tests | Operations | Verified for local rehearsal |
| T08 | Repudiation | Broker outage silently loses durable audit evidence | Ready-but-degraded signal, local fallback diagnostics, queue/DLQ/oldest-age/drain checks and alerts | Backend/Operations | Outage, availability, persistence, recovery, backlog, and DLQ verified locally |
| T09 | Information disclosure | Backup theft reveals messages, sessions, or identity data | Encrypted backup, restrictive ownership, recoverable key held separately, future off-host policy | Operations | Encrypted local recovery verified; off-host production store pending |
| T10 | Tampering/DoS | Restore overwrites the active database or starts incompatible schema/image | Isolated target requirement, explicit `MOVE`, Flyway validation/migration, compatibility check, guarded DR mode | Operations/Database | Verified with isolated application/data restore smoke |
| T11 | DoS | Login abuse, oversized requests, socket exhaustion, or reconnect storms exhaust resources | Existing SQL throttling/body/frame limits plus edge rate/connection limits and bounded idle timeouts | Backend/Operations | Limits and local regression baseline verified |
| T12 | Tampering | Malicious/compromised dependency, action, base image, or mutable tag enters deployment | Pinned dependencies/actions/images, SBOM, dependency/image/config/secret scans, immutable digest deployment | CI/Operations | Application, RabbitMQ, NGINX, and migration images verified; SQL Server vendor-image residual risk accepted through 2026-11-26 |
| T13 | Information disclosure | Local ignored secrets are sent to a remote image builder | `.dockerignore`, narrow build context, clean-checkout image test, secret scan | CI | Repository-owned build context and Git-filtered secret scan verified locally; hosted CI evidence pending |
| T14 | DoS | Disk fills with logs, backups, SQL/Rabbit data, or DLQ records | Stdout logging/read-only root, bounded backup retention, disk/queue thresholds, diagnostics and runbook | Operations | Local checks verified; production alert delivery pending |
| T15 | Tampering | Concurrent deployment or failed migration leaves incompatible schema/application | One-shot migrator, environment concurrency, migration-before-rollout, immutable image, schema-aware rollback | CI/Database | Repository workflow and local deployment paths verified; hosted rollout pending |
| T16 | Information disclosure | Error/audit diagnostics expose message content or credentials | Safe client problems, bounded diagnostic fields, query/header/body redaction, access control and retention | Backend/Operations | Verified with privacy sentinels and scans |
| T17 | DoS | One slow/failing socket delays recipients or request completion | Existing independent asynchronous fan-out; bounded connection limits; REST recovery remains authoritative | Backend | Implemented |
| T18 | Repudiation | Missed/duplicated/reordered socket frame produces incorrect client state | Client idempotency/gap detection and REST reconciliation; canonical client guide and transport tests | Client/Backend | Implemented contract |
| T19 | Spoofing/Information disclosure | Public client presents a phishing interface, captures credentials/content, or imitates an owner-built client | ADR-0019 honest client boundary; user guidance; TLS; secure token handling; session/account revocation; no official-client attestation claim before IE-01 | Operations/Backend/Client | Accepted initial residual risk; production evidence pending |
| T20 | Elevation/Information disclosure | A client is treated as trusted merely because it supplies an Origin, client label, user-agent, custom header, or embedded shared secret | Treat those values as browser/diagnostic metadata only; derive user from session; enforce role/membership/resource authorization; prohibit shared private application secrets | Backend/Client | Accepted architecture boundary |
| T21 | DoS/Spoofing | Internet scanning, credential guessing, malformed traffic, or connection floods exhaust or compromise the public edge | Cloudflare public TLS/Tunnel; zero public origin ports; NGINX/application limits; login throttling; patching, monitoring, revocation, incident tests | Operations/Backend | Production evidence pending |
| T22 | Information disclosure | A separately operated deployment is mistaken for a federated/shared instance and receives this deployment's users, secrets, backups, or data | Isolated deployment boundary; no federation/multi-tenancy; independent credentials/data/operations; licensing handled separately | Product/Operations | Accepted architecture boundary |
| T23 | Spoofing/DoS | Future native certificate, issuer, installation key, or server pin is stolen, expires, cannot rotate, or locks out legitimate clients | IE-01 distinct non-exportable installation keys where supported; issuance/revocation/rotation; CA/pin recovery; staged native enforcement and rollback | Infrastructure/Client/Operations | Required post-X track; not implemented |
| T24 | Spoofing/Information disclosure | Browser pairing is replayed, approved for the wrong key/user, phished, or represented as proof of exact website code | IE-02 mobile-signed single-use bounded pairing and browser proof-of-possession; IE-03 official-domain, supply-chain, CSP/XSS, revocation, and user-confirmation controls | Infrastructure/Client/Backend | Required post-X tracks; not implemented |
| T25 | DoS/Tampering | Windows restart, Docker Desktop/WSL2 startup, VHDX/storage behavior, or power loss leaves the stack unavailable or damages durable state | X1 Windows execution-plane contract; BitLocker recovery; bounded Docker readiness task; cold-reboot and power-loss recovery; SQL/Rabbit integrity checks; off-host restore | Host/Operations | X1 production evidence pending |
| T26 | Tampering/Elevation | Mutable/rebuilt image, stale production branch, compromised deployment runner, or unreviewed migration reaches production | Annotated release tag; build once; private registry digest; protected environment approval; signed/checksummed release manifest; workstation runner; production receives artifacts only; concurrency and schema-aware rollback | CI/Operations | X1 implementation pending |
| T27 | Information disclosure/Tampering | Weak router-share transport exposes credentials, a share administrator deletes backups, or an attacker replaces both package and checksum | Prefer SMB 2/3 and prohibit SMB1; compress, encrypt, and authenticate the backup package before transfer; protect the archive passphrase separately; authenticate the manifest; retrieve/decrypt/decompress/restore; explicitly accept the router drive's deletion/no-snapshot risk | Recovery/Operations | X1 implementation pending |
| T28 | Elevation/Information disclosure | Active SQL/RabbitMQ bootstrap/operator credentials remain visible in long-running container metadata | Service-specific secret delivery where supported; provision durable operator; revoke/rotate bootstrap value; inspect containers; prove retained bootstrap value is invalid; keep ChatBackend runtime-only | Database/Operations | X1 implementation pending |
| T29 | Information disclosure/DoS | Public detailed readiness leaks internal state or unlimited WebSocket handshakes exhaust the application before connection limits apply | Minimal public liveness; private detailed readiness; handshake-attempt and concurrent-connection limits; forwarded-source validation; reconnect/oversize/idle tests | Edge/Operations | X1 implementation pending |
| T30 | Tampering/DoS/Information disclosure | Future partial uploads exhaust disk, splice or corrupt chunks, spoof media types, escape storage paths, expose unauthorized ranges, or leave messages pointing at incomplete content | ET-02 50 MiB object and 25 MiB request limits; authenticated owner-scoped tus resources; exact offset and checksum validation; server-side type sniffing; opaque paths; isolated temporary storage; expiry/orphan cleanup; atomic `AVAILABLE` promotion; authorization on every full/ranged download; storage alerts and recovery tests | Backend/Operations/Client | Required by ET-02; not implemented |

## 5. Required security verification

Milestone completion requires evidence for every pending control above and no unaccepted high or
critical residual risk. At minimum:

1. inspect proxy/application/audit records after requests containing sentinel query/header tokens;
2. exercise allowed/disallowed/missing Origin and every supported hardened token transport;
3. run clean-install and Milestone 8 upgrade permission suites;
4. prove untrusted SQL certificates fail;
5. prove runtime database and broker accounts cannot administer their services;
6. interrupt RabbitMQ and verify API availability plus visible degradation and eventual drain;
7. create and verify an uncompressed checksum backup, package it with external compression and
   client-side encryption, then retrieve, decrypt, decompress, restore, migrate, and smoke-test it;
8. scan source, SBOM, application image, and every pinned infrastructure image;
9. exercise request/socket limits and load regression without durable-state corruption;
10. inspect running containers for privileged credentials, root UID, unexpected writable paths, and
    published ports;
11. prove that the origin exposes no public inbound port and only the dedicated Cloudflare hostname
    reaches NGINX; prove that ChatBackend, SQL Server, RabbitMQ,
    administration, monitoring SQL, and Docker control cannot be reached directly;
12. exercise Internet-origin login throttling, bootstrap closure, malformed/oversized requests,
    HTTP/WSS connection limits, session revocation, and durable recovery; and
13. verify public certificate trust and expiry/renewal through representative remote clients;
14. prove Windows sign-in startup, cold reboot, power-loss recovery, BitLocker recovery readiness, and
    persistent SQL/Rabbit data on the selected host;
15. prove build-once digest promotion, protected deployment approval, release-manifest verification,
    and fail-closed schema-incompatible rollback;
16. transfer and retrieve a compressed/pre-encrypted package from the router network drive, verify its
    authenticated manifest, and restore it without relying on the production host; and
17. prove any bootstrap values retained in container metadata are invalid and detailed readiness is
    not publicly exposed.

## 6. Residual risks and production activation

The repository rehearsal does not eliminate single-host failure, supply a production domain/certificate,
select an off-host backup service, prove alert delivery, establish a production patch owner, or prove a
production SLA. These are explicit production-activation prerequisites rather than reasons to weaken
repository-owned controls.

ADR-0019 accepts public HTTPS/WSS access while keeping every internal service private. The initial
server authenticates users and does not attest exact frontend software. The stakeholder accepts the
residual risk that a user-selected malicious client can compromise that user's credentials and
accessible content; server-side authentication, authorization, throttling, revocation, monitoring,
and incident response remain mandatory.

IE-01 through IE-03 are required post-X Infrastructure Evolution Tracks for native installation
trust, the mobile-authorized linked-browser protocol, and the official web companion. They are not
implemented and must not be represented as current production controls.

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
