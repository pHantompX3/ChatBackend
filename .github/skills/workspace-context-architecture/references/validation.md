# Validation

## Integrity checks

- all referenced paths exist
- no broken internal links in new files
- no secrets or machine-local sensitive values copied into docs
- no generated/vendor files changed unintentionally

## Cold-agent walk

Starting from AGENTS.md, confirm quick navigation to:

- project purpose and baseline stack
- source and tests
- architecture and ADRs
- build/test commands
- development runbooks
- operations and rollout references

## Journey A: implementation

Scenario: change behavior in a backend subsystem.

Must be able to reach:

- applicable global/scoped rules
- architecture and domain context
- target source and tests
- validation commands

## Journey B: investigation/planning

Scenario: propose change to an existing feature.

Must be able to reach:

- current architecture and decisions
- relevant runbooks/specs
- planning artifacts location (if used)
- expected output location for new decisions

## Final report

Summarize:

- what was validated
- what passed
- what remains ambiguous
