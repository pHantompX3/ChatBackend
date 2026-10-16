# ChatBackend Platform Evolution

## Product Strategy, Enhancement Specification, Track Register, and Planning Standard

**Document version:** 0.3
**Status:** Authoritative post-Milestone enhancement roadmap
**Last reviewed:** 2026-09-02
**Applies after:** Completed Milestones 0–9
**Production boundary:** Milestone X remains the separate, paused production-activation program
**Product audit basis:**
`docs/audit/platform-evolution-product-and-customer-experience-audit-2026-08-29.md`

---

## 1. Purpose and Authority

This document is the canonical system of record for enhancements considered after completion of the
original ChatBackend implementation roadmap. It preserves the completed platform baseline while
providing a disciplined product and engineering framework for evaluating new backend capabilities.
An idea recorded here is not an implementation commitment.

The original roadmap remains in
`docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`. Its numbered milestones culminate
in the deferred Milestone X production activation. New platform capabilities are organized here as
**Evolution Tracks**, not as additional milestones.

An Evolution Track is a bounded platform outcome that can be independently investigated, audited,
planned, implemented, verified, and released. When promoted, it produces an implementation-ready
development guide under `docs/development-guide/`.

This specification owns:

- stable product and deployment context;
- platform experience principles;
- Evolution Track definitions, status, dependencies, and preliminary estimates;
- portfolio comparison and recommended sequencing;
- prioritization and stakeholder decision rules; and
- common promotion and completion gates.

The product and customer-experience audit records why version 0.2 changed. Development guides own
implementation details for promoted tracks. ADRs own architecture decisions. The client integration
guide owns verified consumer responsibilities and recovery behavior. These documents must link rather
than become competing systems of record.

This repository does not implement a client or prescribe client-specific screens, navigation, local
storage, or presentation. It defines platform capabilities that allow compatible clients to offer a
high-quality experience consistently.

---

## 2. Product and Deployment Context

Future planning must remain proportional to the actual project:

- ChatBackend is a personal project for private use by a small, closed group.
- Expected transaction volume and concurrent-user count are low.
- The intended deployment is one on-premises physical computer or server.
- Core capabilities must not require SaaS or cloud infrastructure.
- Operational simplicity, maintainability, recoverability, and understandable failure behavior take
  precedence over hypothetical high-scale designs.
- Web, mobile, desktop, and other compatible clients may share the same HTTP and WebSocket contracts,
  while remaining outside this repository.
- Separate installations may reuse the platform independently; multi-tenancy within one deployment
  is not a product goal.
- The primary stakeholder owns final scope and priority decisions.

These constraints are architectural inputs. A track must not introduce distributed infrastructure,
multi-region assumptions, enterprise-scale machinery, or third-party service dependence without
measured evidence and an explicit stakeholder decision.

---

## 3. Platform Experience Principles

Future work is evaluated by the experience it enables across client types.

### 3.1 Trustworthy

Once the server durably accepts an operation, the platform must not silently lose it. Authoritative
state, retry behavior, and failure outcomes must be unambiguous.

### 3.2 Recoverable

A compatible client must be able to converge after restart, disconnection, a missed socket frame, or
an ambiguous response without rebuilding unlimited history or inventing server state.

### 3.3 Understandable

The platform should expose enough durable facts to distinguish pending, accepted, delivered, read,
edited, deleted, expired, inaccessible, and failed states without implying facts it cannot prove.

### 3.4 Expressive

The domain should support useful forms of private communication—text, relationships, files, images,
voice notes, video, and eventually live audio/video—without encoding presentation decisions.

### 3.5 Calm and controllable

Users should be able to control attention, interruption, personal organization, and permitted
interaction through durable platform state rather than inconsistent per-client guesses.

### 3.6 Accessible

Contracts should carry semantic metadata that allows clients to build accessible experiences, such
as meaningful filenames, media duration and dimensions, captions, alternative descriptions,
language, and processing state where appropriate.

### 3.7 Portable and client-independent

Capabilities and recovery rules should behave consistently across compatible client implementations.
Clients should discover supported capabilities and limits instead of relying on hard-coded platform
assumptions. Under ADR-0019, the initial public edge authenticates users rather than attesting client
software. The separate Infrastructure Evolution sequence owns native trust in IE-01, browser-pairing
protocol support in IE-02, and the official web companion in IE-03.

### 3.8 Self-hostable and proportionate

The complete core experience must remain practical to operate, back up, restore, and understand on
the intended single host.

### 3.9 Public access, client trust, and deployment independence

Enhancements must preserve ADR-0019's public authenticated edge and private internal-service
boundary. They must not infer frontend authenticity from spoofable metadata or shared embedded
secrets. The Infrastructure Evolution specification owns the required future native trust,
linked-browser protocol, and official web-companion work in IE-01 through IE-03. Platform tracks must
cross-reference those boundaries rather than duplicate their security protocols or client delivery.

Do not infer federation, cross-deployment messaging, public delegated-client registration, or shared
administrative authority. Independently operated deployments retain separate identity, data, secrets,
recovery, and operations.

---

## 4. Existing Platform Invariants

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
8. RabbitMQ supports asynchronous processing and audit delivery but is not authoritative storage for
   messages or media.
9. Production exposes only the hardened HTTPS/WSS edge; user authentication and authorization are
   initially authoritative, IE-01 through IE-03 own future native and linked-browser trust, and
   independently operated deployments remain isolated.
10. The application remains a modular monolith unless a demonstrated need justifies another boundary.
11. The hardened single-host model and least-privilege principals remain the deployment baseline.
12. Durable files are included in backup, restore, retention, and capacity planning alongside SQL
    metadata.
13. A best-effort realtime signal never replaces a durable recovery contract.

---

## 5. Enhancement Priorities

Enhancement decisions proceed in this order.

### 5.1 Establish platform value

Determine whether the capability materially improves reliability, clarity, recovery, accessibility,
expressive power, attention control, or consistency across clients.

Questions include:

- Which platform experience principles does it advance?
- What current user effort, uncertainty, or client workaround does it remove?
- Is the beneficiary the whole closed group, a conversation participant, or an operator?
- Can multiple client types consume the same contract?
- Does it create a reusable foundation or only an isolated visible feature?

### 5.2 Compare value with effort and elapsed time

High value alone does not establish priority. Compare it with implementation effort, realistic
calendar time, confidence, dependencies, operational burden, risk, and reversibility using section
9. The value-to-effort ratio is a signal, not an automated decision.

### 5.3 Apply proportional performance

Ensure the design is efficient for a low-volume, single-host deployment. Optimize measured or
structurally evident hot paths, not speculative scale.

Preferred techniques include bounded requests, cursor pagination, streaming where valuable,
database indexes justified by query shape, asynchronous processing for genuinely expensive work,
and efficient local file transfer. Caching, horizontal scaling, distributed object storage, CDNs,
and clustered processing require evidence before adoption.

### 5.4 Apply security and privacy as acceptance gates

Security gates the chosen capability and performance design. It must prevent the enhancement from
weakening authentication, authorization, privacy, durability, abuse resistance, auditability, or
recovery.

End-to-end encryption and client-managed key ecosystems remain deferred. Existing TLS, password
hashing, encrypted backups, secret handling, least privilege, and private-resource authorization
remain mandatory.

---

## 6. Scope Boundaries

### 6.1 In scope

- Backend capabilities that can benefit compatible clients
- Durable domain models, synchronization contracts, and recovery behavior
- Self-hosted file and media support
- Self-hosted realtime signaling and justified media relay components
- Measured single-host performance improvements
- Proportional security controls required by each enhancement
- Accessibility-enabling platform metadata
- Postman, automated tests, observability, migration, operations, and documentation changes needed
  to keep an enhancement supportable

### 6.2 Out of scope unless explicitly promoted

- Client UI, visual design, navigation, or local persistence implementation
- Browser-, mobile-, or desktop-framework selection
- SaaS or cloud-only dependencies
- Multi-tenancy, federation, public channels, communities, or social discovery
- Multi-region deployment or multi-instance realtime distribution
- Kubernetes or orchestration introduced for hypothetical load
- Bots, public plugin ecosystems, or third-party media-provider ecosystems
- Public/delegated client registration, open Internet API ecosystems, or OAuth-style frontend grants
- Federation, shared identities, or messaging between independently operated deployments
- Per-device client-certificate installation or frontend binary attestation
- End-to-end encryption, per-device key exchange, encrypted search, or recovery-key ecosystems
- Enterprise moderation, compliance, analytics, or retention systems not required by the closed group
- Production activation work already tracked by Milestone X

---

## 7. Evolution Track Model

### 7.1 Identifier and name

Tracks use stable identifiers such as `ET-01`. Identifiers do not represent versions, priority, or
mandatory order. A renamed, rejected, deferred, or superseded track retains its identifier so history
remains understandable.

Detailed guides use names such as:

```text
docs/development-guide/evolution-track-01-durable-account-sync.md
```

### 7.2 Lifecycle

- **Candidate** — valuable enough to retain, but not audited or scheduled.
- **Discovery** — repository evidence, outcomes, requirements, estimates, and alternatives are being
  investigated.
- **Ready for guide** — material scope decisions are resolved and a detailed guide may be produced.
- **Planned** — an audited, implementation-ready development guide exists.
- **In progress** — implementation has begun.
- **Verified** — implementation and required evidence satisfy exit criteria.
- **Deferred** — intentionally retained with no current implementation priority.
- **Rejected** — intentionally excluded, with rationale.
- **Superseded** — replaced by another track or cross-cutting mechanism.

### 7.3 Required track metadata

Each track entry or Discovery record must maintain:

- status and status rationale;
- intended beneficiary and problem or opportunity;
- experience principles advanced;
- goals, non-goals, and proposed capability boundary;
- dependencies and work unlocked;
- preliminary or validated value, effort, elapsed time, and confidence;
- risk, operational burden, and reversibility;
- recommended priority and rationale;
- primary stakeholder decision, including explicit delegation where applicable;
- last reviewed date; and
- next decision or evidence trigger.

### 7.4 Promotion rule

Before a track becomes Ready for guide, its Discovery record must:

1. verify current code, schema, API, test, configuration, and documentation behavior;
2. identify the specific outcome, beneficiary, principles advanced, and non-goals;
3. resolve material domain, API, persistence, transaction, concurrency, and recovery semantics;
4. validate value, effort, elapsed-time, confidence, dependency, risk, and reversibility estimates;
5. define proportional performance expectations;
6. define security, privacy, accessibility, and abuse acceptance gates;
7. identify migrations, configuration, operations, Postman, and compatibility implications;
8. record stakeholder decisions and unresolved questions;
9. define measurable platform and completion outcomes; and
10. obtain explicit stakeholder promotion or documented delegated authority.

---

## 8. Development Guide Contract

A promoted track guide must be derived from current repository evidence, this specification, and the
track's Discovery record. It must not merely expand a candidate summary.

Every guide must contain:

1. executive outcome, beneficiary, principles advanced, and track status;
2. verified baseline with file and line evidence;
3. goals, non-goals, assumptions, and explicit decisions;
4. validated value, effort, elapsed-time, confidence, dependencies, and risk assessment;
5. domain model and invariants;
6. API, capability-discovery, and WebSocket contracts;
7. SQL Server schema, indexes, migration, and transaction design;
8. asynchronous processing and failure recovery where applicable;
9. authorization, privacy, abuse, accessibility, and audit requirements;
10. proportional performance design and budgets;
11. backward compatibility, rollout, reversibility, and rollback behavior;
12. ordered implementation steps small enough to review safely;
13. exact unit, integration, migration, concurrency, Postman, and operational tests;
14. outcome measures and acceptance criteria;
15. documentation and client-responsibility updates; and
16. completion checklist and evidence requirements.

Before implementation, the guide must receive a skeptical repository-backed audit. Findings must be
resolved in the guide rather than retained only in chat context.

---

## 9. Estimation, Comparison, and Stakeholder Decision Method

### 9.1 Rating scale

| Rating | Value | Effort | Indicative elapsed time |
|---:|---|---|---|
| 1 | Minor improvement | Very small | Hours to a few days |
| 2 | Useful improvement | Small | Several days |
| 3 | Material improvement | Moderate | Roughly one to two weeks |
| 4 | High platform value | Large | Several weeks |
| 5 | Foundational or transformational | Very large | Extended or multi-track work |

Effort includes domain, API, schema, migration, testing, security, operations, Postman, and
documentation. Elapsed time is separate because review, stakeholder availability, manual validation,
operations rehearsal, and dependencies can extend calendar time.

### 9.2 Confidence

- **High:** Current behavior and likely implementation shape are well understood.
- **Medium:** The boundary is credible, but Discovery may materially change details or estimates.
- **Low:** Important domain, dependency, operational, or feasibility decisions remain unresolved.

Ranges should be used instead of false precision. Low confidence favors Discovery, not implementation.

### 9.3 Comparison signal

```text
value-to-effort signal = value / effort
```

The signal helps compare opportunities but never authorizes work. A lower-ratio foundation may still
take precedence because it removes future cost or unlocks high-value dependent work. A high-ratio
minor feature must not continually displace foundational recovery work.

Every decision also considers:

- elapsed time and stakeholder availability;
- estimate confidence;
- prerequisites and work unlocked;
- recovery and operational complexity;
- security and privacy acceptance;
- compatibility and reversibility; and
- whether the scope can be split into a smaller coherent outcome.

### 9.4 Decision authority

The primary stakeholder makes the final decision to promote, defer, reject, split, or reorder work.
The stakeholder may explicitly delegate that decision. When delegated, the responsible actioner must:

1. recommend the most logically sensible option using the complete comparison;
2. favor foundational capabilities when candidates are otherwise comparable;
3. avoid treating the numerical signal as an automatic answer;
4. identify uncertainty and request Discovery where estimates are weak; and
5. record the decision and rationale in this specification or the applicable Discovery record.

---

## 10. Portfolio Summary and Preliminary Comparison

These estimates guide selection for Discovery. They are not implementation estimates or stakeholder
approval.

| Recommended order | Initiative | Status | Value | Effort | Time | Confidence | Primary dependency |
|---:|---|---|---:|---:|---|---|---|
| 1 | ET-10 Platform Capabilities and Limits | Candidate | 4 | 1 | Days | High | None |
| 2 | ET-01 Durable Account Synchronization and Conversation Navigation | Candidate | 5 | 3 | 1–3 weeks | Medium | None |
| 3 | ET-11 Identity and Conversation Profiles | Candidate | 4 | 2 | 1–2 weeks | Medium | ET-02 for profile media only |
| 4 | ET-12 Personal Organization and Attention Controls | Candidate | 4 | 2–3 | 1–3 weeks | Medium | ET-01 audit |
| 5 | ET-02 Durable Media and Attachment Foundation | Candidate | 5 | 4 | 3–6 weeks | Medium-low | None |
| 6 | ET-03 Voice Notes and Media Semantics | Candidate | 4 | 2 after ET-02 | 1–2 weeks | Medium | ET-02 |
| 7 | ET-04 Rich Message Relationships | Candidate | 4 | 3 | 2–4 weeks | Medium | Message audit; coordinate ET-02/03 |
| 8 | ET-05 Message and Conversation Search | Candidate | 3 | 3 | 2–4 weeks | Medium-low | Stable message/media semantics |
| 9 | ET-13 User Interaction Controls | Candidate | 3 | 2–3 | 1–3 weeks | Medium-low | Identity/conversation policy audit |
| 10 | ET-08 Notification and Attention Events | Candidate | 3 | 2 | 1–2 weeks | Medium | ET-12 preference state |
| 11 | ET-06 Ephemeral Conversation Signals | Candidate | 2–3 | 1–2 | Days–1 week | High | Existing WebSocket baseline |
| 12 | ET-17 Data Portability | Candidate | 3 | 3 | 2–4 weeks | Low | Media lifecycle if files included |
| 13 | ET-15 Structured Group Tools | Candidate | 3 | 3 | 2–4 weeks | Low | ET-04 relationship patterns |
| 14 | ET-14 Message Retention and Ephemeral Content | Candidate | 3 | 4 | 3–6 weeks | Low | ET-01 and ET-02 where applicable |
| 15 | ET-16 Session and Device Visibility | Candidate | 2–3 | 2 | 1–2 weeks | Medium | Session audit; coordinate IE-01/IE-02 |
| 16 | ET-07 Self-Hosted Live Calling | Candidate | 4 | 5 | 4–8+ weeks | Low | Authenticated realtime baseline |
| 17 | ET-D1 Advanced Content Encryption | Deferred | 2 currently | 5 | Extended | Medium | Changed trust model |
| — | ET-09 Targeted Performance Track | Superseded | — | — | — | — | Replaced by QW-01 |

The audit's complete reasoning is preserved in
`docs/audit/platform-evolution-product-and-customer-experience-audit-2026-08-29.md`.

---

## 11. Detailed Evolution Track Register

### ET-10 — Platform Capabilities and Limits

**Status:** Candidate
**Intended beneficiary:** Every compatible client and its implementers
**Principles:** Understandable, portable, self-hostable
**Preliminary assessment:** Value 4; effort 1; days; high confidence
**Dependencies:** None
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected; first recommended Discovery candidate

**Problem:** Clients otherwise infer supported features and limits from errors, documentation, or
hard-coded assumptions, making compatibility and optional rollout unnecessarily fragile.

**Outcome:** Provide a small machine-readable contract describing server/API and realtime protocol
versions, enabled capabilities, supported message/media kinds, relevant payload or upload limits, and
whether optional features such as search, calls, and media processing are enabled.

**Boundary:** This is not a dynamic plugin framework or general-purpose negotiation protocol. It may
be delivered as a coherent early slice of ET-01 if Discovery confirms that this reduces duplication.

**Discovery focus:** Public versus authenticated facts, cache semantics, backward-compatible field
addition, configuration ownership, OpenAPI representation, and how clients distinguish unsupported
from temporarily unavailable capabilities.

### ET-01 — Durable Account Synchronization and Conversation Navigation

**Status:** Candidate
**Intended beneficiary:** Conversation participants and every reconnecting client
**Principles:** Trustworthy, recoverable, understandable, portable
**Preliminary assessment:** Value 5; effort 3; 1–3 weeks; medium confidence
**Dependencies:** None; coordinate ET-10
**Work unlocked:** Organization, notification, search, retention, and richer message state
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected; first recommended major Discovery track

**Problem:** The platform lists conversations but does not expose inbox-ready last activity,
last-message summary, or aggregate unread facts. Forward message history cannot recover missed edits
or deletions to older messages, and membership or conversation changes have no realtime or durable
account-level change contract.

**Outcome:** Expose stable, bounded, authoritative facts that let clients restore and incrementally
reconcile the user's accessible conversation state without unlimited refreshes or speculative local
reconstruction.

**Candidate scope:**

- deterministic conversation ordering and stable pagination;
- last activity and privacy-safe last-message summary;
- aggregate unread and latest-sequence facts;
- bounded recovery for missed message edits and deletions;
- recovery for conversation metadata and membership changes;
- initial bootstrap versus incremental account synchronization; and
- event/reconciliation interaction when socket signals are missed.

**Non-goals:** Client inbox layout, cache schema, distributed event streaming, or a speculative general
event-sourcing framework.

**Discovery focus:** Whether to use an account-scoped change cursor, targeted revision endpoints, or a
simpler bounded synchronization summary; transactionally correct change ordering; membership privacy;
pagination under concurrent activity; and indexes justified by the chosen query shape.

### ET-11 — Identity and Conversation Profiles

**Status:** Candidate
**Intended beneficiary:** Every participant identifying people and conversations
**Principles:** Understandable, expressive, accessible, portable
**Preliminary assessment:** Value 4; effort 2; 1–2 weeks; medium confidence
**Dependencies:** ET-02 only for profile or conversation images
**Last reviewed:** 2026-09-02
**Stakeholder decision:** Candidate contract accepted for planning; implementation not selected

**Problem:** Current identity summaries expose stable IDs and usernames, while conversation summaries
expose title and role. They do not provide a shared platform basis for display identity or richer group
identity.

**Outcome:** Allow clients to present stable, human-readable participant and conversation identity
without tying login identity to presentation.

**Candidate scope:** Display name distinct from username; optional profile description; optional
profile and conversation images; group title, description, and image mutation; safe username-change
semantics; and durable identity-change reconciliation.

**Non-goals:** Contact import, address-book synchronization, public profiles, social graphs, or an
enterprise directory.

**Discovery focus:** Visibility rules, uniqueness, history after renaming, impersonation ambiguity,
image authorization, accessibility descriptions, and how profile changes reach clients.

### ET-12 — Personal Organization and Attention Controls

**Status:** Candidate
**Intended beneficiary:** A user organizing conversations consistently across clients
**Principles:** Calm and controllable, understandable, portable
**Preliminary assessment:** Value 4; effort 2–3; 1–3 weeks; medium confidence
**Dependencies:** ET-01 audit; coordinates ET-08
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected; split into independently valuable slices

**Problem:** Personal conversation state is otherwise reconstructed separately by each client, causing
pin, archive, mute, unread, and saved-item behavior to diverge across devices.

**Outcome:** Provide durable per-user organization and attention facts without prescribing client
navigation or notification rendering.

**Candidate slices:** Pin/favorite conversation; archive/unarchive; manual mark unread/read; durable
mute policy; starred/saved messages; and potentially clear/delete history for the current user.

**Non-goals:** Client folders, visual ordering mechanics, drafts, notification UI, or bundling every
slice into one release.

**Discovery focus:** Interaction with server unread cursors, ordering rules, direct/group parity,
multi-client consistency, deletion semantics, and whether saved messages belong here or in ET-04.

### ET-02 — Durable Media and Attachment Foundation

**Status:** Candidate
**Intended beneficiary:** Participants sharing images, documents, audio, and video
**Principles:** Trustworthy, recoverable, expressive, accessible, self-hostable
**Preliminary assessment:** Value 5; effort 4; 3–6 weeks; medium-low confidence
**Dependencies:** None
**Work unlocked:** ET-03, profile images, media search, ephemeral media, complete portability
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected

**Problem:** The platform has no authorized durable binary-content lifecycle. Placing bytes in SQL,
RabbitMQ, audit payloads, or WebSocket frames would weaken performance and operations.

**Outcome:** Introduce one generic, authorized attachment lifecycle with SQL-authoritative metadata
and persistent on-premises filesystem content.

**Resolved transfer contract:** Use a dedicated binary-transfer surface rather than embedding bytes
in message JSON, multipart message creation, RabbitMQ, or WebSocket frames. Adopt the mature tus 1.0
HTTP protocol for resumable uploads: create an upload resource, append bytes through offset-checked
`PATCH` requests, and recover the authoritative offset through `HEAD`. Limit each assembled
attachment to 50 MiB and each upload request body to 25 MiB. Require a declared total length at
creation; deferred or unknown-length uploads and tus concatenation are out of scope. Use the tus
expiration, checksum, and termination behavior needed for safe recovery and cleanup. Reassess the
IETF Resumable Uploads for HTTP draft immediately before implementation, but do not build against an
unstable draft merely because it may later become a standard.

The development guide should preserve the following explicit route families unless its repository
audit proves a necessary refinement:

- `OPTIONS /api/v1/attachment-uploads` — advertise supported tus version, extensions, and maximum;
- `POST /api/v1/attachment-uploads` — authorize and create one upload resource;
- `HEAD|PATCH|DELETE /api/v1/attachment-uploads/{uploadId}` — inspect offset, append one bounded
  binary chunk, or cancel;
- `GET /api/v1/attachments/{attachmentId}` — read authoritative metadata and lifecycle state; and
- `GET /api/v1/attachments/{attachmentId}/content` — stream authorized full or ranged bytes.

These are HTTP resources because HTTP is the interoperable transport through the existing public
edge, but they are not generic JSON message endpoints. Upload bodies use
`application/offset+octet-stream`; content downloads use the verified media type; message mutations
contain attachment identifiers only.

Authorized downloads stream from the on-premises content store and support the HTTP byte-range
contract (`Range`, `206 Partial Content`, `Accept-Ranges`, `Content-Range`, `ETag`, and `If-Range`) so
clients can seek and resume without loading an entire object into application memory. Authorization
is evaluated for every full or ranged request. WebSockets signal committed attachment/message state
only; they do not carry binary content.

**Candidate lifecycle and scope:** `PENDING`, `UPLOADING`, `VERIFYING`, `AVAILABLE`, `ABORTED`, and
`EXPIRED` metadata states; owner and conversation authorization; SHA-256 whole-object verification
plus supported per-chunk integrity; bounded type policy and server-side content sniffing; opaque
server paths; temporary-file isolation; atomic promotion after verification; ordered message
association; captions and alternative descriptions; safe filename, dimensions, duration, and media
type metadata; cancellation and resume; retention and orphan cleanup; capacity evidence; backup; and
restore. A message may reference only an `AVAILABLE` attachment. Completion and message association
must be idempotent and transactionally prevent visible messages from pointing at incomplete content.

Clients own pre-upload compression, image resizing, and audio/video transcoding when a source exceeds
the contract or an efficient representation is desired. The backend never extracts arbitrary
archives or trusts filename extensions/client MIME declarations as proof of content type.

**Non-goals:** S3-compatible infrastructure without evidence, CDN, server-side distributed
transcoding, global deduplication, third-party media hosting, live call media, payloads above 50 MiB,
or client-selected upload chunks above 25 MiB.

**Remaining discovery focus:** Library fit with Quarkus, exact tus extensions, upload expiration,
atomic association, path isolation, malware/type validation proportional to the trust model, per-user
and global storage limits, processing failure state, edit/delete/forward behavior, cache policy,
Cloudflare behavior tests, and reconciliation after partial upload. ADR-0020 accepts the bounded
Tunnel path and its possible future-redesign risk for this personal non-scaling deployment.

### ET-03 — Voice Notes and Media Semantics

**Status:** Candidate
**Intended beneficiary:** Participants communicating through recorded conversational audio
**Principles:** Expressive, understandable, accessible
**Preliminary assessment:** Value 4; effort 2 after ET-02; 1–2 weeks; medium confidence
**Dependencies:** ET-02
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected

**Problem:** A voice note has different intent and presentation semantics from an arbitrary audio file,
even though both can share storage machinery.

**Outcome:** Persist a voice-note message semantic, duration and format metadata, derived waveform
state where justified, and forwarding provenance.

The contract must distinguish a voice note recorded for the current conversation, a voice note
forwarded through the platform, and a generic audio attachment. The server may validate metadata but
cannot prove the physical recording source. Forwarding must not leak inaccessible source details.

**Discovery focus:** Required codec/container support, duration validation, waveform ownership,
processing failure, accessible captions or language metadata, transcript boundaries, forwarding,
deletion, seeking/range behavior, and whether transcript generation remains client-owned.

**Non-goals:** Mandatory transcription, playback UI, playback speed, persistent playback position, or
cloud speech processing.

### ET-04 — Rich Message Relationships

**Status:** Candidate
**Intended beneficiary:** Participants communicating with clearer context and lightweight responses
**Principles:** Expressive, understandable, recoverable
**Preliminary assessment:** Value 4; effort 3; 2–4 weeks; medium confidence
**Dependencies:** Message-domain audit; coordinate ET-02 and ET-03
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected; deliver in slices

**Problem:** Flat messages cannot durably express replies, reactions, forwarding, mentions, or
important-message relationships consistently across clients.

**Candidate slices:** Replies and quoted context; reactions; forwarding provenance; mentions; pinned
conversation messages; and per-user saved/starred messages if not owned by ET-12.

**Boundary:** Each relationship must preserve authorization through membership changes, prevent
cross-conversation disclosure, define edit/delete effects, and remain reconcilable when realtime
events are missed.

**Discovery focus:** Prefer replies/reactions as the first coherent slice; decide snapshot versus live
quoted content; reaction uniqueness; mention identity; tombstone behavior; relationship pagination;
and whether forwarding creates an independent message with bounded provenance.

### ET-05 — Message and Conversation Search

**Status:** Candidate
**Intended beneficiary:** Participants retrieving previously shared information
**Principles:** Understandable, accessible, self-hostable
**Preliminary assessment:** Value 3; effort 3; 2–4 weeks; medium-low confidence
**Dependencies:** Stable message/media semantics
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected

**Outcome:** Provide simple, bounded, authorized SQL Server-backed search without a separate search
cluster or external service.

Search must respect current membership and private-resource behavior; define edited, deleted,
expired, attachment, voice-note, and relationship treatment; and avoid leaking matches through
counts, timing, or inaccessible metadata.

**Discovery focus:** Search scope and query language, SQL Server full-text versus simpler indexed
queries, pagination/ranking stability, authorization query plans, filename/caption inclusion,
operational index maintenance, and measured performance on the representative dataset.

### ET-13 — User Interaction Controls

**Status:** Candidate
**Intended beneficiary:** Participants controlling unwanted or inappropriate interaction
**Principles:** Calm and controllable, trustworthy, understandable
**Preliminary assessment:** Value 3; effort 2–3; 1–3 weeks; medium-low confidence
**Dependencies:** Identity and conversation-policy audit
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected

**Candidate scope:** Lightweight user blocking; direct-conversation creation rules; invitation
controls; and deterministic effects on existing direct conversations and shared groups.

**Non-goals:** Reporting workflows, public moderation, trust scores, automated enforcement, or an
enterprise abuse platform.

**Discovery focus:** Whether blocking affects existing history, shared-group visibility, directory
search, realtime events, receipts, group membership, and unblock behavior without leaking private
state.

### ET-08 — Notification and Attention Events

**Status:** Candidate
**Intended beneficiary:** Participants receiving relevant attention signals without excessive noise
**Principles:** Calm and controllable, portable, understandable
**Preliminary assessment:** Value 3; effort 2; 1–2 weeks; medium confidence
**Dependencies:** ET-12 preference state and event-model audit
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected

**Outcome:** Provide durable notification preferences and client-independent notification event facts.
Candidate controls include mute duration, message versus mention policy, call attention policy, and
privacy-safe event payloads.

**Non-goals:** Client notification UI or a mandatory proprietary mobile push service. A client or
optional adapter remains responsible for OS-specific delivery.

**Discovery focus:** Preference precedence, multi-client consistency, deduplication identity,
mentions, reactions, calls, muted conversation behavior, and reconciliation after missed events.

### ET-06 — Ephemeral Conversation Signals

**Status:** Candidate
**Intended beneficiary:** Active participants receiving timely conversational context
**Principles:** Expressive, understandable
**Preliminary assessment:** Value 2–3; effort 1–2; days to one week; high confidence
**Dependencies:** Existing authenticated WebSocket baseline
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected; useful quick win but not foundational

**Candidate scope:** Typing and deliberately ephemeral presence signals that are best-effort, bounded,
rate-limited, membership-authorized, and never treated as SQL-authoritative messaging or receipt state.

Durable last-seen behavior requires a separate privacy decision. Socket connectivity alone must not
silently create durable presence history.

### ET-17 — Data Portability

**Status:** Candidate
**Intended beneficiary:** Users and the operator preserving access to privately hosted data
**Principles:** Trustworthy, recoverable, self-hostable
**Preliminary assessment:** Value 3; effort 3; 2–4 weeks; low confidence
**Dependencies:** ET-02 if export includes attachments
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected

**Candidate scope:** Authorized, bounded export of a user's accessible conversations and media in a
documented format, with predictable tombstone, membership, identity, and timestamp semantics.

**Boundary:** Database disaster recovery remains an operator concern and is not replaced by user data
export. Import, cross-instance migration, and full account deletion require separate decisions.

**Discovery focus:** Export ownership, snapshot consistency, asynchronous generation, expiry,
download authorization, storage cost, audit safety, media inclusion, and whether import is valuable.

### ET-15 — Structured Group Tools

**Status:** Candidate
**Intended beneficiary:** Small groups coordinating decisions and highlighting information
**Principles:** Expressive, understandable
**Preliminary assessment:** Value 3; effort 3; 2–4 weeks; low confidence
**Dependencies:** ET-04 relationship patterns
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected

**Candidate scope:** Polls, durable vote state, group-level pinned information, and structured system
events for important membership or group-property changes.

**Non-goals:** Public channels, communities, events platforms, task management, or large collaboration
suites.

**Discovery focus:** Poll edit/close/delete behavior, single versus multiple selection, voter privacy,
membership changes, vote reconciliation, authorization, and relationship with ordinary messages.

### ET-14 — Message Retention and Ephemeral Content

**Status:** Candidate
**Intended beneficiary:** Participants controlling personal and shared content lifecycle
**Principles:** Calm and controllable, trustworthy, understandable
**Preliminary assessment:** Value 3; effort 4; 3–6 weeks; low confidence
**Dependencies:** ET-01; ET-02 for ephemeral media
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected; intentionally later due to hidden complexity

**Candidate slices:** Delete or hide for self; conversation-level disappearing-message policy; and
view-once media.

**Boundary:** Expiry, deletion for everyone, deletion for self, and loss of authorization are distinct
states. The track must define multi-client races, offline recipients, backup/restore, audit records,
tombstones, attachment cleanup, forwarding, and legal/operational expectations even for private use.

**Discovery focus:** Split delete-for-self from disappearing/view-once work if it offers a smaller
coherent outcome. Do not infer privacy guarantees that the server cannot enforce after a client has
already obtained content.

### ET-16 — Session and Device Visibility

**Status:** Candidate
**Intended beneficiary:** Users understanding and controlling authenticated access
**Principles:** Trustworthy, understandable, calm and controllable
**Preliminary assessment:** Value 2–3; effort 2; 1–2 weeks; medium confidence
**Dependencies:** Current session model audit; coordinate with IE-01 native enrollment and IE-02
linked-browser lifecycle
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected

**Candidate scope:** Self-service listing and revocation of the current user's sessions, privacy-safe
session labels or last-used facts, and clear behavior when the active session is revoked.

**Ownership boundary:** ET-16 owns generalized user-facing session/device visibility semantics.
IE-01 owns cryptographic native-installation enrollment and IE-02 owns linked-browser pairing and
lifecycle. If ET-16 ships first, its data and API design must leave compatible extension points for
those later credential types; it is not a mirrored consumer track for IE-02. IE-03 is the official
client-side consumer deliverable for the IE-02 protocol.

**Non-goals:** Implementing IE-01 certificate issuance, IE-02 pairing, or IE-03's web client;
device-specific message receipts; a generic device-management framework; or precise location
tracking.

### ET-07 — Self-Hosted Live Calling

**Status:** Candidate
**Intended beneficiary:** Participants needing live one-to-one communication
**Principles:** Expressive, understandable, self-hostable
**Preliminary assessment:** Value 4; effort 5; 4–8+ weeks; low confidence
**Dependencies:** Existing authenticated WebSocket baseline; network traversal evidence
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Not selected; one-to-one audio is the recommended first slice

**Outcome:** Provide platform-authorized WebRTC call setup. ChatBackend owns authorization,
invitation, signaling, lifecycle state, and optional call-history events. Live media does not travel
through ordinary REST bodies, RabbitMQ, SQL Server, or existing application event frames.

**Required lifecycle decisions:** Invite, ringing, accept, reject, busy, cancel, timeout, missed,
connected, reconnecting, and ended; simultaneous-call collision; multiple active sessions; membership
or session revocation; caller cancellation racing with acceptance; negotiation failure; and durable
missed-call/history behavior.

Prefer peer-to-peer media. Add a self-hosted TURN relay only when actual network traversal requires it.
Video, group calling, SFU, recording, and large calls require separate stakeholder decisions.

### ET-D1 — Advanced Content Encryption

**Status:** Deferred
**Intended beneficiary:** Revisit only if the trust model changes
**Principles:** Trustworthy
**Preliminary assessment:** Current value 2; effort 5; extended; medium confidence
**Dependencies:** Explicit product, threat-model, device, and key-management decisions
**Last reviewed:** 2026-08-29
**Stakeholder decision:** Deferred

End-to-end encryption, per-device keys, encrypted attachments, multi-device key synchronization,
recovery keys, encrypted search, and server-blind content remain deliberately low priority. Active
tracks must not introduce speculative cryptographic abstractions in preparation for this work.

TLS, password hashing, encrypted backup, secret handling, least privilege, and authorization remain
mandatory.

### ET-09 — Targeted Single-Host Performance Improvements

**Status:** Superseded by QW-01
**Last reviewed:** 2026-08-29

Performance does not compete with user-value initiatives as a permanent feature track. A standalone
performance track may be created later only for a measured, independently bounded remediation
outcome. The existing identifier remains in this register for historical continuity.

---

## 12. Permanent Quality Workstreams and Acceptance Gates

These requirements apply to relevant tracks and do not compete for portfolio priority.

### QW-01 — Measured Single-Host Performance

- Maintain representative budgets for message operations, synchronization, history, search,
  WebSocket dispatch, file transfer, and media processing.
- Optimize measured regressions or structurally evident query shapes introduced by promoted work.
- Prefer indexes, batching, streaming, bounded concurrency, and slow-consumer isolation before caches
  or distributed infrastructure.
- Validate with the personal single-host workload, not invented high-volume targets.

### QW-02 — Accessibility-Enabling Contracts

- Identify semantic metadata required by accessible clients.
- Preserve meaningful names, captions, alternative descriptions, dimensions, duration, language,
  processing state, and relationship context where applicable.
- Do not claim client accessibility based solely on backend metadata; record the platform capability
  and the remaining client responsibility accurately.

### QW-03 — Security and Privacy

- Authorize every conversation, message, relationship, file, search, and call operation.
- Prevent file paths, names, types, and sizes from bypassing storage boundaries.
- Prevent cross-conversation relationships and search behavior from exposing inaccessible metadata.
- Bound rate, storage, payload, and processing to prevent straightforward exhaustion.
- Exclude message bodies, file contents, tokens, and sensitive query values from logs and durable
  audit records.
- Document threat-model changes and accepted residual risks.

### QW-04 — Durability, Recovery, and Operations

- Define transaction and asynchronous handoff failure semantics.
- Include new durable metadata and files in backup and restore.
- Give orphaned, rejected, deleted, expired, and partially processed content deterministic cleanup.
- Preserve reconciliation correctness when realtime events are missed.
- Define operator-visible capacity, failure, and recovery evidence proportional to the capability.

### QW-05 — Contract and Repository Completion

- Canonical and feature-specific tests pass.
- Postman contracts and human-run flows are updated where relevant.
- OpenAPI, optional AsyncAPI-equivalent artifacts, migrations, configuration, runbooks, ADRs, and
  threat model remain aligned.
- `CHANGELOG.md` records the notable change.
- The client responsibility and recovery guide is audited and updated.
- Outcome evidence is recorded; code alone does not complete a track.

---

## 13. Product Outcome Measures

Tracks select only the measures relevant to their outcome. This personal project does not require
invasive user analytics.

Candidate evidence includes:

- no loss or duplication of durably accepted messages;
- bounded convergence after reconnect, restart, or missed events;
- number of requests and volume needed for initial and incremental restoration;
- representative message, conversation, search, and media operation latency;
- media upload completion, retry, range-delivery, cleanup, backup, and restore behavior;
- call establishment and deterministic failure outcomes when calling exists;
- reduction in documented client workaround responsibilities;
- successful capability discovery across compatible contract versions;
- no authorization or private-resource disclosure regression; and
- structured primary-stakeholder evaluation after the documented manual journey.

The development guide must turn selected measures into exact acceptance criteria and commands.

---

## 14. Recommended Dependency-Aware Waves

Waves express current portfolio logic. They are not releases, commitments, or a requirement to
implement every preceding track.

### Wave 1 — Foundational client consistency

1. ET-10 Platform Capabilities and Limits
2. ET-01 Durable Account Synchronization and Conversation Navigation
3. ET-11 Identity and Conversation Profiles

### Wave 2 — Personal control and attention

1. ET-12 Personal Organization and Attention Controls
2. ET-08 Notification and Attention Events
3. ET-13 User Interaction Controls

### Wave 3 — Rich communication foundation

1. ET-02 Durable Media and Attachment Foundation
2. ET-03 Voice Notes and Media Semantics
3. ET-04 Rich Message Relationships in independently valuable slices

### Wave 4 — Retrieval, group utility, and portability

1. ET-05 Message and Conversation Search
2. ET-15 Structured Group Tools
3. ET-17 Data Portability

### Wave 5 — Ambient and live communication

1. ET-06 Ephemeral Conversation Signals
2. ET-07 one-to-one audio
3. Video or group calling only after separate stakeholder decisions

### Wave 6 — Optional lifecycle and privacy work

1. ET-14 Message Retention and Ephemeral Content
2. ET-D1 only if the trust model and stakeholder priority change

---

## 15. Governance and Maintenance

- This file remains authoritative for the post-Milestone product strategy, initiative register,
  estimates, priorities, and track status.
- The audit outcome preserves the reasoning for the version 0.2 portfolio refinement; future material
  audits should create dated evidence rather than overwrite history.
- Update this register when a candidate is added, re-estimated, materially re-scoped, promoted,
  deferred, rejected, superseded, or verified.
- Preliminary estimates must be replaced or annotated when Discovery produces better evidence.
- A recommendation is not stakeholder authorization.
- Record the primary stakeholder's final or delegated decision and rationale before promotion.
- Detailed implementation facts belong in development guides, ADRs, API specifications, and runbooks.
- Record notable planning and implementation changes in `CHANGELOG.md`.
- Keep application versions independent from Evolution Track identifiers.
- Keep Milestone X production activation separate unless explicitly resumed.
- Preserve multi-agent instruction parity when routing or completion rules change.
- A track is complete only when implementation, evidence, canonical documentation, Postman impact,
  client-responsibility audit, outcome assessment, and operational implications are resolved.

---

## 16. Immediate Next Decision

No Evolution Track is selected for implementation.

The primary stakeholder should select a candidate for Discovery using the comparison in section 10.
If selection is delegated, the current recommendation is:

1. ET-10 as the smallest high-return foundation, potentially as an early slice coordinated with
   ET-01;
2. ET-01 as the first major Discovery track;
3. ET-11;
4. ET-12; and
5. ET-02.

Only after explicit selection should a Discovery record or development guide be drafted. The purpose
of the ordering is not to maximize feature count; it is to use stakeholder time deliberately while
building a trustworthy, recoverable, expressive, controllable, accessible, and self-hosted platform.
