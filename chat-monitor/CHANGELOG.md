# Changelog

All notable ChatMonitor changes are recorded here using Keep a Changelog categories. ChatMonitor is
not independently released while it remains embedded in the ChatBackend repository.

## [Unreleased]

### Fixed

- Excluded and archive-validated local secrets and active environment registries from distributable
  packages, and made restore safely displace or roll back the SQLite database plus WAL/SHM sidecars.
- Preserved SQL `DATETIME2(7)` cursor precision and textual UUID ordering, resumed collection after
  retained-history gaps, restricted HTTP aggregation to completed-request events, and retained
  partially overlapping time buckets.
- Delivered still-firing alerts after suppression expires, protected alert mutations with loopback,
  same-origin, and explicit-action checks, and sanitized unmatched backend routes before monitoring.

### Added

- Created the X2-A single-package Node/Vite foundation with loopback-only Fastify serving, validated
  fixture and SQL configuration, redacted structured logging, response security headers, synthetic
  normalized-event fixtures, automated tests, browser-bundle scanning, and scoped documentation.
- Added the X2-B sanitized SQL audit projection, dedicated projection-only monitor principal,
  hardened credential provisioning, and positive/negative SQL permission verification.
- Added the X2-C durable collection core: immutable checksummed SQLite migrations, source-neutral
  fixture/SQL adapters, atomic event/cursor persistence, hourly/daily aggregation, bounded
  non-overlapping catch-up, explicit failure/gap states, retention, and recovery-focused tests.
- Added the Docker Compose workstation runtime with a non-root read-only container, host-loopback
  publication, health/restart/log controls, file-mounted SQL trust material, and the persistent
  `chat-monitor-data` named volume.
- Added a validated server-side multi-environment registry, concurrent per-environment collectors,
  environment-partitioned SQLite events and aggregates, bounded discovery/status APIs, and an
  accessible dashboard selector with classification-specific themes and explicit production context.
- Added explicit planned-environment profiles so an undeployed Production target remains visible
  while truthfully reporting `SOURCE_UNAVAILABLE` without running a fixture or collector.
- Completed X2-D through X2-F with bounded metrics/operation/sanitized-failure APIs, a
  freshness-first Chart.js dashboard, durable confirmed alert states, acknowledgement and bounded
  suppression, checksum-verified SQLite backup/restore, package/install commands, and pinned
  loopback-only Uptime Kuma/Beszel workstation definitions.
- Added the explicit hardened-local Compose override and operator route that securely joins
  ChatMonitor to the private SQL network, reads the live audit projection, and keeps intentionally
  planned environments from generating false outage alerts.

### Changed

- Moved live production-source onboarding, administrator-Mac alerting, characterization, and final
  acceptance from X3 into the dedicated X4 handoff; X3 now supplies only the independently deployed
  production candidate and production-side monitoring prerequisites.
