#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENVIRONMENT=""
ARTIFACT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment) ENVIRONMENT="${2:-}"; shift 2 ;;
    --artifact) ARTIFACT="${2:-}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done
if [[ ! "${ENVIRONMENT}" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "--environment <safe-name> is required." >&2
  exit 1
fi
if [[ -z "${ARTIFACT}" ]]; then
  ARTIFACT="$(find "${REPO_ROOT}/backups/${ENVIRONMENT}" -type f -name 'wl_chat_*.bak' -print 2>/dev/null | sort | tail -n 1)"
fi
if [[ -z "${ARTIFACT}" || ! -f "${ARTIFACT}" ]]; then
  echo "Backup artifact not found." >&2
  exit 1
fi

SECRETS_FILE="${WL_CHAT_HARDENED_ENV_FILE:-${REPO_ROOT}/deploy/hardened.env}"
set -a
# shellcheck disable=SC1090
source "${SECRETS_FILE}"
set +a

SQLSERVER_CONTAINER="${WL_CHAT_SQLSERVER_CONTAINER:-wl-chat-sqlserver-hardened}"
RESTORE_USERNAME="${WL_CHAT_RESTORE_USERNAME:-wl_chat_restore}"
RESTORE_PASSWORD="${WL_CHAT_RESTORE_PASSWORD:-}"
artifact_name="$(basename -- "${ARTIFACT}")"
container_path="/var/opt/mssql/backup/verify_${RANDOM}_${artifact_name}"

expected_sha256="$(sed -n 's/.*"backupSha256": "\([0-9a-f]*\)".*/\1/p' "$(dirname -- "${ARTIFACT}")/manifest.json")"
actual_sha256="$(shasum -a 256 "${ARTIFACT}" | awk '{print $1}')"
if [[ -z "${expected_sha256}" || "${actual_sha256}" != "${expected_sha256}" ]]; then
  echo "Backup checksum does not match its manifest." >&2
  exit 1
fi

docker exec -i "${SQLSERVER_CONTAINER}" /bin/bash -ceu \
  'umask 077; cat > "$1"' -- "${container_path}" < "${ARTIFACT}"
docker exec "${SQLSERVER_CONTAINER}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U "${RESTORE_USERNAME}" -P "${RESTORE_PASSWORD}" -C -b \
  -Q "RESTORE VERIFYONLY FROM DISK = N'${container_path}' WITH CHECKSUM;"

echo "Backup checksum and SQL Server RESTORE VERIFYONLY checks passed."
echo "This is preliminary media verification, not a restore drill."
