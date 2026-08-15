# Agent Context Surfaces

## AGENTS.md / GEMINI.md

Use as the root human-and-agent router.

Should include:

- project identity and scope
- where authoritative docs live
- where code/test/ops/decision records live
- concise constraints for safe edits

Should avoid:

- large policy duplication from other files
- unstable implementation details

## .agents/rules/*.md & .github/copilot-instructions.md

Use for concise global and scoped coding rules that apply repository-wide or per-directory.

Keep small and stable. Prefer linking to authoritative references.

## Scoped rule files

Use for targeted folders/technologies where loading only-in-scope context improves accuracy.

Rules:

- narrow apply-to scope
- avoid repeating universal rules
- reference canonical docs

## Skills

Use for reusable capabilities that are procedural and likely to recur.

A skill should not become a dumping ground for general project facts.

## Multi-Agent Parity

Whenever editing files in `.agents/`, ensure corresponding instructions and skills in `.github/` (`.github/copilot-instructions.md`, `.github/instructions/`, `.github/skills/`) and `AGENTS.md` are updated to maintain complete parity across all agent ecosystems.

