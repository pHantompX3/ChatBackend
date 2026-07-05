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
SA_PASSWORD="${MSSQL_SA_PASSWORD:-}"
BOOTSTRAP_SCRIPT="${REPO_ROOT}/scripts/database/bootstrap/V0__create_wl_chat_database.sql"
COMPOSE_FILE="${REPO_ROOT}/compose.yaml"
SQL_CONTAINER_NAME="${WL_CHAT_SQL_CONTAINER_NAME:-local_sql_server}"

if [[ -z "${SA_PASSWORD}" ]]; then
  echo "MSSQL_SA_PASSWORD is required."
  echo "Example: export MSSQL_SA_PASSWORD='your_sa_password'"
  exit 1
fi

if [[ ! -f "${BOOTSTRAP_SCRIPT}" ]]; then
  echo "Bootstrap script not found: ${BOOTSTRAP_SCRIPT}"
  exit 1
fi

echo "Bootstrapping database '${DB_NAME}' via ${BOOTSTRAP_SCRIPT}..."

# Prefer compose service if compose file is present and has sqlserver service.
if [[ -s "${COMPOSE_FILE}" ]] && docker compose -f "${COMPOSE_FILE}" config --services 2>/dev/null | grep -qx "sqlserver"; then
  docker compose -f "${COMPOSE_FILE}" exec -T sqlserver \
    /opt/mssql-tools18/bin/sqlcmd \
    -S localhost \
    -U sa \
    -P "${SA_PASSWORD}" \
    -C \
    -d master \
    -b \
    -i /dev/stdin < "${BOOTSTRAP_SCRIPT}"
else
  # Fallback for setups using an existing standalone SQL Server container.
  if ! docker ps --format '{{.Names}}' | grep -qx "${SQL_CONTAINER_NAME}"; then
    echo "No usable compose sqlserver service and container '${SQL_CONTAINER_NAME}' is not running."
    echo "Start SQL Server container or set WL_CHAT_SQL_CONTAINER_NAME to your container name."
    exit 1
  fi

  docker exec -i "${SQL_CONTAINER_NAME}" \
    /opt/mssql-tools18/bin/sqlcmd \
    -S localhost \
    -U sa \
    -P "${SA_PASSWORD}" \
    -C \
    -d master \
    -b \
    -i /dev/stdin < "${BOOTSTRAP_SCRIPT}"
fi

echo "Bootstrap complete."
