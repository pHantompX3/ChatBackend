#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
SQL_FILE="${SCRIPT_DIR}/hardening/provision-backup-encryption.sql"
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
MASTER_KEY_PASSWORD="${WL_CHAT_BACKUP_MASTER_KEY_PASSWORD:-}"
CERTIFICATE_PASSWORD="${WL_CHAT_BACKUP_CERTIFICATE_PASSWORD:-}"
BACKUP_LOGIN="${WL_CHAT_BACKUP_USERNAME:-wl_chat_backup}"
RESTORE_LOGIN="${WL_CHAT_RESTORE_USERNAME:-wl_chat_restore}"

for variable_name in OPERATOR_PASSWORD MASTER_KEY_PASSWORD CERTIFICATE_PASSWORD; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "${variable_name} is required." >&2
    exit 1
  fi
done

encode_password_hex() {
  printf '%s' "$1" | iconv -f UTF-8 -t UTF-16LE | od -An -v -tx1 | tr -d ' \n'
}

export_certificate=true
if docker exec "${SQLSERVER_CONTAINER}" test \
  -s /var/opt/mssql/backup/wl_chat_backup_certificate.cer \
  && docker exec "${SQLSERVER_CONTAINER}" test \
    -s /var/opt/mssql/backup/wl_chat_backup_certificate.pvk; then
  export_certificate=false
fi

docker exec -i "${SQLSERVER_CONTAINER}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U "${OPERATOR_USERNAME}" -P "${OPERATOR_PASSWORD}" -C -b \
  -v BACKUP_LOGIN="${BACKUP_LOGIN}" \
  -v RESTORE_LOGIN="${RESTORE_LOGIN}" \
  -v EXPORT_CERTIFICATE="${export_certificate}" \
  -v MASTER_KEY_PASSWORD_HEX="$(encode_password_hex "${MASTER_KEY_PASSWORD}")" \
  -v CERTIFICATE_PASSWORD_HEX="$(encode_password_hex "${CERTIFICATE_PASSWORD}")" \
  < "${SQL_FILE}"

echo "Backup encryption certificate and least-privilege access are ready."
