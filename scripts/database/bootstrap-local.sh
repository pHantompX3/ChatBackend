#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

DEFAULT_SECRETS_FILE="${REPO_ROOT}/scripts/config/local.secrets.env"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${DEFAULT_SECRETS_FILE}}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

DB_NAME="${WL_CHAT_DB_NAME:-wl_chat}"
DB_PORT="${WL_CHAT_DB_PORT:-1433}"
DB_HOST="${WL_CHAT_DB_HOST:-host.docker.internal}"
SA_PASSWORD="${MSSQL_SA_PASSWORD:-}"
BOOTSTRAP_DIR="${REPO_ROOT}/scripts/database/flyway/master"

if [[ -z "${SA_PASSWORD}" ]]; then
  echo "MSSQL_SA_PASSWORD is required."
  echo "Example: export MSSQL_SA_PASSWORD='your_sa_password'"
  exit 1
fi

if [[ ! -d "${BOOTSTRAP_DIR}" ]]; then
  echo "Bootstrap directory not found: ${BOOTSTRAP_DIR}"
  exit 1
fi

echo "Bootstrapping database '${DB_NAME}' with Flyway scripts in ${BOOTSTRAP_DIR}..."
docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -v "${BOOTSTRAP_DIR}:/flyway/sql" \
  flyway/flyway:10.17.3 \
  -url="jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=master;encrypt=true;trustServerCertificate=true" \
  -user="sa" \
  -password="${SA_PASSWORD}" \
  -locations="filesystem:/flyway/sql" \
  migrate

echo "Bootstrap complete."
