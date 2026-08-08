---
name: workspace-context-architect
description: Audit and improve workspace context architecture for reliable Copilot orientation, routing, canonical documentation, and cold-agent onboarding.
argument-hint: Optional priorities or constraints for the context architecture pass
---

# Workspace Context Architect

Use this prompt as the launcher for repository context-architecture work.

## Canonical methodology

- Primary reusable skill: [.github/skills/workspace-context-architecture/SKILL.md](../skills/workspace-context-architecture/SKILL.md)
- Audit checklist: [.github/skills/workspace-context-architecture/references/audit-checklist.md](../skills/workspace-context-architecture/references/audit-checklist.md)
- Methodology principles: [.github/skills/workspace-context-architecture/references/methodology.md](../skills/workspace-context-architecture/references/methodology.md)
- Copilot surfaces guide: [.github/skills/workspace-context-architecture/references/copilot-context-surfaces.md](../skills/workspace-context-architecture/references/copilot-context-surfaces.md)
- Validation protocol: [.github/skills/workspace-context-architecture/references/validation.md](../skills/workspace-context-architecture/references/validation.md)

## Execution contract

1. Run read-only inventory before modifications.
2. Preserve working repository strengths.
3. Route to canonical sources; remove duplication/conflict.
4. Keep root and always-loaded context concise.
5. Apply non-destructive changes first.
6. Run cold-agent and integrity validation before final report.

## Deliverable

Produce:

- workspace assessment (strengths, weaknesses, maturity)
- explicit file-level change log (path -> purpose -> reason)
- resulting context-routing hierarchy
- Copilot loading model (always-on vs scoped vs manual)
- canonical source map
- validation evidence and remaining issues
