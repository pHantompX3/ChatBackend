#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

COLLECTION_PATH="${WL_CHAT_NEWMAN_COLLECTION:-${REPO_ROOT}/postman/collections/chat-backend-user-flows.postman_collection.json}"
ENVIRONMENT_PATH="${WL_CHAT_NEWMAN_ENVIRONMENT:-${REPO_ROOT}/postman/environments/local.example.postman_environment.json}"
FLOW_FOLDER="${WL_CHAT_NEWMAN_FLOW_FOLDER:-Run-all API smoke journey}"
BASE_URL="${WL_CHAT_FLOW_BASE_URL:-http://localhost:8080}"
ADMIN_PASSWORD="${WL_CHAT_FLOW_ADMIN_PASSWORD:-AdminPassw0rd!}"
MEMBER_PASSWORD="${WL_CHAT_FLOW_MEMBER_PASSWORD:-MemberPassw0rd!}"
REPORT_DIR="${WL_CHAT_NEWMAN_REPORT_DIR:-${TMPDIR:-/tmp}/wl-chat-newman-report}"

mkdir -p "${REPORT_DIR}"

if ! command -v node >/dev/null 2>&1; then
  echo "node is required to run Newman" >&2
  exit 1
fi

if [[ ! -f "${COLLECTION_PATH}" ]]; then
  echo "Collection not found: ${COLLECTION_PATH}" >&2
  exit 1
fi

if [[ ! -f "${ENVIRONMENT_PATH}" ]]; then
  echo "Environment not found: ${ENVIRONMENT_PATH}" >&2
  exit 1
fi

echo "[newman-flow] Running folder '${FLOW_FOLDER}' against ${BASE_URL}"
echo "[newman-flow] Report directory: ${REPORT_DIR}"

npx --yes newman run "${COLLECTION_PATH}" \
  --environment "${ENVIRONMENT_PATH}" \
  --folder "${FLOW_FOLDER}" \
  --reporters cli,junit,json \
  --reporter-junit-export "${REPORT_DIR}/newman-junit.xml" \
  --reporter-json-export "${REPORT_DIR}/newman-report.json" \
  --env-var "base_url=${BASE_URL}" \
  --env-var "bootstrap_admin_password=${ADMIN_PASSWORD}" \
  --env-var "member_password=${MEMBER_PASSWORD}"

echo "[newman-flow] Completed successfully"
