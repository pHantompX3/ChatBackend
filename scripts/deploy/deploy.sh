#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/deploy/compose.hardened.yaml"
ENV_FILE="${WL_CHAT_HARDENED_ENV_FILE:-${REPO_ROOT}/deploy/hardened.env}"
MODE="${1:-}"

if [[ "${MODE}" != "clean" && "${MODE}" != "upgrade" ]]; then
  echo "Usage: $0 clean|upgrade" >&2
  exit 1
fi

export WL_CHAT_HARDENED_ENV_FILE="${ENV_FILE}"
"${SCRIPT_DIR}/validate-environment.sh"
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config --quiet
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --wait sqlserver queue

export WL_CHAT_SQLSERVER_CONTAINER=wl-chat-sqlserver-hardened
export WL_CHAT_QUEUE_CONTAINER=wl-chat-queue-hardened
export WL_CHAT_SECRETS_FILE="${ENV_FILE}"
export WL_CHAT_DB_HOST=sqlserver
export WL_CHAT_DB_PORT=1433
export WL_CHAT_FLYWAY_DOCKER_NETWORK=wl-chat-hardened_data
export WL_CHAT_SQL_TRUSTSTORE_FILE="${REPO_ROOT}/deploy/tls/generated/sql-truststore.p12"
export WL_CHAT_MIGRATOR_DB_URL="jdbc:sqlserver://sqlserver:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=false;trustStore=/flyway/tls/sql-truststore.p12;trustStorePassword=${WL_CHAT_SQL_TRUSTSTORE_PASSWORD}"

"${SCRIPT_DIR}/provision-rabbitmq.sh"

if [[ "${MODE}" == "clean" ]]; then
  export WL_CHAT_PRESERVE_RUNTIME_FIXED_ROLES=true
  "${REPO_ROOT}/scripts/database/provision-hardened-principals.sh"
  export WL_CHAT_FLYWAY_TARGET=20260814100000
  "${REPO_ROOT}/scripts/database/migrate-hardened.sh"
  unset WL_CHAT_FLYWAY_TARGET
fi

export WL_CHAT_PRESERVE_RUNTIME_FIXED_ROLES=false
"${REPO_ROOT}/scripts/database/provision-hardened-principals.sh"
"${REPO_ROOT}/scripts/database/provision-backup-encryption.sh"
"${REPO_ROOT}/scripts/database/migrate-hardened.sh"
"${REPO_ROOT}/scripts/database/verify-hardened-permissions.sh" \
  --scenario "$(if [[ "${MODE}" == "clean" ]]; then echo clean; else echo upgrade-milestone-8; fi)"

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d app proxy
for attempt in {1..60}; do
  if "${SCRIPT_DIR}/smoke-test.sh" >/dev/null 2>&1; then
    echo "Hardened ${MODE} deployment is healthy."
    exit 0
  fi
  if [[ "${attempt}" -eq 60 ]]; then
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --no-color --tail=160 app proxy
    echo "Hardened deployment did not become healthy." >&2
    exit 1
  fi
  sleep 2
done
