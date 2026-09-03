# AGENTS

## Project identity

ChatBackend is a Java 25 Quarkus backend for a private messaging platform, currently using a modular-monolith architecture and SQL Server.

## Fast orientation

- Primary setup and runbook: [README.md](README.md)
- Visual architecture and repository navigation map:
  [project-architecture.svg](project-architecture.svg) (editable source:
  [project-architecture.excalidraw](project-architecture.excalidraw))
- Release history: [CHANGELOG.md](CHANGELOG.md)
- Versioning and changelog policy: [docs/development-guide/versioning-and-changelog-policy.md](docs/development-guide/versioning-and-changelog-policy.md)
- Architecture overview: [docs/architecture/single-host-layered-container-architecture.md](docs/architecture/single-host-layered-container-architecture.md)
- Architecture decisions (ADR): [docs/architecture/decision](docs/architecture/decision)
- Development guides: [docs/development-guide](docs/development-guide)
- Operations strategy: [docs/operations/environment-strategy-and-rollout-plan.md](docs/operations/environment-strategy-and-rollout-plan.md)
- API/domain specs: [docs/private-instant-messaging-platform-spec-v0.2-sql-server.md](docs/private-instant-messaging-platform-spec-v0.2-sql-server.md)
- Post-Milestone platform evolution specification and track register:
  [docs/platform-evolution-specification.md](docs/platform-evolution-specification.md)
- Post-production infrastructure evolution specification and track register:
  [docs/infrastructure-evolution-specification.md](docs/infrastructure-evolution-specification.md)
- Client integration and recovery contract: [docs/client-integration/client-responsibility-and-recovery-guide.md](docs/client-integration/client-responsibility-and-recovery-guide.md)

## Code map

- Application source: [src/main/java](src/main/java)
- Runtime config: [src/main/resources](src/main/resources)
- Tests: [src/test](src/test)
- DB bootstrap/migrations: [scripts/database](scripts/database)
- CI workflows: [.github/workflows](.github/workflows)

## Graph-first context optimization

- For broad, unfamiliar, architectural, cross-module, planning, review, or investigation work, inspect
  [project-architecture.svg](project-architecture.svg) first. Use its zones and source-path metadata
  in [project-architecture.excalidraw](project-architecture.excalidraw) to select the smallest relevant
  set of code, tests, configuration, ADRs, specifications, guides, and operational documents.
- For a narrowly scoped task whose exact target files and governing contract are already known, go
  directly to those sources; reading the whole graph must not become fixed overhead.
- The graph is a navigation index, not authority. Before reasoning, reviewing, or editing, verify the
  selected implementation and canonical documents because source changes may be newer than the map.
- Treat the graph as stale when a change adds, removes, or materially rewires a represented component,
  dependency, capability, deployment boundary, or canonical documentation route. Invoke the
  [Quarkus graph skill](.agents/skills/quarkus-graph-generator/SKILL.md) and update both graph artifacts
  in the same change set. Ordinary internal edits that do not alter the map do not require a redraw.
- Use stable graph node identifiers and existing spatial zones during updates so accumulated agent
  navigation familiarity is preserved.

## Canonical change rules

- Build/verify command of record: `./mvnw clean verify`
- Do not rewrite applied Flyway versions; add a new migration instead.
- Do not use SQL Server `sa` as the application runtime principal.
- Record new architecture decisions as ADR files under [docs/architecture/decision](docs/architecture/decision).
- Update [CHANGELOG.md](CHANGELOG.md) in the same change set for every notable change, following the
  canonical [versioning and changelog policy](docs/development-guide/versioning-and-changelog-policy.md).
- Keep [docs/platform-evolution-specification.md](docs/platform-evolution-specification.md) authoritative
  when an Evolution Track is added, re-scoped, promoted, deferred, rejected, superseded, or verified.
- Keep [docs/infrastructure-evolution-specification.md](docs/infrastructure-evolution-specification.md)
  authoritative when an Infrastructure Evolution Track is added, re-scoped, promoted, deferred,
  rejected, superseded, or verified.
- Record every verified client-facing responsibility, recovery rule, UX implication, or backend
  capability gap in the canonical [client integration and recovery guide](docs/client-integration/client-responsibility-and-recovery-guide.md)
  when it is learned. Before declaring any milestone, Platform Evolution Track, or Infrastructure
  Evolution Track complete, audit the work against that guide and update it, or explicitly report
  that the review found no client-facing changes.
- Multi-agent guardrail parity: Whenever any rule, instruction, or skill is added or updated in one agent surface (e.g. `.github/` or `.agents/`), update all equivalent agent surfaces in the same change set to prevent drift across different agent tools.

## Multi-agent context architecture & guardrail synchronization

This repository supports diverse AI agent environments. All agents, regardless of vendor or model eccentricities, must adhere to synchronized guardrails:

- **Universal entrypoint**: [AGENTS.md](AGENTS.md)
- **Antigravity / Gemini customization surface**:
  - Global rules: [.agents/rules/repository-instructions.md](.agents/rules/repository-instructions.md)
  - Scoped Java rules: [.agents/rules/backend-java.md](.agents/rules/backend-java.md)
  - Scoped DB rules: [.agents/rules/database-scripts.md](.agents/rules/database-scripts.md)
  - Context architecture skill: [.agents/skills/workspace-context-architecture/SKILL.md](.agents/skills/workspace-context-architecture/SKILL.md)
  - Quarkus graph skill: [.agents/skills/quarkus-graph-generator/SKILL.md](.agents/skills/quarkus-graph-generator/SKILL.md)
- **GitHub Copilot customization surface**:
  - Global instructions: [.github/copilot-instructions.md](.github/copilot-instructions.md)
  - Scoped Java instructions: [.github/instructions/backend-java.instructions.md](.github/instructions/backend-java.instructions.md)
  - Scoped DB instructions: [.github/instructions/database-scripts.instructions.md](.github/instructions/database-scripts.instructions.md)
  - Context architecture skill: [.github/skills/workspace-context-architecture/SKILL.md](.github/skills/workspace-context-architecture/SKILL.md)
  - Quarkus graph skill: [.github/skills/quarkus-graph-generator/SKILL.md](.github/skills/quarkus-graph-generator/SKILL.md)
  - Workspace architect prompt: [.github/prompts/workspace-context-architect.prompt.md](.github/prompts/workspace-context-architect.prompt.md)

### Agent branch naming convention

When an agent creates a development branch, prefix it with the agent identifier (e.g.
`antigravity/<feature>`, `codex/<feature>`). Milestone branch names are reserved for the original
Milestones 0–9 and deferred Milestone X roadmap.

## What to avoid changing casually

- CI workflow behavior in [.github/workflows](.github/workflows) without validation evidence.
- Database bootstrap/migration ordering and scripts under [scripts/database](scripts/database).
- Canonical docs and ADR linkage without updating cross-references.
- De-synchronizing guardrail definitions between `.github/` and `.agents/`.
