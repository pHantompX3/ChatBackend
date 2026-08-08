# Copilot Instructions

## Scope

These are concise repository-wide rules. Keep them stable and avoid duplicating detailed documentation that already has a canonical home.

## Global coding and validation rules

- Prefer minimal, targeted changes over broad refactors.
- Preserve existing architecture/documentation conventions unless a task explicitly requires change.
- Use `./mvnw clean verify` as the default validation command for meaningful changes.
- When changing behavior, verify corresponding tests in [src/test](../src/test).
- For database evolution, add new Flyway versions under [scripts/database/flyway](../scripts/database/flyway); do not alter already-applied migration history.
- Treat [README.md](../README.md) and [docs/development-guide](../docs/development-guide) as operational references and keep them aligned with evidence-based behavior.

## Canonical routing

- Project orientation and navigation: [AGENTS.md](../AGENTS.md)
- Architecture overview: [docs/architecture/single-host-layered-container-architecture.md](../docs/architecture/single-host-layered-container-architecture.md)
- Decision records: [docs/architecture/decision](../docs/architecture/decision)
- Context architecture skill: [skills/workspace-context-architecture/SKILL.md](skills/workspace-context-architecture/SKILL.md)
