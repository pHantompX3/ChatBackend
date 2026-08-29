#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
MIGRATIONS_DIR="${REPO_ROOT}/scripts/database/flyway/wl_chat"

DEFAULT_SECRETS_FILE="${REPO_ROOT}/scripts/config/local.secrets.env"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${DEFAULT_SECRETS_FILE}}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

SA_PASSWORD="${MSSQL_SA_PASSWORD:-}"
DB_HOST="${WL_CHAT_DB_HOST:-}"
DB_PORT="${WL_CHAT_DB_PORT:-}"
FLYWAY_NETWORK="${WL_CHAT_FLYWAY_DOCKER_NETWORK:-}"
APP_LOGIN="${WL_CHAT_DB_USERNAME:-wl_chat_app}"
APP_PASSWORD="${WL_CHAT_DB_PASSWORD:-}"
FLYWAY_TARGET="${WL_CHAT_FLYWAY_TARGET:-}"

if [[ -n "${CI:-}" ]]; then
  DB_HOST="${DB_HOST:-sqlserver-dev}"
  DB_PORT="${DB_PORT:-1433}"
  FLYWAY_NETWORK="${FLYWAY_NETWORK:-wl-chat-devdocker_default}"
fi

DB_HOST="${DB_HOST:-host.docker.internal}"
DB_PORT="${DB_PORT:-1434}"

if [[ -z "${SA_PASSWORD}" ]]; then
  echo "MSSQL_SA_PASSWORD is required."
  echo "Example: export MSSQL_SA_PASSWORD='your_sa_password'"
  exit 1
fi

if [[ -z "${APP_PASSWORD}" ]]; then
  echo "WL_CHAT_DB_PASSWORD is required."
  exit 1
fi

if [[ ! -d "${MIGRATIONS_DIR}" ]]; then
  echo "Migration directory not found: ${MIGRATIONS_DIR}"
  exit 1
fi

cd "${REPO_ROOT}"

echo "Running Flyway migrations against DevDocker SQL Server (${DB_HOST}:${DB_PORT})..."
docker_run_args=(
  --rm
  -v "${MIGRATIONS_DIR}:/flyway/sql"
)

if [[ "${DB_HOST}" == "host.docker.internal" ]]; then
  docker_run_args+=(--add-host=host.docker.internal:host-gateway)
fi

if [[ -n "${FLYWAY_NETWORK}" ]]; then
  docker_run_args+=(--network "${FLYWAY_NETWORK}")
fi

flyway_args=(
  -url="jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=wl_chat;encrypt=true;trustServerCertificate=true"
  -user="sa"
  -password="${SA_PASSWORD}"
  -locations="filesystem:/flyway/sql"
  -defaultSchema="platform"
  -schemas="platform,identity,messaging,audit"
  -table="flyway_schema_history"
  -placeholders.app_login="${APP_LOGIN}"
  -placeholders.app_password="${APP_PASSWORD}"
)
if [[ -n "${FLYWAY_TARGET}" ]]; then
  flyway_args+=(-target="${FLYWAY_TARGET}")
fi

docker run "${docker_run_args[@]}" \
  flyway/flyway:10.17.3 \
  "${flyway_args[@]}" \
  migrate

echo "DevDocker migrations complete."
