---
name: workspace-context-architecture
description: Use when auditing or restructuring repository context architecture, agent customization, AI instruction routing, authoritative documentation layout, AGENTS.md/repository-instructions/rules/prompts/agents/skills organization, or cold-agent onboarding/discoverability. Do not use for ordinary feature coding that does not change workspace context architecture.
---

# Workspace Context Architecture

## Purpose

This skill performs conservative, evidence-based workspace context architecture work for this repository. It prioritizes discoverability, canonical sources, scoped routing, and low-noise context loading.

## Use this skill when

- auditing repository context or documentation organization
- designing or refactoring AGENTS.md routing
- modifying repository instructions or rules
- creating or refining scoped rule files
- creating/reviewing reusable prompts, agents, or skills
- reducing duplicated or conflicting AI instructions
- performing cold-agent onboarding and navigation audits
- aligning stable knowledge vs active working-state artifacts

## Do not use this skill for

- ordinary implementation tasks unrelated to context architecture
- broad source-code refactors that are not documentation/routing driven

## Operating model

1. Read-first, modify-second.
2. Preserve what already works.
3. Route to canonical sources instead of duplicating facts.
4. Keep universal instructions small and stable.
5. Prefer scoped/contextual instructions for selective loading.
6. Separate stable references from changing task state.
7. Validate with cold-agent walk tests.

## Procedure

1. Run discovery and inventory from [references/audit-checklist.md](references/audit-checklist.md).
2. Apply design principles from [references/methodology.md](references/methodology.md).
3. Use context surface semantics in [references/context-surfaces.md](references/context-surfaces.md).
4. Implement conservative structural improvements.
5. Run validation in [references/validation.md](references/validation.md).
6. Produce an evidence-based report.

## Required safeguards

- Do not delete useful documentation silently.
- Do not move source trees just to match a methodology template.
- Do not create empty scaffolding.
- Do not embed machine-specific absolute paths in persistent instructions.
- Do not duplicate policy across AGENTS.md, repository instructions, scoped rules, prompts, and skills.
- Maintain full parity across multi-agent surfaces (.agents/ <-> .github/ <-> AGENTS.md) whenever guardrails are altered.


## Deliverable contract

For every run, produce:

- workspace maturity assessment
- strengths to preserve
- weaknesses/drift findings
- file-by-file changes (path -> purpose -> reason)
- final context routing model
- skill routing model
- cold-agent validation results
- unresolved issues requiring human decisions
