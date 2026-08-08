# AGENTS

## Project identity

ChatBackend is a Java 25 Quarkus backend for a private messaging platform, currently using a modular-monolith architecture and SQL Server.

## Fast orientation

- Primary setup and runbook: [README.md](README.md)
- Architecture overview: [docs/architecture/single-host-layered-container-architecture.md](docs/architecture/single-host-layered-container-architecture.md)
- Architecture decisions (ADR): [docs/architecture/decision](docs/architecture/decision)
- Development guides: [docs/development-guide](docs/development-guide)
- Operations strategy: [docs/operations/environment-strategy-and-rollout-plan.md](docs/operations/environment-strategy-and-rollout-plan.md)
- API/domain specs: [docs/private-instant-messaging-platform-spec-v0.2-sql-server.md](docs/private-instant-messaging-platform-spec-v0.2-sql-server.md)

## Code map

- Application source: [src/main/java](src/main/java)
- Runtime config: [src/main/resources](src/main/resources)
- Tests: [src/test](src/test)
- DB bootstrap/migrations: [scripts/database](scripts/database)
- CI workflows: [.github/workflows](.github/workflows)

## Canonical change rules

- Build/verify command of record: `./mvnw clean verify`
- Do not rewrite applied Flyway versions; add a new migration instead.
- Do not use SQL Server `sa` as the application runtime principal.
- Record new architecture decisions as ADR files under [docs/architecture/decision](docs/architecture/decision).

## Copilot context architecture

- Reusable context-architecture capability: [.github/skills/workspace-context-architecture/SKILL.md](.github/skills/workspace-context-architecture/SKILL.md)
- Workspace-context architect prompt entrypoint: [.github/prompts/workspace-context-architect.prompt.md](.github/prompts/workspace-context-architect.prompt.md)

## What to avoid changing casually

- CI workflow behavior in [.github/workflows](.github/workflows) without validation evidence.
- Database bootstrap/migration ordering and scripts under [scripts/database](scripts/database).
- Canonical docs and ADR linkage without updating cross-references.
