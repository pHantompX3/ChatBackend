#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
MIGRATIONS_DIR="${REPO_ROOT}/scripts/database/flyway/wl_chat"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${REPO_ROOT}/scripts/config/local.secrets.env}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

DB_HOST="${WL_CHAT_DB_HOST:-host.docker.internal}"
DB_PORT="${WL_CHAT_DB_PORT:-1434}"
DB_URL="${WL_CHAT_MIGRATOR_DB_URL:-jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=wl_chat;encrypt=true;trustServerCertificate=false}"
MIGRATOR_USERNAME="${WL_CHAT_MIGRATOR_USERNAME:-messenger_migrator}"
MIGRATOR_PASSWORD="${WL_CHAT_MIGRATOR_PASSWORD:-}"
RUNTIME_LOGIN="${WL_CHAT_DB_USERNAME:-wl_chat_app}"
FLYWAY_NETWORK="${WL_CHAT_FLYWAY_DOCKER_NETWORK:-}"
FLYWAY_IMAGE="${WL_CHAT_FLYWAY_IMAGE:-}"
FLYWAY_TARGET="${WL_CHAT_FLYWAY_TARGET:-}"
SQL_TRUSTSTORE_FILE="${WL_CHAT_SQL_TRUSTSTORE_FILE:-}"

if [[ -z "${MIGRATOR_PASSWORD}" ]]; then
  echo "WL_CHAT_MIGRATOR_PASSWORD is required." >&2
  exit 1
fi

if [[ "${FLYWAY_IMAGE}" != *@sha256:* && "${FLYWAY_IMAGE}" != sha256:* ]]; then
  echo "WL_CHAT_FLYWAY_IMAGE must identify a reviewed digest-pinned migration image." >&2
  exit 1
fi

if [[ ! "${MIGRATOR_USERNAME}" =~ ^[A-Za-z][A-Za-z0-9_]{0,127}$ ]]; then
  echo "Unsafe migrator login name: ${MIGRATOR_USERNAME}" >&2
  exit 1
fi

if [[ ! "${RUNTIME_LOGIN}" =~ ^[A-Za-z][A-Za-z0-9_]{0,127}$ ]]; then
  echo "Unsafe runtime login name: ${RUNTIME_LOGIN}" >&2
  exit 1
fi

docker_run_args=(--rm -v "${MIGRATIONS_DIR}:/flyway/sql:ro")
if [[ "${DB_HOST}" == "host.docker.internal" ]]; then
  docker_run_args+=(--add-host=host.docker.internal:host-gateway)
fi
if [[ -n "${FLYWAY_NETWORK}" ]]; then
  docker_run_args+=(--network "${FLYWAY_NETWORK}")
fi
if [[ -n "${SQL_TRUSTSTORE_FILE}" ]]; then
  if [[ ! -f "${SQL_TRUSTSTORE_FILE}" ]]; then
    echo "SQL truststore not found: ${SQL_TRUSTSTORE_FILE}" >&2
    exit 1
  fi
  docker_run_args+=(-v "${SQL_TRUSTSTORE_FILE}:/flyway/tls/sql-truststore.p12:ro")
fi

flyway_args=(
  -url="${DB_URL}"
  -user="${MIGRATOR_USERNAME}"
  -password="${MIGRATOR_PASSWORD}"
  -locations="filesystem:/flyway/sql"
  -defaultSchema="platform"
  -schemas="platform,identity,messaging,audit"
  -table="flyway_schema_history"
  -placeholders.app_login="${RUNTIME_LOGIN}"
  -placeholders.app_password="unused-preprovisioned-runtime-password"
)
if [[ -n "${FLYWAY_TARGET}" ]]; then
  flyway_args+=(-target="${FLYWAY_TARGET}")
fi

docker run "${docker_run_args[@]}" "${FLYWAY_IMAGE}" \
  "${flyway_args[@]}" \
  migrate

echo "Hardened Flyway migration completed as ${MIGRATOR_USERNAME}."
