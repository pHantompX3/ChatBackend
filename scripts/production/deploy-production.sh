#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment
mode="${1:-}"
[[ -z "${mode}" || "${mode}" == "--activate-candidate" ]] \
  || { echo "Usage: $0 [--activate-candidate]" >&2; exit 1; }
active_release_dir="${REPO_ROOT}/active-release"
if [[ "${mode}" == "--activate-candidate" ]]; then
  release_manifest="${REPO_ROOT}/release-manifest.json"
  release_signature="${REPO_ROOT}/release-manifest.sig"
elif [[ -s "${active_release_dir}/release-manifest.json" \
  && -s "${active_release_dir}/release-manifest.sig" ]]; then
  release_manifest="${active_release_dir}/release-manifest.json"
  release_signature="${active_release_dir}/release-manifest.sig"
else
  release_manifest="${REPO_ROOT}/release-manifest.json"
  release_signature="${REPO_ROOT}/release-manifest.sig"
fi
"${SCRIPT_DIR}/verify-release.sh" "${release_manifest}" "${release_signature}" \
  "${WL_CHAT_SECRET_ROOT}/release-public-key.pem"
export WL_CHAT_APP_IMAGE="$(sed -n 's/.*"applicationImage": "\([^"]*\)".*/\1/p' "${release_manifest}")"
export WL_CHAT_FLYWAY_IMAGE="$(sed -n 's/.*"migrationImage": "\([^"]*\)".*/\1/p' "${release_manifest}")"
export WL_CHAT_RELEASE_ID="$(sed -n 's/.*"releaseTag": "\([^"]*\)".*/\1/p' "${release_manifest}")"
"${SCRIPT_DIR}/validate-production-config.sh"

COMPOSE=(docker compose --env-file "${PRODUCTION_ENV_FILE}" -f "${REPO_ROOT}/deploy/compose.production.yaml")
export WL_CHAT_SQLSERVER_CONTAINER=wl-chat-production-sqlserver-1
export WL_CHAT_QUEUE_CONTAINER=wl-chat-production-queue-1
export WL_CHAT_SECRETS_FILE=/dev/null
export WL_CHAT_DB_OPERATOR_USERNAME=sa
export WL_CHAT_DB_OPERATOR_PASSWORD="$(read_secret sql-sa-password)"
export WL_CHAT_DB_USERNAME=wl_chat_app
export WL_CHAT_DB_PASSWORD="$(sed -n 's/^quarkus\.datasource\.password=//p' "${WL_CHAT_SECRET_ROOT}/application.properties")"
export WL_CHAT_MIGRATOR_USERNAME=messenger_migrator
export WL_CHAT_MIGRATOR_PASSWORD="$(sed -n 's/^flyway\.password=//p' "${WL_CHAT_SECRET_ROOT}/flyway.conf")"
export WL_CHAT_BACKUP_USERNAME=wl_chat_backup
export WL_CHAT_BACKUP_PASSWORD="$(read_secret sql-backup-password)"
export WL_CHAT_RESTORE_USERNAME=wl_chat_restore
export WL_CHAT_RESTORE_PASSWORD="$(read_secret sql-restore-password)"
export WL_CHAT_MAINTENANCE_USERNAME=wl_chat_maintenance
export WL_CHAT_MAINTENANCE_PASSWORD="$(read_secret sql-maintenance-password)"
export WL_CHAT_QUEUE_USERNAME=wl_chat_queue
export WL_CHAT_QUEUE_PASSWORD="$(read_secret rabbitmq-runtime-password)"
export WL_CHAT_DB_HOST=sqlserver
export WL_CHAT_DB_PORT=1433
export WL_CHAT_FLYWAY_DOCKER_NETWORK=wl-chat-production_data
export WL_CHAT_SQL_TRUSTSTORE_FILE="${WL_CHAT_SECRET_ROOT}/sql-truststore.p12"
export WL_CHAT_MIGRATOR_DB_URL="$(sed -n 's/^flyway\.url=//p' "${WL_CHAT_SECRET_ROOT}/flyway.conf")"

for value_name in WL_CHAT_DB_PASSWORD WL_CHAT_MIGRATOR_PASSWORD WL_CHAT_BACKUP_PASSWORD \
  WL_CHAT_RESTORE_PASSWORD WL_CHAT_MAINTENANCE_PASSWORD; do
  if [[ -z "${!value_name}" ]]; then
    echo "Unable to load ${value_name} from its service-specific secret file." >&2
    exit 1
  fi
done

"${COMPOSE[@]}" pull
"${COMPOSE[@]}" up -d --wait sqlserver queue
"${REPO_ROOT}/scripts/deploy/provision-rabbitmq.sh"

export WL_CHAT_PRESERVE_RUNTIME_FIXED_ROLES=true
"${REPO_ROOT}/scripts/database/provision-hardened-principals.sh"
export WL_CHAT_FLYWAY_TARGET=20260814100000
"${REPO_ROOT}/scripts/database/migrate-hardened.sh"
unset WL_CHAT_FLYWAY_TARGET
export WL_CHAT_PRESERVE_RUNTIME_FIXED_ROLES=false
"${REPO_ROOT}/scripts/database/provision-hardened-principals.sh"
"${REPO_ROOT}/scripts/database/migrate-hardened.sh"
"${REPO_ROOT}/scripts/database/provision-hardened-principals.sh"
"${REPO_ROOT}/scripts/database/verify-hardened-permissions.sh" --scenario production

"${COMPOSE[@]}" up -d app proxy cloudflared
"${COMPOSE[@]}" ps
if [[ "${mode}" == "--activate-candidate" ]]; then
  mkdir -p "${active_release_dir}"
  install -m 0644 "${release_manifest}" "${active_release_dir}/release-manifest.json.next"
  install -m 0644 "${release_signature}" "${active_release_dir}/release-manifest.sig.next"
  mv -f "${active_release_dir}/release-manifest.json.next" \
    "${active_release_dir}/release-manifest.json"
  mv -f "${active_release_dir}/release-manifest.sig.next" \
    "${active_release_dir}/release-manifest.sig"
fi
echo "Production candidate deployed. Run public smoke and observation checks before acceptance."
