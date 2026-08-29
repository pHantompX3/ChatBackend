# Versioning and Changelog Policy

## Purpose

This policy defines how ChatBackend application versions, release tags, and the root
`CHANGELOG.md` are maintained. It applies to human contributors and coding agents.

The application is one deployable modular monolith. One application version therefore describes
the complete backend artifact; individual modules do not receive independent versions.

## Version model

ChatBackend follows Semantic Versioning:

```text
MAJOR.MINOR.PATCH
```

- Before production readiness, development remains in the `0.x` range.
- The Maven version for active work uses `<next-version>-SNAPSHOT`.
- Completed pre-production milestones may be released as `0.MINOR.0` when a deliberate release is
  created.
- `1.0.0` is reserved for the first production-ready release.
- After `1.0.0`, increment `PATCH` for compatible fixes, `MINOR` for compatible functionality, and
  `MAJOR` for incompatible public-contract changes.

Milestone numbers and version numbers are related by the current roadmap but are not permanently
coupled. After Milestone 5, choose versions according to compatibility and release intent rather than
assuming every planning milestone must increment the minor number.

## Independent version systems

Do not conflate the application version with these independent contracts:

- `/api/v1` is the HTTP API generation and changes only when the public API compatibility policy
  requires another generation.
- Flyway versions are immutable timestamped database-history identifiers. Never rename or rewrite an
  applied migration to match an application release.
- Audit payload schema versions describe serialized audit compatibility.
- Dependency versions describe third-party components and do not determine the application version.

## Changelog contract

`CHANGELOG.md` is the canonical human-readable release ledger.

Every notable change must update the `[Unreleased]` section in the same change set. Notable changes
include:

- user-visible or client-visible behavior,
- HTTP endpoints, request/response contracts, status codes, or authorization behavior,
- durable schema, migration, or data-semantics changes,
- security, privacy, audit, and observability behavior,
- deployment, configuration, compatibility, or operational requirements,
- dependency/platform upgrades with meaningful runtime or build impact,
- completed milestones and material architecture decisions,
- important fixes, including concurrency and data-integrity fixes.

Pure formatting, comment-only cleanup, generated-file refreshes with no contract change, and
test-only refactoring normally do not need an entry. If a test exposes or documents a meaningful
behavioral guarantee, record that guarantee rather than the test mechanics.

Use Keep a Changelog headings where applicable:

```text
Added
Changed
Deprecated
Removed
Fixed
Security
```

Write entries for users, client developers, operators, and maintainers. Describe outcomes rather
than commit mechanics. Do not include secrets, private message content, transient debugging data, or
machine-specific paths.

A change that requires a changelog entry is incomplete until the entry is present. Reviewers should
treat missing or inaccurate release notes as documentation drift.

## Development workflow

During normal development:

1. Keep `pom.xml` at the next intended version with the `-SNAPSHOT` suffix.
2. Add notable work under `[Unreleased]` as it is implemented.
3. Keep the changelog consistent with README status snapshots, milestone guides, ADRs, API/Postman
   contracts, and actual behavior.
4. Do not create a historical release section merely because a branch or pull request is merged.

Planning documentation may be recorded under `[Unreleased]` when it establishes a material contract
that guides implementation. The eventual implementation entry should describe the delivered
capability and replace or refine planning-only wording.

## Release workflow

For an intentional application release:

1. Confirm `[Unreleased]` accurately covers all notable changes since the previous release.
2. Select the Semantic Versioning increment based on compatibility and release intent.
3. Change the Maven project version from `X.Y.Z-SNAPSHOT` to `X.Y.Z`.
4. Move the relevant `[Unreleased]` entries into `## [X.Y.Z] - YYYY-MM-DD`.
5. Leave a new empty `[Unreleased]` section at the top.
6. Run the canonical repository validation, including `./mvnw clean verify`.
7. Commit the release metadata and create an annotated Git tag named `vX.Y.Z` from that commit.
8. Build and publish deployable artifacts from the tagged commit, using both the version and source
   revision as artifact metadata.
9. After the release, advance `pom.xml` to the next intended `-SNAPSHOT` version in a separate change.

Do not create retroactive tags for the reconstructed `0.0.1` through `0.4.0` changelog snapshots.

## Current version baseline

- Reconstructed Milestone 0 snapshot: `0.0.1`
- Reconstructed Milestone 1 snapshot: `0.1.0`
- Reconstructed Milestone 2 snapshot: `0.2.0`
- Reconstructed Milestone 3 snapshot: `0.3.0`
- Reconstructed Milestone 4 snapshot: `0.4.0`
- Milestone 5 development baseline: `0.5.0-SNAPSHOT`
- Milestone 6 development baseline: `0.6.0-SNAPSHOT`
- Completed Milestone 7 development: `0.7.0-SNAPSHOT`
- Completed Milestone 8 development: `0.8.0-SNAPSHOT`
- Completed Milestone 9 development baseline: `0.9.0-SNAPSHOT`
- First production-ready release: `1.0.0`

The reconstructed versions document logical historical boundaries only. Repository history remains
the authority for the commits that actually existed before formal release management began.

Post-Milestone enhancements are organized as Evolution Tracks under
`docs/platform-evolution-specification.md`. Track identifiers such as `ET-01` are planning identifiers,
not versions. Choose application versions from compatibility and release intent; do not derive a
version number from an Evolution Track number.
