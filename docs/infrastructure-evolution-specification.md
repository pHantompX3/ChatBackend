# ChatBackend Infrastructure Evolution

## Infrastructure Capability Specification, Track Register, and Planning Standard

**Document version:** 0.1

**Status:** Authoritative post-production infrastructure evolution roadmap

**Last reviewed:** 2026-08-30

**Applies after:** Milestone X2 production acceptance, except for design dependencies explicitly
required earlier

**Required tracks:** IE-01 Native Client Trust Foundation, IE-02 Mobile-Authorized Linked-Browser
Protocol, and IE-03 Official Linked Web Companion

---

## 1. Purpose and authority

This document is the canonical system of record for infrastructure capabilities that are required or
considered after the Milestone X production-activation program. It complements, but does not replace,
the [Platform Evolution Specification](platform-evolution-specification.md).

Platform Evolution Tracks describe messaging and user-experience capabilities exposed by
ChatBackend. Infrastructure Evolution Tracks describe cross-cutting trust, delivery, operational, or
runtime foundations that may support or gate those capabilities and their clients.

An Infrastructure Evolution Track is a bounded outcome that can be investigated, audited, planned,
implemented, verified, and released independently. A required track is a committed future
requirement, but its status and dependency gates still control when implementation begins.

This specification owns:

- Infrastructure Evolution Track definitions and status;
- cross-track dependencies with Milestone X and Platform Evolution;
- value, effort, risk, and sequencing assessments;
- common promotion and completion gates; and
- the long-lived intent for enrolled-client and device trust.

ADRs own accepted architecture decisions. Development guides own executable implementation plans.
The client integration guide owns verified consumer responsibilities. Security documents own active
threats and accepted residual risk. These sources must link rather than duplicate implementation
details.

## 2. Current production trust baseline

Milestone X will establish a conventional public HTTPS/WSS service suitable for remote native-mobile
and web clients:

```text
Internet client
    │ trusted HTTPS/WSS
    ▼
router/firewall
    │ TCP 443 only
    ▼
NGINX public edge
    │ private Docker network
    ▼
ChatBackend
    ├── private SQL Server
    └── private RabbitMQ
```

The baseline authenticates the human user and enforces server-side role, membership, and resource
authorization. It does not claim to identify or attest the frontend implementation.

The declared client direction is mobile-first. Milestone X may expose the existing protocol for
controlled validation, but no supported official browser application is promised before IE-03.

Under this baseline:

- an unsanctioned client can reach the authentication boundary;
- it receives no user authority without valid credentials;
- static application secrets, Origin, CORS, user-agent strings, client labels, and custom headers are
  not dependable client authentication;
- users must enter credentials only into clients they trust;
- the operator remains responsible for server security, authentication, authorization, throttling,
  monitoring, revocation, and incident response; and
- compromise of one user can affect that user's accessible private and group content and can be used
  to act as that user until the session/account is revoked.

This is an explicit proportional starting point, not the final enrolled-client trust model.

## 3. Infrastructure evolution principles

### 3.1 Preserve durable authority

Infrastructure controls must not bypass SQL-backed identity, session, role, membership, messaging, or
recovery rules.

### 3.2 Layer trust

Device/client trust supplements user authentication. It never turns possession of a device key into
user authority.

### 3.3 Avoid shared distributable secrets

No private credential shared by every copy of an application may be treated as proof of client
identity. Public application packages and browser assets cannot keep static secrets confidential.

### 3.4 Prefer per-installation revocability

Where device trust is implemented, each installation receives a distinct identity and can be
revoked without disabling every client of that platform.

### 3.5 Preserve recoverability

Key loss, device replacement, certificate-authority recovery, pin rotation, and emergency access must
have explicit procedures. A security control that can permanently strand every legitimate user is
not production-ready.

### 3.6 Remain self-hostable

Core enforcement, certificate issuance, revocation, audit, backup, and recovery must remain operable
on owner-controlled infrastructure. Optional platform attestation may strengthen enrollment but
cannot silently become an undocumented availability dependency.

### 3.7 Stage compatibility deliberately

Infrastructure enforcement must support measured introduction, observation, migration, and rollback.
A required future control does not justify breaking existing clients before compatible replacements
exist.

## 4. Track lifecycle

Infrastructure Evolution Tracks use these statuses:

- **Candidate:** recorded for evaluation; not committed.
- **Required:** stakeholder-declared future requirement.
- **Discovery:** architecture and dependencies are being investigated.
- **Planned:** audited implementation guide and acceptance criteria are approved.
- **In progress:** implementation has begun.
- **Implemented:** code and migrations are complete but operational acceptance may remain.
- **Accepted:** implementation and operational evidence pass.
- **Deferred:** intentionally postponed with rationale and revisit trigger.
- **Rejected:** not planned under the documented constraints.
- **Superseded:** replaced by another track or decision.

## 5. Track register and priority order

| Priority | ID | Track | Status | Value | Effort | Dependency |
|---|---|---|---|---|---|---|
| 1 | IE-01 | Native Client Trust Foundation | Required | High security/control and foundation value | High | X2 plus a supported native-client foundation |
| 2 | IE-02 | Mobile-Authorized Linked-Browser Protocol | Required | High trust foundation; limited direct UX until IE-03 | High | IE-01 accepted |
| 3 | IE-03 | Official Linked Web Companion | Required | High remote/desktop UX after its protocol exists | High | IE-02 accepted plus an official domain/deployment |

The order is dependency-driven:

1. IE-01 establishes the enrolled mobile installation that can act as a durable trust anchor.
2. IE-02 lets that trusted mobile installation authorize a separate browser-held key, but exposes no
   supported public web experience by itself.
3. IE-03 builds and operates the official website that uses the accepted pairing protocol.

All three are stakeholder-declared requirements. Their timing is deferred by dependencies, not by
uncertainty about whether they belong in the intended platform.

Each track must be independently deployable:

- IE-01 must work without any browser capability.
- IE-02 must remain disabled or harmless until an official companion consumes it.
- IE-03 must consume the accepted IE-02 contract without requiring a breaking rewrite of IE-01.

---

## 6. IE-01 — Native Client Trust Foundation

### 6.1 Required outcome

IE-01 must enable the public edge and ChatBackend to distinguish an owner-enrolled native
installation from an arbitrary compatible network client while authenticating and authorizing the
human user separately.

The trust expression is:

```text
approved native installation
+ valid per-installation identity
+ valid user session
+ server-side authorization
```

No layer substitutes for another.

### 6.2 Why this is separate from Milestone X

Milestone X deploys and validates the existing backend. Mandatory native-client admission depends on
a real mobile application, secure platform key storage, certificate enrollment, revocation, and
recovery that do not yet exist. Coupling them to X1 would block initial production activation and mix
host/recovery work with a new client-identity subsystem.

X1 must avoid designs that prevent later native mTLS. X2 must record the authentication, session,
edge, and incident baseline against which IE-01 is evaluated.

### 6.3 In scope

IE-01 includes:

- a private client-certificate authority or equivalent owner-controlled issuer;
- protected offline/root and operational issuing-key design;
- per-installation native asymmetric key generation;
- platform-protected, preferably non-exportable private keys;
- bounded enrollment, approval, issuance, renewal, rotation, revocation, and replacement;
- NGINX/API-gateway mutual-TLS verification for native-client paths;
- integrity-protected propagation of a validated installation identity to ChatBackend;
- durable enrolled-installation metadata and status;
- ordinary user authentication after native-client admission;
- optional native verification/pinning of the public server key;
- user/operator device visibility and revocation;
- HTTP and WebSocket behavior through native mTLS;
- audit records for the device-trust lifecycle;
- staged optional-to-required enforcement;
- CA, device-key, pin, and lockout recovery; and
- optional native platform-attestation evaluation during enrollment.

### 6.4 Non-goals

IE-01 does not:

- replace passwords, sessions, roles, membership, or resource authorization;
- provide end-to-end message encryption;
- guarantee application integrity on rooted, jailbroken, instrumented, or hostile devices;
- place one shared private certificate in every application artifact;
- implement browser pairing or an official website;
- introduce a BFF or generic proxy hop;
- create public third-party client registration or delegated OAuth;
- introduce multi-tenancy or federation; or
- require a cloud certificate-authority service.

### 6.5 Native installation model

Each supported native installation generates its own asymmetric key. The private key should be
non-exportable where the platform supports it. Enrollment submits a public-key certificate request
plus the minimum approved installation evidence. The owner-controlled issuer returns a bounded-life
client certificate tied to a durable installation record.

One Android-wide, Apple-wide, or release-wide private certificate embedded in the application is
prohibited. Distributed artifacts are inspectable, and compromise of one shared key would impersonate
every installation.

Normal public server-certificate validation remains mandatory. Optional public-key pinning requires
backup pins, overlap, emergency rotation, expiry, rollback, and forced-upgrade behavior to be proven
before enforcement.

### 6.6 Gateway and application enforcement

The selected gateway must:

- verify the approved client-certificate chain;
- reject missing, expired, untrusted, revoked, or malformed native identities on protected paths;
- remove untrusted inbound copies of installation-identity headers;
- pass only validated integrity-protected identity context to ChatBackend;
- enforce bounded handshake, connection, header, request, and WebSocket policy;
- expose privacy-safe reason-coded admission telemetry;
- support issuer/intermediate overlap during rotation; and
- retain an emergency rollback path that never publishes private backend ports.

Gateway validation is installation admission, not user authorization. ChatBackend must continue to
validate the user session and must enforce active installation state where required by the final
design.

### 6.7 Enrollment, privacy, and durable state

The implementation guide must threat-model a bounded enrollment mechanism such as owner-issued
single-use enrollment codes, authenticated-user enrollment plus owner approval, or optional platform
attestation. It must prevent anonymous mass issuance, replay, cross-user enrollment, issuance before
durable approval, and distributed reusable secrets.

Likely durable concepts include installation ID, user/owner relationship, public-key or certificate
identifier, status, issuance/expiry/revocation timestamps, and bounded descriptive/last-seen
metadata. Hardware advertising IDs, invasive fingerprints, location, contacts, and unrelated
telemetry are prohibited without a separate requirement and privacy review.

Database and API design remain open until repository-backed planning. Migrations remain forward-only.

### 6.8 Revocation and recovery

IE-01 must support independent user/operator revocation, immediate rejection of new connections,
bounded closure of established HTTP/WebSocket use, and clear distinction between device, session,
account, and account-wide actions.

Before mandatory enforcement, prove lost/replacement-device handling, renewal, expiry, issuer
rotation, root/intermediate restoration, pin rotation, compromised-issuer response, clock skew,
database restoration, and emergency compatibility rollback. CA and recovery material require
encrypted access-controlled off-host backup and restore exercises.

### 6.9 Compatibility rollout

```text
DESIGN_ONLY
  -> OPTIONAL_OBSERVE
  -> OPTIONAL_ENROLLED_NATIVE
  -> REQUIRED_FOR_SELECTED_NATIVE_CLIENTS
  -> REQUIRED_FOR_ALL_SUPPORTED_NATIVE_CLIENTS
```

Each transition needs acceptance evidence and rollback criteria. Mandatory native enforcement cannot
begin until supported applications enroll/renew/recover, revocation works, CA recovery passes,
approved operator/Postman tooling exists, and monitoring covers trust failures.

IE-01 does not promise browser access. Conventional public access may remain temporarily available
only under an explicit compatibility policy; it must not be mistaken for enrolled native traffic.

### 6.10 Testing and acceptance

Acceptance requires:

- missing, wrong-issuer, expired, malformed, and revoked certificate rejection;
- distinct per-installation identities and no shared private key in distributed artifacts;
- concurrent/replayed enrollment resistance and post-commit issuance;
- real native secure-key, HTTP, WebSocket, renewal, and revocation integration;
- user authorization after installation admission;
- device/session/account revocation interactions;
- issuer, certificate, and pin rotation;
- backup and clean-host CA/trust-data recovery;
- compatibility rollout and rollback;
- denial-of-service characterization; and
- audit privacy and retention.

### 6.11 Dependencies, value, and effort

| Dimension | Assessment |
|---|---|
| Stakeholder requirement | Required |
| Security/control value | High |
| Direct user-visible value | Low to medium |
| Foundation value | Very high |
| Implementation effort | High |
| Operational effort | Medium to high |
| Dependency | X2 and a real native-client foundation |
| Recommended order | First Infrastructure Evolution delivery |

IE-01 is first because IE-02 cannot securely authorize a browser without an accepted trusted mobile
installation.

### 6.12 Open planning decisions

Resolve certificate hierarchy/tooling, enrollment approval, installation ownership, lifetimes,
revocation latency, identity propagation, secure-key support, optional attestation, pinning,
operator/test clients, compatibility duration, CA recovery, metadata retention, and emergency
lockout rollback.

---

## 7. IE-02 — Mobile-Authorized Linked-Browser Protocol

### 7.1 Required outcome

IE-02 must provide the durable backend protocol through which an IE-01-enrolled mobile installation
can authorize, inspect, renew, and revoke a separate browser companion key.

IE-02 is a backend/security exchange, not a website. Completing it must not publish an unfinished web
experience or weaken native-client enforcement.

The trust statement is:

```text
browser-generated proof-of-possession key
+ explicit approval signed by an active enrolled mobile installation
+ bounded linked-companion credential/session
+ normal user authorization
```

This proves mobile authorization of a browser-held key. It does not prove that every executing
JavaScript byte is owner-authored.

### 7.2 Pairing state machine

The planned semantic lifecycle is:

```text
PENDING
  -> MOBILE_REVIEWED
  -> APPROVED
  -> PROOF_VERIFIED
  -> ACTIVE
  -> EXPIRED | REVOKED | REJECTED
```

A planning guide may refine names, but it must preserve explicit pending, approved-but-unclaimed,
active, terminal, and expiry behavior.

A candidate exchange is:

1. A browser/test harness generates a distinct proof-of-possession key.
2. It submits the public key and receives a random, single-use, short-lived pairing identifier.
3. It displays a QR payload containing only bounded non-secret pairing data.
4. An authenticated IE-01 mobile app scans the QR and independently retrieves authoritative pending
   details from ChatBackend.
5. The mobile app displays domain/context, approximate browser description, request time, requested
   capability, expiry, and key fingerprint.
6. The user approves or rejects.
7. The mobile app signs approval over the pairing identifier, browser key, nonce, expiry, and
   relevant context using its enrolled installation key.
8. ChatBackend verifies active installation, user, nonce, expiry, and single-use state, then durably
   records approval.
9. The browser proves possession of its private key.
10. ChatBackend activates a bounded companion credential/session linked to that key.
11. The user/operator can list and revoke the companion independently.

### 7.3 Backend scope

IE-02 includes:

- pending-pairing creation, retrieval, approval, rejection, claim, expiry, and cleanup;
- cryptographic binding of mobile approval to the exact browser public key and nonce;
- durable companion-device records and lifecycle;
- proof-of-possession for activation and subsequent authentication as selected by design;
- HTTP and WebSocket companion admission;
- list, revoke, expire, rotate, and session/account interaction;
- replay, concurrency, race, and privacy controls;
- audit events and monitoring signals;
- capability/version discovery for the future official site;
- a disabled-by-default or non-advertised rollout state; and
- a reference integration harness that is not presented as the official web client.

### 7.4 Non-goals

IE-02 does not:

- build or host the official website;
- require a generic pass-through BFF;
- claim that a public web origin alone authenticates frontend software;
- keep browser private keys on the server;
- require the mobile device to relay all browser messages;
- require the mobile device to remain continuously online after pairing;
- enable a companion before proof-of-possession completes; or
- make a browser companion equivalent to an IE-01 native certificate.

### 7.5 Security and privacy requirements

Pairings must use cryptographically random identifiers, short expiry, one-time transitions, bounded
attempts, and transactional concurrency control. QR content is a pointer/request, not authority. The
mobile application must retrieve and display authoritative details before approval.

The approval signature must bind the browser key and anti-replay values. Logs/audit must not contain
private keys, raw sessions, full QR payloads where reusable, or unnecessary fingerprints. Pairing,
claim, and proof endpoints require dedicated throttling.

Phishing remains possible if a user knowingly approves a malicious pairing. The mobile UI must make
the official expected origin/context and key fingerprint conspicuous, and compromise guidance must
treat suspicious linked companions as immediately revocable sessions.

### 7.6 Recovery and compatibility

IE-02 must support lost browser storage, browser reset, expiry, mobile-device replacement, companion
revocation, account disable, session revoke-all, server restart, database restore, and version skew.
Failure or disablement of IE-02 must not affect IE-01 native messaging.

The backend may ship before IE-03 only when the capability is feature-disabled, owner/test-only, or
otherwise inaccessible as an advertised production experience. Existing HTTP/WSS contracts must
remain compatible.

### 7.7 Testing and acceptance

Acceptance requires deterministic state-machine tests, expiry, replay, concurrency, duplicate
approval/claim, wrong-user/wrong-device, revoked-mobile, lost-key, proof failure, HTTP/WSS
authorization, independent companion revocation, account/session interactions, audit privacy,
migration/rollback compatibility, restart/restore, and a protocol-level reference harness.

IE-02 acceptance proves the backend protocol. It does not claim a usable web product.

### 7.8 Dependencies, value, and effort

| Dimension | Assessment |
|---|---|
| Stakeholder requirement | Required |
| Security/foundation value | High |
| Direct user-visible value alone | Low |
| Implementation effort | High |
| Operational effort | Medium |
| Dependency | IE-01 accepted |
| Downstream dependency | IE-03 |
| Recommended order | Second Infrastructure Evolution delivery |

IE-02 precedes IE-03 because stabilizing and auditing the protocol before building the official site
reduces simultaneous backend/client uncertainty and allows independent verification.

### 7.9 Open planning decisions

Resolve browser key mechanism, proof-of-possession format, pairing expiry, mobile approval payload,
companion credential/session form, WebSocket binding, linked-device permissions, mobile-presence
requirements for sensitive actions, feature-flag strategy, retention, and version negotiation.

---

## 8. IE-03 — Official Linked Web Companion

### 8.1 Required outcome

IE-03 must build and operate the official web application that consumes the accepted IE-02 protocol.
It is a mobile-authorized companion experience, not an independently password-only web client.

The official site is a separate client artifact and should ultimately live in its own repository.
This ChatBackend repository remains authoritative for backend contracts and records cross-repository
acceptance evidence and version compatibility.

### 8.2 Scope

IE-03 includes:

- official domain, DNS, trusted HTTPS, deployment, and ownership;
- browser key generation and protected origin-scoped storage;
- QR pairing initiation and clear pending/expiry/error behavior;
- mobile approval status and proof-of-possession claim;
- linked-companion session lifecycle;
- normal ChatBackend HTTP and WebSocket messaging/recovery behavior;
- linked-device listing and self-revocation;
- secure update and dependency policy;
- CSP, XSS, CSRF/cookie controls where applicable, clickjacking protection, and safe browser storage;
- accessibility, responsive desktop/tablet behavior, and supported-browser policy;
- offline/reconnect behavior consistent with the client responsibility guide;
- production monitoring, incident response, and rollback; and
- protocol version/capability compatibility with IE-02.

### 8.3 No mandatory BFF

IE-03 must not introduce a pass-through BFF merely to create a second public hop. NGINX and
ChatBackend already provide TLS termination, routing, limits, authorization, and WebSocket handling.

A future BFF requires a separate demonstrated need such as multi-service aggregation, server-held
third-party secrets, substantial contract translation, server rendering, or a materially distinct
browser-session model. If introduced later, it must have explicit validation that cannot already live
at NGINX/ChatBackend and its own failure/recovery analysis.

### 8.4 Trust and limitation

The browser private key must never be exported to or stored by the server. Origin-scoped
non-exportable WebCrypto or WebAuthn-backed credentials are candidates to evaluate. WebAuthn may
provide stronger relying-party-domain binding but requires support/recovery validation.

The companion identity proves that an enrolled mobile installation approved a browser-held key. It
cannot perfectly attest every JavaScript instruction, defeat compromise of the official site, or
prevent a user from approving a malicious pairing. Strong official-site supply-chain, CSP, XSS,
domain, and update controls remain mandatory.

### 8.5 Independent delivery and rollback

IE-03 deploys only after IE-02 is accepted. Its absence or outage must not affect native mobile
clients. The site may be disabled or rolled back without database rollback or weakening IE-01.

IE-03 must use versioned capability discovery and fail safely if the backend protocol is too old or
new. A staged pilot should precede general availability to the closed group.

### 8.6 Testing and acceptance

Acceptance requires real browser/mobile pairing, supported-browser key persistence, QR expiry and
error UX, proof-of-possession, HTTP/WSS messaging, reconnect/reconciliation, lost-storage recovery,
revocation, account/session interactions, accessibility, CSP/XSS/CSRF controls, dependency/build
integrity, production deployment, monitoring, and rollback.

Browser automation alone is insufficient; at least one supported mobile installation must approve and
revoke a real deployed browser companion.

### 8.7 Dependencies, value, and effort

| Dimension | Assessment |
|---|---|
| Stakeholder requirement | Required |
| User-experience value | High |
| Security value | Medium to high |
| Implementation effort | High |
| Operational effort | Medium |
| Dependency | IE-02 accepted and official domain/deployment available |
| Recommended order | Third Infrastructure Evolution delivery |

IE-03 has the largest direct browser UX payoff, but implementing it before IE-02 would force protocol
and UI decisions to change together and would weaken independent acceptance.

### 8.8 Open planning decisions

Resolve client repository/technology, official domain, browser support, WebCrypto versus WebAuthn,
session/cookie transport, UI scope, offline cache, sensitive-action reapproval, deployment/hosting,
monitoring, accessibility target, update policy, and extraction/versioning contracts.

---

## 9. Cross-track release model

```text
Milestone X1 -> Milestone X2 -> accepted production
                                  |
                                  v
                     IE-01 Native Trust
                                  |
                      enrolled mobile anchor
                                  |
                                  v
                 IE-02 Linked-Browser Protocol
                                  |
                    accepted dormant backend
                                  |
                                  v
                IE-03 Official Web Companion
```

Parallel discovery is permitted, but acceptance remains ordered. Platform Evolution work may proceed
between these deliveries provided it does not assume a later trust state.

## 10. Promotion and completion gates

Before any Infrastructure Evolution Track is Planned:

- inspect current backend and applicable client repositories, tests, deployment, and security model;
- produce a repository-backed architecture, privacy, and threat-model audit;
- resolve decisions that materially change implementation;
- define forward-compatible API/database changes and staged rollout;
- document client, deployment, monitoring, backup, and recovery effects;
- create and audit an implementation-ready development guide; and
- update the changelog and canonical routing.

Before a track is Accepted:

- builds and automated tests pass in every affected repository;
- migrations and upgrade paths pass;
- security, concurrency, failure, and abuse tests pass;
- real gateway/client integration evidence passes where applicable;
- backup, restore, rotation, revocation, and rollback are exercised;
- monitoring and incident procedures are operational;
- the client responsibility guide is reconciled; and
- the stakeholder accepts residual risks.

## 11. Relationship to Platform Evolution

Infrastructure and Platform Evolution are parallel portfolios. A Platform Evolution Track may depend
on one of IE-01 through IE-03, and infrastructure work may need platform session/device capabilities.
Neither portfolio automatically outranks the other. Priority remains a stakeholder decision based on
value, effort, elapsed time, dependency, operational burden, user friction, and security risk.
