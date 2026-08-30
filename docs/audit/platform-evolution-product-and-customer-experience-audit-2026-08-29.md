# Platform Evolution Product and Customer Experience Audit

## Audit Outcome, Portfolio Comparison, and Prioritization Recommendation

**Audit date:** 2026-08-29
**Status:** Complete planning evidence; no implementation authorized
**Perspective:** Senior product ownership, customer experience, and service excellence
**Audited document:** `docs/platform-evolution-specification.md` version 0.1
**Result incorporated into:** `docs/platform-evolution-specification.md` version 0.2

---

## 1. Executive Assessment

The original Platform Evolution specification established strong engineering governance, appropriate
single-host constraints, and a sensible initial feature register. It was suitable as an enhancement
register, but it was not yet complete as a product strategy or prioritization instrument.

The principal gap was that initiatives were feature-led rather than outcome-led. Potential value was
described qualitatively, but the roadmap did not define the experience principles being advanced,
compare value with delivery effort and elapsed time, expose estimation confidence, or give the primary
stakeholder a durable decision mechanism.

The audit outcome is **ready with revisions**. The master specification has been refined to:

- define the platform experience outcomes that future clients should be able to deliver;
- distinguish product initiatives from permanent quality gates;
- expand synchronization into a foundational account-level recovery concern;
- add missing identity, organization, interaction-control, capability-discovery, retention,
  portability, and session-visibility initiatives;
- add preliminary value, effort, time, confidence, dependency, risk, and reversibility assessments;
- preserve the primary stakeholder's final prioritization authority;
- define a rational default recommendation when prioritization is delegated; and
- organize the portfolio into dependency-aware delivery waves without treating them as commitments.

No Evolution Track was promoted to Discovery or implementation by this audit.

---

## 2. Audit Scope and Method

The audit considered:

1. the Platform Evolution specification;
2. the implemented conversation, message, delivery, identity, session, and realtime contracts;
3. the canonical client responsibility and recovery guide;
4. the original platform specification and completed milestone baseline;
5. the stated personal, closed-group, single-host, self-hosted product constraints; and
6. familiar private-messaging capabilities as comparative prompts, not as requirements to copy.

The assessment used four lenses:

- **Product value:** Does the platform capability enable a materially better, clearer, more reliable,
  more expressive, or more accessible experience across client types?
- **Customer excellence:** Does it reduce uncertainty, recovery burden, interruption, inconsistency,
  or preventable user effort?
- **Delivery proportionality:** Is the capability appropriately sized for a personal, low-volume,
  single-host deployment?
- **Decision quality:** Can the primary stakeholder compare value, effort, elapsed time, uncertainty,
  dependencies, and risk before committing?

External product examples were used only to identify established communication needs. They do not
override ChatBackend's self-hosted scope or authorize cloud dependencies. Reference examples include:

- Matrix client-server synchronization, receipts, typing, presence, relations, and VoIP concepts:
  <https://spec.matrix.org/latest/client-server-api/>
- Signal reactions and view-once media behavior:
  <https://support.signal.org/hc/en-us/articles/360039929972-Message-Reactions> and
  <https://support.signal.org/hc/en-us/articles/360038443071-View-Once-Media>
- WhatsApp poll and voice-message transcript behavior:
  <https://faq.whatsapp.com/796470361614974/> and
  <https://faq.whatsapp.com/241617298315321/>

---

## 3. Product Context Confirmed

The following constraints were confirmed and must remain planning inputs:

- ChatBackend is a personal platform for a small, trusted, closed group.
- Core operation must remain possible on one on-premises physical computer or server.
- SaaS and cloud-only dependencies are not acceptable for core capabilities.
- Multiple client types may share the same backend contracts, but client implementation is outside
  this repository.
- Multi-tenancy, federation, public broadcast, and enterprise moderation are not product goals.
- User-experience planning means backend capabilities that enable a better client experience, not UI
  or client-framework design.
- Performance work must respond to measured or structurally evident needs rather than hypothetical
  high-volume traffic.
- Security gates accepted capability and performance designs; it does not justify speculative heavy
  cryptographic work.
- End-to-end encryption and client-managed key ecosystems remain deliberately deferred.
- SQL Server remains authoritative for durable message and relational state.
- HTTP remains the durable mutation path; WebSockets remain best-effort delivery signals with REST
  reconciliation.

---

## 4. Repository-Verified Baseline

### 4.1 Capabilities already present

The current platform already provides:

- invitation-based identity onboarding and authenticated sessions;
- direct and group conversations with membership, roles, leaving, removal, and ownership transfer;
- paginated conversation retrieval;
- idempotent durable message creation with monotonic per-conversation sequence numbers;
- forward message history, edits, and soft deletion with tombstones;
- per-user delivery/read positions, unread count for an individual conversation, and aggregate sender
  receipt state;
- best-effort WebSocket events for durable message and receipt changes; and
- RabbitMQ-backed audit delivery with SQL persistence and safe client error responses.

Primary evidence includes:

- `src/main/java/com/wayden/messenger/conversation/api/ConversationResource.java`
- `src/main/java/com/wayden/messenger/conversation/api/ConversationResponse.java`
- `src/main/java/com/wayden/messenger/message/api/ConversationMessageResource.java`
- `src/main/java/com/wayden/messenger/message/api/MessageResponse.java`
- `src/main/java/com/wayden/messenger/delivery/api/DeliveryResource.java`
- `src/main/java/com/wayden/messenger/delivery/api/MessagePositionResponse.java`
- `src/main/java/com/wayden/messenger/realtime/api/ChatWebSocketEndpoint.java`

### 4.2 Important current experience gaps

The repository does not currently provide a complete account-level synchronization contract. The
conversation list omits last-message, last-activity, and aggregated unread facts. Missed edits or
deletions to already-synchronized messages cannot be recovered through the forward sequence cursor,
and conversation or membership changes have no realtime or durable account change feed.

The client guide already records additional limitations:

- no durable mutation/change cursor;
- no realtime conversation or membership events;
- no WebSocket replay or per-device acknowledgement;
- no refresh-token flow;
- no general idempotency key for non-message mutations;
- no machine-readable WebSocket contract; and
- no automated network-level WebSocket integration gate.

See `docs/client-integration/client-responsibility-and-recovery-guide.md`, especially sections 7 and
12.

The current identity and conversation summaries are also operational rather than experiential: they
expose stable identifiers, usernames, titles, roles, and timestamps, but not display profiles,
conversation images, summaries, personal organization state, or capability negotiation.

---

## 5. Findings and Resolutions

### High — The roadmap did not define product outcomes

**Impact:** “High value” could mean different things to different actioners, leading to technically
correct features that do not remove the most important experience friction.

**Resolution:** The master specification now defines platform experience principles: trustworthy,
recoverable, understandable, expressive, calm and controllable, accessible, portable, and
self-hostable.

### High — Synchronization was scoped too narrowly

**Impact:** New media and message features would increase client reconstruction burden while the
existing missed-mutation and membership-recovery gaps remained unresolved.

**Resolution:** ET-01 is refined as **Durable Account Synchronization and Conversation Navigation**.
Its Discovery scope includes conversation summaries, account-level change recovery, missed mutation
reconciliation, and membership changes.

### High — Value was not compared with effort and time

**Impact:** Large visible features could displace smaller foundational capabilities with a better
value-to-effort return, while easy but non-foundational work could also distract from necessary
platform foundations.

**Resolution:** Every track must carry preliminary or Discovery-validated value, effort, elapsed-time,
confidence, dependency, risk, and reversibility assessments. Value divided by effort is retained only
as a comparison signal.

### High — Final decision authority was implicit

**Impact:** A calculated score could be mistaken for authorization or implementation order.

**Resolution:** The primary stakeholder explicitly retains final authority. If the stakeholder
delegates a decision, the actioner recommends the most logically sensible choice based on the whole
comparison and records the rationale.

### High — Identity and durable personal controls were missing

**Impact:** Clients would have no shared platform facts for display identity, profile images, pinned or
archived conversations, durable mute preferences, starred messages, or basic interaction controls.

**Resolution:** ET-11, ET-12, and ET-13 have been added for identity/profile, personal organization,
and interaction controls.

### Medium — Media planning emphasized storage more than experience semantics

**Impact:** Safe binary storage could be implemented without captions, multiple-attachment ordering,
range delivery, accessible descriptions, useful processing state, or predictable lifecycle behavior.

**Resolution:** ET-02 now includes those decisions while retaining local filesystem storage and
avoiding speculative object-storage infrastructure.

### Medium — Rich messaging omitted several durable communication relationships

**Impact:** Mentions, pinned messages, saved messages, and group decision tools could later be added
inconsistently.

**Resolution:** ET-04 is refined and ET-15 captures structured group tools. Work must be sliced rather
than delivered as one oversized release.

### Medium — Retention and ephemeral content had no explicit home

**Impact:** Delete-for-self, disappearing messages, and view-once media could be conflated with ordinary
deletion or attachment lifecycle behavior.

**Resolution:** ET-14 is added as a separate, later candidate because its multi-client, cleanup,
backup, audit, and expiry semantics are deceptively complex.

### Medium — Calling scope was not experience-complete

**Impact:** A signaling implementation could omit busy, cancel, timeout, missed-call, collision,
multi-session, and reconnect behavior.

**Resolution:** ET-07 now defines one-to-one audio as the first rational slice and enumerates the call
lifecycle decisions required before implementation. Video and group calling are separate decisions.

### Medium — Clients could not discover supported platform capabilities

**Impact:** Clients would infer feature availability and limits from failures or hard-coded assumptions.

**Resolution:** ET-10 adds a small machine-readable platform capabilities and limits contract.

### Medium — Performance competed with product initiatives

**Impact:** ET-09 did not fit the normal feature lifecycle and could invite speculative optimization.

**Resolution:** ET-09 is retained as a superseded identifier and replaced by QW-01, a permanent
measurement-driven quality workstream applied to every promoted track.

### Low — Accessibility was implicit rather than enforceable

**Impact:** New media and message contracts could omit alt descriptions, filenames, dimensions,
duration, language, captions, or other metadata needed by accessible clients.

**Resolution:** Accessibility-enabling metadata is now a cross-cutting acceptance gate.

### Low — Track governance lacked review metadata

**Impact:** Long-paused candidates could lose their rationale, confidence, and next decision trigger.

**Resolution:** Track metadata now includes intended beneficiary, outcome principles, estimates,
confidence, dependencies, risk, reversibility, last review, next decision, and stakeholder decision.

---

## 6. Estimation and Decision Model

### 6.1 Rating scale

| Rating | Value | Effort | Indicative elapsed time |
|---:|---|---|---|
| 1 | Minor improvement | Very small | Hours to a few days |
| 2 | Useful improvement | Small | Several days |
| 3 | Material improvement | Moderate | Roughly one to two weeks |
| 4 | High platform value | Large | Several weeks |
| 5 | Foundational or transformational | Very large | Extended or multi-track work |

Elapsed time is recorded separately from effort because review cycles, stakeholder availability,
manual validation, operational rehearsal, and dependencies can extend calendar duration without
increasing implementation complexity.

### 6.2 Interpretation safeguards

The rough comparison signal is:

```text
value-to-effort signal = value / effort
```

It must not be treated as an automated ranking. A decision must also account for:

- estimation confidence;
- prerequisites and work unlocked for later tracks;
- security and privacy acceptance;
- recovery and operational complexity;
- reversibility and compatibility;
- stakeholder preference and available time; and
- whether a small feature would distract from a necessary foundation.

Low-confidence estimates favor Discovery, not implementation.

### 6.3 Decision authority

The primary stakeholder makes the final decision to promote, defer, reject, split, or reorder work.
The stakeholder may delegate the decision. When delegated, the responsible actioner must recommend
the most logically sensible option based on the complete comparison, favor foundational work when
otherwise comparable, and record the decision rationale in the master specification.

---

## 7. Comparative Portfolio Outcome

The following estimates are preliminary and exist to guide Discovery selection. Ranges indicate
uncertainty or likely need to split a capability.

| Recommendation order | Initiative | Value | Effort | Signal | Time | Confidence |
|---:|---|---:|---:|---:|---|---|
| 1 | ET-10 Platform Capabilities and Limits | 4 | 1 | 4.0 | Days | High |
| 2 | ET-01 Durable Account Synchronization and Conversation Navigation | 5 | 3 | 1.7 | 1–3 weeks | Medium |
| 3 | ET-11 Identity and Conversation Profiles | 4 | 2 | 2.0 | 1–2 weeks | Medium |
| 4 | ET-12 Personal Organization and Attention Controls | 4 | 2–3 | 1.3–2.0 | 1–3 weeks | Medium |
| 5 | ET-02 Durable Media and Attachment Foundation | 5 | 4 | 1.25 | 3–6 weeks | Medium-low |
| 6 | ET-03 Voice Notes and Media Semantics | 4 | 2 after ET-02 | 2.0 | 1–2 weeks | Medium |
| 7 | ET-04 Rich Message Relationships | 4 | 3 | 1.3 | 2–4 weeks | Medium |
| 8 | ET-05 Message and Conversation Search | 3 | 3 | 1.0 | 2–4 weeks | Medium-low |
| 9 | ET-13 User Interaction Controls | 3 | 2–3 | 1.0–1.5 | 1–3 weeks | Medium-low |
| 10 | ET-08 Notification and Attention Events | 3 | 2 | 1.5 | 1–2 weeks | Medium |
| 11 | ET-06 Ephemeral Conversation Signals | 2–3 | 1–2 | 1.0–3.0 | Days–1 week | High |
| 12 | ET-17 Data Portability | 3 | 3 | 1.0 | 2–4 weeks | Low |
| 13 | ET-15 Structured Group Tools | 3 | 3 | 1.0 | 2–4 weeks | Low |
| 14 | ET-14 Message Retention and Ephemeral Content | 3 | 4 | 0.75 | 3–6 weeks | Low |
| 15 | ET-16 Session and Device Visibility | 2–3 | 2 | 1.0–1.5 | 1–2 weeks | Medium |
| 16 | ET-07 One-to-One Audio Calling | 4 | 5 | 0.8 | 4–8+ weeks | Low |
| 17 | Video or group calling expansion | 2–3 | 5 | 0.4–0.6 | Extended | Low |
| 18 | ET-D1 Advanced Content Encryption | 2 currently | 5 | 0.4 | Extended | Medium |

This order is a recommendation, not approval. Accessibility, performance, security, observability,
backup, recovery, and documentation remain cross-cutting and do not compete for a portfolio slot.

---

## 8. Recommended Delivery Waves

### Wave 1 — Foundational client consistency

- ET-10 Platform Capabilities and Limits
- ET-01 Durable Account Synchronization and Conversation Navigation
- ET-11 Identity and Conversation Profiles

### Wave 2 — Personal control and attention

- ET-12 Personal Organization and Attention Controls
- ET-08 Notification and Attention Events
- ET-13 User Interaction Controls

### Wave 3 — Rich communication foundation

- ET-02 Durable Media and Attachment Foundation
- ET-03 Voice Notes and Media Semantics
- ET-04 Rich Message Relationships, delivered in reviewable slices

### Wave 4 — Retrieval, group utility, and portability

- ET-05 Message and Conversation Search
- ET-15 Structured Group Tools
- ET-17 Data Portability

### Wave 5 — Ambient and live communication

- ET-06 Ephemeral Conversation Signals
- ET-07 one-to-one audio first
- video or group calling only after separate stakeholder decisions

### Wave 6 — Optional lifecycle and privacy work

- ET-14 Message Retention and Ephemeral Content
- ET-D1 only if the trust model and stakeholder priority change

Waves express dependency logic and portfolio coherence. They are not releases, promises, or a rule
that every earlier candidate must be implemented.

---

## 9. Deliberately Deferred or Excluded Ideas

The audit does not recommend prioritizing:

- channels, communities, public broadcast, or stories;
- federation or multi-tenancy;
- bots, plugin ecosystems, or social recommendations;
- large-group conferencing, SFUs, or call recording;
- cloud AI summaries or mandatory cloud transcription;
- third-party GIF, sticker, or media-provider dependencies;
- enterprise moderation, legal retention, or client-activity analytics;
- speculative distributed storage, caching, or orchestration; or
- advanced content encryption while the accepted trust model remains unchanged.

These ideas may be reconsidered only through an explicit scope decision supported by new evidence.

---

## 10. Final Audit Recommendation

The next rational planning action is not implementation. The primary stakeholder should select the
next Discovery candidate using the comparison in this audit and the refined master specification.

If that choice is delegated, the recommended sequence begins with:

1. ET-10 Platform Capabilities and Limits;
2. ET-01 Durable Account Synchronization and Conversation Navigation;
3. ET-11 Identity and Conversation Profiles;
4. ET-12 Personal Organization and Attention Controls; and
5. ET-02 Durable Media and Attachment Foundation.

ET-10 may be planned as a small early slice within ET-01 if Discovery confirms that doing so keeps the
contracts coherent without broadening ET-01 excessively.

The purpose of this ordering is not to maximize feature count. It is to build a trustworthy,
recoverable, expressive, controllable, accessible, and self-hosted platform foundation while using
the primary stakeholder's time deliberately.
