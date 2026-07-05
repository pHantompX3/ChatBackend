#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

"${SCRIPT_DIR}/bootstrap-devdocker.sh"
"${SCRIPT_DIR}/migrate-devdocker.sh"

echo "DevDocker database bootstrap + migration complete."
