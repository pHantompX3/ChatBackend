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

- Advanced the Maven development version to `0.5.0-SNAPSHOT` for Milestone 5 development.
- Replaced the unsafe client-supplied sender message stub and standalone `/api/v1/messages` route
  with authenticated conversation-scoped message APIs.

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
