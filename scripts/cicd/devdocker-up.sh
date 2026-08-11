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
RESET_DB="${WL_CHAT_RESET_DB:-false}"
ENABLE_QUEUE="${WL_CHAT_ENABLE_QUEUE:-true}"

if [[ -z "${MSSQL_SA_PASSWORD:-}" ]]; then
  echo "MSSQL_SA_PASSWORD is required."
  echo "Example: export MSSQL_SA_PASSWORD='your_sa_password'"
  exit 1
fi

if [[ "${ENABLE_QUEUE}" == "true" && -z "${WL_CHAT_QUEUE_PASSWORD:-}" ]]; then
  echo "WL_CHAT_QUEUE_PASSWORD is required when WL_CHAT_ENABLE_QUEUE=true."
  echo "Set it in scripts/config/local.secrets.env or export it in your shell."
  exit 1
fi

cd "${REPO_ROOT}"

if ! docker image inspect "${APP_IMAGE}" >/dev/null 2>&1; then
  echo "Image ${APP_IMAGE} not found locally. Building it now..."
  docker build -t "${APP_IMAGE}" .
fi

export WL_CHAT_APP_IMAGE="${APP_IMAGE}"

if [[ "${RESET_DB}" == "true" ]]; then
  echo "WL_CHAT_RESET_DB=true: resetting DevDocker stack and SQL volume (destructive)."
  docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans
fi

if [[ "${ENABLE_QUEUE}" == "true" ]]; then
  if ! docker compose -f "${COMPOSE_FILE}" up -d --wait queue-dev >/tmp/wl_chat_queue_up.log 2>&1; then
    if grep -qiE 'unknown flag: --wait|unknown option: --wait|no such option: --wait' /tmp/wl_chat_queue_up.log; then
      echo "docker compose '--wait' not supported, using manual queue health wait..."
      docker compose -f "${COMPOSE_FILE}" up -d queue-dev

      queue_cid="$(docker compose -f "${COMPOSE_FILE}" ps -q queue-dev)"
      if [[ -z "${queue_cid}" ]]; then
        echo "Could not determine queue container ID."
        exit 1
      fi

      for i in {1..45}; do
        health_status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}nohealth{{end}}' "${queue_cid}" 2>/dev/null || true)"
        if [[ "${health_status}" == "healthy" ]]; then
          break
        fi
        if [[ $i -eq 45 ]]; then
          echo "Queue did not become healthy in time (status: ${health_status})."
          docker compose -f "${COMPOSE_FILE}" logs --no-color --tail=120 queue-dev || true
          exit 1
        fi
        sleep 2
      done
    else
      cat /tmp/wl_chat_queue_up.log
      exit 1
    fi
  fi

  # Definitions import seeds topology but does not create users in this workspace.
  # Ensure the configured queue user exists and has expected privileges.
  if docker compose -f "${COMPOSE_FILE}" exec -T queue-dev rabbitmqctl list_users | awk 'NR>1{print $1}' | grep -Fxq "${WL_CHAT_QUEUE_USERNAME}"; then
    docker compose -f "${COMPOSE_FILE}" exec -T queue-dev \
      rabbitmqctl change_password "${WL_CHAT_QUEUE_USERNAME}" "${WL_CHAT_QUEUE_PASSWORD}"
  else
    docker compose -f "${COMPOSE_FILE}" exec -T queue-dev \
      rabbitmqctl add_user "${WL_CHAT_QUEUE_USERNAME}" "${WL_CHAT_QUEUE_PASSWORD}"
  fi

  docker compose -f "${COMPOSE_FILE}" exec -T queue-dev \
    rabbitmqctl set_user_tags "${WL_CHAT_QUEUE_USERNAME}" administrator
  docker compose -f "${COMPOSE_FILE}" exec -T queue-dev \
    rabbitmqctl set_permissions -p "${WL_CHAT_QUEUE_VHOST:-/}" "${WL_CHAT_QUEUE_USERNAME}" ".*" ".*" ".*"
fi

if ! docker compose -f "${COMPOSE_FILE}" up -d --wait sqlserver-dev >/tmp/wl_chat_sql_up.log 2>&1; then
  if grep -qiE 'unknown flag: --wait|unknown option: --wait|no such option: --wait' /tmp/wl_chat_sql_up.log; then
    echo "docker compose '--wait' not supported, using manual SQL health wait..."
    docker compose -f "${COMPOSE_FILE}" up -d sqlserver-dev

    sql_cid="$(docker compose -f "${COMPOSE_FILE}" ps -q sqlserver-dev)"
    if [[ -z "${sql_cid}" ]]; then
      echo "Could not determine SQL Server container ID."
      exit 1
    fi

    for i in {1..60}; do
      health_status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}nohealth{{end}}' "${sql_cid}" 2>/dev/null || true)"
      if [[ "${health_status}" == "healthy" ]]; then
        break
      fi
      if [[ $i -eq 60 ]]; then
        echo "SQL Server did not become healthy in time (status: ${health_status})."
        docker compose -f "${COMPOSE_FILE}" logs --no-color --tail=120 sqlserver-dev || true
        exit 1
      fi
      sleep 2
    done
  else
    cat /tmp/wl_chat_sql_up.log
    exit 1
  fi
fi

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
if [[ "${ENABLE_QUEUE}" == "true" ]]; then
  echo "Queue URL: amqp://${WL_CHAT_QUEUE_USERNAME:-wl_chat_queue}:***@<queue-host>:${WL_CHAT_QUEUE_PORT:-5672}${WL_CHAT_QUEUE_VHOST:-/}"
fi
