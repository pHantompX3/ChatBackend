#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

previous_manifest="${1:-}"
previous_signature="${2:-}"
public_key="${WL_CHAT_RELEASE_PUBLIC_KEY:-${WL_CHAT_SECRET_ROOT}/release-public-key.pem}"
"${SCRIPT_DIR}/verify-release.sh" "${previous_manifest}" "${previous_signature}" "${public_key}"

rollback="$(sed -n 's/.*"rollbackAllowed": \([^,]*\).*/\1/p' "${previous_manifest}" | tr -d ' ')"
[[ "${rollback}" == "true" ]] || { echo "Previous release manifest does not authorize rollback." >&2; exit 1; }
minimum="$(sed -n 's/.*"minimumCompatibleSchema": "\([0-9]*\)".*/\1/p' "${previous_manifest}")"
maximum="$(sed -n 's/.*"maximumCompatibleSchema": "\([0-9]*\)".*/\1/p' "${previous_manifest}")"
image="$(sed -n 's/.*"applicationImage": "\([^"]*\)".*/\1/p' "${previous_manifest}")"

sa_password="$(read_secret sql-sa-password)"
current_schema="$(docker exec wl-chat-production-sqlserver-1 /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "${sa_password}" -C -W -h -1 -d wl_chat \
  -Q 'SET NOCOUNT ON; SELECT MAX(CAST([version] AS bigint)) FROM platform.flyway_schema_history WHERE success = 1;' \
  | tr -d '[:space:]')"
[[ "${current_schema}" =~ ^[0-9]+$ && "${minimum}" =~ ^[0-9]+$ && "${maximum}" =~ ^[0-9]+$ ]] \
  || { echo "Schema compatibility evidence is incomplete." >&2; exit 1; }
if (( current_schema < minimum || current_schema > maximum )); then
  echo "Current schema ${current_schema} is outside rollback range ${minimum}-${maximum}." >&2
  exit 1
fi

export WL_CHAT_APP_IMAGE="${image}"
docker compose --env-file "${PRODUCTION_ENV_FILE}" -f "${REPO_ROOT}/deploy/compose.production.yaml" \
  up -d app proxy cloudflared
"${SCRIPT_DIR}/verify-public-edge.sh" "${WL_CHAT_PUBLIC_HOSTNAME}"
active_release_dir="${REPO_ROOT}/active-release"
mkdir -p "${active_release_dir}"
install -m 0644 "${previous_manifest}" "${active_release_dir}/release-manifest.json.next"
install -m 0644 "${previous_signature}" "${active_release_dir}/release-manifest.sig.next"
mv -f "${active_release_dir}/release-manifest.json.next" \
  "${active_release_dir}/release-manifest.json"
mv -f "${active_release_dir}/release-manifest.sig.next" \
  "${active_release_dir}/release-manifest.sig"
echo "Schema-compatible application rollback completed; database migrations were not reversed."
