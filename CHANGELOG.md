# Changelog

All notable changes to ChatBackend are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and application
versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). See
`docs/development-guide/versioning-and-changelog-policy.md` for the repository's release procedure
and changelog requirements.

## Retrospective version note

Versions `0.0.1` through `0.7.0` are reconstructed milestone snapshots based on repository history.
They describe the logical application versions represented by completed milestones, but formal
release tags begin when the project intentionally performs a production release under the current policy.

## [Unreleased]

### Added

- Advanced Maven project version to `0.9.0-SNAPSHOT` for active Milestone 9 development.
- Added the implementation-ready Milestone 9 operational-hardening guide covering a hardened
  single-instance container deployment, NGINX TLS/WebSocket ingress, database-principal separation,
  verified SQL Server backup restoration, supply-chain evidence, a reproducible load baseline, and a
  threat-model review.
- Revised the Milestone 9 plan after a repository-backed pre-implementation audit: threat modelling
  and audit-query redaction now precede ingress work; database provisioning covers clean and upgrade
  paths with distinct operator/migrator/runtime/backup/restore authorities; SQL TLS, encrypted backups,
  least-privilege RabbitMQ degradation, monitoring, CI replacement, two-stage load validation, and
  exact acceptance commands are explicit.
- Added ADR-0017 and the initial system-specific STRIDE threat model for the hardened single-instance
  NGINX, ChatBackend, SQL Server, RabbitMQ, CI, and recovery boundary.
- Added a non-root, labelled application image and an executable hardened Compose rehearsal where
  NGINX is the only public service, SQL Server uses verified TLS, RabbitMQ topology is operator-owned,
  and application/data/audit networks are separated.
- Added operator, migrator, runtime, backup, and guarded restore SQL Server provisioning; forward-only
  runtime permission migrations; and automated clean-install, upgrade, and negative authority checks.
- Added SQL Server AES-256 certificate-encrypted backup creation, checksum/`RESTORE VERIFYONLY`
  validation, and a guarded isolated restore drill that runs DBCC, forward migrations, application
  readiness, and durable-data evidence checks.
- Added CycloneDX SBOM generation, opt-in OWASP dependency review, digest-pinned Trivy scan tooling,
  a security-gates workflow, and reproducible HTTP/WebSocket k6 characterization/regression harness.
  The OWASP gate reads its NVD API key from a masked CI environment variable so advisory refreshes
  do not depend on unauthenticated public-feed throughput.
- Added a non-root, SQL Server-only migration image derived from Flyway 13.3.0, removing vulnerable
  unused database drivers from the hardened migration boundary while retaining the fixed Microsoft
  JDBC driver.
- Added hardened deployment, rollback, monitoring, backup/restore, and load-test runbooks plus a
  current database-principal authority reference.
- Added an executable WebSocket policy probe that verifies disallowed Origin, missing credential,
  and disabled query-token close codes through the hardened TLS proxy.
- Added the `WL-Chat-HardDocker` Postman environment template for the local hardened HTTPS/WSS stack,
  including deterministic validation and optional cloud synchronization support.
- Generated an identical `ca.pem` alias for the public rehearsal CA so Postman can import the trusted
  authority without disabling certificate verification or receiving a private key.
- Added the Milestone X production-activation backlog so public hosting, managed certificates and
  secrets, off-host recovery, alert delivery, and production capacity evidence remain explicitly
  tracked without reopening Milestone 9.

- Advanced Maven project version to `0.8.0-SNAPSHOT` for active Milestone 8 development.
- Added the implementation-ready Milestone 8 development guide (`docs/development-guide/milestone-8-websockets-step-by-step.md`)
  for Quarkus WebSockets Next real-time event signaling, connection management, post-commit transactional
  fan-out, active membership authorization filtering, and deterministic REST reconnect recovery.
- Established multi-agent guardrail parity and bidirectional synchronization across `.github/` and
  `.agents/` customization surfaces for all AI agents.
- Implemented the authenticated `/api/v1/ws` realtime signaling endpoint, multi-device connection
  registry, active-member-only post-commit fan-out, heartbeat responses, and immediate disconnects
  following session revocation.
- Added realtime events for durable message creation, editing, deletion, delivery advancement, and
  read advancement, plus optional WebSocket commands that delegate delivery/read acknowledgements
  to the existing SQL-backed application service.
- Added focused coverage for WebSocket session/user authentication, connection lifecycle snapshots,
  multi-device fan-out, membership isolation, and unchanged-acknowledgement suppression.
- Added a Quarkus WebSocket transport integration test covering a real authenticated handshake,
  ping/pong, post-commit versus rolled-back event publication, and multi-socket session-revocation
  closure with code `4401`.
- Added a canonical client responsibility and recovery guide covering offline outbox behavior,
  idempotent sends, socket lifecycle, ordered reconciliation, delivery/read semantics, failure
  handling, privacy transitions, first-client acceptance criteria, and current backend capability gaps.
- Established a repository-wide milestone-completion cycle requiring new client-facing learnings to
  be recorded when discovered and the client responsibility guide to be audited before every
  milestone is declared complete.
- Documented the agreed WebSocket validation boundary: repository automation covers protocol
  components and durable REST recovery, while the project owner currently performs network-level
  handshake and frame integration testing through a separate Postman Desktop collection.
- Added a synchronized Postman setup collection and intuitive manual guide that provision two socket
  participants, persist separate session-token variables, trigger message/receipt lifecycle events,
  exercise bidirectional acknowledgements, and verify durable evidence directly in SQL Server.
- Added the paired socket-participant bring-down runner and named the human test actors W (Wayden) and
  L (Lacara), with a complete exchange covering liveness, multi-connection fan-out, all current event
  types, socket acknowledgements, invalid commands, reconnect recovery, and session-revocation closes.
- Added an environment-specific `ws_base_url` variable for reusable Local, DevDocker, and Production
  WebSocket connections in Postman Desktop.

### Changed

- Added a least-capability, one-shot SQL Server volume initializer so fresh hardened named volumes are
  owned by the image's `10001:10001` account before the non-root database process starts. SQL Server's
  writable secrets directory is durable and initialized with the generated TLS identity so Service
  Master Key material survives container replacement.
- Replaced padded base64 password transport in hardened SQL principal, backup-certificate, and
  isolated-restore provisioning with hexadecimal UTF-16 transport because `sqlcmd -v` removes trailing
  `=` padding; decode failures now fail explicitly.
- Made the hardened readiness smoke assertion accept standards-compliant pretty-printed JSON rather
  than requiring a minified health payload, and made its WebSocket boundary check send an actual
  HTTP/1.1 upgrade request.
- Mounted the hardened NGINX virtual host over the image's bundled default server so port 80 reliably
  performs the documented HTTPS redirect instead of returning the base image's `404` response.
- Streamed backup artifacts into SQL Server verification as the container's non-root account so
  `RESTORE VERIFYONLY` can read them without a privileged ownership repair.
- Ensured requests rejected before the normal audit request filter still persist valid method/path
  metadata with fully redacted query values instead of falling into the audit dead-letter path.
- Ensured those early-rejected requests also recover canonical source-address, forwarding, user-agent,
  and device metadata in the response audit pass, preserving spoof-resistant network evidence for
  authentication failures.
- Completed the Milestone 9 client-responsibility review; the existing hardened TLS, Origin,
  credential-transport, reconnect, and REST-reconciliation guidance already covers the verified
  client-facing behavior, so only its review date and applicable development version changed.
- Pinned GitHub Actions, CI database/migration images, application build bases, and scanner tooling to
  immutable reviewed digests or commit SHAs, and made filesystem/image/secret scanning a required CI
  job. The local database gate now addresses GitHub's exact service-container ID rather than
  rediscovering it through a mutable image tag.
- Upgraded the RabbitMQ Java client from `5.21.0` to `5.33.1` to remediate three High findings reported
  by the Milestone 9 Trivy gate.
- Upgraded the supported Quarkus 3.33 LTS patch line from `3.33.2.1` to `3.33.3.1`, moved the runtime
  image to the explicit Temurin Java 25 Ubuntu 22.04 variant, and upgraded Trivy to `0.74.0` in response
  to High dependency/base-tool findings from the first hardened image scan.
- Updated hardened rehearsal candidates to SQL Server 2022 CU26, RabbitMQ 4.3.4, current stable
  unprivileged NGINX, and the repository-owned Flyway 13.3.0 migration image; all promoted references
  remain digest-pinned.
- Recorded the clean RabbitMQ, NGINX, application, and SQL Server-only migration image scans and
  surfaced Microsoft SQL Server 2022 CU26's remaining vendor-binary High findings explicitly rather
  than adding a broad suppression.
- Recorded the project owner's time-limited acceptance of the visible SQL Server 2022 CU26
  vendor-helper findings for the private local Milestone 9 rehearsal through 2026-11-26. SQL Server
  remains internal-only, the findings remain unsuppressed, and production use requires separate
  review.
- Verified the final Milestone 9 source with the canonical 138-test build, clean SpotBugs analysis,
  CycloneDX JSON/XML generation, refreshed Postman discovery and strict collection/environment
  validation, Flyway naming checks, Compose/YAML/shell validation, and a rebuilt non-root application
  image that passes the repository's High/Critical scan gate.

### Fixed

- Made the DevDocker shutdown helper load the same ignored secrets file as startup so Compose can
  interpolate required configuration while retiring containers without deleting named volumes.
- Gave Argon2/JNA a dedicated, bounded executable tmpfs in the otherwise read-only hardened
  application container and made native-library initialization fail startup, preventing health-only
  smoke checks from reporting a deployment as usable when authentication cannot hash passwords.
- Extended the isolated restore drill to use the hardened native-runtime mount and, when supplied a
  complete synthetic fixture, prove restored authentication, message history, and delivery/read
  cursor behavior through the application API instead of relying only on row counts.
- Isolated filesystem scans to a temporary Git-filtered snapshot and image scans to a read-only
  exported archive so ignored local secrets and the Docker daemon socket are never mounted into the
  scanner container; dependency identification now runs offline after vulnerability databases are
  cached to remove Maven Central from the verification path. Added a time-limited, PURL-scoped,
  evidence-backed disposition for Trivy's normalization of the fixed SQL Server JDBC `13.2.1.jre11`
  artifact to its internal `13.2.1` bundle version.
- Infrastructure scanning now collects evidence for every selected digest before failing the gate,
  preventing an early upstream image finding from hiding later image results.
- Made generated rehearsal TLS mounts readable by the non-root application and proxy containers while
  retaining an owner-only host directory and read-only, service-specific mounts.
- Moved NGINX PID and temporary request/proxy state into its bounded writable `/tmp` mount so the
  custom configuration remains compatible with the unprivileged, read-only proxy container.
- Quoted the bounded trace-header regular expression so NGINX parses its repetition braces as part of
  the pattern rather than configuration-block syntax.
- Hardened startup now parses SQL Server JDBC properties exactly and honors the effective last value,
  preventing malformed or duplicate parameters from bypassing verified-TLS enforcement.
- Redacted every non-empty HTTP query before structured logging and RabbitMQ/SQL durable audit
  publication, preventing WebSocket query tokens and other parameter values from entering audit
  evidence.
- Added a hardened WebSocket handshake policy that disables query-token authentication, enforces an
  explicit browser-Origin allowlist, preserves non-browser no-Origin support, and rejects policy
  violations before session authentication.
- Made RabbitMQ audit loss visible through ready-but-degraded health data and switched an established
  broker failure back to bounded local durable persistence while the connection retry loop continues.
- Removed remote push-triggered `sa` database mutation; remote migration is now manual,
  environment-protected, and accepts only a dedicated migrator credential.
- Hardened migration now refuses a missing or mutable migration image reference instead of falling
  back to an upstream tag.

- Made per-user registry registration/removal atomic, isolated failures while closing revoked
  sessions, and changed realtime fan-out to independent asynchronous sends so one socket cannot
  delay or prevent delivery attempts to later sockets.
- Closed unregistered or expired sockets with `4401`, closed the authentication/registration
  revocation race through immediate session revalidation, retained session expiry in connection
  metadata, and excluded expired connections from event fan-out.
- Aligned WebSocket bearer parsing with REST scheme casing/whitespace behavior and rejected
  fractional, string, or out-of-range acknowledgement sequences before durable cursor mutation.
- Corrected Milestone 8 ping/pong and acknowledgement examples to match the implemented
  `{"type":"pong"}` response and `sequence` command field.
- Corrected the manual WebSocket SQL evidence examples to bracket SQL Server schemas and objects,
  and made participant account/session verification resolve IDs internally from the generated W/L
  usernames so manual UUID lookup is unnecessary.
- Corrected the manual WebSocket SQL evidence query to select the identity account's actual
  `system_role` and `status` columns instead of a nonexistent `enabled` column.
- Replaced the manual socket acknowledgement's angle-bracket conversation placeholder with directly
  resolvable Postman variables for the conversation UUID and numeric message sequence.
- Added acknowledgement preflight guidance requiring a T01-created sequence and T06 committed
  high-water verification instead of a guessed sequence value.
- Expanded the W/L manual capability exchange into granular send/receive instructions with exact
  socket commands, expected event-envelope shapes, client-side handling, REST correlation, recovery,
  error, multi-connection, and teardown evidence at every step.
- Persisted complete W/L bearer-header variables for manual WebSocket tabs, preventing a successful
  protocol upgrade followed by an immediate `4401` close when a raw token lacks the `Bearer ` scheme.
- Added complete Postman URL components to the socket workflow collections so Postman Cloud retains
  their request URLs, and normalized omitted-versus-empty request headers during strict drift checks.
- Updated pre-existing message and delivery unit tests for the Milestone 8 event dependencies and
  restored the Spotless build gate across all realtime-related source files.
- Prevented duplicate realtime deletion and acknowledgement events when the durable state did not
  change, rejected sockets for disabled users, and replaced the live connection-set view with an
  immutable snapshot for safe concurrent fan-out.
- Removed an unsupported WebSockets Next timeout property that Quarkus silently ignored; liveness
  remains provided by the supported automatic ping interval and client reconnect reconciliation.

## [0.7.0] - 2026-08-14

### Added

- Implemented a shared RFC 9457 problem representation, safe framework/JSON exception boundaries,
  stable problem occurrence IDs, authentication headers, and privileged root-cause audit capture.
- Added generated OpenAPI 3.1 JSON/YAML snapshots, bearer/public-operation metadata, documented
  login throttling responses, and OpenAPI-to-Postman operation parity validation.
- Added strict collection limits, bounded cursor pagination for active conversation members,
  SQL-backed account/source login throttling, trusted-proxy network-source resolution, and JSON
  application/audit logging with server request and trace correlation.
- Added ADR-0015 to establish Java-to-OpenAPI-to-Postman contract direction, replica-safe SQL-backed
  login throttling, explicit limit behavior, and structured request correlation.

### Changed

- Changed Postman discovery from source-regex inventory to the committed generated OpenAPI contract.

### Fixed

- Raised the HTTP request-body cap so the maximum valid message remains accepted when JSON uses
  six-byte Unicode escape sequences.

## [0.6.0] - 2026-08-14

### Added

- Added authenticated per-user delivery and read acknowledgements with monotonic SQL Server cursor
  updates, committed-history bounds, stale-retry idempotency, and atomic read-implies-delivery
  behavior.
- Added own-position and derived unread-count queries plus sender-only aggregate delivery/read status
  for direct and group conversations.
- Added delivery-specific problem responses, post-commit safe audit metadata, bounded fresh-
  transaction deadlock retries, schema/permission checks, concurrency/privacy coverage, and an
  executable Postman reconnect-reconciliation journey.
- Added ADR-0014 to establish per-user rather than per-device cursors, explicit acknowledgement as
  the only proof of delivery/read state, and aggregate-only group receipt visibility.

### Fixed

- Rejected fractional, string, and out-of-range delivery acknowledgement sequences instead of
  allowing Jackson to coerce them into signed integers; retained delivery targets and committed
  high-water context in failure audits, and avoided exclusive-style locks for read-only status
  queries.

## [0.5.0] - 2026-08-13

### Added

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
- Added repository-wide semantic-versioning and changelog-management guardrails.

### Changed

- Replaced the unsafe client-supplied sender message stub and standalone `/api/v1/messages` route
  with authenticated conversation-scoped message APIs.

### Fixed

- Normalized Postman Cloud's omitted empty response arrays and implicit default environment-variable
  types during drift checks, while reporting the first differing field for actionable failures.
- Corrected generated and curated Postman message contracts to use bearer authentication, valid
  request bodies, accurate response examples, and executable status assertions.
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
