#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
CA_FILE="${REPO_ROOT}/deploy/tls/generated/ca.crt"
PROXY_CERT="${REPO_ROOT}/deploy/tls/generated/proxy.crt"
BASE_URL="${WL_CHAT_HARDENED_BASE_URL:-https://localhost}"
MAX_BACKUP_AGE_SECONDS="${WL_CHAT_MAX_BACKUP_AGE_SECONDS:-86400}"

curl --fail --silent --show-error --cacert "${CA_FILE}" "${BASE_URL}/health/live"
curl --fail --silent --show-error --cacert "${CA_FILE}" "${BASE_URL}/health/ready"

if ! openssl x509 -checkend 604800 -noout -in "${PROXY_CERT}"; then
  echo "Proxy certificate expires in fewer than seven days." >&2
  exit 1
fi

for container in \
  wl-chat-proxy-hardened wl-chat-app-hardened wl-chat-sqlserver-hardened wl-chat-queue-hardened; do
  docker inspect --format '{{.Name}} status={{.State.Status}} restarts={{.RestartCount}}' "${container}"
done

docker exec wl-chat-queue-hardened rabbitmqctl list_queues -p /wl-chat \
  name messages messages_ready messages_unacknowledged

latest_backup="$(find "${REPO_ROOT}/backups" -type f -name 'wl_chat_*.bak' -print 2>/dev/null | sort | tail -n 1)"
if [[ -z "${latest_backup}" ]]; then
  echo "No local backup evidence found; confirm the off-host schedule separately." >&2
  exit 1
fi
now="$(date +%s)"
if stat -f %m "${latest_backup}" >/dev/null 2>&1; then
  backup_modified="$(stat -f %m "${latest_backup}")"
else
  backup_modified="$(stat -c %Y "${latest_backup}")"
fi
backup_age="$((now - backup_modified))"
if (( backup_age > MAX_BACKUP_AGE_SECONDS )); then
  echo "Latest local backup evidence is ${backup_age}s old, exceeding ${MAX_BACKUP_AGE_SECONDS}s." >&2
  exit 1
fi

docker system df
echo "Hardened status checks passed; external alert delivery and off-host storage require environment evidence."
