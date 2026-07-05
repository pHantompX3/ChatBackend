#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
BRANCH_NAME="${1:-}"

if [[ -z "${BRANCH_NAME}" ]]; then
  echo "Usage: scripts/cicd/local-trigger.sh <branch>"
  exit 1
fi

if [[ "${WL_CHAT_SKIP_LOCAL_TRIGGERS:-0}" == "1" ]]; then
  echo "[local-trigger] Skipped because WL_CHAT_SKIP_LOCAL_TRIGGERS=1"
  exit 0
fi

cd "${REPO_ROOT}"

case "${BRANCH_NAME}" in
  main)
    echo "[local-trigger] Running local dev trigger for branch 'main'"
    ./scripts/database/init-local.sh
    ./mvnw -q -DskipTests compile
    ;;
  production)
    echo "[local-trigger] Running production trigger placeholder for branch 'production'"
    ./scripts/cicd/production-deploy-placeholder.sh
    ;;
  *)
    echo "[local-trigger] No local trigger configured for branch '${BRANCH_NAME}'"
    ;;
esac
