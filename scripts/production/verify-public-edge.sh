#!/usr/bin/env bash
set -euo pipefail

hostname="${1:-}"
[[ "${hostname}" =~ ^[A-Za-z0-9.-]+$ ]] \
  || { echo "Usage: $0 <production-hostname>" >&2; exit 1; }

base="https://${hostname}"
curl --fail --silent --show-error --proto '=https' --tlsv1.2 "${base}/health/live" >/dev/null
ready_status="$(curl --silent --output /dev/null --write-out '%{http_code}' "${base}/health/ready")"
q_status="$(curl --silent --output /dev/null --write-out '%{http_code}' "${base}/q/health")"
[[ "${ready_status}" == "404" ]] || { echo "Detailed readiness is public (${ready_status})." >&2; exit 1; }
[[ "${q_status}" == "404" ]] || { echo "Quarkus management surface is public (${q_status})." >&2; exit 1; }

ws_status="$(curl --http1.1 --silent --output /dev/null --write-out '%{http_code}' --max-time 15 \
  --header 'Connection: Upgrade' --header 'Upgrade: websocket' \
  --header 'Sec-WebSocket-Version: 13' --header 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  "${base}/api/v1/ws" || true)"
if [[ "${ws_status}" != "401" && "${ws_status}" != "426" ]]; then
  echo "Unauthenticated WebSocket boundary returned ${ws_status}." >&2
  exit 1
fi
echo "Public liveness, private readiness, and unauthenticated WebSocket boundary passed."

