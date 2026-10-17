# Milestone X4 — Administrator Monitoring Onboarding and Production Acceptance

## Status

**Status:** Planned; begins after X2 and X3 are independently accepted
**Parent program:** [Milestone X — Production Activation](milestone-x-production-activation.md)
**Required predecessors:** [Milestone X2 — Monitoring Foundation](milestone-x2-monitoring-and-production-acceptance.md) and [Milestone X3 — Production Host Deployment and Edge Activation](milestone-x3-production-host-deployment-and-edge-activation.md)
**Execution boundary:** administrator Mac and its connections to the accepted production candidate
**Last refined:** 2026-10-16

## 1. Purpose

X4 installs and operates the completed X2 monitoring foundation on the administrator Mac, onboards
the accepted X3 production candidate, characterizes live behavior, and owns the final production and
`1.0.0` decision. All work performed on the administrator Mac—including ChatMonitor, monitoring
hubs, dashboards, local monitoring history, production-source credentials, notification delivery,
and external probes—belongs here rather than X3.

X4 does not reconfigure the production application stack casually. A production-side defect found
during onboarding returns through the normal X1/X3 correction and verification path. Monitoring
must remain fail-open relative to ChatBackend: turning off or losing the administrator Mac cannot
delay, reject, or mutate messaging traffic.

## 2. Required handoffs

X2 supplies:

- reproducible ChatMonitor image/package and administrator-workstation Compose lifecycle;
- SQLite schema, source adapters, collector, aggregates, dashboard, retention, alert-state engine,
  backup/restore, startup, and recovery tooling;
- sanitized deterministic fixtures and lower-environment acceptance evidence;
- the environment-registry contract and production onboarding checklist; and
- candidate metrics, thresholds, dashboards, alerts, and operational exercises requiring live proof.

X3 supplies:

- deployed release and schema identities plus production readiness evidence;
- private monitoring endpoint roles and network allowlist evidence;
- provisioned projection-only SQL identity and any approved production-side agent endpoint;
- certificate-authority material intended for monitor trust, never production private keys;
- production backup/recovery and current known-risk status; and
- a sanitized handoff pointing to protected credential and operational evidence locations.

X4 must not compensate for an incomplete X3 handoff by broadening production privileges, exposing a
management port, disabling TLS validation, or embedding credentials in Git or browser assets.

## 3. Administrator-Mac onboarding scope

X4 performs, records, and verifies:

1. Administrator-Mac Docker Desktop lifecycle, power/sleep expectations, disk capacity, local
   account ownership, firewall, loopback publication, bounded logs, and auto-start behavior.
2. ChatMonitor installation from the accepted X2 artifact with its Docker-managed named volume,
   read-only configuration mounts, ignored secret files, health checks, restart policy, and clean
   uninstall/reinstall path.
3. Production environment registry activation: replace the gold `planned` profile with the verified
   SQL/source configuration while retaining stable environment identity and keeping Local green and
   Staging blue independently available.
4. Production read-only SQL projection onboarding with verified TLS, bounded pages, short timeouts,
   safe retry/backoff, actual query-plan evidence, lock/latency budgets, and negative proof that the
   monitor cannot read raw protected tables or mutate production.
5. Availability, certificate, public-edge, private-LAN, host/container, SQL, RabbitMQ audit
   backlog/DLQ, backup-age, deployment-version, collector freshness, disk, and capacity monitoring
   from the administrator-machine boundary.
6. Monitoring-history backup/restore, clean-workstation recovery, restart catch-up, retention of at
   least 62 days of detailed events, aggregate retention, and explicit `CURRENT`, `CATCHING_UP`,
   `SOURCE_UNAVAILABLE`, `COLLECTION_FAILED`, and `HISTORY_GAP` behavior.
7. Live alert destinations, severity routing, maintenance suppression, delivery retry/failure,
   privacy-safe payloads, and tested pending/firing/recovery transitions.
8. Dashboard verification using real production-derived sanitized telemetry: request volume,
   latency, failure rate, expected `4xx` separation, route/operation views, collector health,
   freshness, gaps, and bounded failure investigation.
9. Production characterization: normal latency/throughput/resource ranges, alert-noise review,
   capacity assumptions, backup duration, catch-up rate, and evidence-based thresholds.
10. A feedback record for every live learning. Shared monitor improvements return to Local and
    Staging first, pass automated and rehearsal verification, and only then promote back to
    Production. Production-only topology remains an explicit overlay rather than being copied into
    lower environments where it does not apply.
11. Final integrated incident exercises, residual-risk review, operator runbook validation,
    stakeholder approval, and the deliberate `1.0.0` release decision.

## 4. Environment-promotion and learning contract

Operational learning flows downward; changes flow upward:

```text
Production observation
        |
        v
Requirement, defect, or threshold proposal
        |
        v
Local implementation + deterministic tests
        |
        v
Staging/rehearsal validation
        |
        v
Production promotion
```

The common baseline applied where supported includes application health/readiness, request volume,
server failures, latency, collector freshness, SQL/RabbitMQ availability, and container health.
Production-specific checks include Cloudflare Tunnel, public hostname/certificate, off-host backup,
production storage, and external reachability. Lower environments use equivalent simulations or
local checks; they must not fabricate a production topology.

Environment switching changes only the displayed partition. All configured collectors continue
independently, and no cross-environment total is implied unless a future comparison view explicitly
names and distinguishes every included environment.

## 5. Monitoring independence and recovery

ChatMonitor and third-party monitoring hubs live on the administrator Mac. Their dashboard/API bind
to loopback unless a later decision records a LAN consumer, authentication, TLS, bind address, and
firewall allowlist. They are never exposed directly to the Internet.

After administrator-Mac or network downtime, ChatMonitor resumes from its durable cursor and fetches
all still-retained production events. It must report a visible history gap instead of claiming
currency when authoritative retention no longer covers the cursor. Monitoring data is a derived,
sanitized operational projection—not a backup or replacement for authoritative production records.

## 6. Evidence and stop conditions

Evidence is stored outside Git and referenced through a sanitized manifest. Stop X4 when:

- X2 or X3 handoff identity cannot be verified;
- production access requires write, schema, unrestricted raw-table, or public-management authority;
- TLS verification, source identity, or environment separation is bypassed;
- monitoring causes production blocking, excessive locks, material latency, or source mutation;
- retained history, catch-up state, or a gap is represented inaccurately;
- alerts expose credentials, message content, private keys, or unrestricted sensitive context;
- required monitoring backup/recovery or alert delivery cannot be proven;
- canonical documentation contradicts the deployed monitoring behavior; or
- an unaccepted High or Critical risk remains.

## 7. Exit criteria

X4 and the parent Milestone X are complete only when:

- the X2 monitor and the X3 production candidate are independently accepted;
- ChatMonitor and approved monitoring hubs run persistently on the administrator Mac and remain
  recoverable from documented backups;
- Production replaces its planned profile with a verified, read-only live source while Local and
  Staging remain independently usable;
- live monitoring catches up after downtime, retains at least 62 days, cannot mutate production,
  and truthfully exposes all collector states;
- required availability, service, certificate, backup, queue, resource, deployment, and monitoring
  alerts deliver and recover through tested paths;
- dashboard metrics and thresholds are characterized with live evidence and remain bounded,
  privacy-safe, and operationally useful;
- production continues operating when every administrator-Mac monitoring component is stopped;
- the production-to-lower-environment feedback and bottom-to-top promotion record is established;
- canonical architecture, monitoring, operations, threat-model, client-responsibility, and changelog
  documentation matches the accepted system;
- every residual risk has an owner, reason, review date, and explicit disposition;
- no unaccepted High or Critical risk remains; and
- the primary stakeholder deliberately approves normal production use and release `1.0.0`.

Passing X4 means the private personal platform is deployed, observable, recoverable, characterized,
and accepted for normal use. It creates no public-service SLA, multi-tenant offering, or open client
ecosystem.

## 8. Non-goals

X4 excludes production-host deployment work already owned by X3, new messaging features, media,
multi-tenancy, federation, end-to-end encryption, multi-instance distribution, IE-01 through IE-03
implementation, public client telemetry ingestion, commercial operations, and paid infrastructure.
