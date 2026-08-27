#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
CA_FILE="${REPO_ROOT}/deploy/tls/generated/ca.crt"
BASE_URL="${WL_CHAT_HARDENED_BASE_URL:-https://localhost}"

curl --fail --silent --show-error --cacert "${CA_FILE}" "${BASE_URL}/health/live" >/dev/null
ready_payload="$(curl --fail --silent --show-error --cacert "${CA_FILE}" "${BASE_URL}/health/ready")"
if ! printf '%s' "${ready_payload}" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
  echo "Readiness response was not UP." >&2
  exit 1
fi

http_status="$(curl --http1.1 --silent --output /dev/null --write-out '%{http_code}' \
  --max-time 15 \
  --cacert "${CA_FILE}" \
  --header 'Connection: Upgrade' \
  --header 'Upgrade: websocket' \
  --header 'Sec-WebSocket-Version: 13' \
  --header 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  "${BASE_URL}/api/v1/ws" || true)"
if [[ "${http_status}" != "101" && "${http_status}" != "401" && "${http_status}" != "426" ]]; then
  echo "Unauthenticated WebSocket probe returned unexpected HTTP status ${http_status}." >&2
  exit 1
fi

echo "Hardened HTTPS health and WebSocket upgrade boundary checks passed."
