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
newest="$(find "${root}" -mindepth 1 -maxdepth 1 -type d -name '20??????T??????Z' -print | sort | tail -n 1)"
[[ -n "${newest}" ]] || { echo "No timestamped backups found; nothing to prune."; exit 0; }

while IFS= read -r candidate; do
  [[ "${candidate}" != "${newest}" ]] || continue
  echo "Retention candidate: ${candidate}"
  if [[ "${mode}" == "--apply" ]]; then rm -rf -- "${candidate}"; fi
done < <(find "${root}" -mindepth 1 -maxdepth 1 -type d -name '20??????T??????Z' -mtime +365 -print)
echo "Retention ${mode} complete; newest verified directory was protected."

