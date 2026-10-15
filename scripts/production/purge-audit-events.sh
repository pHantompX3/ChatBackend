#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

days="${WL_CHAT_AUDIT_RETENTION_DAYS:-90}"
if [[ ! "${days}" =~ ^[0-9]+$ || "${days}" -lt 62 ]]; then
  echo "Audit retention must be an integer of at least 62 days." >&2
  exit 1
fi

container="${WL_CHAT_SQLSERVER_CONTAINER:-wl-chat-production-sqlserver-1}"
password="$(read_secret sql-maintenance-password)"
docker exec "${container}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U wl_chat_maintenance -P "${password}" -C -b -d wl_chat \
  -Q "EXEC audit.purge_http_audit_events @retention_days=${days}, @batch_size=1000, @max_batches=20;"

