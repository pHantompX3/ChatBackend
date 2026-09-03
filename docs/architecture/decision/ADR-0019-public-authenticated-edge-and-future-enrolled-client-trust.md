# ADR-0019: Expose an Authenticated Public Edge and Require Future Enrolled-Client Trust

**Status:** Accepted

**Date:** 2026-08-30

**Supersedes:** [ADR-0018](ADR-0018-restrict-client-access-to-owner-controlled-networks.md)

**Required follow-up:** [Infrastructure Evolution IE-01 through IE-03](../../infrastructure-evolution-specification.md#5-track-register-and-priority-order)

## Context

The intended clients are expected to be mobile-heavy and require ordinary remote use. Restricting the
production API to an owner-controlled LAN or manually enrolled VPN would materially constrain that
experience and no longer reflects the stakeholder's product direction.

A public HTTPS/WSS service cannot reliably prove that a request came from an exact frontend merely
from IP address, Origin, CORS, user-agent, a public client identifier, or a static secret distributed
inside an application. NGINX can reduce attack surface and enforce transport and request policy, but
it cannot attest application source code.

The stakeholder accepts the proportional initial model: the server authenticates the human user and
authorizes every resource operation; users must provide credentials only to clients they trust.
Compromise through a user-selected malicious client can expose that user's accessible content and
permit actions as that user until containment.

The stakeholder has also declared a mobile-first trust sequence: enrolled native installations,
followed by a durable protocol for mobile-authorized browser companions, followed by the official
website that consumes that protocol. These capabilities depend on real clients and must not block
initial production activation.

## Decision

### 1. Public HTTPS/WSS is the Milestone X access baseline

The production NGINX edge is reachable through the authenticated public HTTPS/WSS boundary. ADR-0020
refines the transport for the confirmed CGNAT environment: outbound-only Cloudflare Tunnel reaches
private NGINX, and the origin publishes no inbound port. ChatBackend, SQL Server, RabbitMQ, Docker
control interfaces, and administrative services remain private and are never directly published.

Every public application operation continues to require normal ChatBackend authentication and
server-side authorization. Public reachability grants no messaging authority.

### 2. User identity, not frontend identity, is initially authoritative

The initial production service authenticates users rather than attempting to certify frontend
software. The server does not claim that a request originated from an owner-built or sanctioned
client.

The operator remains responsible for secure transport, password/session protection, authorization,
rate limiting, bootstrap closure, patching, monitoring, revocation, and incident response. Users are
responsible for protecting their devices and credentials and for using clients they trust.

This responsibility statement is an operational/product boundary, not a determination of legal
liability.

### 3. NGINX is an edge control, not client attestation

NGINX must provide trusted TLS termination, WebSocket proxying, bounded request/connection policy,
safe forwarding headers, security headers where applicable, and operational telemetry. It must not
treat Origin, CORS, user-agent, client labels, custom headers, or a shared embedded secret as proof of
client authenticity.

### 4. Trusted public certificates are required

Remote native clients must be able to validate the server without installing a private authority.
Milestone X must therefore select a publicly trusted certificate and DNS/renewal approach before
public activation. Rehearsal or locally trusted CA certificates are not sufficient for the public
mobile production path.

### 5. Mobile-first client and companion trust are required post-X capabilities

IE-01 requires distinct per-installation native keys/certificates, gateway mutual TLS, ordinary user
authentication, revocation, rotation, and recovery. IE-02 then requires the backend protocol through
which an active enrolled mobile installation approves a separate browser proof-of-possession key.
IE-03 finally builds the official linked web companion.

A shared private certificate embedded in application artifacts is prohibited. Browser JavaScript
never receives a native or server private identity. A generic pass-through BFF is not required and
does not attest browser software. Mandatory native enforcement and browser availability are deferred
until their respective clients, recovery, monitoring, and staged rollout pass.

### 6. Public delegated authorization remains separate

This decision does not create public third-party client registration, OAuth delegated authorization,
multi-tenancy, federation, or an open developer ecosystem. Compatible protocol clients may reach the
initial public authentication boundary, but they receive only the authority of credentials supplied
to them.

## Consequences

### Positive

- Native mobile clients can operate remotely without an always-on VPN.
- The first production release uses conventional, interoperable HTTPS/WSS and the existing user
  authentication model.
- Internal services remain private behind one hardened edge.
- Client integrity is not falsely inferred from spoofable network metadata.
- The stronger enrolled-client model is committed without coupling X1 to nonexistent clients.

### Negative and residual risk

- Arbitrary clients can reach authentication endpoints before IE-01 enforcement.
- A malicious client can capture credentials and content voluntarily supplied by a user.
- Public exposure increases password guessing, denial-of-service, scanning, parser, dependency, and
  operational risks.
- Account compromise can affect group content and other participants, not only the compromised
  user's private view.
- The operator must maintain a public domain/certificate path, edge policy, monitoring, patching, and
  incident response.
- IE-01 through IE-03 add certificate-authority, native-device, pairing-protocol, official-site, and
  recovery complexity in separate increments.

## Required verification

Milestone X must prove:

- only the NGINX HTTPS/WSS edge is publicly reachable;
- direct access to ChatBackend, SQL Server, RabbitMQ, administration, and Docker control fails;
- public certificates validate through supported remote clients;
- authentication throttling and bootstrap closure work through the edge;
- forwarding, Origin/CORS, request-size, header, connection, and WebSocket policies behave as
  documented;
- session and account revocation contain credential compromise;
- Internet-origin smoke, abuse, and recovery tests pass; and
- monitoring detects edge, authentication, certificate, resource, and security failures.

IE-01 through IE-03 own the later native and linked-browser verification defined in the
Infrastructure Evolution specification.

## Revisit conditions

Revisit this decision if public exposure cannot be made reliable through the available ISP/router,
the stakeholder restores a VPN-only requirement, legal/privacy obligations change, or IE-01 through
IE-03 are ready to change client-admission behavior.

## References

- [ADR-0017: Harden One ChatBackend Instance Behind NGINX](ADR-0017-harden-single-instance-deployment.md)
- [ADR-0018: Restrict Client Access to Owner-Controlled Networks](ADR-0018-restrict-client-access-to-owner-controlled-networks.md)
- [ADR-0020: Use Cloudflare Tunnel for CGNAT Production Ingress](ADR-0020-use-cloudflare-tunnel-for-cgnat-production-ingress.md)
- [Infrastructure Evolution Specification](../../infrastructure-evolution-specification.md)
- [Milestone X production activation](../../development-guide/milestone-x-production-activation.md)
- [Client responsibility and recovery guide](../../client-integration/client-responsibility-and-recovery-guide.md)
- [Production threat model](../../security/threat-model.md)
