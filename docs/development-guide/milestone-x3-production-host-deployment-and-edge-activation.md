# Milestone X3 — Production Host Deployment and Edge Activation

## Status

**Status:** Planned; begins after the X1 repository foundation is accepted
**Parent program:** [Milestone X — Production Activation](milestone-x-production-activation.md)
**Required predecessors:** [Milestone X1 — Production Infrastructure and Recovery Foundation](milestone-x1-production-infrastructure-and-recovery.md) and the repository-owned production prerequisites from [Milestone X2 — Monitoring Foundation](milestone-x2-monitoring-and-production-acceptance.md)
**Required successor:** [Milestone X4 — Administrator Monitoring Onboarding and Production Acceptance](milestone-x4-admin-monitoring-and-production-acceptance.md)
**Execution boundary:** Windows production machine and its production external services only
**Last refined:** 2026-10-16

## 1. Purpose

X3 deploys and proves the production runtime independently of the administrator Mac. It owns every
activity performed on the remote Windows production machine and the external control planes required
to make that machine operate: Docker Hub pull access, Cloudflare Tunnel, DNS, public TLS, router
storage, and the production release workflow.

X3 does not install, configure, or validate ChatMonitor on the administrator Mac. It prepares the
read-only monitoring projection, production monitoring identity, bounded network path, and optional
lightweight production-host agent needed by X4, but it does not require the monitoring workstation
to be available. ChatBackend must remain operable when the administrator Mac is offline.

X3 introduces no messaging feature. It consumes immutable, reviewed X1 outputs and X2's
repository-owned production prerequisites, without running the X2 administrator-Mac monitor, and
produces a sanitized production handoff for X4.

## 2. Required X1 and X2 handoffs

X1 supplies:

- signed release bundle and immutable application/migration image digests;
- production Compose, NGINX, Cloudflare, secret, Windows, migration, rollback, backup, restore,
  retention, diagnostics, and evidence tooling;
- locally verified schema version and least-privilege contracts, including `wl_chat_monitor`;
- production-host startup, firewall, storage, and recovery procedures; and
- the operator manual and unresolved environment-value checklist.

X3 must reject an unsigned, unverifiable, schema-incompatible, or materially incomplete handoff.

X2 supplies only the production-side contracts needed on the remote machine:

- the forward-only monitoring projection/principal migration;
- projection column and bounded-cursor contracts;
- negative-permission expectations for `wl_chat_monitor`; and
- optional lightweight host-agent installation requirements, if that agent is accepted in X2.

X2 does not supply a running administrator-Mac service to X3, and X3 acceptance cannot depend on
ChatMonitor, its local SQLite history, its dashboard, or its alert delivery being online.

## 3. Production-machine and external activation scope

X3 performs, records, and verifies:

1. Windows 11, BitLocker, WSL2, Docker Desktop, SSH, time, storage, power-recovery, startup, patch,
   firewall, and administrator-ownership configuration on the production host.
2. Production secret generation and file permissions, Docker Hub pull authentication,
   release-signature trust, least-privilege SQL/RabbitMQ identities, and rotation/revocation paths.
3. Cloudflare account and tunnel creation, dedicated DNS hostname, public TLS, authenticated
   `cloudflared`-to-NGINX TLS, zero public origin ports, connector auto-start, and connector-token
   rotation/recovery.
4. Production deployment from the signed annotated-tag bundle, migration-before-application
   ordering, immutable-digest evidence, readiness, host-local smoke flows, and schema-aware rollback.
5. Private SQL Server and RabbitMQ boundaries, NGINX-only application ingress, outbound-egress
   restrictions, and proof that Docker control and administrative services are not Internet-facing.
6. Router SMB 2/3 backup destination, encrypted and signed package transfer, retention, retrieval,
   isolated restore, `DBCC CHECKDB`, RPO, and RTO evidence.
7. Windows sign-in startup, cold reboot, controlled service outage, power-loss recovery, audit
   retention, disk/capacity boundaries, incident diagnostics, and production runbook usability.
8. Production-side prerequisites for X4: the read-only SQL monitoring projection/principal,
   allowlisted private-network access from the administrator machine, and any approved lightweight
   host/container agent installed with least privilege and no public management port.
9. HTTPS/WSS reachability through Cloudflare from a client outside the origin host, abuse limits,
   certificate validity, and representative API/WebSocket smoke flows. These checks may use an
   operator-controlled mobile connection, but they do not depend on ChatMonitor or the administrator
   Mac.
10. A sanitized X4 handoff containing deployed release/schema identity, private monitoring endpoint
    roles, certificate authorities, required secret-file names, backup/restore status, known risks,
    and protected-evidence references—never credential values or private keys.

The established [X1 Production Operator Manual](../operations/x1-production-operator-manual.md)
remains the executable production-host procedure. X3 records actual environment values and evidence
without duplicating or weakening its safety stops.

## 4. Independent-delivery contract

X3 must be independently closable while the administrator Mac and every monitoring service are
offline. Its acceptance proves that:

- ChatBackend starts, serves HTTPS/WSS through the public edge, and persists durable state;
- production services recover through the documented restart and host-reboot paths;
- deployment, migration, rollback, backup, retrieval, and restore work without ChatMonitor;
- the production host exposes only approved edge and administrative boundaries;
- monitoring preparation grants read-only/sanitized access and creates no runtime dependency; and
- all remaining administrator-Mac work is explicitly transferred to X4.

Passing X3 does **not** approve normal production use or release `1.0.0`. It establishes a deployed
production candidate ready for independent monitoring onboarding and final acceptance in X4.

## 5. Evidence and stop conditions

Evidence is stored outside Git and referenced through a sanitized manifest. Stop X3 when:

- an artifact, signature, digest, or migration identity cannot be verified;
- a secret or privileged port is exposed outside its approved boundary;
- migration or rollback compatibility is unknown;
- SQL Server, RabbitMQ, Docker control, or broad administration is Internet-reachable;
- origin traffic can bypass the intended Cloudflare/NGINX boundary;
- backup retrieval and isolated restore cannot meet the accepted objectives;
- restart, reboot, or connector recovery is unproven;
- the X4 monitoring access boundary would require write authority or unrestricted raw data; or
- an unaccepted High or Critical risk remains.

## 6. Exit criteria

X3 is complete only when:

- every production-host and external activation item in section 3 has retained evidence;
- the signed production release and schema identity are recorded and reproducible;
- HTTPS/WSS, private service boundaries, deployment, rollback, backup, and recovery are proven;
- the production host recovers automatically or through documented bounded intervention after the
  accepted reboot, outage, and power-loss scenarios;
- a retrieved off-host backup restores within accepted RPO/RTO;
- the production monitoring projection, identity, network boundary, and optional agent are ready for
  X4 without making monitoring a runtime dependency;
- the sanitized X4 handoff is complete;
- canonical production-host architecture, operations, threat-model, and changelog documentation
  matches the deployed candidate; and
- every residual risk has an owner, reason, review date, and explicit disposition, with no
  unaccepted High or Critical production-host risk remaining.

## 7. Non-goals

X3 excludes administrator-Mac setup, ChatMonitor production-source onboarding, monitoring-history
backup, dashboard validation, threshold tuning, alert delivery, production characterization, final
normal-use approval, and the `1.0.0` decision. It also excludes new messaging features, media,
multi-tenancy, federation, end-to-end encryption, multi-instance distribution, IE-01 through IE-03
implementation, public client telemetry ingestion, commercial operations, and paid infrastructure.
