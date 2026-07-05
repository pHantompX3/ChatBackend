#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

cd "${REPO_ROOT}"

git config core.hooksPath .githooks
chmod +x .githooks/pre-push
chmod +x scripts/cicd/local-trigger.sh scripts/cicd/production-deploy-placeholder.sh

echo "Installed Git hooks path: .githooks"
echo "pre-push hook is active."
