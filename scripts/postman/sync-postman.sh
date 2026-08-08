#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v node >/dev/null 2>&1; then
  echo "node is required for Postman synchronization" >&2
  exit 1
fi

node "${SCRIPT_DIR}/sync-postman.mjs" "$@"
