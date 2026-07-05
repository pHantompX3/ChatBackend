#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/compose.devdocker.yaml"

DEFAULT_SECRETS_FILE="${REPO_ROOT}/scripts/config/local.secrets.env"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${DEFAULT_SECRETS_FILE}}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

APP_PORT="${WL_CHAT_APP_PORT:-8081}"
APP_IMAGE="${WL_CHAT_APP_IMAGE:-wl-chat-app-dev:latest}"

if [[ -z "${MSSQL_SA_PASSWORD:-}" ]]; then
  echo "MSSQL_SA_PASSWORD is required."
  echo "Example: export MSSQL_SA_PASSWORD='your_sa_password'"
  exit 1
fi

cd "${REPO_ROOT}"

if ! docker image inspect "${APP_IMAGE}" >/dev/null 2>&1; then
  echo "Image ${APP_IMAGE} not found locally. Building it now..."
  docker build -t "${APP_IMAGE}" .
fi

export WL_CHAT_APP_IMAGE="${APP_IMAGE}"

docker compose -f "${COMPOSE_FILE}" up -d --wait sqlserver-dev
"${REPO_ROOT}/scripts/database/init-devdocker.sh"
docker compose -f "${COMPOSE_FILE}" up -d app-dev

for i in {1..45}; do
  if curl -fsS "http://localhost:${APP_PORT}/q/health/live" >/dev/null 2>&1; then
    break
  fi
  if [[ $i -eq 45 ]]; then
    echo "App did not become healthy on port ${APP_PORT} in time."
    exit 1
  fi
  sleep 2
done

echo "DevDocker stack is up."
echo "App URL: http://localhost:${APP_PORT}"
