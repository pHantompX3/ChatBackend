# ChatMonitor Security and Privacy

## Trust boundary

ChatMonitor is an administrator-local tool. Native development binds directly to loopback. The
container listens on its private network interface, while Compose publishes it only to
`127.0.0.1:8090`; LAN or public exposure is not authorized. X2-B provides a dedicated SQL principal that
can select only `audit.monitoring_http_event`; tests prove that it cannot read raw audit/identity/
messaging data, write through the view, execute audit maintenance, or create schema objects.

## Configuration and secrets

- `.env` and `secrets/*` are ignored and must be readable only by the local operator.
- Fixture mode requires no credential.
- SQL mode fails startup without host, username, and exactly one password input.
- The container reads each SQL password from its profile's `/run/secrets/*` file; the private CA
  bundle is mounted separately. Neither passwords nor trust material are copied into the image or
  named volume.
- SQL encryption is mandatory and `trustServerCertificate=true` is rejected.
- Source credentials stay in server memory and are never returned by configuration/status APIs.
- No secret or private source configuration may use a `VITE_` prefix.
- `npm run scan:browser` rejects known server-side identifiers and private-key material in built
  dashboard assets.
- The browser environment registry is an allowlisted projection containing only ID, label,
  classification, source mode, and collection state. SQL hosts, databases, usernames, password-file
  paths, credentials, and trust-material paths remain server-only.
- Every SQL environment uses its own least-privilege credential file. A registry document may refer
  to that file but cannot contain an inline password.

## Data minimization

Accepted events contain allowlisted low-cardinality dimensions and numeric measurements. The monitor
must not persist or log message/attachment content, credentials, authorization/cookie headers, raw
request headers, arbitrary client logs, IP addresses, user agents, or direct user identities. The
fixture corpus follows the same rule.

## HTTP and logs

Fastify applies a small request-body bound. All responses receive CSP, no-store, no-referrer,
same-origin resource, MIME-sniffing, and clickjacking protections. Operational logs are structured;
sensitive key names are recursively redacted. Routes—not raw URLs—are logged to avoid query data.

## Local data boundary

SQLite contains only validated sanitized envelopes, bounded low-cardinality dimensions, numeric
measurements, collection state, cursors, gaps, and aggregates. It is not an audit backup. Unknown
versions/events/dimensions/measurements and content-bearing fields are rejected before persistence.
SQL transport encryption and certificate verification are mandatory.

Every event, cursor, state, history gap, and aggregate is keyed or partitioned by the stable
environment ID. The UI cannot request an unconfigured partition.

The container runs as UID/GID 10001 with a read-only root filesystem, all Linux capabilities
dropped, and `no-new-privileges`. It receives no Docker socket. Only `/var/lib/chat-monitor` is
writable through the named volume; deleting a container or running `docker compose down` therefore
does not delete history. Docker log rotation is bounded independently from SQLite retention.

Failure APIs return only allowlisted normalized dimensions and numeric measurements already in
SQLite. Alert delivery contains environment ID, rule ID, state, and observation time only; it does
not include user identifiers, request payloads, message content, raw SQL errors, or credentials.
Acknowledgement and suppression are loopback-only administration actions, and suppression is bounded
to 24 hours. Mutations additionally require a same-origin browser request when an `Origin` header is
present and the explicit `X-Chat-Monitor-Action: 1` action header. This prevents another website from
using the operator's browser to mutate local alert state.

The backend audit filter and monitoring projection never expose an unmatched raw request path. Such
records use the fixed `unmatched` route dimension, preventing identifiers or query-derived content
from becoming a metric label.

## Dependency inventory

Direct runtime dependencies are MIT licensed: Fastify, `@fastify/static`, Zod, `better-sqlite3`,
`mssql`, and Chart.js. Direct development dependencies are MIT licensed except Playwright, which is
Apache-2.0. Exact versions are locked in `package-lock.json`; CI uses `npm ci`, verifies the project,
and performs a high-severity production-dependency audit.
