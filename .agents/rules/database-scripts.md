# Database Scripts Scope Instructions

## Scope intent

These rules apply when editing database bootstrap, migration scripts, or Flyway SQL files (`scripts/database`).

## Canonical references

- Milestone implementation guide: [docs/development-guide/milestone-1-database-foundation-step-by-step.md](../../docs/development-guide/milestone-1-database-foundation-step-by-step.md)
- SQL Server principals and permissions: [docs/database/sql-server-principals-and-permissions.md](../../docs/database/sql-server-principals-and-permissions.md)
- Operations rollout context: [docs/operations/environment-strategy-and-rollout-plan.md](../../docs/operations/environment-strategy-and-rollout-plan.md)

## Required migration posture

- Never rewrite applied Flyway versions; add a new versioned migration.
- Keep scripts idempotent where practical and explicit about target database/schema.
- Keep runtime principal least-privilege and avoid `sa` for application runtime access.

## Validation

- Validate script behavior with repository bootstrap/migrate scripts under [scripts/database](../../scripts/database).
- For meaningful changes, run `./mvnw clean verify` and confirm health/readiness behavior remains aligned.

## Multi-agent parity

- Corresponding GitHub Copilot instruction: [.github/instructions/database-scripts.instructions.md](../../.github/instructions/database-scripts.instructions.md). Keep both in sync.

