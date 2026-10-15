#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

for tool in docker openssl tar; do
  command -v "${tool}" >/dev/null 2>&1 || { echo "Required tool not found: ${tool}" >&2; exit 1; }
done

container="${WL_CHAT_SQLSERVER_CONTAINER:-wl-chat-production-sqlserver-1}"
password="$(read_secret sql-backup-password)"
restore_password="$(read_secret sql-restore-password)"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
release="${WL_CHAT_RELEASE_ID:-unknown}"
[[ "${release}" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Unsafe release identifier." >&2; exit 1; }

staging="${WL_CHAT_BACKUP_STAGING_ROOT}/${timestamp}"
destination="${WL_CHAT_BACKUP_DESTINATION}/${timestamp}"
mkdir -p "${staging}" "${destination}" "${WL_CHAT_PRODUCTION_ROOT}/status"
chmod 0700 "${staging}"
backup="wl_chat_${timestamp}.bak"
container_backup="/var/opt/mssql/backup/${backup}"

docker exec "${container}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U wl_chat_backup -P "${password}" -C -b -d wl_chat \
  -Q "BACKUP DATABASE [wl_chat] TO DISK=N'${container_backup}' WITH COPY_ONLY, INIT, CHECKSUM, STATS=10;"
docker exec "${container}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U wl_chat_restore -P "${restore_password}" -C -b \
  -Q "RESTORE VERIFYONLY FROM DISK=N'${container_backup}' WITH CHECKSUM;"
docker cp "${container}:${container_backup}" "${staging}/${backup}"
docker exec "${container}" rm -f -- "${container_backup}"
chmod 0600 "${staging}/${backup}"

backup_sha="$(sha256_file "${staging}/${backup}")"
backup_bytes="$(wc -c < "${staging}/${backup}" | tr -d ' ')"
archive="wl_chat_${timestamp}.tar.gz.enc"
tar -C "${staging}" -czf - "${backup}" \
  | openssl enc -aes-256-cbc -pbkdf2 -salt \
      -pass "file:${WL_CHAT_SECRET_ROOT}/backup-archive-password" \
      -out "${staging}/${archive}"
archive_sha="$(sha256_file "${staging}/${archive}")"

cat > "${staging}/manifest.json" <<EOF
{
  "schemaVersion": 1,
  "database": "wl_chat",
  "createdAtUtc": "${timestamp}",
  "releaseId": "${release}",
  "nativeBackup": "${backup}",
  "nativeBackupSha256": "${backup_sha}",
  "nativeBackupBytes": ${backup_bytes},
  "encryptedPackage": "${archive}",
  "encryptedPackageSha256": "${archive_sha}",
  "sqlBackupCompression": false,
  "sqlBackupEncryption": false,
  "packageEncryption": "AES-256-CBC/PBKDF2 with detached RSA-SHA256 manifest signature"
}
EOF
openssl dgst -sha256 -sign "${WL_CHAT_SECRET_ROOT}/backup-manifest-signing-key.pem" \
  -out "${staging}/manifest.sig" "${staging}/manifest.json"
openssl dgst -sha256 -verify "${WL_CHAT_SECRET_ROOT}/backup-manifest-public-key.pem" \
  -signature "${staging}/manifest.sig" "${staging}/manifest.json"

printf '%s  %s\n' "${archive_sha}" "${archive}" > "${staging}/package.sha256"
cp "${staging}/${archive}" "${staging}/manifest.json" "${staging}/manifest.sig" \
  "${staging}/package.sha256" "${destination}/"
[[ "$(sha256_file "${destination}/${archive}")" == "${archive_sha}" ]] \
  || { echo "Off-host package checksum mismatch." >&2; exit 1; }

rm -f -- "${staging:?}/${backup}"
printf '%s\n' "${timestamp}" > "${WL_CHAT_PRODUCTION_ROOT}/status/last-successful-backup-utc"
echo "Verified encrypted backup package transferred to ${destination}."
