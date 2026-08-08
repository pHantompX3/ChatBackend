# Copilot Context Surfaces

## AGENTS.md

Use as the root human-and-agent router.

Should include:

- project identity and scope
- where authoritative docs live
- where code/test/ops/decision records live
- concise constraints for safe edits

Should avoid:

- large policy duplication from other files
- unstable implementation details

## .github/copilot-instructions.md

Use for concise global coding rules that should apply repository-wide when present.

Keep small and stable. Prefer linking to authoritative references.

## Scoped .instructions.md files

Use for targeted folders/technologies where loading only-in-scope context improves accuracy.

Rules:

- narrow applyTo patterns
- avoid repeating universal rules
- reference canonical docs

## .prompt.md files

Use for reusable operational workflows, not as canonical architecture references.

Prompts should point to canonical docs and skills.

## .agent.md files

Create only for clear specialization that cannot be solved by routing + scoped instructions.

## Skills

Use for reusable capabilities that are procedural and likely to recur.

A skill should not become a dumping ground for general project facts.
