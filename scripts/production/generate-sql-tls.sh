#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment
mkdir -p "${WL_CHAT_SECRET_ROOT}"
chmod 0700 "${WL_CHAT_SECRET_ROOT}"

ca_key="${WL_CHAT_SECRET_ROOT}/sql-ca.key"
ca_cert="${WL_CHAT_SECRET_ROOT}/sql-ca.pem"
if [[ ! -s "${ca_key}" || ! -s "${ca_cert}" ]]; then
  openssl req -x509 -newkey rsa:3072 -sha256 -days 3650 -nodes \
    -subj '/CN=WL Chat Production SQL CA' -keyout "${ca_key}" -out "${ca_cert}"
fi

work_dir="$(mktemp -d)"
trap 'rm -rf -- "${work_dir}"' EXIT
cat > "${work_dir}/sql.ext" <<'EOF'
subjectAltName=DNS:sqlserver
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
EOF
openssl req -new -newkey rsa:3072 -nodes -subj '/CN=sqlserver' \
  -keyout "${work_dir}/sqlserver.key" -out "${work_dir}/sqlserver.csr"
openssl x509 -req -sha256 -days 365 -in "${work_dir}/sqlserver.csr" \
  -CA "${ca_cert}" -CAkey "${ca_key}" -CAcreateserial \
  -extfile "${work_dir}/sql.ext" -out "${work_dir}/sqlserver.crt"
install -m 0600 "${work_dir}/sqlserver.key" "${WL_CHAT_SECRET_ROOT}/sqlserver.key"
install -m 0644 "${work_dir}/sqlserver.crt" "${WL_CHAT_SECRET_ROOT}/sqlserver.crt"

rm -f -- "${WL_CHAT_SECRET_ROOT}/sql-truststore.p12"
keytool -importcert -noprompt -alias wl-chat-production-sql-ca -file "${ca_cert}" \
  -keystore "${WL_CHAT_SECRET_ROOT}/sql-truststore.p12" -storetype PKCS12 \
  -storepass "$(read_secret sql-truststore-password)"
chmod 0600 "${ca_key}" "${WL_CHAT_SECRET_ROOT}/sql-truststore.p12"
chmod 0644 "${ca_cert}"
openssl x509 -in "${WL_CHAT_SECRET_ROOT}/sqlserver.crt" -noout -subject -issuer -dates

