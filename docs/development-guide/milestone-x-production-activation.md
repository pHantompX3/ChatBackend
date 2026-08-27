# Milestone X — Production Activation Backlog

## Status

Pocketed for future planning. No production environment is currently claimed.

## Purpose

Milestone X converts the completed single-instance backend and Milestone 9 hardened local rehearsal
into an operated production deployment. It tracks environment ownership and evidence that cannot be
completed responsibly inside the repository without a selected host, domain, secret store, backup
destination, alert channel, and service objectives.

## Entry decisions

Before implementation begins, identify:

1. hosting provider, region, x86-64 host shape, operating system, and patch owner;
2. public domain, DNS owner, certificate issuer, and renewal owner;
3. OCI registry, image promotion policy, and deployment approver;
4. production SQL Server placement, edition/licence, storage, and maintenance owner;
5. managed secret-delivery mechanism and credential-rotation owner;
6. encrypted off-host backup destination, retention/immutability policy, and recovery owner;
7. monitoring platform, alert destinations, on-call owner, and incident escalation path;
8. accepted RPO, RTO, load thresholds, availability objective, and maintenance window.

## Workstreams

1. Provision the private application, SQL Server, and RabbitMQ networks with only the HTTPS/WSS edge
   publicly reachable.
2. Replace rehearsal certificates with publicly trusted and automatically renewed certificates.
3. Deliver runtime/operator credentials from the selected secret store; keep them out of images,
   source control, deployment logs, and long-running containers that do not need them.
4. Promote reviewed immutable image digests and exercise migration-before-rollout and schema-aware
   rollback in the selected environment.
5. Schedule encrypted off-host backups, enforce retention/immutability, monitor age and transfer, and
   perform a timed isolated restore from the retrieved off-host artifact.
6. Route health, certificate, disk, restart, audit degradation/backlog/DLQ, backup/restore age,
   deployment, and security-gate alerts to the accountable responders.
7. Run characterization and regression tests against production-representative resources and data,
   then record approved thresholds and capacity limits.
8. Complete the security/threat-model production review, incident exercise, release checklist, and
   deliberate `1.0.0` readiness decision.

## Exit criteria

- Public HTTPS/WSS works with trusted certificates and renewal has been exercised.
- SQL Server and RabbitMQ remain private and least-privilege inventories match the hardened model.
- Managed secret rotation and immutable image promotion are proven.
- A retrieved off-host encrypted backup restores within the accepted RTO and meets the accepted RPO.
- External alerts reach the assigned responder and an incident/rollback exercise is recorded.
- Production-representative load thresholds pass and capacity assumptions are documented.
- No unaccepted High/Critical risk remains, and production release approval is recorded.

## Non-goals

Milestone X does not introduce multi-tenancy, end-to-end encryption, multi-instance realtime
distribution, or new client/product features. Those require separate product decisions and plans.
