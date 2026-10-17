# ChatMonitor Agent Instructions

## Identity and boundary

ChatMonitor is an independently runnable Node.js 22 companion project. It collects bounded,
read-only operational projections into a local SQLite store and serves a loopback-only dashboard.
It must never become a runtime dependency of ChatBackend, mutate ChatBackend state, or expose source
credentials to browser code.

The authoritative X2/X4 monitoring scope boundary remains in
[Milestone X2](../docs/development-guide/milestone-x2-monitoring-and-production-acceptance.md) and
[Milestone X4](../docs/development-guide/milestone-x4-admin-monitoring-and-production-acceptance.md).

## Fast orientation

- Human/operator entrypoint: [README.md](README.md)
- Architecture and dependency direction: [docs/architecture.md](docs/architecture.md)
- Security and privacy boundaries: [docs/security.md](docs/security.md)
- Metrics semantics: [docs/metrics-catalog.md](docs/metrics-catalog.md)
- Local operations and recovery: [docs/operations.md](docs/operations.md)
- Local release history: [CHANGELOG.md](CHANGELOG.md)
- Server composition: [src/server](src/server)
- Browser dashboard: [src/client](src/client)
- Shared wire contracts only: [src/shared](src/shared)
- Automated verification: [test](test)

## Required commands

Run commands from `chat-monitor/`:

```text
npm ci
npm run verify
npm run test:browser
npm run container:config
npm run container:up
```

Use `npm run dev` only for development. `npm start` must serve the immutable `dist/client` build;
it must never start the Vite development server.

## Change rules

- Keep this as one npm package until extraction into its own repository is explicitly approved.
- Maintain dependency direction: client -> shared wire shapes; server -> shared; neither side imports
  the other's implementation.
- Keep credentials and source configuration server-only. Never prefix a secret with `VITE_`.
- Keep listeners loopback-only unless a later accepted plan explicitly changes the threat model.
- Preserve the Docker Compose service boundary, host-loopback port publication, non-root/read-only
  runtime, and `chat-monitor-data` named volume. Never mount the Docker socket.
- Treat SQLite migrations as immutable after release; add a new ordered migration for corrections.
- Keep collection read-only, bounded, parameterized, resumable, and unable to affect ChatBackend.
- Do not record message content, credentials, raw headers, arbitrary metadata, or direct identities.
- Update this project's README/changelog and the root changelog for notable capability changes.
- Update the root architecture graph when a represented component, dependency, deployment boundary,
  or canonical documentation route changes.
- Keep the mirrored repository rules in
  [`.agents/rules/chat-monitor.md`](../.agents/rules/chat-monitor.md) and
  [`.github/instructions/chat-monitor.instructions.md`](../.github/instructions/chat-monitor.instructions.md)
  aligned.

## Generated and runtime exclusions

Do not inspect or commit `node_modules/`, `dist/`, `var/`, coverage, Playwright output, SQLite files,
logs, `.env`, or local credentials unless the task explicitly concerns generated-output validation.
