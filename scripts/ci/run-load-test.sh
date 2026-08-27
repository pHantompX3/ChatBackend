#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
PHASE=""
if [[ "${1:-}" == "--phase" ]]; then
  PHASE="${2:-}"
fi
if [[ "${PHASE}" != "characterization" && "${PHASE}" != "regression" ]]; then
  echo "Usage: $0 --phase characterization|regression" >&2
  exit 1
fi

K6_IMAGE="${WL_CHAT_K6_IMAGE:-}"
if [[ "${K6_IMAGE}" != *@sha256:* ]]; then
  echo "WL_CHAT_K6_IMAGE must be a reviewed digest-pinned k6 image." >&2
  exit 1
fi
required=(
  WL_CHAT_LOAD_BASE_URL WL_CHAT_LOAD_WS_BASE_URL WL_CHAT_LOAD_TOKEN
  WL_CHAT_LOAD_CONVERSATION_ID
)
for variable_name in "${required[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "${variable_name} is required." >&2
    exit 1
  fi
done

result_dir="${REPO_ROOT}/load-test/results/${PHASE}"
mkdir -p "${result_dir}"
docker run --rm --add-host=host.docker.internal:host-gateway \
  -v "${REPO_ROOT}/load-test:/load-test:ro" \
  -v "${result_dir}:/results" \
  -e "WL_CHAT_LOAD_PHASE=${PHASE}" \
  -e "WL_CHAT_LOAD_BASE_URL=${WL_CHAT_LOAD_BASE_URL}" \
  -e "WL_CHAT_LOAD_WS_BASE_URL=${WL_CHAT_LOAD_WS_BASE_URL}" \
  -e "WL_CHAT_LOAD_TOKEN=${WL_CHAT_LOAD_TOKEN}" \
  -e "WL_CHAT_LOAD_CONVERSATION_ID=${WL_CHAT_LOAD_CONVERSATION_ID}" \
  -e "WL_CHAT_LOAD_INSECURE_TLS=${WL_CHAT_LOAD_INSECURE_TLS:-false}" \
  "${K6_IMAGE}" run --summary-export "/results/summary-$(date -u +%Y%m%dT%H%M%SZ).json" \
  /load-test/chat-backend.js

echo "${PHASE} load test completed. Raw summaries are local, ignored artifacts."
