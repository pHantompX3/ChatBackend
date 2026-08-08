#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MASTER_DIR="${SCRIPT_DIR}/flyway/master"
WL_CHAT_DIR="${SCRIPT_DIR}/flyway/wl_chat"

if [[ ! -d "${MASTER_DIR}" ]]; then
  echo "master migration directory not found: ${MASTER_DIR}" >&2
  exit 1
fi

if [[ ! -d "${WL_CHAT_DIR}" ]]; then
  echo "wl_chat migration directory not found: ${WL_CHAT_DIR}" >&2
  exit 1
fi

timestamp_pattern='^V[0-9]{14}__[a-z0-9_]+\.sql$'
violations=()

for file_path in "${MASTER_DIR}"/*.sql; do
  file_name="$(basename -- "${file_path}")"

  if [[ ! "${file_name}" =~ ${timestamp_pattern} ]]; then
    violations+=("master/${file_name}")
  fi
done

for file_path in "${WL_CHAT_DIR}"/*.sql; do
  file_name="$(basename -- "${file_path}")"

  if [[ ! "${file_name}" =~ ${timestamp_pattern} ]]; then
    violations+=("wl_chat/${file_name}")
  fi
done

if [[ ${#violations[@]} -gt 0 ]]; then
  {
    echo "Flyway migration naming validation failed under scripts/database/flyway:"
    for violation in "${violations[@]}"; do
      echo "- ${violation}"
    done
    echo "All migration files must match: VYYYYMMDDHHMMSS__description_in_snake_case.sql"
  } >&2
  exit 1
fi

echo "Flyway migration naming validation passed."