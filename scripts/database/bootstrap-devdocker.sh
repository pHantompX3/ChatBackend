#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/compose.devdocker.yaml"
BOOTSTRAP_SCRIPT="${REPO_ROOT}/scripts/database/bootstrap/V0__create_wl_chat_database.sql"

DEFAULT_SECRETS_FILE="${REPO_ROOT}/scripts/config/local.secrets.env"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${DEFAULT_SECRETS_FILE}}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

SA_PASSWORD="${MSSQL_SA_PASSWORD:-}"

if [[ -z "${SA_PASSWORD}" ]]; then
  echo "MSSQL_SA_PASSWORD is required."
  echo "Example: export MSSQL_SA_PASSWORD='your_sa_password'"
  exit 1
fi

if [[ ! -f "${BOOTSTRAP_SCRIPT}" ]]; then
  echo "Bootstrap script not found: ${BOOTSTRAP_SCRIPT}"
  exit 1
fi

cd "${REPO_ROOT}"

echo "Bootstrapping wl_chat on DevDocker SQL Server (localhost:1434)..."
docker compose -f "${COMPOSE_FILE}" exec -T sqlserver-dev \
  /opt/mssql-tools18/bin/sqlcmd \
  -S localhost \
  -U sa \
  -P "${SA_PASSWORD}" \
  -C \
  -d master \
  -b \
  -i /dev/stdin < "${BOOTSTRAP_SCRIPT}"

echo "DevDocker bootstrap complete."
