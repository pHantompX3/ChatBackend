#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

package_dir=""
target=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --package-dir) package_dir="${2:-}"; shift 2 ;;
    --isolated-target) target="${2:-}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done
[[ -d "${package_dir}" ]] || { echo "--package-dir must identify a retrieved package." >&2; exit 1; }
[[ "${target}" =~ ^[A-Za-z][A-Za-z0-9_]{0,63}$ && "${target}" != "wl_chat" ]] \
  || { echo "--isolated-target must be a safe name other than wl_chat." >&2; exit 1; }
"${SCRIPT_DIR}/verify-production-backup.sh" "${package_dir}"

archive_name="$(awk 'NF == 2 {print $2}' "${package_dir}/package.sha256")"
work_dir="$(mktemp -d)"
suffix="$(date +%s)-${RANDOM}"
container="wl-chat-production-restore-${suffix}"
volume="wl-chat-production-restore-${suffix}"
cleanup() {
  docker rm -f "${container}" >/dev/null 2>&1 || true
  docker volume rm "${volume}" >/dev/null 2>&1 || true
  rm -rf -- "${work_dir}"
}
trap cleanup EXIT

openssl enc -d -aes-256-cbc -pbkdf2 \
  -pass "file:${WL_CHAT_SECRET_ROOT}/backup-archive-password" \
  -in "${package_dir}/${archive_name}" | tar -xzf - -C "${work_dir}"
cp "${package_dir}/manifest.json" "${work_dir}/manifest.json"
backup="$(find "${work_dir}" -maxdepth 1 -type f -name 'wl_chat_*.bak' -print -quit)"
[[ -s "${backup}" ]] || { echo "Decrypted package does not contain a backup." >&2; exit 1; }
expected_backup_sha="$(sed -n 's/.*"nativeBackupSha256": "\([0-9a-f]*\)".*/\1/p' "${work_dir}/manifest.json")"
[[ "$(sha256_file "${backup}")" == "${expected_backup_sha}" ]] \
  || { echo "Decrypted native backup checksum mismatch." >&2; exit 1; }

sa_password="$(read_secret sql-sa-password)"
docker volume create "${volume}" >/dev/null
docker run -d --name "${container}" -e ACCEPT_EULA=Y -e MSSQL_PID=Express \
  --mount "type=bind,source=${WL_CHAT_SECRET_ROOT}/sql-sa-password,target=/run/secrets/sql-sa-password,readonly" \
  -v "${volume}:/var/opt/mssql" \
  --entrypoint /bin/bash "${WL_CHAT_SQL_IMAGE}" -c \
  'export MSSQL_SA_PASSWORD="$(cat /run/secrets/sql-sa-password)"; exec /opt/mssql/bin/sqlservr' >/dev/null
for attempt in {1..60}; do
  if docker exec "${container}" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa \
    -P "${sa_password}" -C -Q "SELECT 1" >/dev/null 2>&1; then break; fi
  [[ "${attempt}" -lt 60 ]] || { docker logs --tail 100 "${container}" >&2; exit 1; }
  sleep 2
done

backup_container="/var/opt/mssql/backup/$(basename -- "${backup}")"
docker exec -u 0 "${container}" mkdir -p /var/opt/mssql/backup
docker cp "${backup}" "${container}:${backup_container}"
docker exec -u 0 "${container}" chown 10001:10001 "${backup_container}"
docker exec -u 0 "${container}" chmod 0600 "${backup_container}"
file_list="$(docker exec "${container}" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa \
  -P "${sa_password}" -C -W -h -1 -s '|' -Q "RESTORE FILELISTONLY FROM DISK=N'${backup_container}';")"
data_name="$(printf '%s\n' "${file_list}" | awk -F'|' '$3 ~ /D/ {gsub(/^ +| +$/, "", $1); print $1; exit}')"
log_name="$(printf '%s\n' "${file_list}" | awk -F'|' '$3 ~ /L/ {gsub(/^ +| +$/, "", $1); print $1; exit}')"
[[ -n "${data_name}" && -n "${log_name}" ]] \
  || { echo "Unable to identify backup logical files." >&2; exit 1; }

docker exec "${container}" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa \
  -P "${sa_password}" -C -b -Q \
  "RESTORE VERIFYONLY FROM DISK=N'${backup_container}' WITH CHECKSUM; RESTORE DATABASE [${target}] FROM DISK=N'${backup_container}' WITH CHECKSUM, MOVE N'${data_name}' TO N'/var/opt/mssql/data/${target}.mdf', MOVE N'${log_name}' TO N'/var/opt/mssql/data/${target}_log.ldf'; DBCC CHECKDB(N'${target}') WITH NO_INFOMSGS;"
echo "Isolated SQL Server Express restore and DBCC CHECKDB passed for ${target}."
