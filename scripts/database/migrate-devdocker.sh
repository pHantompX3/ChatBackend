#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
MIGRATIONS_DIR="${REPO_ROOT}/src/main/resources/db/migration"

DEFAULT_SECRETS_FILE="${REPO_ROOT}/scripts/config/local.secrets.env"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${DEFAULT_SECRETS_FILE}}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

SA_PASSWORD="${MSSQL_SA_PASSWORD:-}"
DB_HOST="${WL_CHAT_DB_HOST:-host.docker.internal}"
APP_LOGIN="${WL_CHAT_DB_USERNAME:-wl_chat_app}"
APP_PASSWORD="${WL_CHAT_DB_PASSWORD:-}"

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

echo "Running Flyway migrations against DevDocker SQL Server (localhost:1434)..."
docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -v "${MIGRATIONS_DIR}:/flyway/sql" \
  flyway/flyway:10.17.3 \
  -url="jdbc:sqlserver://${DB_HOST}:1434;databaseName=wl_chat;encrypt=true;trustServerCertificate=true" \
  -user="sa" \
  -password="${SA_PASSWORD}" \
  -locations="filesystem:/flyway/sql" \
  -placeholders.app_login="${APP_LOGIN}" \
  -placeholders.app_password="${APP_PASSWORD}" \
  migrate

echo "DevDocker migrations complete."
