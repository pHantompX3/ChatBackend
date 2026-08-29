#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENVIRONMENT=""
TARGET_DATABASE=""
ARTIFACT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment) ENVIRONMENT="${2:-}"; shift 2 ;;
    --isolated-target) TARGET_DATABASE="${2:-}"; shift 2 ;;
    --artifact) ARTIFACT="${2:-}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done
if [[ ! "${ENVIRONMENT}" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "--environment <safe-name> is required." >&2
  exit 1
fi
if [[ ! "${TARGET_DATABASE}" =~ ^[A-Za-z][A-Za-z0-9_]{0,63}$ \
  || "${TARGET_DATABASE}" == "wl_chat" ]]; then
  echo "--isolated-target must be a safe database name other than wl_chat." >&2
  exit 1
fi
if [[ -z "${ARTIFACT}" ]]; then
  ARTIFACT="$(find "${REPO_ROOT}/backups/${ENVIRONMENT}" -type f -name 'wl_chat_*.bak' -print 2>/dev/null | sort | tail -n 1)"
fi
if [[ -z "${ARTIFACT}" || ! -f "${ARTIFACT}" ]]; then
  echo "Backup artifact not found." >&2
  exit 1
fi

artifact_dir="$(cd -- "$(dirname -- "${ARTIFACT}")" && pwd)"
for filename in wl_chat_backup_certificate.cer wl_chat_backup_certificate.pvk manifest.json; do
  if [[ ! -s "${artifact_dir}/${filename}" ]]; then
    echo "Required restore artifact missing: ${artifact_dir}/${filename}" >&2
    exit 1
  fi
done

SECRETS_FILE="${WL_CHAT_HARDENED_ENV_FILE:-${REPO_ROOT}/deploy/hardened.env}"
set -a
# shellcheck disable=SC1090
source "${SECRETS_FILE}"
set +a

required=(
  WL_CHAT_SQL_IMAGE WL_CHAT_APP_IMAGE WL_CHAT_DB_OPERATOR_PASSWORD WL_CHAT_DB_PASSWORD
  WL_CHAT_MIGRATOR_PASSWORD WL_CHAT_BACKUP_MASTER_KEY_PASSWORD
  WL_CHAT_BACKUP_CERTIFICATE_PASSWORD WL_CHAT_SQL_TRUSTSTORE_PASSWORD
)
for variable_name in "${required[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "${variable_name} is required." >&2
    exit 1
  fi
done

started_at="$(date +%s)"
suffix="${RANDOM}${RANDOM}"
network="wl-chat-restore-${suffix}"
sql_container="wl-chat-restore-sql-${suffix}"
app_container="wl-chat-restore-app-${suffix}"
data_volume="wl-chat-restore-data-${suffix}"
secrets_volume="wl-chat-restore-secrets-${suffix}"
cleanup() {
  docker rm -f "${app_container}" "${sql_container}" >/dev/null 2>&1 || true
  docker network rm "${network}" >/dev/null 2>&1 || true
  docker volume rm "${data_volume}" "${secrets_volume}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "${network}" >/dev/null
docker volume create "${data_volume}" >/dev/null
docker volume create "${secrets_volume}" >/dev/null
docker run --rm --user 0:0 --entrypoint /bin/bash \
  -v "${data_volume}:/var/opt/mssql/data" \
  -v "${secrets_volume}:/var/opt/mssql/secrets" \
  -v "${REPO_ROOT}/deploy/tls/generated:/source-tls:ro" \
  "${WL_CHAT_SQL_IMAGE}" -ceu '
    chown -R 10001:10001 /var/opt/mssql/data /var/opt/mssql/secrets
    chmod -R u+rwX,g+rwX,o-rwx /var/opt/mssql/data /var/opt/mssql/secrets
    install -o 10001 -g 10001 -m 0640 /source-tls/sqlserver.crt /var/opt/mssql/secrets/sqlserver.crt
    install -o 10001 -g 10001 -m 0640 /source-tls/sqlserver.key /var/opt/mssql/secrets/sqlserver.key
  '
docker run -d --name "${sql_container}" --network "${network}" --network-alias sqlserver \
  -e ACCEPT_EULA=Y -e MSSQL_PID=Developer \
  -e "MSSQL_SA_PASSWORD=${WL_CHAT_DB_OPERATOR_PASSWORD}" \
  -v "${data_volume}:/var/opt/mssql/data" \
  -v "${secrets_volume}:/var/opt/mssql/secrets" \
  -v "${artifact_dir}:/restore:ro" \
  -v "${REPO_ROOT}/deploy/sql/mssql.conf:/var/opt/mssql/mssql.conf:ro" \
  "${WL_CHAT_SQL_IMAGE}" >/dev/null

for attempt in {1..60}; do
  if docker exec "${sql_container}" /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P "${WL_CHAT_DB_OPERATOR_PASSWORD}" -C -Q "SELECT 1" \
    >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 60 ]]; then
    docker logs --tail=160 "${sql_container}" >&2
    echo "Isolated SQL Server did not become ready." >&2
    exit 1
  fi
  sleep 2
done

encode_password_hex() {
  printf '%s' "$1" | iconv -f UTF-8 -t UTF-16LE | od -An -v -tx1 | tr -d ' \n'
}
backup_filename="$(basename -- "${ARTIFACT}")"
master_key_password_hex="$(encode_password_hex "${WL_CHAT_BACKUP_MASTER_KEY_PASSWORD}")"
certificate_password_hex="$(encode_password_hex "${WL_CHAT_BACKUP_CERTIFICATE_PASSWORD}")"
runtime_password_hex="$(encode_password_hex "${WL_CHAT_DB_PASSWORD}")"
migrator_password_hex="$(encode_password_hex "${WL_CHAT_MIGRATOR_PASSWORD}")"

docker exec -i "${sql_container}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "${WL_CHAT_DB_OPERATOR_PASSWORD}" -C -b \
  -v TARGET_DATABASE="${TARGET_DATABASE}" \
  -v BACKUP_FILE="${backup_filename}" \
  -v RUNTIME_LOGIN="${WL_CHAT_DB_USERNAME:-wl_chat_app}" \
  -v MIGRATOR_LOGIN="${WL_CHAT_MIGRATOR_USERNAME:-messenger_migrator}" \
  -v MASTER_KEY_PASSWORD_HEX="${master_key_password_hex}" \
  -v CERTIFICATE_PASSWORD_HEX="${certificate_password_hex}" \
  -v RUNTIME_PASSWORD_HEX="${runtime_password_hex}" \
  -v MIGRATOR_PASSWORD_HEX="${migrator_password_hex}" <<'SQL'
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
USE [master];

DECLARE @master_key_password nvarchar(128) = CONVERT(nvarchar(128),
    CAST(N'' AS xml).value('xs:hexBinary("$(MASTER_KEY_PASSWORD_HEX)")', 'varbinary(256)'));
DECLARE @certificate_password nvarchar(128) = CONVERT(nvarchar(128),
    CAST(N'' AS xml).value('xs:hexBinary("$(CERTIFICATE_PASSWORD_HEX)")', 'varbinary(256)'));
DECLARE @runtime_password nvarchar(128) = CONVERT(nvarchar(128),
    CAST(N'' AS xml).value('xs:hexBinary("$(RUNTIME_PASSWORD_HEX)")', 'varbinary(256)'));
DECLARE @migrator_password nvarchar(128) = CONVERT(nvarchar(128),
    CAST(N'' AS xml).value('xs:hexBinary("$(MIGRATOR_PASSWORD_HEX)")', 'varbinary(256)'));
DECLARE @sql nvarchar(max);

IF @master_key_password IS NULL OR @certificate_password IS NULL
    OR @runtime_password IS NULL OR @migrator_password IS NULL
BEGIN
    THROW 50003, 'An isolated-restore password could not be decoded.', 1;
END;

SET @sql = N'CREATE MASTER KEY ENCRYPTION BY PASSWORD = ' + QUOTENAME(@master_key_password, '''') + N';';
EXEC sys.sp_executesql @sql;
SET @sql = N'CREATE CERTIFICATE [wl_chat_backup_certificate] '
    + N'FROM FILE = N''/restore/wl_chat_backup_certificate.cer'' '
    + N'WITH PRIVATE KEY (FILE = N''/restore/wl_chat_backup_certificate.pvk'', '
    + N'DECRYPTION BY PASSWORD = ' + QUOTENAME(@certificate_password, '''') + N');';
EXEC sys.sp_executesql @sql;

RESTORE VERIFYONLY FROM DISK = N'/restore/$(BACKUP_FILE)' WITH CHECKSUM;
RESTORE DATABASE [$(TARGET_DATABASE)] FROM DISK = N'/restore/$(BACKUP_FILE)'
    WITH CHECKSUM,
    MOVE N'wl_chat' TO N'/var/opt/mssql/data/$(TARGET_DATABASE).mdf',
    MOVE N'wl_chat_log' TO N'/var/opt/mssql/data/$(TARGET_DATABASE)_log.ldf';
DBCC CHECKDB (N'$(TARGET_DATABASE)') WITH NO_INFOMSGS;

DECLARE @runtime_sid varbinary(85);
DECLARE @migrator_sid varbinary(85);
SET @sql = N'SELECT @runtime = [sid] FROM [$(TARGET_DATABASE)].sys.database_principals '
    + N'WHERE [name] = N''$(RUNTIME_LOGIN)''; '
    + N'SELECT @migrator = [sid] FROM [$(TARGET_DATABASE)].sys.database_principals '
    + N'WHERE [name] = N''$(MIGRATOR_LOGIN)'';';
EXEC sys.sp_executesql @sql,
    N'@runtime varbinary(85) OUTPUT, @migrator varbinary(85) OUTPUT',
    @runtime_sid OUTPUT, @migrator_sid OUTPUT;

SET @sql = N'CREATE LOGIN [$(RUNTIME_LOGIN)] WITH PASSWORD = '
    + QUOTENAME(@runtime_password, '''') + N', SID = '
    + CONVERT(nvarchar(max), @runtime_sid, 1) + N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;';
EXEC sys.sp_executesql @sql;
SET @sql = N'CREATE LOGIN [$(MIGRATOR_LOGIN)] WITH PASSWORD = '
    + QUOTENAME(@migrator_password, '''') + N', SID = '
    + CONVERT(nvarchar(max), @migrator_sid, 1) + N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;';
EXEC sys.sp_executesql @sql;
SQL

export WL_CHAT_DB_HOST=sqlserver
export WL_CHAT_DB_PORT=1433
export WL_CHAT_FLYWAY_DOCKER_NETWORK="${network}"
export WL_CHAT_SQL_TRUSTSTORE_FILE="${REPO_ROOT}/deploy/tls/generated/sql-truststore.p12"
export WL_CHAT_MIGRATOR_DB_URL="jdbc:sqlserver://sqlserver:1433;databaseName=${TARGET_DATABASE};encrypt=true;trustServerCertificate=false;trustStore=/flyway/tls/sql-truststore.p12;trustStorePassword=${WL_CHAT_SQL_TRUSTSTORE_PASSWORD}"
"${REPO_ROOT}/scripts/database/migrate-hardened.sh"

docker run -d --name "${app_container}" --network "${network}" -p 127.0.0.1::8080 \
  --read-only --tmpfs /tmp:size=64m,mode=1777 \
  --tmpfs /app/native-tmp:size=16m,mode=0700,uid=10001,gid=10001,exec \
  --user 10001:10001 --cap-drop ALL --security-opt no-new-privileges \
  -e JAVA_TOOL_OPTIONS=-Djna.tmpdir=/app/native-tmp \
  -e QUARKUS_PROFILE=hardened -e QUARKUS_HTTP_HOST=0.0.0.0 \
  -e "WL_CHAT_DB_URL=jdbc:sqlserver://sqlserver:1433;databaseName=${TARGET_DATABASE};encrypt=true;trustServerCertificate=false;trustStore=/app/tls/sql-truststore.p12;trustStorePassword=${WL_CHAT_SQL_TRUSTSTORE_PASSWORD}" \
  -e "WL_CHAT_DB_USERNAME=${WL_CHAT_DB_USERNAME:-wl_chat_app}" \
  -e "WL_CHAT_DB_PASSWORD=${WL_CHAT_DB_PASSWORD}" \
  -e WL_CHAT_AUDIT_RABBITMQ_ENABLED=false \
  -e "WL_CHAT_WEBSOCKET_ALLOWED_ORIGINS=${WL_CHAT_WEBSOCKET_ALLOWED_ORIGINS:-https://localhost}" \
  -v "${REPO_ROOT}/deploy/tls/generated/sql-truststore.p12:/app/tls/sql-truststore.p12:ro" \
  "${WL_CHAT_APP_IMAGE}" >/dev/null

host_port="$(docker port "${app_container}" 8080/tcp | head -n 1 | awk -F: '{print $NF}')"
for attempt in {1..45}; do
  if curl -fsS "http://127.0.0.1:${host_port}/q/health/ready" >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 45 ]]; then
    docker logs --tail=160 "${app_container}" >&2
    echo "Application could not read the isolated restored database." >&2
    exit 1
  fi
  sleep 2
done

restore_smoke_variables=(
  WL_CHAT_RESTORE_SMOKE_USERNAME WL_CHAT_RESTORE_SMOKE_PASSWORD
  WL_CHAT_RESTORE_SMOKE_CONVERSATION_ID WL_CHAT_RESTORE_SMOKE_MESSAGE_ID
  WL_CHAT_RESTORE_SMOKE_SEQUENCE
)
configured_restore_smoke_variables=0
for variable_name in "${restore_smoke_variables[@]}"; do
  if [[ -n "${!variable_name:-}" ]]; then
    configured_restore_smoke_variables="$((configured_restore_smoke_variables + 1))"
  fi
done
if (( configured_restore_smoke_variables != 0 \
  && configured_restore_smoke_variables != ${#restore_smoke_variables[@]} )); then
  echo "Supply all restore smoke variables or none of them." >&2
  exit 1
fi

if (( configured_restore_smoke_variables == ${#restore_smoke_variables[@]} )); then
  if [[ ! "${WL_CHAT_RESTORE_SMOKE_SEQUENCE}" =~ ^[1-9][0-9]*$ ]]; then
    echo "WL_CHAT_RESTORE_SMOKE_SEQUENCE must be a positive integer." >&2
    exit 1
  fi
  login_body="$(jq -cn \
    --arg username "${WL_CHAT_RESTORE_SMOKE_USERNAME}" \
    --arg password "${WL_CHAT_RESTORE_SMOKE_PASSWORD}" \
    '{username:$username,password:$password}')"
  restored_token="$(curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    --data "${login_body}" \
    "http://127.0.0.1:${host_port}/api/v1/sessions" | jq -er '.token')"
  restored_history="$(curl --fail --silent --show-error \
    -H "Authorization: Bearer ${restored_token}" \
    "http://127.0.0.1:${host_port}/api/v1/conversations/${WL_CHAT_RESTORE_SMOKE_CONVERSATION_ID}/messages?afterSequence=0&limit=50")"
  printf '%s' "${restored_history}" | jq -e \
    --arg message_id "${WL_CHAT_RESTORE_SMOKE_MESSAGE_ID}" \
    --argjson sequence "${WL_CHAT_RESTORE_SMOKE_SEQUENCE}" \
    '.items | any(.messageId == $message_id and .sequenceNumber == $sequence)' >/dev/null
  restored_position="$(curl --fail --silent --show-error \
    -H "Authorization: Bearer ${restored_token}" \
    "http://127.0.0.1:${host_port}/api/v1/conversations/${WL_CHAT_RESTORE_SMOKE_CONVERSATION_ID}/position")"
  printf '%s' "${restored_position}" | jq -e \
    --argjson sequence "${WL_CHAT_RESTORE_SMOKE_SEQUENCE}" \
    '.latestSequence >= $sequence
      and .lastDeliveredSequence >= $sequence
      and .lastReadSequence >= $sequence' >/dev/null
fi

evidence_dir="${REPO_ROOT}/backups/${ENVIRONMENT}/restore-evidence"
mkdir -p "${evidence_dir}"
evidence_file="${evidence_dir}/$(date -u +%Y%m%dT%H%M%SZ).txt"
docker exec "${sql_container}" /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "${WL_CHAT_DB_OPERATOR_PASSWORD}" -C -b -d "${TARGET_DATABASE}" \
  -Q "SELECT COUNT_BIG(*) AS users FROM [identity].[user_account]; SELECT COUNT_BIG(*) AS conversations FROM [messaging].[conversation]; SELECT COUNT_BIG(*) AS messages, SUM(CASE WHEN deleted_at IS NOT NULL AND body IS NULL THEN 1 ELSE 0 END) AS tombstones FROM [messaging].[message]; SELECT COUNT_BIG(*) AS sessions FROM [identity].[session]; SELECT MAX([version]) AS flyway_version FROM [platform].[flyway_schema_history];" \
  >"${evidence_file}"
elapsed_seconds="$(( $(date +%s) - started_at ))"
printf '\nrestore_elapsed_seconds=%s\nrestore_objective_seconds=3600\n' \
  "${elapsed_seconds}" >>"${evidence_file}"
if (( configured_restore_smoke_variables == ${#restore_smoke_variables[@]} )); then
  printf 'authentication_message_cursor_smoke=passed\n' >>"${evidence_file}"
else
  printf 'authentication_message_cursor_smoke=not_configured\n' >>"${evidence_file}"
fi

echo "Isolated restore, DBCC, current migration, data invariants, and application readiness passed."
if (( configured_restore_smoke_variables == ${#restore_smoke_variables[@]} )); then
  echo "Restored authentication, message history, and delivery/read cursor smoke passed."
fi
echo "Evidence: ${evidence_file}"
