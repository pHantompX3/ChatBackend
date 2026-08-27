#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
K6_IMAGE="${WL_CHAT_K6_IMAGE:-}"

if [[ "${K6_IMAGE}" != *@sha256:* ]]; then
  echo "WL_CHAT_K6_IMAGE must be a reviewed digest-pinned k6 image." >&2
  exit 1
fi
if [[ -z "${WL_CHAT_LOAD_WS_BASE_URL:-}" || -z "${WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN:-}" ]]; then
  echo "WL_CHAT_LOAD_WS_BASE_URL and WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN are required." >&2
  exit 1
fi

docker run --rm --add-host=host.docker.internal:host-gateway \
  -v "${REPO_ROOT}/load-test:/load-test:ro" \
  -e "WL_CHAT_LOAD_WS_BASE_URL=${WL_CHAT_LOAD_WS_BASE_URL}" \
  -e "WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN=${WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN}" \
  -e "WL_CHAT_LOAD_INSECURE_TLS=${WL_CHAT_LOAD_INSECURE_TLS:-false}" \
  "${K6_IMAGE}" run /load-test/websocket-policy.js

echo "Hardened WebSocket policy checks passed through the TLS proxy."
