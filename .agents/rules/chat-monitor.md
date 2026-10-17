# ChatMonitor Scope Instructions

## Scope intent

These rules apply to the independently runnable Node.js companion project under `chat-monitor/`.

## Architecture and behavior references

- Local project entrypoint: [chat-monitor/README.md](../../chat-monitor/README.md)
- Scoped agent route: [chat-monitor/AGENTS.md](../../chat-monitor/AGENTS.md)
- Architecture: [chat-monitor/docs/architecture.md](../../chat-monitor/docs/architecture.md)
- Security: [chat-monitor/docs/security.md](../../chat-monitor/docs/security.md)
- X2 delivery contract: [docs/development-guide/milestone-x2-monitoring-and-production-acceptance.md](../../docs/development-guide/milestone-x2-monitoring-and-production-acceptance.md)

## Required implementation posture

- Preserve ChatMonitor as one independently runnable npm package; do not import ChatBackend source.
- Keep client, server, and shared wire-contract boundaries explicit.
- Keep source credentials server-only and never expose secrets through `VITE_` variables or browser assets.
- Keep listeners loopback-only unless a later accepted plan explicitly changes the threat model.
- Preserve Compose host-loopback publication, the non-root/read-only container boundary, and the
  `chat-monitor-data` named volume; never mount the Docker socket.
- Keep source access read-only, bounded, parameterized, resumable, and unable to affect ChatBackend.
- Treat released SQLite migrations as immutable and add ordered migrations for corrections.
- Exclude message content, credentials, raw headers, arbitrary metadata, and direct user identities.
- Do not inspect generated/runtime trees (`node_modules`, `dist`, `var`, coverage, Playwright output)
  unless the task concerns their verification.

## Validation

- Run `npm ci` and `npm run verify` from `chat-monitor/` for meaningful changes.
- Run `npm run container:config` for Compose/runtime changes and perform the documented fixture
  container smoke test when Docker behavior changes.
- Run `npm run test:browser` when dashboard behavior, static serving, or security headers change.
- Update ChatMonitor and root changelogs for notable capabilities.
- Update both root architecture graph artifacts when represented boundaries or routes change.

## Multi-agent parity

Corresponding GitHub Copilot instruction:
[.github/instructions/chat-monitor.instructions.md](../../.github/instructions/chat-monitor.instructions.md).
Keep both aligned.
