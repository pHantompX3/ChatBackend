#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
WL_CHAT_DIR="${SCRIPT_DIR}/flyway/wl_chat"

if [[ ! -d "${WL_CHAT_DIR}" ]]; then
  echo "wl_chat migration directory not found: ${WL_CHAT_DIR}" >&2
  exit 1
fi

timestamp_pattern='^V[0-9]{14}__[a-z0-9_]+\.sql$'
violations=()

for file_path in "${WL_CHAT_DIR}"/*.sql; do
  file_name="$(basename -- "${file_path}")"

  case "${file_name}" in
    V1__create_app_login_and_user.sql|V2__grant_app_permissions.sql|V3__create_logical_schemas.sql)
      continue
      ;;
  esac

  if [[ ! "${file_name}" =~ ${timestamp_pattern} ]]; then
    violations+=("${file_name}")
  fi
done

if [[ ${#violations[@]} -gt 0 ]]; then
  {
    echo "Flyway migration naming validation failed for scripts/database/flyway/wl_chat:"
    for violation in "${violations[@]}"; do
      echo "- ${violation}"
    done
    echo "Allowed legacy files: V1__create_app_login_and_user.sql, V2__grant_app_permissions.sql, V3__create_logical_schemas.sql"
    echo "All newer migrations must match: VYYYYMMDDHHMMSS__description_in_snake_case.sql"
  } >&2
  exit 1
fi

echo "Flyway migration naming validation passed."