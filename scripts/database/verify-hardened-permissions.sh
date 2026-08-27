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

SCENARIO=""
if [[ "${1:-}" == "--scenario" ]]; then
  SCENARIO="${2:-}"
fi
if [[ "${SCENARIO}" != "clean" && "${SCENARIO}" != "upgrade-milestone-8" ]]; then
  echo "Usage: $0 --scenario clean|upgrade-milestone-8" >&2
  exit 1
fi

SQLSERVER_CONTAINER="${WL_CHAT_SQLSERVER_CONTAINER:-wl-chat-sqlserver-dev}"
RUNTIME_USERNAME="${WL_CHAT_DB_USERNAME:-wl_chat_app}"
RUNTIME_PASSWORD="${WL_CHAT_DB_PASSWORD:-}"
MIGRATOR_USERNAME="${WL_CHAT_MIGRATOR_USERNAME:-messenger_migrator}"
MIGRATOR_PASSWORD="${WL_CHAT_MIGRATOR_PASSWORD:-}"

if [[ -z "${RUNTIME_PASSWORD}" || -z "${MIGRATOR_PASSWORD}" ]]; then
  echo "WL_CHAT_DB_PASSWORD and WL_CHAT_MIGRATOR_PASSWORD are required." >&2
  exit 1
fi

sqlcmd_as() {
  local username="$1"
  local password="$2"
  local query="$3"
  docker exec "${SQLSERVER_CONTAINER}" /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U "${username}" -P "${password}" -C -b -d wl_chat -Q "${query}"
}

expect_denied() {
  local description="$1"
  local query="$2"
  if sqlcmd_as "${RUNTIME_USERNAME}" "${RUNTIME_PASSWORD}" "${query}" >/dev/null 2>&1; then
    echo "Runtime principal unexpectedly succeeded: ${description}" >&2
    exit 1
  fi
}

sqlcmd_as "${RUNTIME_USERNAME}" "${RUNTIME_PASSWORD}" \
  "SELECT TOP (1) [id] FROM [identity].[user_account]; SELECT TOP (1) [id] FROM [messaging].[conversation];" \
  >/dev/null

expect_denied "CREATE TABLE" "CREATE TABLE [dbo].[__forbidden_runtime_ddl] ([id] int NOT NULL);"
expect_denied "Flyway history update" \
  "UPDATE [platform].[flyway_schema_history] SET [description] = [description] WHERE 1 = 0;"
expect_denied "audit deletion" \
  "DELETE FROM [audit].[http_audit_event] WHERE 1 = 0;"

sqlcmd_as "${MIGRATOR_USERNAME}" "${MIGRATOR_PASSWORD}" \
  "BEGIN TRANSACTION; CREATE TABLE [platform].[__migrator_permission_probe] ([id] int NOT NULL); ROLLBACK TRANSACTION; SELECT TOP (1) [installed_rank] FROM [platform].[flyway_schema_history];" \
  >/dev/null

echo "Hardened permission verification passed for scenario ${SCENARIO}."
