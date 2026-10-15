# Milestone X3 — Production Deployment, Integration, and Acceptance

## Status

**Status:** Planned; blocked only on external-system execution after X1 and X2 handoff
**Parent program:** [Milestone X — Production Activation](milestone-x-production-activation.md)
**Required predecessors:** X1 repository foundation accepted and X2 monitoring foundation accepted
**Last refined:** 2026-10-15

## 1. Purpose

X3 isolates every activity that depends on the real Windows production machine, Cloudflare account,
Hostinger DNS zone, home Internet path, Docker Hub production credentials, router storage, live alert
destinations, or other operator-controlled external state. It deploys the X1 artifacts, onboards the
X2 monitoring foundation, proves recovery and operations in the selected environment, and owns the
deliberate production and `1.0.0` decision.

X3 introduces no messaging feature. It consumes immutable, reviewed outputs from X1 and X2.

## 2. Required handoffs

X1 supplies:

- signed release bundle and immutable application/migration image digests;
- production Compose, NGINX, Cloudflare, secret, Windows, migration, rollback, backup, restore,
  retention, diagnostics, and evidence tooling;
- locally verified schema version and least-privilege contracts; and
- the operator manual and unresolved environment-value checklist.

X2 supplies:

- reproducible ChatMonitor build and package for the administrator machine;
- SQLite schema, source-adapter contract, SQL projection/principal migration, collector, aggregates,
  dashboard, retention, local alert-state engine, backup/restore, and startup tooling;
- sanitized deterministic fixtures, including future official-client telemetry fixtures; and
- an explicit list of live thresholds, destinations, credentials, and exercises requiring X3 proof.

## 3. External activation scope

X3 performs, records, and verifies:

1. Windows 11, BitLocker, WSL2, Docker Desktop, SSH, time, storage, startup, patch, and administrator
   ownership configuration on the production host.
2. Production secret generation/delivery, Docker Hub pull authentication, release-signature trust,
   least-privilege SQL/RabbitMQ identities, and rotation/revocation evidence.
3. Cloudflare account/tunnel creation, dedicated Hostinger DNS hostname, public TLS, authenticated
   connector-to-NGINX TLS, zero public origin ports, and recovery/credential rotation.
4. Production deployment from the signed annotated-tag bundle, migration-before-application ordering,
   readiness, smoke flows, immutable-digest evidence, and schema-aware rollback behavior.
5. Router SMB 2/3 backup destination, encrypted/signed package transfer, retention, retrieval,
   independent restore, `DBCC CHECKDB`, RPO, and RTO evidence.
6. Windows sign-in startup, cold reboot, controlled outage, power-loss recovery, audit retention,
   disk/capacity boundaries, incident diagnostics, and runbook usability.
7. ChatMonitor installation on the administrator machine, live read-only SQL projection onboarding,
   loopback-default dashboard, startup, monitoring database backup/recovery, catch-up after outage,
   query plans, lock/latency budgets, and retained-history behavior.
8. Live availability, certificate, host/container, audit transport/backlog/DLQ, backup-age,
   deployment, collector freshness, capacity, and security-gate alert routing and recovery.
9. External HTTPS/WSS behavior from outside the home LAN, abuse limits, certificate validity, and
   client-representative smoke flows.
10. Final evidence review, residual-risk ownership/review dates, production approval, and the `1.0.0`
    release decision.

The established [X1 Production Operator Manual](../operations/x1-production-operator-manual.md)
remains the executable host/deployment procedure. X3 supplements it with the X2 monitoring handoff
and final acceptance evidence rather than duplicating its safety instructions.

## 4. ChatMonitor production onboarding

ChatMonitor initially lives on the administrator machine. Its dashboard and local API bind to
loopback unless X3 explicitly records a LAN consumer, bind address, firewall source list,
authentication, and TLS decision. It is never exposed directly to the Internet.

X3 enables only the implemented SQL-audit source adapter and approved host/synthetic sources. The
source-neutral model is verified so future official clients can be onboarded later, but X3 does not
create a public client-telemetry ingress. Any such ingress requires its own architecture/security
decision and must apply the X2 privacy, schema, idempotency, offline-buffer, authentication,
revocation, and quota contract.

Monitoring remains fail-open relative to ChatBackend: stopping the administrator machine or every
monitoring process cannot delay, reject, or mutate messaging requests. After an outage, ChatMonitor
must resume from its durable cursor and visibly report `CURRENT`, `CATCHING_UP`,
`SOURCE_UNAVAILABLE`, `COLLECTION_FAILED`, or `HISTORY_GAP`.

## 5. Evidence and stop conditions

Evidence is stored outside Git and referenced through a sanitized manifest. Stop activation when:

- an artifact/signature/digest cannot be verified;
- a secret or privileged port is exposed outside its approved boundary;
- migration or rollback compatibility is unknown;
- SQL/RabbitMQ/admin services are Internet-reachable;
- backup retrieval and isolated restore cannot meet accepted objectives;
- ChatMonitor causes production blocking, excessive locks, or source mutation;
- public HTTPS/WSS or alert delivery cannot be independently verified; or
- an unaccepted High or Critical risk remains.

## 6. Exit criteria

X3 and the parent Milestone X are complete only when:

- every external activation item in section 3 has retained evidence;
- production and monitoring recover automatically or through documented bounded intervention after
  the accepted reboot, outage, and host-loss scenarios;
- the retrieved off-host backup restores within the accepted RPO/RTO;
- live monitoring catches up after downtime, retains at least 62 days, cannot mutate production, and
  delivers every required alert and recovery notification;
- production-representative capacity and safety thresholds are accepted;
- canonical architecture, threat model, operations, client-responsibility, and changelog documents
  match the deployed environment;
- every residual risk has an owner, reason, review date, and explicit disposition;
- no unaccepted High or Critical risk remains; and
- the primary stakeholder deliberately approves normal production use and release `1.0.0`.

Passing X3 means the private personal platform is deployed, observable, recoverable, and accepted for
normal use. It creates no public-service SLA, multi-tenant offering, or open client ecosystem.

## 7. Non-goals

X3 excludes new messaging features, media, multi-tenancy, federation, end-to-end encryption,
multi-instance distribution, IE-01 through IE-03 implementation, public client telemetry ingestion,
commercial operations, and paid infrastructure requirements.
