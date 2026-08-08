#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RESET_DB="${WL_CHAT_RESET_DB:-false}"

if [[ "${RESET_DB}" == "true" ]]; then
	echo "WL_CHAT_RESET_DB=true: resetting local wl_chat database and Flyway history (destructive)."
	"${SCRIPT_DIR}/reset-local.sh"
fi

"${SCRIPT_DIR}/bootstrap-local.sh"
"${SCRIPT_DIR}/migrate-local.sh"

echo "Local database bootstrap + migration complete."
