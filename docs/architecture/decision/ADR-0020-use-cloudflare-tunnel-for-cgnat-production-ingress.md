# ADR-0020: Use Cloudflare Tunnel for CGNAT Production Ingress

**Status:** Accepted

**Date:** 2026-09-02

**Refines:** [ADR-0019](ADR-0019-public-authenticated-edge-and-future-enrolled-client-trust.md)

## Context

Digicel assigns the production connection an IPv4 address in carrier-shared `100.64.0.0/10` space.
Ordinary router forwarding cannot publish NGINX through that CGNAT boundary, and requesting a public
IPv4 from the ISP is not an available dependency. Native IPv6 ingress is not proven end to end and
would exclude IPv4-only clients.

The currently implemented platform transports text messages, HTTP API traffic, and WebSocket events.
Future images, documents, audio, voice notes, and video are not X1 capabilities. The stakeholder
requires their authoritative bytes to remain on the production server rather than Cloudflare R2,
Images, Stream, or another hosted media store.

## Decision

X1 uses Cloudflare Tunnel as the public HTTPS/WSS ingress. A production `cloudflared` service creates
outbound-only connections to Cloudflare and routes the dedicated API hostname to the private NGINX
edge. No WAN port forwarding or public origin address is required.

NGINX remains the application policy boundary for request limits, WebSocket proxying, trusted
forwarding, security headers, and application telemetry. ChatBackend, SQL Server, RabbitMQ, Docker,
SSH, RDP, router administration, and backup shares remain private.

Cloudflare is ingress only. X1 does not adopt R2, Images, Stream, Workers-based media storage, or any
other paid media service. Durable application and future media storage remain on the production host.

X1 approval still covers only the currently implemented text/API/WebSocket workload. The future
ET-02 media track may, however, use this Tunnel for a deliberately bounded attachment contract:
an assembled attachment is limited to 50 MiB and each resumable upload request to 25 MiB. Clients
compress, resize, or transcode source media before upload when necessary. The production server
stores the authoritative bytes and streams authorized downloads; WebSockets carry lifecycle events,
never attachment bytes.

The stakeholder accepts the residual risk that Cloudflare could restrict this low-volume use or
change its plan or terms, requiring the transfer path to be redesigned. This acceptance is
proportionate to a personal, 5–10-user learning deployment that is not intended to scale on this
infrastructure. It is not approval for an unlimited media pipe, live audio/video relay, CDN behavior,
or files larger than the stated contract.

## Consequences

### Positive

- CGNAT no longer blocks the current production workload.
- No inbound router or Windows Firewall port is opened.
- The public hostname supports ordinary HTTPS/WSS clients without a client VPN.
- SQL and application data remain on premises.
- X1 introduces no paid Cloudflare media product.

### Negative and residual risk

- Remote availability now depends on Cloudflare, DNS, the local `cloudflared` service, and outbound
  Internet connectivity.
- Cloudflare terminates the public TLS connection and is in the HTTP/WebSocket request path.
- Tunnel credentials become production secrets requiring least privilege, rotation, and recovery.
- Cloudflare plan or service-term changes can require an ingress redesign.
- Bounded future media delivery has an approved architectural path but remains unimplemented until
  ET-02 supplies its storage, authorization, transfer, cleanup, backup, and recovery contract.

## Required verification

- Pin and scan the `cloudflared` image or binary and run it without host-network or Docker-control
  access.
- Store the tunnel credential outside Git and expose it only to the connector.
- Route only the dedicated API hostname to private NGINX.
- Prove HTTPS, WSS, forwarding-header trust, reconnect behavior, session revocation, and request limits
  from an external network.
- Prove that the origin has no public inbound ports and every internal/admin service remains private.
- Exercise connector restart, credential rotation, Cloudflare outage behavior, DNS recovery, and a
  documented private-LAN operator path.
- Record Cloudflare dependency health and certificate/tunnel expiry or credential failure for X2
  monitoring.

## Revisit conditions

Revisit before ET-02 implementation, when Cloudflare terms/pricing materially change, a directly
routable ISP address becomes available, native IPv6 becomes universally acceptable, or the operator
removes the external-service dependency.

## References

- [Cloudflare Tunnel](https://developers.cloudflare.com/tunnel/)
- [Cloudflare Tunnel FAQ](https://developers.cloudflare.com/cloudflare-one/faq/cloudflare-tunnels-faq/)
- [Cloudflare service-specific terms](https://www.cloudflare.com/service-specific-terms-application-services/)
- [ADR-0019](ADR-0019-public-authenticated-edge-and-future-enrolled-client-trust.md)
- [Milestone X1](../../development-guide/milestone-x1-production-infrastructure-and-recovery.md)
