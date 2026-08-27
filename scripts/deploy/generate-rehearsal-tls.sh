#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/deploy/tls/generated"
TRUSTSTORE_PASSWORD="${WL_CHAT_SQL_TRUSTSTORE_PASSWORD:-}"

if [[ -z "${TRUSTSTORE_PASSWORD}" ]]; then
  echo "WL_CHAT_SQL_TRUSTSTORE_PASSWORD is required." >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"
chmod 0700 "${OUTPUT_DIR}"
work_dir="$(mktemp -d)"
trap 'rm -rf -- "${work_dir}"' EXIT

cat >"${work_dir}/proxy.ext" <<'EOF'
subjectAltName=DNS:localhost,IP:127.0.0.1
extendedKeyUsage=serverAuth
EOF
cat >"${work_dir}/sql.ext" <<'EOF'
subjectAltName=DNS:sqlserver,DNS:localhost,IP:127.0.0.1
extendedKeyUsage=serverAuth
EOF

openssl req -x509 -newkey rsa:3072 -sha256 -days 30 -nodes \
  -subj "/CN=WL Chat Rehearsal CA" \
  -keyout "${work_dir}/ca.key" -out "${work_dir}/ca.crt"

for name in proxy sqlserver; do
  common_name="${name}"
  extension_file="${work_dir}/${name%%server}.ext"
  openssl req -new -newkey rsa:3072 -nodes -subj "/CN=${common_name}" \
    -keyout "${work_dir}/${name}.key" -out "${work_dir}/${name}.csr"
  openssl x509 -req -sha256 -days 30 \
    -in "${work_dir}/${name}.csr" \
    -CA "${work_dir}/ca.crt" -CAkey "${work_dir}/ca.key" -CAcreateserial \
    -extfile "${extension_file}" -out "${work_dir}/${name}.crt"
done

keytool -importcert -noprompt -alias wl-chat-rehearsal-ca \
  -file "${work_dir}/ca.crt" \
  -keystore "${work_dir}/sql-truststore.p12" -storetype PKCS12 \
  -storepass "${TRUSTSTORE_PASSWORD}"

install -m 0644 "${work_dir}/ca.crt" "${OUTPUT_DIR}/ca.crt"
install -m 0644 "${work_dir}/ca.crt" "${OUTPUT_DIR}/ca.pem"
install -m 0644 "${work_dir}/proxy.crt" "${OUTPUT_DIR}/proxy.crt"
# Bind-mounted rehearsal files must be readable by the non-root container UIDs. The host directory is
# owner-only, while the individual files are read-only and mounted into only their intended service.
# Production must use its secret store to apply service-specific ownership instead.
install -m 0644 "${work_dir}/proxy.key" "${OUTPUT_DIR}/proxy.key"
install -m 0644 "${work_dir}/sqlserver.crt" "${OUTPUT_DIR}/sqlserver.crt"
install -m 0644 "${work_dir}/sqlserver.key" "${OUTPUT_DIR}/sqlserver.key"
install -m 0644 "${work_dir}/sql-truststore.p12" "${OUTPUT_DIR}/sql-truststore.p12"

echo "Generated 30-day rehearsal certificates under deploy/tls/generated."
