#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output="${WL_CHAT_EVIDENCE_ROOT}/diagnostics/${timestamp}"
mkdir -p "${output}"
chmod 0700 "${output}"
compose=(docker compose --env-file "${PRODUCTION_ENV_FILE}" -f "${REPO_ROOT}/deploy/compose.production.yaml")

"${compose[@]}" ps --format json > "${output}/compose-ps.json"
"${compose[@]}" images --format json > "${output}/compose-images.json"
docker stats --no-stream --format '{{json .}}' > "${output}/container-stats.jsonl"
"${compose[@]}" logs --no-color --since 15m --tail 300 \
  | sed -E 's/(Bearer|password|token|secret)[=: ]+[^ ,}\"]+/\1=[REDACTED]/Ig' \
  > "${output}/recent-redacted.log"
"${SCRIPT_DIR}/check-sql-capacity.sh" > "${output}/sql-capacity.txt" || true
docker exec wl-chat-production-queue-1 rabbitmq-diagnostics -q alarms \
  > "${output}/rabbitmq-alarms.txt" 2>&1 || true
docker exec wl-chat-production-queue-1 rabbitmqctl list_queues -p /wl-chat \
  name messages_ready messages_unacknowledged > "${output}/rabbitmq-queues.txt" 2>&1 || true

find "${output}" -type f -exec chmod 0600 {} +
echo "Sanitized bounded diagnostic bundle written to ${output}. Review before sharing."

