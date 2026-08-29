#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${WL_CHAT_HARDENED_ENV_FILE:-${REPO_ROOT}/deploy/hardened.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Hardened environment file not found: ${ENV_FILE}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

required=(
  WL_CHAT_SQL_IMAGE WL_CHAT_RABBITMQ_IMAGE WL_CHAT_NGINX_IMAGE WL_CHAT_FLYWAY_IMAGE
  WL_CHAT_APP_IMAGE WL_CHAT_DB_OPERATOR_PASSWORD WL_CHAT_DB_PASSWORD
  WL_CHAT_MIGRATOR_PASSWORD WL_CHAT_BACKUP_PASSWORD WL_CHAT_RESTORE_PASSWORD
  WL_CHAT_BACKUP_MASTER_KEY_PASSWORD WL_CHAT_BACKUP_CERTIFICATE_PASSWORD
  WL_CHAT_QUEUE_OPERATOR_USERNAME WL_CHAT_QUEUE_OPERATOR_PASSWORD
  WL_CHAT_QUEUE_USERNAME WL_CHAT_QUEUE_PASSWORD WL_CHAT_SQL_TRUSTSTORE_PASSWORD
  WL_CHAT_WEBSOCKET_ALLOWED_ORIGINS
)
for variable_name in "${required[@]}"; do
  value="${!variable_name:-}"
  if [[ -z "${value}" ]]; then
    echo "${variable_name} is missing or still contains a placeholder." >&2
    exit 1
  fi
  case "${value}" in
    *[Rr][Ee][Pp][Ll][Aa][Cc][Ee]*)
      echo "${variable_name} is missing or still contains a placeholder." >&2
      exit 1
      ;;
  esac
done

for variable_name in \
  WL_CHAT_SQL_IMAGE WL_CHAT_RABBITMQ_IMAGE WL_CHAT_NGINX_IMAGE WL_CHAT_FLYWAY_IMAGE WL_CHAT_APP_IMAGE; do
  if [[ "${!variable_name}" != *@sha256:* && "${!variable_name}" != sha256:* ]]; then
    echo "${variable_name} must be pinned to a sha256 digest." >&2
    exit 1
  fi
done

for filename in ca.crt proxy.crt proxy.key sqlserver.crt sqlserver.key sql-truststore.p12; do
  if [[ ! -s "${REPO_ROOT}/deploy/tls/generated/${filename}" ]]; then
    echo "Missing TLS artifact deploy/tls/generated/${filename}." >&2
    exit 1
  fi
done

echo "Hardened environment validation passed."
