#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/compose.devdocker.yaml"

cd "${REPO_ROOT}"

docker compose -f "${COMPOSE_FILE}" down

echo "DevDocker stack stopped."
