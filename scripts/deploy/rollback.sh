#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/deploy/compose.hardened.yaml"
ENV_FILE="${WL_CHAT_HARDENED_ENV_FILE:-${REPO_ROOT}/deploy/hardened.env}"

if [[ -z "${WL_CHAT_PREVIOUS_APP_IMAGE:-}" ]]; then
  echo "WL_CHAT_PREVIOUS_APP_IMAGE must identify the previously verified digest." >&2
  exit 1
fi
if [[ "${WL_CHAT_PREVIOUS_APP_IMAGE}" != *@sha256:* && "${WL_CHAT_PREVIOUS_APP_IMAGE}" != sha256:* ]]; then
  echo "WL_CHAT_PREVIOUS_APP_IMAGE must be digest pinned." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a
export WL_CHAT_APP_IMAGE="${WL_CHAT_PREVIOUS_APP_IMAGE}"

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d app proxy
"${SCRIPT_DIR}/smoke-test.sh"
echo "Application rollback completed. Database migrations were intentionally not reversed."
