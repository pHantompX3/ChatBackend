# Audit Checklist

## Phase 1: Repository inventory

- identify existing AI customization surfaces
- identify canonical technical references
- identify active planning/state artifacts
- identify CI/build/test validation surfaces

## Phase 2: Agent context surfaces

- AGENTS.md / GEMINI.md (root and nested)
- .agents/rules/*.md
- .agents/skills/**/SKILL.md
- .github/copilot-instructions.md & .github/instructions/
- other model-specific instruction files

## Phase 3: Routing and canonicality

- verify clear root entrypoint for orientation
- verify major concerns have canonical references
- detect duplicate/conflicting instruction text
- detect stale absolute paths or machine-specific assumptions

## Phase 4: Workspace lifecycle organization

- distinguish stable references from active work where applicable
- avoid parallel systems of record

## Phase 5: Cold-agent usability

Confirm a new agent can quickly answer:

1. what project is this
2. where code lives
3. where architecture decisions live
4. which rules apply
5. how to build/test
6. which docs are mandatory before behavior changes
7. where to record new decisions
8. where persistent working artifacts go
9. how canonical info is identified
10. what should not be changed casually
