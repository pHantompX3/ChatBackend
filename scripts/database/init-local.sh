#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

"${SCRIPT_DIR}/bootstrap-local.sh"
"${SCRIPT_DIR}/migrate-local.sh"

echo "Local database bootstrap + migration complete."
