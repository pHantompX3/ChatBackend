#!/usr/bin/env bash
set -euo pipefail

QUEUE_CONTAINER="${WL_CHAT_QUEUE_CONTAINER:-wl-chat-queue-hardened}"
RUNTIME_USERNAME="${WL_CHAT_QUEUE_USERNAME:-wl_chat_queue}"
RUNTIME_PASSWORD="${WL_CHAT_QUEUE_PASSWORD:-}"
VHOST="/wl-chat"

if [[ -z "${RUNTIME_PASSWORD}" ]]; then
  echo "WL_CHAT_QUEUE_PASSWORD is required." >&2
  exit 1
fi
if [[ ! "${RUNTIME_USERNAME}" =~ ^[A-Za-z][A-Za-z0-9_]{0,127}$ ]]; then
  echo "Unsafe RabbitMQ username." >&2
  exit 1
fi

if docker exec "${QUEUE_CONTAINER}" rabbitmqctl list_users --silent \
  | awk '{print $1}' | grep -Fxq "${RUNTIME_USERNAME}"; then
  docker exec "${QUEUE_CONTAINER}" rabbitmqctl change_password \
    "${RUNTIME_USERNAME}" "${RUNTIME_PASSWORD}"
else
  docker exec "${QUEUE_CONTAINER}" rabbitmqctl add_user \
    "${RUNTIME_USERNAME}" "${RUNTIME_PASSWORD}"
fi

docker exec "${QUEUE_CONTAINER}" rabbitmqctl set_user_tags "${RUNTIME_USERNAME}"
docker exec "${QUEUE_CONTAINER}" rabbitmqctl set_permissions -p "${VHOST}" \
  "${RUNTIME_USERNAME}" '^$' '^audit\.events$' '^audit\.events$'

if docker exec "${QUEUE_CONTAINER}" rabbitmqctl authenticate_user \
  "${RUNTIME_USERNAME}" "${RUNTIME_PASSWORD}" >/dev/null; then
  echo "RabbitMQ runtime principal provisioned without management tags or topology authority."
fi
