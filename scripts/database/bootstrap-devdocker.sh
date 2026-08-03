#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
BOOTSTRAP_DIR="${REPO_ROOT}/scripts/database/flyway/master"

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

if [[ ! -d "${BOOTSTRAP_DIR}" ]]; then
  echo "Bootstrap directory not found: ${BOOTSTRAP_DIR}"
  exit 1
fi

cd "${REPO_ROOT}"

echo "Bootstrapping wl_chat on DevDocker SQL Server (localhost:1434) using Flyway..."
docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -v "${BOOTSTRAP_DIR}:/flyway/sql" \
  flyway/flyway:10.17.3 \
  -url="jdbc:sqlserver://host.docker.internal:1434;databaseName=master;encrypt=true;trustServerCertificate=true" \
  -user="sa" \
  -password="${SA_PASSWORD}" \
  -locations="filesystem:/flyway/sql" \
  migrate

echo "DevDocker bootstrap complete."
