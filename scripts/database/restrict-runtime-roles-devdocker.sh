#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${REPO_ROOT}/scripts/config/local.secrets.env}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

SQLSERVER_CONTAINER="${WL_CHAT_SQLSERVER_CONTAINER:-wl-chat-sqlserver-dev}"
SA_PASSWORD="${MSSQL_SA_PASSWORD:-}"
APP_LOGIN="${WL_CHAT_DB_USERNAME:-wl_chat_app}"

if [[ -z "${SA_PASSWORD}" ]]; then
  echo "MSSQL_SA_PASSWORD is required." >&2
  exit 1
fi

if [[ ! "${APP_LOGIN}" =~ ^[A-Za-z][A-Za-z0-9_]{0,127}$ ]]; then
  echo "Unsafe runtime login name: ${APP_LOGIN}" >&2
  exit 1
fi

docker exec "${SQLSERVER_CONTAINER}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost \
  -U sa \
  -P "${SA_PASSWORD}" \
  -C \
  -b \
  -d wl_chat \
  -Q "IF IS_ROLEMEMBER(N'db_datareader', N'${APP_LOGIN}') = 1 ALTER ROLE [db_datareader] DROP MEMBER [${APP_LOGIN}]; IF IS_ROLEMEMBER(N'db_datawriter', N'${APP_LOGIN}') = 1 ALTER ROLE [db_datawriter] DROP MEMBER [${APP_LOGIN}];"

echo "DevDocker runtime fixed-role memberships removed."
