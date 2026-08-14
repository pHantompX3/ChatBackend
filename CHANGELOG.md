# Changelog

All notable changes to ChatBackend are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and application
versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). See
`docs/development-guide/versioning-and-changelog-policy.md` for the repository's release procedure
and changelog requirements.

## Retrospective version note

Versions `0.0.1` through `0.4.0` are reconstructed milestone snapshots based on repository history.
They describe the logical application versions represented by completed milestones, but those
versions were not published or tagged retroactively. Versioned release tags begin only when the
project intentionally performs a release under the current policy.

## [Unreleased]

### Added

- Implemented a shared RFC 9457 problem representation, safe framework/JSON exception boundaries,
  stable problem occurrence IDs, authentication headers, and privileged root-cause audit capture.
- Added generated OpenAPI 3.1 JSON/YAML snapshots, bearer/public-operation metadata, documented
  login throttling responses, and OpenAPI-to-Postman operation parity validation.
- Added strict collection limits, bounded cursor pagination for active conversation members,
  SQL-backed account/source login throttling, trusted-proxy network-source resolution, and JSON
  application/audit logging with server request and trace correlation.
- Added the implementation-ready Milestone 7 plan for a common RFC 9457 problem contract,
  generated OpenAPI, strict request/pagination limits, bounded member traversal, shared login
  throttling, structured JSON logs, correlation, and complete Postman operation coverage.
- Added ADR-0015 to establish Java-to-OpenAPI-to-Postman contract direction, replica-safe SQL-backed
  login throttling, explicit limit behavior, and structured request correlation.
- Added authenticated per-user delivery and read acknowledgements with monotonic SQL Server cursor
  updates, committed-history bounds, stale-retry idempotency, and atomic read-implies-delivery
  behavior.
- Added own-position and derived unread-count queries plus sender-only aggregate delivery/read status
  for direct and group conversations.
- Added delivery-specific problem responses, post-commit safe audit metadata, bounded fresh-
  transaction deadlock retries, schema/permission checks, concurrency/privacy coverage, and an
  executable Postman reconnect-reconciliation journey.
- Added the implementation-ready Milestone 6 plan for explicit per-user delivery/read
  acknowledgements, derived unread counts, sender-only aggregate status, reconnect reconciliation,
  authorization, auditing, and SQL Server concurrency coverage.
- Added ADR-0014 to establish per-user rather than per-device cursors, explicit acknowledgement as
  the only proof of delivery/read state, and aggregate-only group receipt visibility.
- Added the implementation-ready Milestone 5 plan for durable message send, deterministic history
  retrieval, idempotency, editing, soft deletion, authorization, auditing, and concurrency tests.
- Added repository-wide semantic-versioning and changelog-management guardrails.
- Added durable authenticated text-message persistence with server-owned IDs, sender identity,
  timestamps, and per-conversation sequence numbers.
- Added sender-scoped client-message idempotency with concurrency-safe duplicate recovery and
  bounded whole-transaction deadlock retries.
- Added forward sequence-based history pagination, sender editing, sender/group-moderator
  soft-deletion, and retained history tombstones.
- Added message-specific safe problem responses and privileged audit metadata that records message
  identifiers and bounded failure diagnostics without recording message bodies.
- Added SQL Server schema, least-privilege, rollback, API authorization, pagination, moderation,
  audit-redaction, and synchronized concurrency tests.
- Added executable Postman send, retry, history, edit, delete, and tombstone workflows.

### Changed

- Changed Postman discovery from source-regex inventory to the committed generated OpenAPI contract.
- Advanced the Maven development version to `0.7.0-SNAPSHOT` for Milestone 7 development.
- Advanced the Maven development version to `0.6.0-SNAPSHOT` for Milestone 6 development.
- Advanced the Maven development version to `0.5.0-SNAPSHOT` for Milestone 5 development.
- Replaced the unsafe client-supplied sender message stub and standalone `/api/v1/messages` route
  with authenticated conversation-scoped message APIs.

### Fixed

- Rejected fractional, string, and out-of-range delivery acknowledgement sequences instead of
  allowing Jackson to coerce them into signed integers; retained delivery targets and committed
  high-water context in failure audits, and avoided exclusive-style locks for read-only status
  queries.
- Normalized Postman Cloud's omitted empty response arrays and implicit default environment-variable
  types during drift checks, while reporting the first differing field for actionable failures.
- Corrected generated and curated Postman message contracts to use bearer authentication, valid
  request bodies, accurate response examples, and executable status assertions.
- Raised the HTTP request-body cap so the maximum valid message remains accepted when JSON uses
  six-byte Unicode escape sequences.
- Corrected exhausted deadlock telemetry to report two performed retries and distinguish exhaustion
  from an active retry.
- Added deterministic coverage for duplicate-winner recovery, bounded deadlock retries, membership
  removal ordering, and role-demotion ordering during administrative deletion.

## [0.4.0] - 2026-08-13

### Added

- Added durable direct and group conversations, retained memberships, conversation roles, and
  canonical direct-participant pairs.
- Added authenticated active-user discovery, conversation listing/details, membership management,
  ownership transfer, and seek pagination.
- Added SQL Server, API, authorization, concurrency, and Postman coverage for the conversation
  lifecycle.

### Changed

- Extended privileged HTTP audit records with bounded root-cause diagnostics while keeping client
  error responses safe.

### Fixed

- Serialized concurrent creation of the same direct conversation with a transaction-scoped SQL
  Server application lock, eliminating the missing-row range-lock deadlock.

## [0.3.0] - 2026-08-11

### Added

- Added opaque session creation, hashed token persistence, expiry, logout, and authenticated request
  filtering.
- Added disabled-user enforcement and administrative revocation of all sessions for a target user.
- Added session API, SQL Server integration, authorization, Postman, and smoke-flow coverage.

### Changed

- Added dedicated application and HTTP-audit logging configuration and expanded environment-aware
  Postman workflows.

## [0.2.0] - 2026-08-08

### Added

- Added controlled administrator bootstrap, invite-only user creation, invitation creation,
  revocation, redemption, and normalized username enforcement.
- Added Argon2id password hashing and durable identity, invitation, and security-audit persistence.
- Added asynchronous HTTP audit delivery with optional RabbitMQ transport and SQL Server
  persistence.
- Added identity unit/integration tests and executable Postman API and user-flow collections.

### Fixed

- Hardened concurrent invitation redemption, duplicate-username handling, migration retry behavior,
  and shared SQL Server Testcontainer ownership.

## [0.1.0] - 2026-08-08

### Added

- Added forward-only Flyway bootstrap and application migration flows for SQL Server.
- Added the `platform`, `identity`, `messaging`, and `audit` logical schemas and separated migration
  and least-privilege runtime principals.
- Added real SQL Server migration, schema-verification, and runtime-permission tests.
- Added local database initialization/reset tooling and CI bootstrap-and-migrate validation.
- Added Postman discovery, validation, inspection, and synchronization tooling.

## [0.0.1] - 2026-08-05

### Added

- Established the Java 25, Quarkus, Maven Wrapper, formatting, static-analysis, and health-endpoint
  foundation.
- Established the modular-monolith package structure, Docker-based development environment, CI
  workflows, architecture records, operational documentation, and repository runbooks.
