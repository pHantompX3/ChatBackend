# ADR-0001: Use a modular monolith for the backend

- Status: Accepted
- Date: 2026-08-04
- Decision owners: Project maintainers

## Context

The current team size and deployment model favor a single service that is easy to
build, test, deploy, and operate. The repository already runs a single Quarkus
application with clear package boundaries and shared SQL Server persistence.

## Decision

Build one deployable Quarkus application with explicit internal feature
boundaries. Do not introduce microservices until independent deployment,
ownership, scaling, or failure isolation becomes a demonstrated requirement.

## Alternatives considered

### Microservices from the start

This was rejected because it would add distributed-system overhead before there
is evidence that service splitting is required.

### Layered monolith without boundary rules

This was rejected because boundary drift is likely without explicit module
ownership and conventions.

## Consequences

### Positive

- Faster local development and onboarding.
- Lower operational complexity for CI/CD and runtime.
- Easier transaction and migration management with one deployable artifact.

### Negative

- Independent scaling of subdomains is not available yet.
- Fault isolation is process-wide until decomposition happens.

### Risks and mitigations

- Risk: boundary erosion over time.
- Mitigation: enforce package-level conventions, architecture reviews, and ADR
  updates when adding new domains.

## Security impact

A single service centralizes authentication, authorization, and auditing logic,
which simplifies consistency but increases blast radius if the service is
compromised. Mitigate via defense-in-depth controls and least-privilege DB
access.

## Operational impact

One deployable artifact simplifies release orchestration, rollback, and health
monitoring. Migration sequencing remains straightforward with a single Flyway
stream and one runtime process.

## Revisit conditions

Revisit this decision if one or more of the following becomes true:

- Independent release cadence is required by multiple domains.
- Sustained throughput requires separate scaling profiles.
- Team ownership boundaries require isolated deployable units.
- Reliability goals require stronger fault isolation than one process can
  provide.
