#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
"${SCRIPT_DIR}/generate-origin-tls.sh"
"${SCRIPT_DIR}/generate-sql-tls.sh"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment
compose=(docker compose --env-file "${PRODUCTION_ENV_FILE}" -f "${REPO_ROOT}/deploy/compose.production.yaml")
"${compose[@]}" run --rm sql-volume-init
"${compose[@]}" restart sqlserver app proxy cloudflared
"${compose[@]}" up -d --wait sqlserver queue app proxy cloudflared
echo "Private origin and SQL transport certificates renewed and services recovered."
