#!/usr/bin/env bash
set -euo pipefail

PRODUCTION_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${PRODUCTION_SCRIPT_DIR}/../.." && pwd)"
PRODUCTION_ENV_FILE="${WL_CHAT_PRODUCTION_ENV_FILE:-${REPO_ROOT}/production.env}"

load_production_environment() {
  if [[ "${WL_CHAT_PRODUCTION_ENV_LOADED:-false}" == "true" ]]; then
    return 0
  fi
  if [[ ! -f "${PRODUCTION_ENV_FILE}" ]]; then
    echo "Production environment file not found: ${PRODUCTION_ENV_FILE}" >&2
    return 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "${PRODUCTION_ENV_FILE}"
  set +a
  export WL_CHAT_PRODUCTION_ENV_LOADED=true
}

read_secret() {
  local filename="$1"
  local path="${WL_CHAT_SECRET_ROOT:?WL_CHAT_SECRET_ROOT is required}/${filename}"
  if [[ ! -f "${path}" || ! -s "${path}" ]]; then
    echo "Required secret file is missing or empty: ${path}" >&2
    return 1
  fi
  tr -d '\r\n' < "${path}"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

require_digest_reference() {
  local variable_name="$1"
  local value="${!variable_name:-}"
  if [[ ! "${value}" =~ @sha256:[0-9a-fA-F]{64}$ ]]; then
    echo "${variable_name} must end with an immutable sha256 digest." >&2
    return 1
  fi
}
