#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

required=(
  WL_CHAT_APP_IMAGE WL_CHAT_FLYWAY_IMAGE WL_CHAT_SQL_IMAGE WL_CHAT_RABBITMQ_IMAGE
  WL_CHAT_NGINX_IMAGE WL_CHAT_CLOUDFLARED_IMAGE WL_CHAT_PUBLIC_HOSTNAME
  WL_CHAT_PRODUCTION_ROOT WL_CHAT_SECRET_ROOT WL_CHAT_EVIDENCE_ROOT
  WL_CHAT_BACKUP_STAGING_ROOT WL_CHAT_BACKUP_DESTINATION WL_CHAT_BACKUP_MOUNT_SOURCE
)
for variable_name in "${required[@]}"; do
  if [[ -z "${!variable_name:-}" || "${!variable_name}" == *'<'* ]]; then
    echo "${variable_name} is missing or contains a placeholder." >&2
    exit 1
  fi
done

for variable_name in WL_CHAT_APP_IMAGE WL_CHAT_FLYWAY_IMAGE WL_CHAT_SQL_IMAGE \
  WL_CHAT_RABBITMQ_IMAGE WL_CHAT_NGINX_IMAGE WL_CHAT_CLOUDFLARED_IMAGE; do
  require_digest_reference "${variable_name}"
done

if [[ "${WL_CHAT_SQL_EDITION:-}" != "Express" ]]; then
  echo "WL_CHAT_SQL_EDITION must be Express for production." >&2
  exit 1
fi
if [[ ! "${WL_CHAT_PUBLIC_HOSTNAME}" =~ ^[A-Za-z0-9.-]+$ ]]; then
  echo "WL_CHAT_PUBLIC_HOSTNAME is not a safe DNS hostname." >&2
  exit 1
fi

case "$(cd -- "${WL_CHAT_SECRET_ROOT}" 2>/dev/null && pwd -P || true)" in
  "${REPO_ROOT}"|"${REPO_ROOT}"/*)
    echo "Production secrets must live outside the repository." >&2
    exit 1
    ;;
esac

secret_files=(
  sql-sa-password application.properties flyway.conf rabbitmq-operator-password
  rabbitmq-runtime-password sql-backup-password sql-restore-password sql-maintenance-password
  sql-monitor-password
  cloudflared-credentials.json origin-ca.pem origin-ca.key origin.crt origin.key
  sql-ca.pem sql-ca.key sqlserver.crt sqlserver.key sql-truststore.p12 sql-truststore-password
  backup-archive-password
  backup-manifest-signing-key.pem backup-manifest-public-key.pem release-public-key.pem
)

secret_root_resolved="$(realpath -e "${WL_CHAT_SECRET_ROOT}")"
validate_windows_acl() {
  local path="$1"
  local windows_path encoded_path
  windows_path="$(wslpath -w "${path}")"
  encoded_path="$(printf '%s' "${windows_path}" | iconv -f UTF-8 -t UTF-16LE | base64 | tr -d '\r\n')"
  powershell.exe -NoProfile -NonInteractive -Command \
    "\$p=[Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('${encoded_path}')); \$bad=(Get-Acl -LiteralPath \$p).Access | Where-Object { \$_.AccessControlType -eq 'Allow' -and \$_.IdentityReference.Value -match '(^Everyone$|Authenticated Users$|BUILTIN\\\\Users$)' }; if (\$bad) { exit 1 }" \
    >/dev/null
}

validate_secret_access() {
  local filename="$1"
  local path="${WL_CHAT_SECRET_ROOT}/${filename}"
  local resolved mode mode_value
  [[ ! -L "${path}" ]] || { echo "Secret files must not be symbolic links: ${path}" >&2; return 1; }
  resolved="$(realpath -e "${path}")"
  [[ "${resolved}" == "${secret_root_resolved}/"* ]] \
    || { echo "Secret path resolves outside WL_CHAT_SECRET_ROOT: ${path}" >&2; return 1; }
  [[ -f "${resolved}" && -s "${resolved}" ]] \
    || { echo "Required secret file is missing or empty: ${resolved}" >&2; return 1; }
  mode="$(stat -c '%a' "${resolved}")"
  mode_value=$((8#${mode}))
  case "${filename}" in
    origin-ca.pem|origin.crt|sql-ca.pem|sqlserver.crt|sql-truststore.p12|backup-manifest-public-key.pem|release-public-key.pem)
      (( (mode_value & 8#022) == 0 )) \
        || { echo "Public trust material must not be group/other writable: ${resolved} (${mode})." >&2; return 1; }
      ;;
    *)
      (( (mode_value & 8#077) == 0 )) \
        || { echo "Private secret has group/other permissions: ${resolved} (${mode})." >&2; return 1; }
      ;;
  esac
  if [[ "${resolved}" == /mnt/[A-Za-z]/* ]]; then
    command -v powershell.exe >/dev/null 2>&1 \
      || { echo "powershell.exe is required to validate Windows ACLs for ${resolved}." >&2; return 1; }
    validate_windows_acl "${resolved}" \
      || { echo "Windows ACL grants a broad principal access to ${resolved}." >&2; return 1; }
  fi
}

for filename in "${secret_files[@]}"; do
  validate_secret_access "${filename}"
done

cloudflared_config="${WL_CHAT_PRODUCTION_ROOT}/cloudflared/config.yml"
if [[ ! -s "${cloudflared_config}" ]]; then
  echo "Cloudflared configuration is missing: ${cloudflared_config}" >&2
  exit 1
fi
if grep -Eq '<[^>]+>|noTLSVerify:[[:space:]]*true' "${cloudflared_config}"; then
  echo "Cloudflared configuration contains a placeholder or disables TLS verification." >&2
  exit 1
fi

docker compose --env-file "${PRODUCTION_ENV_FILE}" \
  -f "${REPO_ROOT}/deploy/compose.production.yaml" config --quiet
echo "Production configuration preflight passed without printing secret values."
