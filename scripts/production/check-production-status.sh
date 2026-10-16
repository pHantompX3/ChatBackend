#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

failed=0
compose=(docker compose --env-file "${PRODUCTION_ENV_FILE}" -f "${REPO_ROOT}/deploy/compose.production.yaml")
for service in sqlserver queue app proxy cloudflared; do
  id="$("${compose[@]}" ps -q "${service}")"
  if [[ -z "${id}" || "$(docker inspect --format '{{.State.Status}}' "${id}")" != "running" ]]; then
    echo "FAILED: ${service} is not running."
    failed=1
  fi
done

heartbeat="${WL_CHAT_PRODUCTION_ROOT}/status/last-successful-backup-utc"
if [[ ! -s "${heartbeat}" ]]; then
  echo "FAILED: no successful off-host backup heartbeat exists."
  failed=1
else
  stamp="$(cat "${heartbeat}")"
  if date -u -d "${stamp}" +%s >/dev/null 2>&1; then
    backup_epoch="$(date -u -d "${stamp}" +%s)"
  else
    backup_epoch="$(date -u -j -f '%Y%m%dT%H%M%SZ' "${stamp}" +%s 2>/dev/null || echo 0)"
  fi
  age="$(( $(date -u +%s) - backup_epoch ))"
  if (( backup_epoch == 0 || age > 93600 )); then
    echo "FAILED: last verified off-host backup is older than 26 hours."
    failed=1
  fi
fi

"${SCRIPT_DIR}/check-sql-capacity.sh" || failed=1
if (( failed != 0 )); then exit 1; fi
echo "Production services, backup freshness, and SQL capacity checks passed."

