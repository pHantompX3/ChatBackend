#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

container="${WL_CHAT_SQLSERVER_CONTAINER:-wl-chat-production-sqlserver-1}"
password="$(read_secret sql-sa-password)"
query="SET NOCOUNT ON; SELECT CAST(SUM(size) * 8.0 / 1024 AS decimal(12,2)) AS data_size_mb FROM sys.database_files WHERE type_desc = 'ROWS';"
size="$({ docker exec "${container}" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa \
  -P "${password}" -C -h -1 -W -d wl_chat -Q "${query}"; } | tr -d '[:space:]')"
if [[ ! "${size}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Unable to determine wl_chat database size." >&2
  exit 1
fi

awk -v size="${size}" 'BEGIN {
  printf "wl_chat data-file size: %.2f MB\n", size
  if (size >= 9216) { print "CRITICAL: production growth beyond 9 GB is prohibited."; exit 2 }
  if (size >= 8704) { print "ESCALATE: database is at or above 8.5 GB."; exit 2 }
  if (size >= 7680) { print "WARNING: database is at or above 7.5 GB."; exit 1 }
}'
