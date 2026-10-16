# Milestone X — Production Activation Program

## Status

**Status:** X1 repository implementation complete, verified, and merged; X2 implementation planning
is active on its dedicated branch; all production-machine, Cloudflare, DNS, Internet-access, and live
acceptance work is isolated in X3.
No production environment is currently claimed.

**Last refined:** 2026-10-16

**Program increments:** X1 foundation, X2 monitoring foundation, then X3 production activation

## 1. Purpose

Milestone X converts the completed single-instance backend and Milestone 9 hardened local rehearsal
into an operated production deployment. It is an operational activation program, not a messaging
feature milestone.

The program is intentionally split into three independently reviewable increments:

1. [Milestone X1 — Production Infrastructure and Recovery Foundation](milestone-x1-production-infrastructure-and-recovery.md)
   implements and locally verifies the deployable, recoverable production foundation.
2. [Milestone X2 — Monitoring Foundation](milestone-x2-monitoring-and-production-acceptance.md)
   builds ChatMonitor and its admin-workstation operating model against fixtures and rehearsal
   environments, without depending on a production machine.
3. [Milestone X3 — Production Deployment, Integration, and Acceptance](milestone-x3-production-deployment-and-acceptance.md)
   performs all external integration: production-host setup, Cloudflare/DNS, live deployment,
   monitoring onboarding, recovery exercises, and the deliberate `1.0.0` decision. Its operator
   procedure is the [X1 Production Operator Manual](../operations/x1-production-operator-manual.md),
   which X3 executes and will rename only when doing so no longer harms established links.

Post-Milestone product enhancements remain governed by the
[Platform Evolution Specification](../platform-evolution-specification.md).

## 2. Canonical routing and precedence

Use this file for program scope, shared decisions, increment dependency, and overall completion.
Use the X1, X2, and X3 guides for implementation-grade requirements and evidence.

| Concern | Canonical implementation guide |
|---|---|
| Deployable Compose, scripts, release contract, recovery tooling | X1 |
| ChatMonitor project, source-neutral event model, local collector/store/dashboard | X2 |
| Host, OS, storage, power, patching, and service lifecycle | X3 |
| Public edge ingress, Cloudflare, firewall, DNS, TLS, and certificates | X3 |
| Secrets, privileged identities, image promotion, migration, rollback | X1 |
| SQL Server, RabbitMQ, backup, restore, incidents, initial capacity | X1 |
| Monitoring workstation implementation and local/rehearsal validation | X2 |
| Live monitoring onboarding, alert delivery, operational exercises, final acceptance | X3 |
| Existing single-host architecture and rehearsal controls | ADR-0017 and Milestone 9 |
| Public authenticated edge and future enrolled-client boundary | ADR-0019 |
| Client behavior and recovery responsibilities | Client Integration and Recovery Guide |

When wording conflicts, accepted ADRs govern architecture decisions, executable operations runbooks
govern already-implemented rehearsal commands, and the applicable X1/X2/X3 guide governs unresolved
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
7. The administrator's development/operations machine is ChatMonitor's first home. Its source-neutral
   telemetry model must allow future official mobile, desktop, and web clients to be onboarded without
   replacing the collector store, aggregate model, dashboard API, or charts.
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

The production host, Windows/Docker Desktop/WSL2 runtime, Docker Hub Personal's single free private
repository, owner,
DHCP-reserved address, BitLocker boundary, approximate workload, and absence of a UPS are recorded in
X1. X1 now fixes a zero-cost direction: SQL Server Express, Cloudflare Tunnel/public TLS for the
current text/API/WebSocket workload, and externally compressed/pre-transfer-encrypted backups on the
router network drive. Cloudflare DNS onboarding, connector hardening, share protocol, and the exact
hostname are X1-A implementation tasks rather than entry
blockers. Monitoring tools, graphing package, alert destinations, and final production thresholds
remain X2 decisions.

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
X1: deployable infrastructure + recovery foundation
                 |
        locally verified artifacts
                 |
                 v
X2: monitoring foundation on the admin machine
                 |
        locally verified monitor
                 |
                 v
X3: production deployment + external integration + acceptance
                 |
            stakeholder approval
                 |
                 v
          production release 1.0.0
```

All three increments are implemented and reviewed separately. X2 may use fixtures and hardened local
rehearsal and has no production dependency. X3 consumes the accepted X1 and X2 handoffs.

X1 or X2 completion does not authorize normal production use. Milestone X is complete only when all
three increments pass and the stakeholder records the final release decision in X3.

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

- every X1 repository-foundation exit criterion passes;
- every X2 exit criterion passes;
- every X3 deployment and acceptance criterion passes;
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

## 9. Current required sequence

1. Re-audit this umbrella, X1, and X2 against the repository and actual target environment.
   **Completed for X1 on 2026-08-30.**
2. Review, merge, and close X1 after its repository evidence is accepted.
3. Implement, verify, document, and close the X2 monitoring foundation on the admin machine.
4. Execute X3 against the production machine, Cloudflare, DNS, backup destination, and live network.
5. Run the X3 readiness review and make the deliberate production/`1.0.0` decision.
