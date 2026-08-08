#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${WL_CHAT_QUEUE_COMPOSE_FILE:-${REPO_ROOT}/compose.queue.yaml}"

cd "${REPO_ROOT}"

docker compose -f "${COMPOSE_FILE}" stop queue-dev

echo "Shared queue server stopped."
