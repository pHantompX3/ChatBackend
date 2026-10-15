#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

mode="${1:---dry-run}"
[[ "${mode}" == "--dry-run" || "${mode}" == "--apply" ]] \
  || { echo "Usage: $0 --dry-run|--apply" >&2; exit 1; }
root="${WL_CHAT_BACKUP_DESTINATION}"
[[ -d "${root}" && "${root}" != "/" ]] || { echo "Unsafe backup destination." >&2; exit 1; }
newest=""
while IFS= read -r candidate; do
  if [[ -f "${candidate}/backup.complete" ]] \
    && "${SCRIPT_DIR}/verify-production-backup.sh" "${candidate}" >/dev/null 2>&1; then
    newest="${candidate}"
    break
  fi
done < <(find "${root}" -mindepth 1 -maxdepth 1 -type d -name '20??????T??????Z' -print | sort -r)
[[ -n "${newest}" ]] \
  || { echo "No cryptographically verified completed backup exists; refusing retention changes." >&2; exit 1; }

while IFS= read -r candidate; do
  [[ "${candidate}" != "${newest}" ]] || continue
  echo "Retention candidate: ${candidate}"
  if [[ "${mode}" == "--apply" ]]; then rm -rf -- "${candidate}"; fi
done < <(find "${root}" -mindepth 1 -maxdepth 1 -type d -name '20??????T??????Z' -mtime +365 -print)
echo "Retention ${mode} complete; newest cryptographically verified package was protected: ${newest}."
