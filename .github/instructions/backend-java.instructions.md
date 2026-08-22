---
applyTo: "src/main/java/**/*.java,src/test/java/**/*.java,src/main/resources/**/*.properties"
---

# Backend Java Scope Instructions

## Scope intent

These rules apply when editing Java backend code or its runtime properties.

## Architecture and behavior references

- API and runtime architecture: [docs/architecture/single-host-layered-container-architecture.md](../../docs/architecture/single-host-layered-container-architecture.md)
- Decision records: [docs/architecture/decision](../../docs/architecture/decision)
- Product/domain baseline: [docs/private-instant-messaging-platform-spec-v0.2-sql-server.md](../../docs/private-instant-messaging-platform-spec-v0.2-sql-server.md)

## Required coding posture

- Keep module boundaries explicit and aligned with the modular-monolith ADR direction.
- Maintain request observability and health endpoint behavior when changing HTTP or data access flows.
- Favor additive migrations for database behavior changes and coordinate with Flyway scripts under [scripts/database/flyway](../../scripts/database/flyway).

## Validation

- Use `./mvnw clean verify` for non-trivial changes.
- Keep or add focused tests under [src/test](../../src/test) when behavior changes.

## Multi-agent parity

- Corresponding Antigravity rule: [.agents/rules/backend-java.md](../../.agents/rules/backend-java.md). Keep both in sync.

