# ChatBackend Platform Evolution

## Enhancement Specification, Track Register, and Planning Standard

**Document version:** 0.1  
**Status:** Authoritative post-Milestone enhancement planning baseline  
**Last reviewed:** 2026-08-29  
**Applies after:** Completed Milestones 0–9  
**Production boundary:** Milestone X remains the separate, paused production-activation program

---

## 1. Purpose

This document is the canonical system of record for enhancements considered after completion of the
original ChatBackend implementation roadmap. It preserves the completed platform baseline while
providing a disciplined way to explore new backend capabilities without treating every idea as an
implementation commitment.

The original roadmap continues to live in
`docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`. Its numbered milestones culminate
in the deferred Milestone X production activation. New platform capabilities are organized here as
**Evolution Tracks**, not as additional milestones.

An Evolution Track is a bounded platform outcome that can be independently investigated, audited,
planned, implemented, verified, and released. When a track becomes ready for implementation, this
specification is expected to produce a detailed development guide under `docs/development-guide/`.

This document intentionally does not implement a client application or prescribe client-specific
screens, workflows, storage technologies, or presentation details. It records platform capabilities
that could enable higher-quality experiences for any compatible client.

---

## 2. Product and Deployment Context

Future planning must remain proportional to the actual project:

- ChatBackend is a personal project for private use by a small, closed group.
- The expected transaction volume and concurrent-user count are low.
- The intended deployment is one on-premises physical computer or server.
- The platform must not require SaaS or cloud infrastructure to provide its core capabilities.
- Operational simplicity, maintainability, and recoverability take precedence over hypothetical
  high-scale designs.
- A variety of client implementations may consume the same HTTP and WebSocket contracts, but client
  applications are outside this repository's implementation scope.
- Separate installations may reuse the platform independently; multi-tenancy within one deployment
  is not a product goal.

These constraints are architectural inputs, not temporary omissions. A track must not introduce
distributed infrastructure, multi-region assumptions, or enterprise-scale machinery without
measured evidence and an explicit scope decision.

---

## 3. Existing Platform Invariants

Every Evolution Track must preserve or deliberately revise these established invariants through an
ADR:

1. SQL Server is authoritative for durable relational and messaging state.
2. HTTP commands perform durable mutations; WebSockets carry non-authoritative delivery signals and
   limited commands that delegate to durable application services.
3. Missed socket events are recovered through durable HTTP reconciliation.
4. Authentication identifies the actor; APIs do not accept redundant actor identity when the access
   token already establishes it.
5. Private-resource authorization avoids leaking resource existence.
6. Message send semantics remain idempotent and conversation sequencing remains monotonic.
7. Forward-only Flyway migrations preserve already-applied database history.
8. RabbitMQ supports asynchronous processing and audit delivery but is not the authoritative store
   for messages or media.
9. The application remains a modular monolith unless a demonstrated need justifies a different
   boundary.
10. The hardened single-host model and least-privilege principals remain the deployment baseline.

---

## 4. Enhancement Priorities

Enhancement decisions use the following order.

### 4.1 Platform capability and UX potential

First determine whether the backend capability could materially improve usability, reliability,
clarity, accessibility, or expressive power for multiple kinds of client. This is platform-oriented
UX enablement, not client design.

Questions include:

- Does the platform expose enough durable state for clients to behave consistently?
- Does it reduce guesswork, excessive polling, or client-specific reconstruction?
- Does it support a meaningful communication capability missing from the current backend?
- Can web, mobile, desktop, and other clients consume the same contract?

### 4.2 Proportional performance

Next ensure the design is efficient for a low-volume, single-host deployment. Optimize measured or
structurally obvious hot paths, but do not build for speculative scale.

Preferred techniques include bounded requests, cursor pagination, streaming where valuable,
database indexes justified by query shape, asynchronous processing for genuinely expensive work,
and efficient local file transfer. Caching, horizontal scaling, distributed object storage, CDNs,
and clustered processing require evidence before adoption.

### 4.3 Security acceptance gate

Security is the mandatory gate on the chosen capability and performance design. It must prevent the
enhancement from weakening authentication, authorization, privacy, durability, abuse resistance,
auditability, or recovery.

Heavy cryptographic features such as end-to-end encryption and client-managed key ecosystems are
explicitly deferred. Existing transport protection, password hashing, encrypted backups, secret
handling, least privilege, and resource authorization remain required.

---

## 5. Scope Boundaries

### 5.1 In scope

- Backend domain and API capabilities that can benefit compatible clients
- Durable data models, synchronization contracts, and recovery behavior
- Self-hosted file and media support
- Self-hosted real-time signaling and, where justified, media relay components
- Measured single-host performance improvements
- Proportional security controls required by each enhancement
- Postman, automated tests, observability, migration, operations, and documentation changes needed
  to keep an enhancement supportable

### 5.2 Out of scope unless explicitly promoted

- Client UI, visual design, navigation, or local persistence implementation
- Browser-, mobile-, or desktop-framework selection
- SaaS or cloud-only dependencies
- Multi-tenancy
- Multi-region deployment
- Multi-instance real-time distribution
- Kubernetes or orchestration introduced for hypothetical load
- End-to-end encryption, per-device key exchange, encrypted search, or recovery-key ecosystems
- Enterprise moderation, compliance, or retention systems not required by the closed-group use case
- Production activation work already tracked by Milestone X

---

## 6. Evolution Track Model

### 6.1 Identifier and name

Tracks use stable identifiers such as `ET-01` and a concise capability name. Track identifiers do not
imply an application version or mandatory implementation order.

Detailed guides should use names such as:

```text
docs/development-guide/evolution-track-01-conversation-navigation-and-sync.md
```

### 6.2 Lifecycle

Each track has one of these states:

- **Candidate** — valuable enough to retain, but not audited or scheduled.
- **Discovery** — repository evidence, requirements, and alternatives are being investigated.
- **Ready for guide** — scope decisions are resolved and a detailed implementation guide may be
  produced.
- **Planned** — an audited, implementation-ready development guide exists.
- **In progress** — implementation has begun.
- **Verified** — implementation and required evidence satisfy the track exit criteria.
- **Deferred** — intentionally retained with no current implementation priority.
- **Rejected** — considered and intentionally excluded, with rationale recorded.
- **Superseded** — replaced by another track or decision.

### 6.3 Promotion rule

A Candidate is not a promise. Before a track becomes Ready for guide, its discovery record must:

1. verify current code, schema, API, test, configuration, and documentation behavior;
2. identify the specific platform outcome and non-goals;
3. resolve material domain, API, persistence, transaction, concurrency, and recovery semantics;
4. define proportional performance expectations;
5. define security and privacy acceptance gates;
6. identify migrations, configuration, operations, Postman, and compatibility implications;
7. record dependencies on other tracks;
8. identify genuine stakeholder decisions;
9. state measurable exit criteria.

---

## 7. Development Guide Contract

When a track is promoted, its development guide must be derived from current repository evidence and
this specification. It must not merely expand the candidate summary.

Every guide must contain:

1. executive outcome and track status;
2. verified current baseline with file and line evidence;
3. goals, non-goals, assumptions, and explicit decisions;
4. domain model and invariants;
5. API and WebSocket contracts;
6. SQL Server schema, indexes, migration, and transaction design;
7. asynchronous processing and failure recovery where applicable;
8. authorization, privacy, abuse, and audit requirements;
9. proportional performance design and budgets;
10. backward compatibility and rollout behavior;
11. ordered implementation steps small enough to review safely;
12. exact unit, integration, migration, concurrency, Postman, and operational tests;
13. documentation and client-responsibility updates;
14. completion checklist and evidence requirements.

Before implementation, the guide must receive the same skeptical repository-backed audit used for
Milestone 9. Audit findings must be resolved in the guide rather than left only in chat context.

---

## 8. Candidate Evolution Tracks

The register below captures current ideas. Ordering reflects present platform value and dependency
logic, not a commitment to implement every track.

### ET-01 — Conversation Navigation and Synchronization

**Status:** Candidate  
**Potential value:** High  
**Dependencies:** None identified; repository audit required

Explore backend support for consistently ordered conversation retrieval, last-activity metadata,
last-message summaries, unread state, stable pagination, and bounded incremental reconciliation.
The platform should expose durable facts without prescribing how a client renders an inbox.

Discovery must first verify which of these capabilities already exist and whether current message and
conversation sequencing can support them without a speculative read model.

### ET-02 — Durable Media and Attachment Foundation

**Status:** Candidate  
**Potential value:** High  
**Dependencies:** None

Introduce one generic, authorized lifecycle for images, documents, audio files, voice-note media, and
pre-recorded video. SQL Server should remain authoritative for metadata and message association;
binary content should live in persistent on-premises file storage rather than message rows,
RabbitMQ, audit payloads, or WebSocket frames.

The initial design should prefer a mounted filesystem and simple single-host operations. It should
cover pending uploads, completion verification, checksums, bounded size and type rules, authorized
download, message association, deletion/retention behavior, orphan cleanup, backup, and recovery.
An S3-compatible service is not required unless later evidence makes it valuable.

### ET-03 — Voice Notes and Media Semantics

**Status:** Candidate  
**Potential value:** High  
**Dependencies:** ET-02

Model voice notes as a conversational message semantic distinct from a generic audio attachment.
A voice note can use the common attachment storage machinery while retaining a durable message kind,
duration and format metadata, optional waveform-processing state, and forwarding provenance.

At minimum, the contract must let clients distinguish:

- a voice note recorded for the current conversation;
- a voice note forwarded through the platform; and
- an arbitrary audio file shared as an attachment.

The server may validate compatible audio metadata but cannot prove the physical recording source.
Forwarding metadata must not leak inaccessible source-conversation details.

### ET-04 — Rich Message Relationships

**Status:** Candidate  
**Potential value:** Medium to high  
**Dependencies:** Repository audit; coordinate with ET-02 and ET-03

Explore replies, quoted context, reactions, and explicit forwarding as durable message-domain
capabilities. The design must preserve authorization across membership changes, avoid cross-
conversation information disclosure, and define edit/delete effects on related messages.

### ET-05 — Message and Conversation Search

**Status:** Candidate  
**Potential value:** Medium  
**Dependencies:** Durable message baseline; coordinate with ET-02 for attachment metadata

Explore authorized SQL Server-backed search appropriate to a small private deployment. Begin with
simple, bounded server-side search and measured indexes. Do not assume a separate search cluster or
external service.

Search must respect current membership and private-resource behavior, define treatment of edited and
deleted content, and avoid leaking matches through counts, timing, or attachment metadata.

### ET-06 — Ephemeral Conversation Signals

**Status:** Candidate  
**Potential value:** Medium  
**Dependencies:** Existing WebSocket signaling baseline

Explore typing, presence, and other intentionally non-durable signals. These signals must remain
best-effort, bounded, rate-limited, and clearly separate from SQL-authoritative message, delivery,
and read state. Durable last-seen behavior, if desired, requires an explicit privacy decision rather
than being implied by socket connectivity.

### ET-07 — Self-Hosted Voice and Video Calling

**Status:** Candidate  
**Potential value:** Medium  
**Dependencies:** Existing authenticated WebSocket baseline; scope decision for one-to-one versus
group calling

Explore platform-authorized WebRTC call setup. ChatBackend should own call authorization, invitations,
signaling, lifecycle state, and optional call-history events. Live audio/video must not be transported
through ordinary REST bodies, RabbitMQ messages, SQL Server, or the existing application event
frames.

For the personal single-host use case, prefer peer-to-peer media. A self-hosted TURN relay should be
added only when actual network traversal requires it. An SFU, call recording, and large group calling
remain separate scope decisions and must not be assumed.

### ET-08 — Notification and Attention Controls

**Status:** Candidate  
**Potential value:** Medium  
**Dependencies:** Conversation and event model audit

Explore durable mute or notification-preference state and platform notification events without
implementing client-specific notification delivery. Reliance on proprietary mobile push services is
not part of the self-hosted core platform.

### ET-09 — Targeted Single-Host Performance Improvements

**Status:** Candidate and cross-cutting  
**Potential value:** Determined by measurement  
**Dependencies:** Concrete workload or another Evolution Track

Maintain repeatable budgets for message operations, conversation retrieval, history, reconciliation,
WebSocket dispatch, file transfer, and media processing. Optimize only measured regressions or query
shapes introduced by a promoted track. Prefer indexes, batching, streaming, bounded concurrency, and
slow-consumer isolation before caches or distributed infrastructure.

### ET-D1 — Advanced Content Encryption

**Status:** Deferred  
**Potential value:** Revisit only if the trust model changes  
**Dependencies:** Explicit product and key-management decisions

End-to-end encryption, per-device keys, encrypted attachments, multi-device key synchronization,
recovery keys, encrypted search, and server-blind content are deliberately low priority for the
closed-group deployment. No active track should introduce speculative cryptographic abstractions to
prepare for this work.

The existing TLS, password hashing, encrypted backup, secret handling, least-privilege, and
authorization controls remain mandatory.

---

## 9. Shared Media Architecture Direction

The following direction is provisional but constrains discovery toward a proportionate solution.

### 9.1 Control plane and data plane

ChatBackend remains the control plane for authorization, durable metadata, message association,
processing state, and lifecycle decisions. Binary file transfer and live media transport are data-
plane concerns.

- Durable media bytes belong in private persistent on-premises storage.
- SQL Server stores identifiers, ownership and conversation association, checksums, sizes, types,
  status, and relevant media metadata.
- WebSockets announce durable state changes but do not carry file bodies.
- RabbitMQ may schedule occasional processing but does not become durable media storage.
- NGINX or ChatBackend may serve authorized files; the detailed guide must choose the simplest secure
  approach and prove that authorization cannot be bypassed.

### 9.2 Media processing

Thumbnail generation, duration extraction, waveform generation, or video conversion may run
asynchronously when needed. A track must not require a processing cluster, GPU, or broad format
matrix for the expected personal workload.

### 9.3 Live calls

WebRTC signaling may use authenticated platform channels, while media uses peer-to-peer transport or
a separately operated on-premises relay. Call media is not durable unless a future, explicit recording
feature is approved with consent, privacy, retention, and storage requirements.

---

## 10. Cross-Cutting Acceptance Gates

Every implemented track must satisfy the gates that apply to its scope.

### 10.1 Capability gate

- The platform contract is client-independent.
- Durable and ephemeral state are unambiguous.
- Offline, retry, idempotency, ordering, and reconciliation behavior are defined.
- Error and partial-failure behavior is actionable without revealing private resources.

### 10.2 Performance gate

- Requests, payloads, pagination, file sizes, and concurrency are bounded.
- New queries have reviewed plans and justified indexes where necessary.
- Expensive processing does not block unrelated message operations.
- Validation uses the personal single-host workload, not invented high-volume targets.

### 10.3 Security and privacy gate

- Conversation membership and resource authorization cover every operation.
- File paths, names, media types, and sizes cannot bypass storage boundaries.
- Cross-conversation relationships do not disclose inaccessible metadata.
- Rate, storage, and processing limits prevent straightforward resource exhaustion.
- Logs and durable audit records exclude message bodies, file contents, tokens, and sensitive query
  values.
- Threat-model changes and accepted residual risks are documented.

### 10.4 Durability and recovery gate

- SQL transactions and asynchronous handoffs have explicit failure semantics.
- Backup and restore include any new durable metadata and file storage.
- Orphaned, rejected, deleted, and partially processed content have deterministic cleanup behavior.
- Reconnect and reconciliation remain correct when real-time events are missed.

### 10.5 Repository completion gate

- Canonical tests and feature-specific tests pass.
- Postman contracts and human-run flows are updated where APIs change.
- OpenAPI, migrations, configuration, runbooks, ADRs, and threat model are aligned.
- `CHANGELOG.md` records the notable change.
- The client responsibility and recovery guide is audited for new consumer obligations.

---

## 11. Decision and Prioritization Method

Before choosing the next track, compare candidates using:

1. platform capability value for the closed group;
2. reuse across different client types;
3. dependency and migration cost;
4. failure and recovery complexity;
5. single-host CPU, storage, and bandwidth impact;
6. security and privacy risk introduced;
7. ongoing operational burden;
8. reversibility and compatibility.

Prefer the smallest coherent capability that establishes a reusable foundation. Avoid combining
unrelated features merely to create a larger release. Related capabilities should share a track only
when they genuinely share domain, persistence, API, processing, and verification requirements.

---

## 12. Governance and Maintenance

- This file is authoritative for the post-Milestone enhancement register, shared assumptions,
  priorities, and track status.
- Detailed implementation facts belong in track development guides, ADRs, API specifications, and
  operational runbooks rather than being duplicated here.
- Update this register when a candidate is added, materially re-scoped, promoted, deferred, rejected,
  superseded, or verified.
- Record notable planning and implementation changes in `CHANGELOG.md`.
- Keep application versions independent from Evolution Track identifiers.
- Keep Milestone X production activation separate unless a stakeholder explicitly resumes it.
- Preserve multi-agent instruction parity when routing or completion rules change.
- A track is not complete based only on code; its evidence, canonical documentation, Postman impact,
  client-responsibility audit, and operational implications must also be resolved.

---

## 13. Immediate Next Planning Step

No Evolution Track is currently selected for implementation. The next planning action should be a
repository-backed comparison of the highest-value candidates, followed by explicit selection of one
track for Discovery. Only then should an implementation guide be drafted.

