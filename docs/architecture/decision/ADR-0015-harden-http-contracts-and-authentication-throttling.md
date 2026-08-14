# ADR-0015: Harden HTTP Contracts and Authentication Throttling

- **Status:** Accepted
- **Date:** 2026-08-14
- **Milestone:** 7 - API hardening

## Context

The backend has stable domain APIs, request and trace identifiers, a global request-body limit,
bounded pagination in the principal history endpoints, module-specific problem responses, and
version-controlled Postman artifacts. Those capabilities grew incrementally and are not yet one
verified public contract:

- problem records are duplicated and framework-generated errors can have a different shape;
- the broad identity runtime-exception mapper can claim failures outside the identity module;
- problem bodies do not identify the failed request occurrence;
- there is no generated OpenAPI artifact;
- pagination validation is inconsistent because some APIs reject invalid limits while others clamp;
- login has no abuse throttle shared by both backend replicas;
- logs include correlation fields but use multiline human-readable text rather than JSON Lines;
- Postman discovery reads source routes directly because no OpenAPI snapshot exists.

Milestone 7 must harden those boundaries without changing durable messaging semantics, introducing
a general gateway framework, or making one backend instance authoritative for security state.

## Decision

### One RFC 9457-compatible problem contract

All `/api/v1` failures use `application/problem+json` and a shared immutable problem representation
containing the RFC fields `type`, `title`, `status`, `detail`, and `instance`, plus the stable
extensions `code` and `requestId`.

- `type` is a stable application-owned URN derived from the public problem code.
- `instance` is a request-occurrence URN derived from the server-generated request ID.
- `detail` is safe for an unprivileged client and never contains SQL, stack, class, host, file, or
  credential information.
- the response status and problem `status` always agree.
- existing public `code` values remain stable unless the old value is demonstrably incorrect.

Domain exception mappers retain ownership of domain-to-status decisions. A common problem factory
owns serialization and headers. Focused common mappers cover malformed JSON, bean validation,
unsupported media types, oversized requests, missing routes/methods under `/api/v1`, and unexpected
exceptions. Operational `/q/*` endpoints remain platform-managed.

The broad `IdentityExceptionMapper implements ExceptionMapper<RuntimeException>` is replaced by a
typed identity mapper so it cannot relabel unrelated failures. Unexpected failures are logged with
their cause and recorded in privileged audit metadata while clients receive only the safe problem.

### Java annotations generate the OpenAPI contract

Application routes, DTOs, and MicroProfile OpenAPI annotations are the authoring source. The build
generates a normalized OpenAPI JSON snapshot under `docs/api/openapi.json`. That snapshot is generated
and validated, never edited manually.

The contract direction is:

```text
Java routes + DTOs + OpenAPI annotations
                  |
                  v
       generated OpenAPI snapshot
                  |
                  v
 Postman endpoint coverage and examples
```

Every application-owned `/api/v1` operation appears in OpenAPI with authentication, request and
response schemas, success responses, and applicable problem responses. Quarkus-managed `/q/*`
health endpoints are operational contracts and are not counted as application API operations.

### Limits fail explicitly

The existing global 32 KiB body limit is retained and tested. Endpoint field constraints remain
narrower where appropriate. Paginated endpoints use documented defaults and hard maxima and reject
limits outside the accepted range with a problem response; they do not silently clamp client input.

The previously unpaged active-member collection becomes seek-paginated in Milestone 7. Because the
project is pre-1.0 and the response must be bounded before public exposure, this deliberate contract
change is made now and represented in OpenAPI and Postman.

### Authentication throttling is targeted and shared

Login throttling is implemented as a focused capability for `POST /api/v1/sessions`, not as a generic
rate-limit framework. SQL Server stores short-lived fixed-window counters so either backend replica
observes the same decision.

Two independent hashed scopes are checked:

- the normalized username, limiting targeted credential attempts; and
- the resolved network source, limiting broad credential spraying.

Raw usernames and network addresses are not stored in the rate-limit table. Source forwarding
headers are accepted only from explicitly configured trusted proxies; otherwise the socket peer is
used. Limits, windows, and the trusted-proxy set are configuration, with safe bounded defaults.

Every login attempt consumes capacity before password verification. Rejection returns `429`, the
same generic authentication detail regardless of account existence, and a bounded `Retry-After`
header. Expired rows are removed in bounded batches during login traffic. No scheduler, cache,
distributed lock service, or third-party retry/rate-limit framework is introduced.

### Correlation and logging

The backend always generates a new request ID. A caller-provided trace ID is propagated only when it
matches the documented length and character policy; otherwise the backend generates one. Both IDs
are returned as headers, included in problem occurrences, written to MDC, and retained in durable
HTTP audit events.

Runtime console and rolling application/audit logs use one JSON object per line. Structured fields
include timestamp, severity, logger, message/event name, request ID, trace ID, HTTP method, normalized
route or safe path, response status, and duration where applicable. Message bodies, passwords,
session/invitation tokens, authorization headers, raw query strings, and SQL parameter payloads are
never logged.

## Consequences

### Positive

- Clients receive one predictable failure envelope for all application APIs.
- OpenAPI, Postman, and the implemented routes can be checked for drift.
- Both replicas enforce the same authentication throttle.
- Pagination and body-size behavior are explicit and testable.
- Logs can be ingested without parsing multiline text.
- Request occurrences can be correlated across client responses, logs, and durable audit records.

### Trade-offs

- Milestone 7 adds one small SQL table and migration for shared login counters.
- The active-member list response changes to a page representation before 1.0.
- OpenAPI annotations add some resource-level documentation code.
- A generated OpenAPI snapshot must be refreshed whenever the API contract changes.
- SQL-backed throttling adds a small write on login attempts; it is intentionally limited to the
  authentication boundary rather than all requests.

## Rejected alternatives

### Per-instance in-memory login counters

Rejected because clients could alternate replicas, counters disappear on restart, and security
behavior would depend on routing.

### Generic rate-limit framework

Rejected because Milestone 7 requires one targeted authentication policy. General endpoint quotas,
plans, or transport limits do not yet exist.

### Account lockout stored on the user row

Rejected because an attacker could deliberately lock out a known user and because throttling is
traffic control, not durable account status.

### Hand-maintained OpenAPI

Rejected because it would create another independently editable contract that could drift from Java
routes and DTOs.

### Generating the entire curated Postman suite from OpenAPI

Rejected because OpenAPI describes operations and schemas but not the stateful onboarding,
conversation, messaging, and reconciliation journeys. OpenAPI drives endpoint coverage; curated
Postman behavior remains executable test code.

## Revisit conditions

Revisit this decision if:

- authentication moves to an external identity provider or edge gateway;
- measured login volume makes SQL-backed counters a material bottleneck;
- the deployment gains a shared cache designed for security-critical atomic counters;
- API compatibility requires `/api/v2`;
- a standard distributed tracing system replaces the current request/trace header contract;
- product requirements introduce general per-user, per-organization, or paid-tier quotas.

