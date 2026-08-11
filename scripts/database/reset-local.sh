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

if [[ -z "${SA_PASSWORD}" ]]; then
  echo "MSSQL_SA_PASSWORD is required."
  echo "Example: export MSSQL_SA_PASSWORD='your_sa_password'"
  exit 1
fi

echo "Resetting local database '${DB_NAME}' and bootstrap Flyway history on ${DB_HOST}:${DB_PORT}..."

docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -e DB_HOST="${DB_HOST}" \
  -e DB_PORT="${DB_PORT}" \
  -e SA_PASSWORD="${SA_PASSWORD}" \
  -e DB_NAME="${DB_NAME}" \
  mcr.microsoft.com/mssql-tools:latest \
  sh -lc '
    SQLCMD="/opt/mssql-tools18/bin/sqlcmd"
    if [[ ! -x "${SQLCMD}" ]]; then
      SQLCMD="/opt/mssql-tools/bin/sqlcmd"
    fi

    "${SQLCMD}" \
      -S "${DB_HOST},${DB_PORT}" \
      -U "sa" \
      -P "${SA_PASSWORD}" \
      -C \
      -b \
      -d "master" \
      -Q "IF DB_ID(N''${DB_NAME}'') IS NOT NULL BEGIN ALTER DATABASE [${DB_NAME}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [${DB_NAME}]; END; IF OBJECT_ID(N''dbo.flyway_schema_history'', N''U'') IS NOT NULL DROP TABLE dbo.flyway_schema_history;"
  '

echo "Local reset complete."
