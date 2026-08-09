#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${WL_CHAT_QUEUE_COMPOSE_FILE:-${REPO_ROOT}/compose.queue.yaml}"

DEFAULT_SECRETS_FILE="${REPO_ROOT}/scripts/config/local.secrets.env"
SECRETS_FILE="${WL_CHAT_SECRETS_FILE:-${DEFAULT_SECRETS_FILE}}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

if [[ -z "${WL_CHAT_QUEUE_PASSWORD:-}" ]]; then
  echo "WL_CHAT_QUEUE_PASSWORD is required."
  echo "Set it in scripts/config/local.secrets.env or export it in your shell."
  exit 1
fi

QUEUE_PORT="${WL_CHAT_QUEUE_PORT:-5672}"
QUEUE_MGMT_PORT="${WL_CHAT_QUEUE_MGMT_PORT:-15672}"
QUEUE_HOST_IP="${WL_CHAT_QUEUE_HOST_IP:-0.0.0.0}"
QUEUE_MGMT_HOST_IP="${WL_CHAT_QUEUE_MGMT_HOST_IP:-127.0.0.1}"
QUEUE_USER="${WL_CHAT_QUEUE_USERNAME:-wl_chat_queue}"
QUEUE_VHOST="${WL_CHAT_QUEUE_VHOST:-/}"

cd "${REPO_ROOT}"

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

for i in {1..30}; do
  if docker exec "${queue_cid}" rabbitmqadmin --username "${QUEUE_USER}" --password "${WL_CHAT_QUEUE_PASSWORD}" list users >/dev/null 2>&1; then
    break
  fi
  if [[ $i -eq 30 ]]; then
    echo "RabbitMQ management API did not become ready in time."
    docker compose -f "${COMPOSE_FILE}" logs --no-color --tail=120 queue-dev || true
    exit 1
  fi
  sleep 2
done

docker exec "${queue_cid}" rabbitmqadmin --username "${QUEUE_USER}" --password "${WL_CHAT_QUEUE_PASSWORD}" \
  declare exchange name="audit.events" type="topic" durable=true >/dev/null
docker exec "${queue_cid}" rabbitmqadmin --username "${QUEUE_USER}" --password "${WL_CHAT_QUEUE_PASSWORD}" \
  declare exchange name="audit.events.dlx" type="direct" durable=true >/dev/null
docker exec "${queue_cid}" rabbitmqadmin --username "${QUEUE_USER}" --password "${WL_CHAT_QUEUE_PASSWORD}" \
  declare queue name="audit.events" durable=true \
  arguments='{"x-dead-letter-exchange":"audit.events.dlx","x-dead-letter-routing-key":"audit.dead"}' >/dev/null
docker exec "${queue_cid}" rabbitmqadmin --username "${QUEUE_USER}" --password "${WL_CHAT_QUEUE_PASSWORD}" \
  declare queue name="audit.events.dlq" durable=true >/dev/null
docker exec "${queue_cid}" rabbitmqadmin --username "${QUEUE_USER}" --password "${WL_CHAT_QUEUE_PASSWORD}" \
  declare binding source="audit.events" destination_type="queue" destination="audit.events" routing_key="audit.#" >/dev/null
docker exec "${queue_cid}" rabbitmqadmin --username "${QUEUE_USER}" --password "${WL_CHAT_QUEUE_PASSWORD}" \
  declare binding source="audit.events.dlx" destination_type="queue" destination="audit.events.dlq" routing_key="audit.dead" >/dev/null

echo "Shared queue server is up."
echo "AMQP endpoint: amqp://${QUEUE_USER}:***@<queue-host>:${QUEUE_PORT}${QUEUE_VHOST}"
echo "Broker bind address: ${QUEUE_HOST_IP}:${QUEUE_PORT}"
echo "Management UI bind: ${QUEUE_MGMT_HOST_IP}:${QUEUE_MGMT_PORT}"
