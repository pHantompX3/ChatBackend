# Milestone 7 Implementation Guide

## API Contracts, Abuse Controls, and Structured Observability

**Project:** Private Messenger

**Milestone:** 7 - API hardening

**Database:** Microsoft SQL Server 2022

**Application stack:** Java 25, Quarkus 3.33 LTS, Maven

**Status:** Planned

**Last reviewed:** 2026-08-14

**Implementation snapshot:** Implementation has not started. The merged Milestone 6 baseline already
contains substantial pieces of this milestone, but they are inconsistent or unverified as one public
contract. This guide defines the remaining consolidation, OpenAPI, limit, authentication-throttle,
JSON logging, correlation, test, and Postman work.

---

## 0. Purpose and Scope

Milestone 7 makes the existing REST backend safe and predictable for independent browser, mobile,
desktop, and service clients before live transport is introduced. It does not add new messaging
semantics. It turns the current HTTP behavior into a generated, bounded, observable, and executable
contract.

This milestone is complete only when the backend can:

1. return one RFC 9457-compatible problem shape for every `/api/v1` failure,
2. preserve safe domain-specific codes without leaking internal exceptions,
3. generate an OpenAPI document that represents every application API operation,
4. detect drift between Java routes, OpenAPI, and committed Postman coverage,
5. reject oversized request bodies before application work is performed,
6. strictly bound every paginated collection read,
7. throttle login attempts consistently across both backend replicas,
8. emit one-line structured JSON logs with safe correlation fields,
9. propagate valid trace IDs and always generate server-owned request IDs, and
10. execute at least one positive and one meaningful negative Postman assertion for every operation.

Milestone 7 does **not** implement WebSockets, push notifications, presence, OpenTelemetry export,
metrics dashboards, TLS termination, NGINX configuration, CORS policy, browser-cookie/CSRF behavior,
general application quotas, invitation throttling, caching, dependency scanning, an SBOM, or load
testing. WebSockets remain Milestone 8; deployment and broader operational hardening remain Milestone
9.

Do not introduce a generic exception hierarchy, rate-limit framework, API gateway abstraction, log
event framework, test-fixture inheritance tree, or speculative database indexes. Add focused shared
types only where Milestone 7 has already demonstrated repeated contract behavior.

---

## 1. Deliverables and Exit Criteria

### Deliverables

- accepted ADR-0015 for the problem, OpenAPI, throttling, limit, and logging boundaries,
- shared immutable RFC 9457 problem representation and response factory,
- typed module mappers plus common framework/unexpected-error mapping,
- stable problem type, occurrence, code, request ID, and relevant response headers,
- SmallRye OpenAPI generation and a normalized committed JSON snapshot,
- explicit bearer-security and public-operation documentation,
- OpenAPI operation/schema/security drift tests,
- verified 32 KiB request-body limit and safe `413` behavior,
- one strict pagination policy with endpoint-specific defaults and maxima,
- seek pagination for active conversation members,
- SQL-backed fixed-window login throttling across replicas,
- trusted network-source resolution and hashed throttle scopes,
- structured JSON console and rolling-file logs,
- hardened request/trace ID validation and propagation,
- complete positive/negative Postman operation coverage and retained stateful journeys,
- updated README, specification, Postman workflow, changelog, and version metadata.

### Exit criteria

- every application-owned `/api/v1` operation appears in generated OpenAPI,
- every documented request/response record has an OpenAPI schema,
- public operations do not advertise bearer authentication and protected operations do,
- every `/api/v1` error response uses `application/problem+json`,
- response status and problem status always match,
- every problem contains a stable `code`, server request ID, and occurrence instance,
- malformed JSON, validation, authentication, authorization, `404`, `405`, `413`, `415`, `429`,
  domain failure, and unexpected failure paths have contract tests,
- internal exception class names, SQL diagnostics, stack traces, paths, hosts, and secrets never
  enter client responses,
- oversized request bodies are rejected and the maximum valid escaped message remains accepted,
- invalid pagination limits are rejected instead of silently clamped,
- collection reads are bounded and seek cursors remain deterministic,
- concurrent login attempts cannot bypass a throttle by reaching different replicas,
- login throttling does not disclose whether a username exists,
- `429` responses include an integer `Retry-After` value within the configured window,
- structured logs are valid JSON Lines and include request/trace correlation,
- logs and durable audit records never contain message bodies, passwords, or tokens,
- every Postman application operation has a positive and meaningful negative assertion,
- discovery, OpenAPI drift, Postman drift, Maven, and repository checks pass.

---

## 2. Current Baseline and Gap Map

### 2.1 Baseline already present

The merged Milestone 6 baseline provides:

- application-owned routes under `/api/v1` and Quarkus health endpoints under `/q/health`,
- module-specific problem records with `type`, `title`, `status`, `detail`, and `code`,
- `application/problem+json` for most domain, validation, and authentication failures,
- safe module-specific internal problem details for conversation, message, and delivery failures,
- privileged root-cause diagnostics in durable HTTP audit records,
- a server-generated `X-Request-Id` and propagated/generated `X-Trace-Id`,
- MDC correlation and response correlation headers,
- a global `quarkus.http.limits.max-body-size=32K` setting,
- message pagination with a default of 50 and maximum of 200,
- conversation pagination with a default of 50 and maximum of 100,
- user-directory pagination with a default of 20 and maximum of 50,
- seek cursors for conversations, users, and message history,
- rolling console/application/audit logs with textual correlation fields,
- version-controlled local, DevDocker, and production Postman environment examples,
- Postman route discovery, local validation, cloud synchronization, and drift inspection,
- real SQL Server integration tests and two-replica architecture requirements.

### 2.2 Gaps closed by Milestone 7

Milestone 7 closes these gaps:

1. duplicated problem records become one common serialization contract,
2. identity no longer owns a broad `RuntimeException` mapper,
3. framework and routing failures receive the same problem envelope,
4. problem occurrences become directly correlatable to request IDs,
5. the implemented API gains a generated and committed OpenAPI snapshot,
6. Postman coverage is checked against OpenAPI instead of source-regex discovery alone,
7. silently clamped pagination becomes strict validation,
8. the active-member collection becomes bounded and seek-paginated,
9. login attempts gain shared abuse throttling,
10. multiline text logs become safe JSON Lines,
11. inbound trace identifiers gain a bounded validation policy,
12. every operation gains explicit positive and negative Postman coverage.

No existing Flyway migration is rewritten. Milestone 7 adds a forward migration only for the login
throttle table and any index proven necessary for the newly bounded member query.

---

## 3. Prerequisites

Before implementation, verify the merged Milestone 6 baseline:

```bash
./scripts/database/validate-flyway-naming.sh
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
./mvnw --batch-mode --no-transfer-progress clean verify
git diff --check
```

Work on `codex/milestone-7`. Advance the application development version to `0.7.0-SNAPSHOT` in the
planning change because this milestone adds backward-compatible platform capability plus one
deliberate pre-1.0 member-list contract correction. Do not create a release tag merely for starting
or completing the milestone.

ADR-0015 is authoritative for shared problem serialization, Java-to-OpenAPI-to-Postman contract
direction, strict limits, replica-safe login throttling, and structured correlation. If
implementation needs materially different throttle storage or proxy trust semantics, amend or
supersede the ADR before coding the alternative.

---

## 4. Milestone 7 Design Decisions

### 4.1 One problem representation, focused mappings

Use a common record conceptually shaped as:

```json
{
  "type": "urn:wl-chat:problem:conversation-access-denied",
  "title": "Conversation access denied",
  "status": 404,
  "detail": "Conversation was not found",
  "instance": "urn:wl-chat:request:7e1c...",
  "code": "CONVERSATION_ACCESS_DENIED",
  "requestId": "7e1c..."
}
```

The exact Java type belongs in `com.wayden.messenger.common.api`. Keep the record closed and explicit;
do not use an untyped map for normal problem fields. Optional extensions are justified only by an
actual client contract, such as a bounded field-error list.

The common factory owns:

- content type,
- type and instance construction,
- request ID inclusion,
- response status,
- `WWW-Authenticate` for `401`,
- `Allow` when a reliable method set exists for `405`, and
- `Retry-After` for `429`.

Correlation must start before route matching so unknown `/api/v1` paths and unsupported methods still
receive request/trace headers. If Quarkus rejects an oversized body before the Jakarta REST filter
chain, add one narrow Vert.x HTTP failure adapter for pre-JAX `/api/v1` failures. That adapter must use
the same problem factory and correlation generator; it must not become a second application routing
or exception framework.

Domain mappers continue to own their public code/title/detail/status mappings. This avoids a single
giant mapper coupled to every domain exception. The common fallback handles only cross-cutting JAX-RS,
JSON, validation, and unexpected failures.

### 4.2 Safe unexpected-failure behavior

Unexpected exceptions return:

```text
status: 500
code: INTERNAL_ERROR
detail: An unexpected error occurred
```

The response includes correlation but no implementation diagnostics. The logger receives the full
throwable. `RequestAuditContext.recordFailure(...)` receives the cause so the RabbitMQ-to-SQL audit
flow retains the bounded failure type, message, location, and root-cause location already established
by prior milestones.

Do not map `Error` subclasses as normal application failures. Do not return a prebuilt raw
`WebApplicationException` response for `/api/v1`; normalize its safe status into the common problem
contract.

### 4.3 OpenAPI is generated, not hand-authored

Add the pinned Quarkus SmallRye OpenAPI extension. Use MicroProfile OpenAPI annotations on resources
and DTOs where reflection alone cannot communicate:

- operation purpose and stable operation ID,
- bearer authentication requirement,
- public-operation override,
- path/query/header parameter constraints,
- success status and response schema,
- problem responses that are meaningful for the operation,
- pagination defaults/maxima,
- request examples only when they add contract value.

Generate `docs/api/openapi.json` in a deterministic normalized form. Do not include build timestamps,
localhost ports, or machine-specific paths. Swagger UI may be enabled in development/test for
inspection but must not be enabled as a public production surface.

The build must compare the runtime-generated normalized document with the committed snapshot and
fail on drift. A separate operation-inventory assertion compares application routes to OpenAPI paths
and methods. Do not rely only on the committed snapshot, because two stale artifacts could otherwise
agree with each other.

### 4.4 Application endpoint boundary

OpenAPI completeness applies to application-owned `/api/v1` operations, including `/api/v1/ping`.
Quarkus-owned `/q/health`, `/q/health/live`, and `/q/health/ready` remain documented in the operations
runbook and Postman collection, but they are not forced into the application OpenAPI document.

### 4.5 Request-size policy

Retain the 32 KiB global body limit. It is large enough for the 4,000 UTF-16-code-unit message limit
even when JSON represents characters with six-byte Unicode escape sequences, while remaining small
enough to reject accidental or abusive payloads early.

Required behavior:

- the maximum valid escaped message succeeds,
- one byte beyond the configured request-body boundary returns `413`,
- the `413` response uses the common problem envelope when the path is under `/api/v1`,
- pre-JAX rejection still receives server request/trace IDs and the same correlation headers,
- a misleading or absent `Content-Length` does not bypass streaming enforcement,
- endpoints that consume no body continue to declare wildcard/no-content behavior deliberately,
- domain field limits remain authoritative below the transport cap.

Do not add per-route body-limit infrastructure unless the global cap and field validators cannot
express a demonstrated requirement.

### 4.6 Pagination policy

Use one small common validator for numeric page sizes and cursor length, with endpoint-owned defaults
and maxima:

| Collection | Default | Maximum | Cursor/order |
|---|---:|---:|---|
| user directory | 20 | 50 | normalized username, user ID |
| conversations | 50 | 100 | updated time, conversation ID |
| active members | 50 | 100 | joined time, user ID |
| message history | 50 | 200 | message sequence |

Rules:

- omitted limit uses the documented default,
- explicit limits below 1 or above the maximum return `400`,
- fractional, overflowed, signed-with-junk, and repeated ambiguous values return `400`,
- cursors are opaque, URL-safe, query-bound where required, and length-limited before decoding,
- responses fetch at most `limit + 1` to determine continuation,
- stable tie-breakers prevent duplicates or omissions,
- authorization is checked before returning private collection details.

Replace current `Math.max/Math.min` clamping in conversation and user search with explicit rejection.

The member-list response becomes the existing common page envelope with `items` and `nextCursor`.
Use the existing `(conversation_id, left_at, joined_at)` access path first. Add a forward-only index
only if the SQL Server execution plan or integration test proves the existing index insufficient.

### 4.7 Login throttle policy

Throttle only `POST /api/v1/sessions` in this milestone. Use two fixed-window scopes:

```text
ACCOUNT  = SHA-256(normalized username)
SOURCE   = SHA-256(canonical resolved network source)
```

Recommended initial defaults, configurable through application properties:

| Scope | Limit | Window |
|---|---:|---:|
| account | 10 attempts | 5 minutes |
| source | 30 attempts | 1 minute |

Treat these as safe starting values, not product entitlements. Tests use explicit configuration and
a controlled clock; they must not sleep.

Every attempt reserves capacity before credential verification. If either scope is exhausted:

- do not execute password verification,
- return `429` and `AUTHENTICATION_RATE_LIMITED`,
- use a generic detail that does not disclose account existence,
- include `Retry-After` as ceiling seconds until the latest applicable window expires,
- audit the scope type and retry interval but never the raw scope value.

Successful login does not reset the shared window. This keeps the policy deterministic and prevents
valid credentials from being used to erase source pressure.

### 4.8 Network-source trust

Default to the socket peer address. Honor `Forwarded` or `X-Forwarded-For` only when the immediate
peer matches an explicitly configured trusted-proxy address/CIDR. Reject malformed forwarded chains
for throttle identity and fall back safely to the peer; never trust a client-supplied first-hop header
by default.

Normalize IPv4/IPv6 textual forms before hashing. Do not place raw network sources in application
logs. The existing privileged HTTP audit policy may retain a bounded source address under its own
access controls.

### 4.9 Structured logging and correlation

Add the Quarkus JSON logging extension compatible with the pinned platform. Configure console,
application rolling file, and dedicated HTTP/audit transport file as JSON Lines. Preserve current
rotation paths and retention unless operations documentation deliberately changes them.

Minimum structured request fields:

```text
timestamp, level, logger, event, requestId, traceId,
method, route/path, status, durationMs
```

Use a normalized route template when available; otherwise use a safe path without query text. Do not
pretty-print JSON inside a log message. Do not serialize entire request/response objects.

`X-Request-Id` remains server-generated for every request. `X-Trace-Id` may be accepted from a client
only if it is 1-128 characters and contains the documented safe ASCII set. Invalid or oversized trace
IDs are replaced, not reflected. Both headers are returned on success and failure.

This milestone does not add spans or a tracing collector. The trace ID is a correlation contract that
Milestone 8 can propagate through WebSocket events and that future observability work can bridge to a
standard tracing system.

### 4.10 Postman contract direction

Keep the curated collection and run-all journey. Change discovery/validation so OpenAPI is the
operation inventory after the snapshot is generated:

1. compare Java-owned runtime operations to generated OpenAPI,
2. compare OpenAPI operations to the committed main Postman collection,
3. preserve curated examples and tests when refreshing operation metadata,
4. fail if an application operation is missing or an obsolete operation remains,
5. keep cloud sync as an explicit credentialed step.

Every operation needs one positive assertion and one meaningful negative assertion. A negative test
may be unauthenticated, forbidden, malformed, invalid, not found, conflict, oversized, or throttled,
depending on the operation. A fabricated invalid method is not sufficient when the operation has a
real domain or authorization failure worth testing.

---

## 5. Step 1 - Advance Version and Add Contract Dependencies

Update `pom.xml` to `0.7.0-SNAPSHOT`. Add only the Quarkus extensions required for:

- SmallRye OpenAPI generation, and
- JSON log formatting.

Use versions from the existing Quarkus BOM. Do not add a separate OpenAPI generator, logging stack,
rate-limit library, cache, or tracing SDK.

Add test dependencies only if the existing Jackson, RestAssured, and JSON tooling cannot inspect the
generated contract. Prefer the dependencies already supplied by Quarkus.

Update `CHANGELOG.md` under `[Unreleased]` for the implementation-ready plan and development version.

---

## 6. Step 2 - Consolidate RFC 9457 Problems

### 6.1 Common API types

Add focused common types such as:

```text
ApiProblem
ApiProblemFactory
ApiProblemType
```

Names may vary, but responsibilities must remain small. `ApiProblemFactory` uses the request-scoped
correlation context and never reaches into domain repositories.

Use lowercase kebab-case in problem type URNs and retain uppercase underscore codes for existing
client compatibility.

### 6.2 Refactor domain mappers

Refactor identity, session, conversation, message, and delivery mappers to return the common record.
Remove duplicated nested `*Problem` records. Replace identity's broad runtime mapping with a typed
identity exception contract and let the common fallback own genuinely unexpected failures.

Preserve all currently valid module codes and privacy-preserving `404` behavior. Do not convert
private-resource outcomes into revealing `403` responses merely for consistency.

### 6.3 Map framework failures

Add explicit contract coverage for:

- empty/malformed JSON,
- unknown JSON properties,
- bean-validation violations,
- invalid path/query/header values,
- missing or unsupported media type,
- unacceptable response media type when applicable,
- unknown `/api/v1` route,
- unsupported method on an `/api/v1` route,
- oversized body,
- authentication failure,
- rate-limit rejection,
- unexpected runtime failure.

Make request correlation a pre-matching concern. Confirm experimentally whether body-size rejection
occurs before Jakarta REST processing in the pinned Quarkus version; when it does, cover only that
transport-level path with the narrow HTTP failure adapter defined in section 4.1.

Do not rewrite successful `204` responses or Quarkus health behavior through the problem layer.

### 6.4 Audit and logging

For every internal error:

- log the throwable once at the owning boundary,
- call `recordFailure` with the original cause chain,
- preserve safe operation and target identifiers,
- avoid duplicate stack traces from both domain and fallback mappers,
- verify the client body contains none of the privileged diagnostics.

---

## 7. Step 3 - Generate and Verify OpenAPI

### 7.1 Document operations

Assign stable operation IDs based on capability rather than Java method name, for example:

```text
sessionLogin
conversationCreateDirect
messageList
deliveryAcknowledgeRead
```

Document all application resources:

- ping,
- bootstrap admin,
- invitations,
- sessions,
- user directory,
- conversations and membership,
- messages,
- delivery/read positions and sender status.

Declare one bearer token security scheme. Public bootstrap, redemption, login, and ping operations
must explicitly show no bearer requirement. Invitation creation/revocation and all conversation,
message, delivery, and administrative session operations remain protected according to existing
authorization behavior.

### 7.2 Generate normalized snapshot

Create a repository script that exports or copies the runtime-generated JSON, then normalizes object
key order and removes approved volatile fields. Write only `docs/api/openapi.json`. The script must be
deterministic and fail clearly if the application contract cannot be generated.

Provide separate modes or scripts for:

- refreshing the snapshot intentionally, and
- validating current output against the snapshot without modifying it.

Do not hide drift by refreshing automatically during `verify`.

### 7.3 Contract verification

Add tests that assert:

- exact application path/method inventory,
- unique nonblank operation IDs,
- bearer scheme definition,
- correct public/protected security declarations,
- request and success schemas,
- common problem schema and content type,
- pagination parameter defaults and maxima,
- declared `429` plus `Retry-After` for session login,
- absence of secrets or environment-specific server URLs.

---

## 8. Step 4 - Enforce Request and Pagination Limits

### 8.1 Request-body tests

Add black-box API tests for bodies below, at, and above relevant boundaries. Construct payloads by
actual encoded byte length, not Java character count. Cover a streamed/chunked request where the test
client supports it.

Ensure the maximum valid message body still succeeds. This is a regression requirement from
Milestone 5 and must not be lost while normalizing `413` behavior.

### 8.2 Common pagination validation

Extract only the repeated parsing/range policy. Cursor semantics remain within their owning module.
Do not create a generic pagination repository or inheritance hierarchy.

Change conversation and user directory limits from clamping to rejection. Keep message history's
existing strict range. Ensure OpenAPI and problem examples describe the same accepted range.

### 8.3 Paginate active members

Extend the conversation repository/service/API with deterministic active-member seek pagination.
The cursor must be conversation-bound and tamper-evident to the same degree as existing opaque
cursors. A cursor created for one conversation must fail when applied to another.

Test stable traversal with identical join timestamps, membership removal between pages, and attempts
by non-members. Preserve the existing rule that only active members can inspect the active list.

Do not add an index before examining the actual SQL Server plan with the current active-member index.

---

## 9. Step 5 - Add Shared Login Throttling

### 9.1 Forward-only migration

Add a migration later than `V20260813150000`, with the planned name:

```text
V20260814100000__create_identity_authentication_rate_limit.sql
```

The table should contain only the minimum shared state:

```text
scope_type       bounded value: ACCOUNT or SOURCE
scope_hash       binary SHA-256 value
window_started_at
window_expires_at
attempt_count
updated_at
```

Use a composite primary/unique key on `(scope_type, scope_hash)`, checks for positive counts and valid
window ordering, and an expiry access path only if bounded cleanup needs it. Grant the runtime
principal `SELECT`, `INSERT`, `UPDATE`, and bounded `DELETE` on this table; retain least privilege on
all other identity tables. The runtime principal still cannot alter schema.

### 9.2 Repository transaction

Implement one focused repository transaction that reserves account and source capacity atomically
or returns the longest retry interval. Use SQL Server locking/conditional update semantics so
concurrent requests cannot all observe spare capacity and oversubscribe it.

Required properties:

- both scope decisions use one database clock/transaction snapshot,
- expired windows reset safely,
- equal-boundary behavior is deterministic,
- counters never exceed their configured numeric domain,
- bounded stale-row cleanup cannot delete an active window,
- a deadlock or database failure becomes a safe authentication internal error, not fail-open access.

Do not add generic transaction retries unless an observed SQL Server deadlock and an ADR justify the
policy. Login throttling must fail closed with a safe `500` on unavailable shared state rather than
silently disabling protection.

### 9.3 Application integration

Normalize the username and resolve the network source before password verification. Hash both
scopes, reserve capacity, then perform the existing login flow. Retain the same invalid-credential
response for unknown username and wrong password.

Put safe throttle outcome, scope type, and retry seconds into audit metadata. Never include raw
username, source, password, or hashes in logs or public problems.

### 9.4 Throttle tests

Cover:

- attempts below the limit,
- the exact limit boundary,
- first rejected attempt,
- automatic recovery after the window,
- account and source scopes independently,
- unknown and existing usernames with indistinguishable rejection,
- successful attempts consuming capacity,
- concurrent attempts against one scope,
- two repository/service instances sharing SQL Server,
- trusted and untrusted forwarded headers,
- IPv4/IPv6 normalization,
- migration constraints and runtime permissions,
- `Retry-After` correctness,
- audit/log redaction.

---

## 10. Step 6 - Emit Structured JSON Logs

### 10.1 Configuration

Configure JSON Lines for:

- console output used by containers,
- the rolling application file,
- the dedicated HTTP/audit transport file.

Keep local developer readability through a documented opt-in text profile only if needed. Production
and container defaults must remain structured. Preserve rotation size, date suffix, compression, and
backup count unless the operations plan changes them explicitly.

### 10.2 Request lifecycle events

Replace the current pretty-printed `incoming.request` message with concise start/completion events.
Prefer one completion event containing status and duration; emit a start event only when it provides
diagnostic value not present in the durable audit flow.

Do not log raw query strings. Put bounded safe values in structured fields through MDC or the JSON
logging facility. Remove request-scoped MDC values after every response, including aborted and error
responses, so pooled threads cannot inherit another request's fields.

### 10.3 Correlation tests

Verify:

- server request IDs are valid UUIDs and ignore spoofed `X-Request-Id`,
- valid trace IDs are returned unchanged,
- absent/invalid/oversized trace IDs are replaced,
- success, validation, authentication, throttle, and internal error responses return both headers,
- problem request ID and instance match the response header,
- log and durable audit correlation values match,
- consecutive requests on reused threads do not leak MDC values,
- representative log lines parse as one JSON object each.

---

## 11. Step 7 - Complete Postman Coverage

### 11.1 Main collection

Update the main collection from the generated OpenAPI operation inventory while preserving curated
request examples, variable captures, and tests. Include response examples for success plus the most
representative problem response for each operation.

Every `/api/v1` operation must have:

- at least one positive status/body/header assertion,
- at least one negative status/problem/code/correlation assertion,
- bearer usage matching OpenAPI,
- no committed real tokens, credentials, user IDs, or environment hosts.

### 11.2 Stateful run-all journey

Extend the run-all flow without turning it into an exhaustive matrix. It should prove at least:

- normal onboarding/login still works,
- malformed input produces the common problem,
- bounded pagination works,
- delivery/read reconciliation remains intact,
- repeated login attempts reach `429`,
- login recovers through an isolated low-window test configuration or dedicated test endpoint setup,
- correlation headers exist throughout the flow.

Never make the normal developer environment wait for a production-size throttle window. Postman
tests must use a configurable local threshold/window or a deterministic reset performed through test
setup, not a public reset API.

### 11.3 Environments and synchronization

Add only non-secret configuration variables needed by executable tests. Keep examples for Local,
DevDocker, and Production structurally aligned. Do not put server-side throttle secrets or trusted
proxy lists in Postman environments.

Run discovery before cloud sync, then sync every configured collection/environment target and inspect
for drift. Committed repository files remain authoritative.

---

## 12. Step 8 - Test Matrix

### 12.1 Problem contract tests

- shared factory serialization and headers,
- every domain mapper's public mappings,
- malformed/unknown JSON behavior,
- validation aggregation and redaction,
- framework `404`, `405`, `413`, `415`, and `406` where applicable,
- safe unexpected failure with privileged audit cause retention,
- response/problem/header correlation consistency.

### 12.2 OpenAPI tests

- path/method completeness,
- operation ID uniqueness,
- schema resolution,
- bearer/public security correctness,
- success/problem response coverage,
- normalized snapshot drift,
- OpenAPI-to-Postman operation parity.

### 12.3 Limit and pagination tests

- body byte boundaries,
- default/minimum/maximum page sizes,
- zero/negative/over-maximum/non-integer/overflowed limits,
- malformed, oversized, cross-query, and cross-conversation cursors,
- deterministic traversal and concurrent-removal behavior,
- bounded repository fetch size.

### 12.4 Throttle tests

- unit tests with controlled clock,
- SQL Server constraint, cleanup, concurrency, and two-instance tests,
- API `429` and `Retry-After`,
- non-enumerating details,
- proxy trust and source normalization,
- logging/audit redaction.

### 12.5 Logging and regression tests

- JSON Lines parseability and expected fields,
- correlation propagation and cleanup,
- no secrets/message content in representative logs,
- all existing identity, session, conversation, message, delivery, and audit tests,
- Postman discovery/validation/drift tests.

Avoid a shared integration-test superclass. Reuse small stateless helpers or composition only when
the hardening matrix demonstrates stable repetition.

---

## 13. Documentation and Compatibility Updates

Update together:

- `README.md` version and Milestone 7 planning/implementation snapshot,
- `CHANGELOG.md` `[Unreleased]`,
- this guide's status and implementation snapshot,
- the Milestone 7 specification snapshot,
- `docs/api/openapi.json`,
- `postman/README.md` source-of-truth flow,
- Postman collections and example environments,
- configuration documentation for limits, throttle windows, trusted proxies, JSON logs, and
  correlation headers,
- SQL principal/permission documentation for the throttle table,
- ADR-0015 if implementation changes an accepted boundary.

OpenAPI generation must not imply a stable 1.0 compatibility promise. The backend remains
`0.7.0-SNAPSHOT`, but contract changes must still be deliberate, documented, and tested.

---

## 14. Local Validation Sequence

Run in this order:

```bash
./scripts/database/validate-flyway-naming.sh
./scripts/openapi/refresh-openapi.sh
./scripts/openapi/validate-openapi.sh
./scripts/postman/discover-postman.sh
./scripts/postman/validate-postman.sh
./mvnw --batch-mode --no-transfer-progress clean verify
git diff --check
git status --short
```

The OpenAPI refresh script is created during implementation; after refreshing intentionally, inspect
the diff before validation. When Postman Cloud credentials are configured:

```bash
./scripts/postman/sync-postman.sh
./scripts/postman/inspect-postman.sh
```

If local port `8081` is occupied during Quarkus tests, use an isolated test port rather than stopping
an unrelated application:

```bash
./mvnw --batch-mode --no-transfer-progress -Dquarkus.http.test-port=0 clean verify
```

---

## 15. Common Failure Modes

1. **Replacing domain semantics with one giant mapper**

   Share serialization, not every domain decision.

2. **Leaving identity mapped to all runtime exceptions**

   Use a typed identity mapper so unrelated failures retain correct ownership.

3. **Returning raw `WebApplicationException` responses**

   Normalize `/api/v1` errors into the common problem envelope.

4. **Putting root causes into `detail`**

   Keep diagnostics in logs and privileged audit metadata only.

5. **Treating a committed OpenAPI file as proof of runtime completeness**

   Compare runtime routes, generated OpenAPI, snapshot, and Postman independently.

6. **Hand-editing generated OpenAPI**

   Fix Java annotations/DTOs and regenerate deterministically.

7. **Generating away curated Postman journeys**

   Use OpenAPI for inventory while preserving executable stateful tests.

8. **Silently clamping invalid limits**

   Reject invalid explicit input and document the range.

9. **Leaving an unbounded member collection**

   Introduce deterministic seek pagination before public exposure.

10. **Adding speculative pagination indexes**

    Inspect the actual SQL Server plan before adding a forward migration.

11. **Using per-instance login counters**

    Store atomic windows in shared SQL Server so replicas agree.

12. **Building a generic quota platform**

    Implement only account/source login throttling required by this milestone.

13. **Trusting forwarding headers from any caller**

    Accept them only through configured trusted peers.

14. **Locking accounts as a throttle**

    Keep traffic windows separate from durable user status.

15. **Logging JSON inside a formatted text line**

    Emit exactly one structured JSON object per line.

16. **Logging raw paths, queries, or request objects indiscriminately**

    Use normalized routes and an allowlist of safe fields.

17. **Treating a trace ID as authentication**

    It is untrusted correlation metadata with validation, never authorization evidence.

18. **Adding OpenTelemetry before there is a collector plan**

    Preserve a clean trace correlation contract and defer export infrastructure.

---

## 16. Definition of Done

Milestone 7 is done when:

- ADR-0015 remains accepted and implementation matches it,
- the Maven development version is `0.7.0-SNAPSHOT`,
- every deliverable and exit criterion in this guide is implemented,
- every `/api/v1` failure uses the common RFC 9457 problem contract,
- all internal failures retain privileged diagnostics without client leakage,
- generated OpenAPI, committed snapshot, Java routes, and Postman operations agree,
- every application operation has positive and negative Postman assertions,
- body and pagination boundaries are strict and tested,
- active-member traversal is bounded and deterministic,
- login throttling is atomic across shared SQL Server and both replicas,
- forwarding headers are trusted only through configured peers,
- logs are safe JSON Lines with request/trace correlation,
- all existing durable message/delivery semantics remain unchanged,
- no WebSocket, general quota, cache, or tracing infrastructure was added prematurely,
- Postman Cloud targets are synchronized with no drift,
- the canonical Maven build and repository validation commands pass,
- `CHANGELOG.md`, README, specification, guide, OpenAPI, and operational docs describe actual behavior.

---

## 17. Recommended Implementation Order

1. Advance Maven and documentation to `0.7.0-SNAPSHOT`.
2. Add OpenAPI and JSON logging extensions from the Quarkus BOM.
3. Add the common problem record/factory and focused tests.
4. Refactor typed domain mappers and add framework/unexpected mappings.
5. Harden request/trace ID validation and occurrence correlation.
6. Annotate application operations and generate the first normalized OpenAPI snapshot.
7. Add runtime-route/OpenAPI/snapshot contract verification.
8. Normalize request-body and strict pagination behavior.
9. Add bounded active-member seek pagination and validate its SQL plan.
10. Add the forward login-throttle migration and least-privilege assertions.
11. Implement atomic hashed account/source throttling and proxy trust.
12. Convert console/application/audit logs to safe JSON Lines.
13. Update Postman discovery to use OpenAPI inventory and complete positive/negative coverage.
14. Synchronize and inspect configured Postman Cloud targets.
15. Update changelog, configuration, permission, and completion-status documentation.
16. Run the complete validation sequence and review for unnecessary abstractions.

---

## 18. References

- `README.md`
- `AGENTS.md`
- `CHANGELOG.md`
- `docs/private-instant-messaging-platform-spec-v0.2-sql-server.md`
- `docs/development-guide/milestone-4-conversations-step-by-step.md`
- `docs/development-guide/milestone-5-messaging-step-by-step.md`
- `docs/development-guide/milestone-6-delivery-and-read-state-step-by-step.md`
- `docs/development-guide/versioning-and-changelog-policy.md`
- `docs/architecture/decision/ADR-0015-harden-http-contracts-and-authentication-throttling.md`
- `docs/architecture/single-host-layered-container-architecture.md`
- `docs/database/sql-server-principals-and-permissions.md`
- `docs/operations/environment-strategy-and-rollout-plan.md`
- `postman/README.md`
- RFC 9457, Problem Details for HTTP APIs
- Quarkus SmallRye OpenAPI guide for the pinned platform
- Quarkus logging guide for the pinned platform
