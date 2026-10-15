#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment
hostname="${1:-${WL_CHAT_PUBLIC_HOSTNAME}}"
[[ "${hostname}" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "Unsafe origin hostname." >&2; exit 1; }
mkdir -p "${WL_CHAT_SECRET_ROOT}"
chmod 0700 "${WL_CHAT_SECRET_ROOT}"

ca_key="${WL_CHAT_SECRET_ROOT}/origin-ca.key"
ca_cert="${WL_CHAT_SECRET_ROOT}/origin-ca.pem"
if [[ ! -s "${ca_key}" || ! -s "${ca_cert}" ]]; then
  openssl req -x509 -newkey rsa:3072 -sha256 -days 3650 -nodes \
    -subj '/CN=WL Chat Production Private Origin CA' -keyout "${ca_key}" -out "${ca_cert}"
fi

work_dir="$(mktemp -d)"
trap 'rm -rf -- "${work_dir}"' EXIT
cat > "${work_dir}/origin.ext" <<EOF
subjectAltName=DNS:${hostname},DNS:proxy
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
EOF
openssl req -new -newkey rsa:3072 -nodes -subj "/CN=${hostname}" \
  -keyout "${work_dir}/origin.key" -out "${work_dir}/origin.csr"
openssl x509 -req -sha256 -days 90 -in "${work_dir}/origin.csr" \
  -CA "${ca_cert}" -CAkey "${ca_key}" -CAcreateserial \
  -extfile "${work_dir}/origin.ext" -out "${work_dir}/origin.crt"
install -m 0600 "${work_dir}/origin.key" "${WL_CHAT_SECRET_ROOT}/origin.key"
install -m 0644 "${work_dir}/origin.crt" "${WL_CHAT_SECRET_ROOT}/origin.crt"
chmod 0600 "${ca_key}"
chmod 0644 "${ca_cert}"

openssl x509 -in "${WL_CHAT_SECRET_ROOT}/origin.crt" -noout -subject -issuer -dates
echo "Private connector-to-NGINX certificate generated for ${hostname}."
echo "Run renew-private-tls.sh to restart and verify the affected services."
