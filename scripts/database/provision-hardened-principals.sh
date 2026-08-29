#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
PROVISION_SQL="${SCRIPT_DIR}/hardening/provision-principals.sql"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${REPO_ROOT}/scripts/config/local.secrets.env}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

SQLSERVER_CONTAINER="${WL_CHAT_SQLSERVER_CONTAINER:-wl-chat-sqlserver-dev}"
OPERATOR_USERNAME="${WL_CHAT_DB_OPERATOR_USERNAME:-sa}"
OPERATOR_PASSWORD="${WL_CHAT_DB_OPERATOR_PASSWORD:-${MSSQL_SA_PASSWORD:-}}"
RUNTIME_LOGIN="${WL_CHAT_DB_USERNAME:-wl_chat_app}"
MIGRATOR_LOGIN="${WL_CHAT_MIGRATOR_USERNAME:-messenger_migrator}"
BACKUP_LOGIN="${WL_CHAT_BACKUP_USERNAME:-wl_chat_backup}"
RESTORE_LOGIN="${WL_CHAT_RESTORE_USERNAME:-wl_chat_restore}"
PRESERVE_RUNTIME_FIXED_ROLES="${WL_CHAT_PRESERVE_RUNTIME_FIXED_ROLES:-false}"

if [[ "${PRESERVE_RUNTIME_FIXED_ROLES}" != "true" && "${PRESERVE_RUNTIME_FIXED_ROLES}" != "false" ]]; then
  echo "WL_CHAT_PRESERVE_RUNTIME_FIXED_ROLES must be true or false." >&2
  exit 1
fi

required_values=(
  OPERATOR_PASSWORD
  WL_CHAT_DB_PASSWORD
  WL_CHAT_MIGRATOR_PASSWORD
  WL_CHAT_BACKUP_PASSWORD
  WL_CHAT_RESTORE_PASSWORD
)
for variable_name in "${required_values[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "${variable_name} is required." >&2
    exit 1
  fi
done

for login in "${RUNTIME_LOGIN}" "${MIGRATOR_LOGIN}" "${BACKUP_LOGIN}" "${RESTORE_LOGIN}"; do
  if [[ ! "${login}" =~ ^[A-Za-z][A-Za-z0-9_]{0,127}$ ]]; then
    echo "Unsafe SQL login name: ${login}" >&2
    exit 1
  fi
done

if [[ ! -f "${PROVISION_SQL}" ]]; then
  echo "Provisioning SQL not found: ${PROVISION_SQL}" >&2
  exit 1
fi

encode_password_hex() {
  printf '%s' "$1" | iconv -f UTF-8 -t UTF-16LE | od -An -v -tx1 | tr -d ' \n'
}

docker exec -i "${SQLSERVER_CONTAINER}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost \
  -U "${OPERATOR_USERNAME}" \
  -P "${OPERATOR_PASSWORD}" \
  -C \
  -b \
  -v RUNTIME_LOGIN="${RUNTIME_LOGIN}" \
  -v MIGRATOR_LOGIN="${MIGRATOR_LOGIN}" \
  -v BACKUP_LOGIN="${BACKUP_LOGIN}" \
  -v RESTORE_LOGIN="${RESTORE_LOGIN}" \
  -v PRESERVE_RUNTIME_FIXED_ROLES="${PRESERVE_RUNTIME_FIXED_ROLES}" \
  -v RUNTIME_PASSWORD_HEX="$(encode_password_hex "${WL_CHAT_DB_PASSWORD}")" \
  -v MIGRATOR_PASSWORD_HEX="$(encode_password_hex "${WL_CHAT_MIGRATOR_PASSWORD}")" \
  -v BACKUP_PASSWORD_HEX="$(encode_password_hex "${WL_CHAT_BACKUP_PASSWORD}")" \
  -v RESTORE_PASSWORD_HEX="$(encode_password_hex "${WL_CHAT_RESTORE_PASSWORD}")" \
  < "${PROVISION_SQL}"

echo "Hardened SQL Server principals provisioned for wl_chat."
