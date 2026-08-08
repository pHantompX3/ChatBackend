#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RESET_DB="${WL_CHAT_RESET_DB:-false}"

if [[ "${RESET_DB}" == "true" ]]; then
	echo "WL_CHAT_RESET_DB=true: resetting DevDocker wl_chat database and Flyway history (destructive)."
	"${SCRIPT_DIR}/reset-devdocker.sh"
fi

"${SCRIPT_DIR}/bootstrap-devdocker.sh"
"${SCRIPT_DIR}/migrate-devdocker.sh"

echo "DevDocker database bootstrap + migration complete."
