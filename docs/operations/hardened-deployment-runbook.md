# Hardened single-instance deployment runbook

## Boundary

This runbook rehearses one ChatBackend instance behind NGINX, with SQL Server and RabbitMQ on private
container networks. It is not a multi-instance or multi-tenant topology. Only NGINX publishes host
ports. Production hosting, certificate issuance, secret storage, off-host backup storage, and alert
delivery remain environment-owner responsibilities.

## Prepare

1. Copy `deploy/hardened.env.example` to the ignored `deploy/hardened.env` file.
2. Replace every placeholder and pin every image to a reviewed digest.
3. Set a strong `WL_CHAT_SQL_TRUSTSTORE_PASSWORD`, then run
   `scripts/deploy/generate-rehearsal-tls.sh`.
4. Run `scripts/deploy/validate-environment.sh`.
5. Review `docker compose --env-file deploy/hardened.env -f deploy/compose.hardened.yaml config`
   without publishing or sharing its rendered secret values.

The rehearsal CA is local-only. Production must replace every generated TLS artifact with material
issued, stored, and rotated by the deployment environment. The generator protects the ignored host
directory as owner-only while making the files readable to the non-root container UIDs that receive
their individual read-only bind mounts; production secret storage must apply service-specific file
ownership instead of relying on this local-rehearsal boundary.

Before deployment, generate the SBOM and scan the application plus every selected infrastructure
image. `WL_CHAT_SCAN_INFRA_IMAGES` is a comma-separated list of the digest-pinned SQL Server,
RabbitMQ, and NGINX image references from `deploy/hardened.env`. The repository-owned migration image
is built from the reviewed Flyway base and contains only the fixed SQL Server JDBC driver:

```text
scripts/ci/generate-sbom.sh
scripts/ci/build-migration-image.sh
WL_CHAT_SCAN_IMAGE=<built-app-image> \
WL_CHAT_SCAN_MIGRATION_IMAGE=<built-migration-image> \
WL_CHAT_SCAN_INFRA_IMAGES=<sql>,<rabbitmq>,<nginx> \
WL_CHAT_TRIVY_IMAGE=<reviewed-trivy-digest> scripts/ci/scan.sh
```

The filesystem scan stages only Git-tracked and non-ignored files. It never mounts ignored local
secrets, generated certificates, backups, reports, or build output into the scanner container. The
application image is scanned from a temporary read-only archive; the Docker daemon socket is never
mounted into the scanner. Scans use Trivy's offline dependency-identification mode after its signed
vulnerability databases are cached, so verification does not depend on Maven Central availability or
send the repository's dependency inventory to package-repository APIs. The scanner always loads the
version-controlled `.trivyignore.yaml`; every entry must remain narrowly scoped, evidenced in the
threat model, assigned to an owner, and time-limited.

Infrastructure scanning writes one report per selected image and finishes scanning the full set
before returning failure. This makes every upstream image's state visible in a single run; any
High/Critical result still blocks deployment unless it is upgraded or receives an explicit,
time-limited disposition under the threat-model policy.

As of 2026-08-27, the selected RabbitMQ, NGINX, application, and repository-owned migration images
pass. SQL Server 2022 CU26 retains the visible, time-limited local-rehearsal risk acceptance recorded
in the threat model; that acceptance does not approve the image for production.

## Deploy

- New empty deployment: `scripts/deploy/deploy.sh clean`
- Existing Milestone 8 database: `scripts/deploy/deploy.sh upgrade`

The clean path deliberately migrates immutable historical scripts before removing historical runtime
fixed-role membership. Both paths finish with forward-only permission migrations and negative runtime
permission assertions. The app never receives operator, migrator, backup, or restore credentials.

Verify:

```text
scripts/deploy/smoke-test.sh
WL_CHAT_K6_IMAGE=<reviewed-k6-image@sha256:...> \
WL_CHAT_LOAD_WS_BASE_URL=<wss-base-url> \
WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN=<allowed-origin> \
scripts/deploy/verify-websocket-policy.sh
docker compose --env-file deploy/hardened.env -f deploy/compose.hardened.yaml ps
docker inspect wl-chat-app-hardened --format '{{.Config.User}}'
```

Expected state: HTTPS health is UP; unauthenticated WebSocket access is rejected; the app runs as UID
10001; only proxy ports 80/443 are published. `/health/ready` remains UP during RabbitMQ loss and the
`audit-transport` readiness data reports `degraded=true` and a local fallback mode.

## Rollback

Set `WL_CHAT_PREVIOUS_APP_IMAGE` to the last verified application digest and run
`scripts/deploy/rollback.sh`. Database migrations are never reversed automatically. If the current
schema is not backward compatible with the previous image, keep the current image, apply a reviewed
forward fix, or use the guarded restore procedure during an approved recovery event.

## Operational checks

At minimum monitor:

- HTTPS certificate expiry at 30, 14, and 7 days;
- `/health/live`, `/health/ready`, container restarts, disk and volume capacity;
- `audit-transport` degradation plus `audit.events` and `audit.events.dlq` depth;
- age of the latest encrypted off-host backup (24-hour rehearsal objective);
- age and elapsed time of the latest successful isolated restore drill (60-minute rehearsal objective);
- failed migration, deployment, rollback, security-scan, and load-test jobs.

RabbitMQ degradation does not make the API unready because the audit path fails open to the bounded
local queue. It is still an operator alert: restore the broker promptly, watch local queue pressure,
then confirm queue/DLQ recovery and durable SQL audit writes.

`scripts/operations/check-hardened-status.sh` provides the repository-owned rehearsal checks for
health, seven-day certificate validity, restart counts, RabbitMQ queue/DLQ depth, local backup age, and
Docker disk use. It deliberately cannot prove off-host retention or alert delivery; the selected
production environment must supply that evidence.
