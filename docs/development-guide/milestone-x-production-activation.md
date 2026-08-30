# Milestone X — Production Activation Program

## Status

**Status:** Pocketed for future implementation; no production environment is currently claimed

**Last refined:** 2026-08-30

**Program increments:** X1 followed by X2

## 1. Purpose

Milestone X converts the completed single-instance backend and Milestone 9 hardened local rehearsal
into an operated production deployment. It is an operational activation program, not a messaging
feature milestone.

The program is intentionally split into two independently reviewable increments:

1. [Milestone X1 — Production Infrastructure and Recovery Foundation](milestone-x1-production-infrastructure-and-recovery.md)
   produces a secure, repeatable, recoverable production candidate.
2. [Milestone X2 — Monitoring, Operational Validation, and Production Acceptance](milestone-x2-monitoring-and-production-acceptance.md)
   makes that candidate observable, exercises its operating model, and governs the deliberate
   `1.0.0` production-release decision.

Post-Milestone product enhancements remain governed by the
[Platform Evolution Specification](../platform-evolution-specification.md).

## 2. Canonical routing and precedence

Use this file for program scope, shared decisions, increment dependency, and overall completion.
Use the X1 and X2 guides for implementation-grade requirements and evidence.

| Concern | Canonical implementation guide |
|---|---|
| Host, OS, storage, power, patching, and service lifecycle | X1 |
| Public edge ingress, firewall, DNS, TLS, and certificates | X1 |
| Secrets, privileged identities, image promotion, migration, rollback | X1 |
| SQL Server, RabbitMQ, backup, restore, incidents, initial capacity | X1 |
| Monitoring workstation and third-party monitoring tools | X2 |
| ChatMonitor project, SQL projection, collection, retention, dashboard | X2 |
| Alerts, monitoring recovery, operational exercises, final acceptance | X2 |
| Existing single-host architecture and rehearsal controls | ADR-0017 and Milestone 9 |
| Public authenticated edge and future enrolled-client boundary | ADR-0019 |
| Client behavior and recovery responsibilities | Client Integration and Recovery Guide |

When wording conflicts, accepted ADRs govern architecture decisions, executable operations runbooks
govern already-implemented rehearsal commands, and the applicable X1/X2 guide governs unresolved
production implementation scope. Production-specific secrets, private topology, and raw operational
evidence remain in protected operator storage.

Requirement language is deliberate:

- **must** is a release or safety requirement;
- **should** is the proportional default and requires a recorded reason to diverge;
- **may** is optional and does not enter scope without an explicit decision; and
- **candidate/preferred** requires the stated validation before adoption.

## 3. Confirmed shared direction

1. Production remains a hardened single-host deployment unless evidence justifies a later ADR.
2. The production machine and operator/development workstation will generally share the same private
   local network.
3. Production exposes one public, authenticated HTTPS/WSS NGINX edge under ADR-0019 so remote native
   mobile and web clients can operate without mandatory VPN enrollment.
4. Human user sessions and server-side authorization are initially authoritative. X1 does not claim
   to identify exact frontend software, and users must supply credentials only to clients they trust.
5. NGINX, firewall, throttling, TLS, monitoring, and revocation reduce public-edge risk but do not
   attest client binaries. ChatBackend, SQL Server, RabbitMQ, Docker control, and administrative
   services remain private.
6. IE-01 through IE-03 are official post-X requirements: native per-installation trust first,
   mobile-authorized linked-browser protocol second, and the official web companion third. X1 must
   not block them, but it does not implement or activate them.
7. The monitoring workstation is the preferred home for dashboards and monitoring history so those
   records can survive production-host loss.
8. Monitoring is never a runtime dependency of ChatBackend.
9. ChatMonitor uses a Vite vanilla-JavaScript client, standards-based HTML/CSS, a small Node.js
   collector/API service, and SQLite. React is not part of the accepted baseline.
10. Audit extraction is one-way, sanitized, read-only, incremental telemetry export—not database
    replication.
11. ChatMonitor retains at least 62 days of detailed projected events. The recommended initial
    defaults are 90 days of detailed events and 365 days of aggregates.
12. Authoritative production audit retention must permit expected collector catch-up and may not be
    set below the 62-day floor without an explicit stakeholder decision.
13. Self-hosted monitoring is preferred. External alert delivery or a second-device heartbeat is
    optional and cannot become a core application dependency.

The exact production host, network, DNS, certificate issuer, registry, SQL placement, backup store,
secret mechanism, monitoring tools, graphing package, alert destinations, thresholds, and accountable
owners are environment decisions to resolve in the applicable increment.

## 4. Standard decision record

Every unresolved production decision must be recorded with:

```text
Decision ID and title:
Status: proposed | accepted | deferred | rejected | blocked
Decision owner:
Date and review date:
Context and constraint:
Selected value/approach:
Recommended default used or reason for divergence:
Alternatives considered:
Security, privacy, availability, cost, and maintenance impact:
Dependencies and sequence:
Implementation outputs:
Verification evidence:
Rollback/recovery behavior:
Residual risk and acceptance:
Canonical documents affected:
```

Naming a product is not enough. A decision is resolved only when ownership, configuration boundary,
lifecycle, failure/recovery behavior, validation evidence, and documentation effects are known.

## 5. Increment dependency and delivery model

```text
Milestone 9 hardened local rehearsal (complete)
                 |
                 v
X1: production infrastructure + recovery foundation
                 |
        secure/recoverable candidate
                 |
                 v
X2: monitoring + operational validation + acceptance
                 |
            stakeholder approval
                 |
                 v
          production release 1.0.0
```

X1 and X2 should be implemented and reviewed separately. X2 planning and fixture-based ChatMonitor
development may proceed in parallel, but X2 production integration and final acceptance depend on the
X1 evidence handoff.

X1 completion does not authorize normal production use. X2 completion without X1 is impossible.
Milestone X is complete only when both increments pass and the stakeholder records the final release
decision.

## 6. Shared evidence and governance

Each increment maintains a sanitized evidence manifest that links to protected evidence instead of
embedding secrets, raw logs, production data, or private topology in Git. At minimum, final program
evidence must cover:

- production host/profile and accountable owners;
- immutable release/image digests and schema version;
- firewall, certificate, principal, deployment, migration, rollback, and smoke results;
- backup identity, off-host transfer, retrieval, restore, integrity, RPO, and RTO evidence;
- characterization thresholds and capacity assumptions;
- monitoring installation, freshness, recovery, query safety, retention, and alert exercises;
- incident and rollback exercises;
- unresolved risks, risk acceptances, review dates, and final approval.

Every implementation change must update the changelog and affected architecture, operations, security,
and client-responsibility documents. Neither increment may claim completion based only on the local
Milestone 9 rehearsal.

## 7. Program exit criteria

Milestone X is complete only when:

- every X1 exit criterion passes and its secure/recoverable candidate handoff is accepted by X2;
- every X2 exit criterion passes;
- production HTTPS/WSS, private SQL/RabbitMQ boundaries, secrets, release promotion, rollback, backup
  recovery, monitoring, alerting, capacity, and incident ownership are proven in the selected
  environment;
- canonical documentation matches deployed behavior;
- all findings are resolved, explicitly risk-accepted with owner/reason/review date, or treated as
  production blockers;
- no unaccepted High or Critical risk remains; and
- the primary stakeholder deliberately approves normal production use and the `1.0.0` release.

## 8. Program non-goals

Milestone X does not introduce:

- multi-tenancy or federation;
- end-to-end encryption;
- multi-instance realtime distribution;
- new messaging product features;
- enterprise observability or invasive user analytics;
- a mandatory cloud platform;
- production database replication to the monitoring workstation;
- monitoring writes to production application data;
- public OAuth/delegated client registration or an open developer ecosystem;
- implementation or mandatory enforcement of IE-01 through IE-03 native trust, linked-browser
  protocol, or official web-companion capabilities; or
- software licensing, pricing, or support terms for independent deployments.

## 9. Required sequence when resumed

1. Re-audit this umbrella, X1, and X2 against the repository and actual target environment.
2. Resolve X1 entry decisions and audit its implementation plan.
3. Implement, verify, document, and review X1; produce its evidence handoff.
4. Resolve X2 entry decisions using X1 evidence and current monitoring requirements.
5. Implement, verify, document, and review X2.
6. Run the final readiness review and make the deliberate production/`1.0.0` decision.
