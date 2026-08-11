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

LOG_ROOT_DIR="${REPO_ROOT}/logs"
LOG_PERIOD_DIR="$(date +%Y/%m)"
export WL_CHAT_LOG_DIR="${LOG_ROOT_DIR}/${LOG_PERIOD_DIR}"
mkdir -p "${WL_CHAT_LOG_DIR}/chat_backend"

echo "Starting Quarkus dev mode with WL_CHAT_LOG_DIR=${WL_CHAT_LOG_DIR}"
cd "${REPO_ROOT}"
exec ./mvnw quarkus:dev
