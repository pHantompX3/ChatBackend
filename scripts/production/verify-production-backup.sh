#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
load_production_environment

package_dir="${1:-}"
[[ -n "${package_dir}" && -d "${package_dir}" ]] \
  || { echo "Usage: $0 <retrieved-off-host-package-directory>" >&2; exit 1; }

checksum_file="${package_dir}/package.sha256"
manifest="${package_dir}/manifest.json"
signature="${package_dir}/manifest.sig"
for file in "${checksum_file}" "${manifest}" "${signature}"; do
  [[ -s "${file}" ]] || { echo "Missing backup evidence file: ${file}" >&2; exit 1; }
done

archive_name="$(awk 'NF == 2 {print $2}' "${checksum_file}")"
[[ "${archive_name}" =~ ^wl_chat_[0-9]{8}T[0-9]{6}Z[.]tar[.]gz[.]enc$ ]] \
  || { echo "Unsafe or unexpected archive filename." >&2; exit 1; }
expected_sha="$(awk 'NF == 2 {print $1}' "${checksum_file}")"
actual_sha="$(sha256_file "${package_dir}/${archive_name}")"
[[ "${actual_sha}" == "${expected_sha}" ]] || { echo "Package checksum mismatch." >&2; exit 1; }

openssl dgst -sha256 -verify "${WL_CHAT_SECRET_ROOT}/backup-manifest-public-key.pem" \
  -signature "${signature}" "${manifest}"
signed_sha="$(sed -n 's/.*"encryptedPackageSha256": "\([0-9a-f]*\)".*/\1/p' "${manifest}")"
[[ "${actual_sha}" == "${signed_sha}" ]] \
  || { echo "Package checksum is not authenticated by the signed manifest." >&2; exit 1; }
echo "Retrieved package checksum and detached manifest signature are valid."
