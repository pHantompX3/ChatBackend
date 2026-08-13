# Copilot Instructions

## Scope

These are concise repository-wide rules. Keep them stable and avoid duplicating detailed documentation that already has a canonical home.

## Global coding and validation rules

- Prefer minimal, targeted changes over broad refactors.
- Preserve existing architecture/documentation conventions unless a task explicitly requires change.
- Use `./mvnw clean verify` as the default validation command for meaningful changes.
- When changing behavior, verify corresponding tests in [src/test](../src/test).
- For database evolution, add new Flyway versions under [scripts/database/flyway](../scripts/database/flyway); do not alter already-applied migration history.
- New application migrations under [scripts/database/flyway/wl_chat](../scripts/database/flyway/wl_chat) must use `VYYYYMMDDHHMMSS__description_in_snake_case.sql`; existing `V1`-`V3` files are grandfathered history and must not be renamed.
- Treat [README.md](../README.md) and [docs/development-guide](../docs/development-guide) as operational references and keep them aligned with evidence-based behavior.
- Update [CHANGELOG.md](../CHANGELOG.md) for every notable change in the same change set. Follow the canonical [versioning and changelog policy](../docs/development-guide/versioning-and-changelog-policy.md); changes requiring release notes are incomplete without them.

## Postman maintenance

Whenever an API endpoint is added, removed, renamed, or materially changed:

- Update the authoritative API contract source (currently implemented routes/DTOs under `src/main/java`).
- Update [postman/collections/chat-backend.postman_collection.json](../postman/collections/chat-backend.postman_collection.json).
- Update paths, methods, parameters, headers, auth configuration, request/response examples, and tests in the affected Postman requests.
- Update [postman/environments/local.example.postman_environment.json](../postman/environments/local.example.postman_environment.json) when non-secret variables change.
- Never commit credentials or tokens into Postman artifacts.
- Run `./scripts/postman/validate-postman.sh` before concluding API-related changes.
- State Postman artifact updates in the completion report.

When a milestone introduces a new user-facing capability or a new combination of APIs that forms a user journey, also review or extend [postman/collections/chat-backend-user-flows.postman_collection.json](../postman/collections/chat-backend-user-flows.postman_collection.json) so the flow-oriented collection reflects the new experience.

- Capture the flow as a named scenario with ordered requests and the variables needed to pass state between steps.
- Prefer adding the flow during milestone wrap-up rather than treating it as a separate cleanup task later.

An API implementation change is incomplete when corresponding Postman artifacts are outdated.

## Canonical routing

- Project orientation and navigation: [AGENTS.md](../AGENTS.md)
- Architecture overview: [docs/architecture/single-host-layered-container-architecture.md](../docs/architecture/single-host-layered-container-architecture.md)
- Decision records: [docs/architecture/decision](../docs/architecture/decision)
- Release ledger: [CHANGELOG.md](../CHANGELOG.md)
- Versioning and changelog policy: [docs/development-guide/versioning-and-changelog-policy.md](../docs/development-guide/versioning-and-changelog-policy.md)
- Context architecture skill: [skills/workspace-context-architecture/SKILL.md](skills/workspace-context-architecture/SKILL.md)
