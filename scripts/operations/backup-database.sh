#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENVIRONMENT=""
if [[ "${1:-}" == "--environment" ]]; then
  ENVIRONMENT="${2:-}"
fi
if [[ ! "${ENVIRONMENT}" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "Usage: $0 --environment <safe-name>" >&2
  exit 1
fi

SECRETS_FILE="${WL_CHAT_HARDENED_ENV_FILE:-${REPO_ROOT}/deploy/hardened.env}"
if [[ -f "${SECRETS_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${SECRETS_FILE}"
  set +a
fi

SQLSERVER_CONTAINER="${WL_CHAT_SQLSERVER_CONTAINER:-wl-chat-sqlserver-hardened}"
BACKUP_USERNAME="${WL_CHAT_BACKUP_USERNAME:-wl_chat_backup}"
BACKUP_PASSWORD="${WL_CHAT_BACKUP_PASSWORD:-}"
if [[ -z "${BACKUP_PASSWORD}" ]]; then
  echo "WL_CHAT_BACKUP_PASSWORD is required." >&2
  exit 1
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_name="wl_chat_${timestamp}.bak"
container_path="/var/opt/mssql/backup/${backup_name}"
output_dir="${REPO_ROOT}/backups/${ENVIRONMENT}/${timestamp}"
mkdir -p "${output_dir}"

docker exec "${SQLSERVER_CONTAINER}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U "${BACKUP_USERNAME}" -P "${BACKUP_PASSWORD}" -C -b -d wl_chat \
  -Q "BACKUP DATABASE [wl_chat] TO DISK = N'${container_path}' WITH COPY_ONLY, INIT, CHECKSUM, COMPRESSION, ENCRYPTION (ALGORITHM = AES_256, SERVER CERTIFICATE = [wl_chat_backup_certificate]), STATS = 10;"

docker cp "${SQLSERVER_CONTAINER}:${container_path}" "${output_dir}/${backup_name}"
docker cp "${SQLSERVER_CONTAINER}:/var/opt/mssql/backup/wl_chat_backup_certificate.cer" \
  "${output_dir}/wl_chat_backup_certificate.cer"
docker cp "${SQLSERVER_CONTAINER}:/var/opt/mssql/backup/wl_chat_backup_certificate.pvk" \
  "${output_dir}/wl_chat_backup_certificate.pvk"
chmod 0600 "${output_dir}"/*

backup_sha256="$(shasum -a 256 "${output_dir}/${backup_name}" | awk '{print $1}')"
certificate_sha256="$(shasum -a 256 "${output_dir}/wl_chat_backup_certificate.cer" | awk '{print $1}')"
backup_size="$(wc -c < "${output_dir}/${backup_name}" | tr -d ' ')"
cat >"${output_dir}/manifest.json" <<EOF
{
  "schemaVersion": 1,
  "environment": "${ENVIRONMENT}",
  "database": "wl_chat",
  "createdAt": "${timestamp}",
  "backupFile": "${backup_name}",
  "backupSha256": "${backup_sha256}",
  "backupBytes": ${backup_size},
  "certificateSha256": "${certificate_sha256}",
  "encryption": "SQL Server AES_256 certificate"
}
EOF
chmod 0600 "${output_dir}/manifest.json"

echo "Encrypted checksum backup created at ${output_dir}/${backup_name}."
echo "The certificate private-key artifact is separately password protected; store it separately off-host."
