# ADR-0017: Harden One ChatBackend Instance Behind NGINX

- Status: Accepted
- Date: 2026-08-26
- Decision owners: Project maintainers

## Context

ChatBackend has a complete durable REST API and non-authoritative WebSocket signaling, but its current
Docker and CI assets are development-oriented. The application writes files by default, runs as the
image default user, is reached directly, commonly migrates with `sa`, gives the runtime database user
broad fixed-role membership, and has no proven backup/restore workflow.

The current realtime connection registry and event dispatcher are process-local. Multiple backend
replicas would therefore make live delivery depend on which process owns a socket unless a separate
cross-instance event design is introduced.

## Decision

Milestone 9 provides one repository-owned, production-capable deployment rehearsal with:

1. one NGINX HTTPS/WSS edge and one ChatBackend instance;
2. SQL Server and RabbitMQ on private container networks;
3. NGINX-supplied/preserved `X-Trace-Id`, with ChatBackend retaining ownership of `X-Request-Id`;
4. a hardened WebSocket profile that rejects query-string tokens, enforces an explicit browser-Origin
   allowlist, accepts absent Origin for non-browser clients, and retains subprotocol or Authorization
   token transport;
5. an operator-provisioned database followed by Flyway as a non-`sa` migrator and ChatBackend as an
   enumerated least-privilege runtime user;
6. separate backup and break-glass restore authority;
7. verified SQL Server TLS for application and migration traffic;
8. encrypted native SQL Server backups that are accepted only after isolated restore and application
   validation;
9. operator-provisioned RabbitMQ topology and a non-administrator application principal;
10. SQL Server as a readiness dependency while RabbitMQ audit transport remains ready-but-degraded;
11. non-root/read-only-capable images, immutable release references, SBOM/scanning evidence, and
    environment-protected migration/deployment orchestration.

Local and DevDocker profiles may temporarily retain query-token WebSocket compatibility and explicit
administrator bootstrap convenience. Those behaviors are not permitted in the hardened profile and
their credentials are never supplied to the long-running application container.

Apache APISIX is not deployed in parallel. It remains a possible later replacement if multi-service
gateway or multi-instance requirements justify another ADR. Multiple ChatBackend replicas remain
deferred until cross-instance realtime delivery is designed.

## Alternatives considered

### Deploy multiple backend replicas now

Rejected because the process-local connection registry cannot provide deterministic realtime fan-out
across replicas. Sticky sessions alone would not distribute committed events to sockets owned by a
different process.

### Introduce APISIX and NGINX together

Rejected because two edge products add configuration and failure modes without a current routing or
scale requirement. NGINX is sufficient for the single-host HTTP/WSS boundary.

### Continue using `sa` for all migrations

Rejected because a leaked deployment credential would grant server-wide authority. An explicit
bootstrap operator remains available only for provisioning, while ordinary migration uses a narrower
database principal.

### Make RabbitMQ a readiness dependency

Rejected because RabbitMQ transports audit records rather than authoritative messaging state. Broker
failure is surfaced as an operational degradation while local asynchronous persistence/fallback keeps
the API available.

## Consequences

### Positive

- The public boundary, database authority, backup evidence, and deployment process become testable.
- Secrets and privileged identities have smaller exposure windows.
- The topology matches current realtime correctness instead of implying unsupported horizontal scale.
- Client recovery remains based on durable REST state.

### Negative

- The single application instance is a process-level availability limit.
- Operators must manage more narrowly scoped credentials and backup encryption material.
- Hardened browser clients must configure an allowed Origin and cannot authenticate through a query
  token.
- RabbitMQ degradation requires explicit monitoring because readiness remains successful.

### Risks and mitigations

- **Single-host failure:** encrypted off-host backup and restore evidence are production-activation
  requirements.
- **Configuration drift:** repository scripts own behavior and CI calls those scripts as thin
  orchestration.
- **Forward-only schema rollback:** deploy older application images only when schema compatibility is
  proven; otherwise use a forward fix or guarded restore.
- **Credential leakage in URLs/logs:** query values are redacted before logs and durable audit
  publication, and hardened WebSockets reject query-token transport.

## Security impact

The decision reduces public ports, removes privileged database secrets from the application, verifies
internal SQL TLS, limits RabbitMQ authority, protects backup confidentiality, and makes WebSocket
browser origins explicit. Remaining production risks require a real host, trusted certificate,
external secret delivery, off-host backup destination, and alert owner.

## Operational impact

Deployments gain separate provisioning, migration, rollout, backup, restore, scanning, and diagnostics
steps. SQL health remains readiness-gating. RabbitMQ failure is diagnosed and alerted as degraded.
Rehearsal begins with a 24-hour RPO objective and a 60-minute restore objective; production must accept
or replace those values.

## Revisit conditions

Revisit this decision when the platform needs multiple backend instances, a frontend/BFF tier,
multi-service gateway policies, cross-host deployment, or a production environment whose managed
services replace repository-owned NGINX, SQL Server, or RabbitMQ responsibilities.

## References

- [Milestone 9 implementation guide](../../development-guide/milestone-9-operational-hardening-step-by-step.md)
- [Single-host layered target](../single-host-layered-container-architecture.md)
- [Environment strategy](../../operations/environment-strategy-and-rollout-plan.md)
- [Threat model](../../security/threat-model.md)
- [ADR-0015](ADR-0015-harden-http-contracts-and-authentication-throttling.md)
- [ADR-0016](ADR-0016-use-websockets-next-for-realtime-signaling.md)
