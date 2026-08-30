# ADR-0018: Restrict Client Access to Owner-Controlled Networks

- Status: Accepted
- Date: 2026-08-30
- Decision owners: Project owner

## Context

ChatBackend is a private, personal messaging platform. A user is the authenticated principal; a web,
mobile, or desktop frontend is normally only an interface acting for that user. Browser and native
frontends cannot safely retain a shared secret that proves their source code or publisher, so adding
a client identifier or embedded API key would not reliably distinguish an official interface from a
copied or malicious one.

The project considered several ways to prevent unsanctioned clients from reaching the backend:

- public HTTPS/WSS combined with user sessions alone;
- browser CORS and WebSocket Origin allowlists;
- per-device mutual TLS certificates;
- a public/delegated-client authorization system with client registration, scopes, authorization
  codes, PKCE, refresh tokens, consent, and revocation;
- source-IP allowlisting; and
- access through an owner-controlled LAN or private VPN.

The owner may occasionally sanction a custom interface created by someone in the closed group, but
does not intend to operate an open developer platform or accept arbitrary Internet clients. A custom
interface can instead be hosted within the owner's trusted network or admitted through an explicitly
managed private network path. Someone requiring independent infrastructure and client freedom can
operate a separate, isolated ChatBackend deployment, subject to a future software-licensing and
distribution decision.

## Decision

### 1. Deployment access boundary

The current production target is **trusted-network client access**, not a publicly extensible API.
The ChatBackend HTTPS/WSS edge is reachable only from:

- approved devices on the owner-controlled LAN;
- approved peers or subnets on an owner-controlled private VPN, if remote access is enabled;
- the monitoring workstation and explicitly authorized operator sources; and
- another narrowly approved private network path documented with equivalent ownership, revocation,
  and firewall controls.

The host firewall is the primary network control. NGINX applies a tested defense-in-depth source
allowlist where its observed client address is trustworthy through the selected Docker/proxy path.
SQL Server, RabbitMQ, container-engine control, monitoring dashboards, and operational interfaces
remain more narrowly private.

### 2. User authentication remains authoritative

Every ordinary REST and WebSocket operation still requires the existing user session, system role,
conversation membership/role, and resource authorization. Network admission does not authenticate a
user and does not grant messaging authority.

The interface itself does not receive a privileged system role merely because it is sanctioned. The
backend must continue deriving the actor from the authenticated session rather than accepting a
client-supplied user identity.

### 3. Sanctioned custom clients

A custom browser, mobile, desktop, or server-hosted interface may use an existing deployment only
after the owner admits its runtime environment to the trusted network boundary. Depending on its
form, that means:

- hosting the interface on the owner-controlled LAN;
- enrolling its device or host as an approved private-VPN peer;
- admitting a fixed and verified source through a narrowly scoped firewall rule; or
- documenting another private path with equivalent removal and monitoring behavior.

Browser clients also require an exact approved HTTP origin and WebSocket Origin. Origin/CORS checks
are browser hardening, not proof of client authenticity. Non-browser headers such as `User-Agent`, a
custom header, or a public client ID are diagnostic claims and cannot establish trust.

The owner must be able to revoke a custom client's network path without disabling unrelated users or
deployments. Client onboarding records the owner, source subnet or VPN address, intended origin,
review date, and revocation procedure without committing private keys or credentials.

### 4. No mandatory per-device client certificates

Mutual TLS and per-device client-certificate installation are rejected for the current platform. The
client setup, issuance, renewal, recovery, revocation, and cross-platform certificate-store burden is
not proportional to the personal closed-group use case.

Server TLS remains mandatory. This decision rejects client certificates, not HTTPS/WSS certificate
validation.

### 5. No public delegated-client platform

The platform will not currently implement general public-client registration, OAuth-style delegated
authorization, PKCE, user consent grants, per-frontend scopes, or public refresh-token infrastructure.
Those mechanisms add material authentication and security complexity without serving the accepted
trusted-network operating model.

Scoped service credentials also remain deferred until a concrete non-human API consumer needs them.
A frontend used by a human is not automatically a service client. ChatMonitor continues using its
dedicated read-only SQL projection rather than a broad human or administrator API token.

### 6. Independent deployments remain isolated

A person or organization that needs to operate clients outside the owner's trusted network may run a
separate ChatBackend instance only under whatever licence and distribution terms the owner later
selects. That instance owns its own:

- users, administrator, sessions, and invitations;
- SQL Server database and encryption/recovery material;
- RabbitMQ broker and credentials;
- network, domain, certificates, secrets, backups, monitoring, and incident response; and
- client-origin and access policy.

This does not provide federation, multi-tenancy, cross-deployment identity, shared conversations,
message exchange, administrative control, or support obligations. Software licensing and commercial
terms are not decided by this ADR.

### 7. Honest enforcement limit

Once an application runs on an admitted device/network and holds a valid user token, the backend
cannot reliably prove which frontend source code produced a request. IP/CIDR, VPN peer, Origin,
`User-Agent`, and client labels can narrow access and improve audit context but do not cryptographically
attest an application build.

The accepted authorization boundary is therefore:

```text
owner-approved network path
+ valid user session
+ server-side role, membership, and resource authorization
```

It is not proof that the request came from an exact frontend binary or source repository.

## Alternatives considered

### Public HTTPS/WSS with user authentication only

Rejected as the default because any Internet client could reach the authentication boundary and a
malicious frontend could collect credentials, tokens, requests, responses, and message content. User
authorization would still limit backend authority, but it would not prevent phishing or data capture
inside that frontend.

### IP allowlist without a private network

Rejected as the general remote-client mechanism because mobile, residential, carrier-NAT, shared-NAT,
and IPv6 privacy addresses are unstable or overly broad. Fixed source rules remain useful for known
server hosts and operator paths.

### Per-device mutual TLS

Rejected because its client installation and lifecycle burden is explicitly unacceptable to the
project owner.

### Public/delegated OAuth client ecosystem

Deferred rather than prohibited forever. It is the more appropriate standards-oriented direction if
the project later becomes a public integration platform, but that is not the current product or
operating model.

### Embed a shared frontend secret or custom header

Rejected because browser and distributed native assets cannot keep such a value confidential. It
would create a copyable marker rather than a dependable trust boundary.

## Consequences

### Positive

- The network and product boundaries match the private personal-use intent.
- Existing user sessions and resource authorization remain the primary application controls.
- Arbitrary Internet clients cannot reach the API merely by discovering its address.
- Custom clients remain possible through explicit owner-controlled onboarding.
- Remote access can be revoked per private-network peer or source without client certificates.
- The project avoids prematurely implementing a security-sensitive public authorization server.
- Independent consumers can operate isolated deployments without changing this deployment into a
  multi-tenant or federated service.

### Negative

- Remote clients require LAN presence, VPN/private-path onboarding, or a separate deployment.
- A VPN requires client software/configuration even though it does not require client certificates.
- The owner must manage network peers, source ranges, firewall rules, and revocation.
- The system cannot identify or attest the exact frontend software used after network and user
  authorization succeed.
- Direct public Internet access and open third-party integrations are intentionally unavailable.

## Security and operational impact

Milestone X must select and verify the actual LAN/VPN subnets, host firewall, NGINX source-address
behavior, trusted proxy chain, origin allowlists, remote-peer onboarding, revocation, monitoring, and
recovery. If WireGuard or another VPN is selected, keys are per-peer secrets outside Git and each peer
receives a stable tunnel address; client public IP stability is not itself required when the peer can
initiate to the owner-controlled endpoint.

The threat model must treat unapproved network access, stolen VPN configuration, admitted malicious
clients, unsafe browser origins, and inability to revoke a peer as explicit abuse cases. Production
validation must prove that an unapproved Internet/LAN source cannot reach the API while approved LAN
and optional VPN paths can complete HTTPS, WSS, authentication, and durable recovery flows.

## Revisit conditions

Revisit this decision if:

- the owner intentionally opens ChatBackend as a public integration platform;
- remote access without owner-controlled network onboarding becomes a product requirement;
- a concrete non-human service needs scoped API credentials;
- independent deployments need federation or shared identity/data;
- a client-attestation mechanism becomes acceptable and proportional; or
- the selected production network cannot provide reliable private ingress.

Reversal requires an updated threat model and a new ADR covering client registration, delegated
authorization, token lifecycle, browser/native security, abuse prevention, and rollout compatibility.

## References

- [ADR-0015: Harden HTTP Contracts and Authentication Throttling](ADR-0015-harden-http-contracts-and-authentication-throttling.md)
- [ADR-0016: Use WebSockets Next for Realtime Signaling](ADR-0016-use-websockets-next-for-realtime-signaling.md)
- [ADR-0017: Harden One ChatBackend Instance Behind NGINX](ADR-0017-harden-single-instance-deployment.md)
- [Milestone X production activation backlog](../../development-guide/milestone-x-production-activation.md)
- [Client responsibility and recovery guide](../../client-integration/client-responsibility-and-recovery-guide.md)
- [Production threat model](../../security/threat-model.md)
