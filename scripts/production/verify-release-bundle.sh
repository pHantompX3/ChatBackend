#!/usr/bin/env bash
set -euo pipefail

artifact_dir="${1:-}"
public_key="${2:-}"
[[ -d "${artifact_dir}" ]] || { echo "Release artifact directory is required." >&2; exit 1; }
for file in deployment-bundle.tar.gz deployment-bundle.sha256 deployment-bundle.sha256.sig; do
  [[ -s "${artifact_dir}/${file}" ]] \
    || { echo "Required bundle evidence missing: ${artifact_dir}/${file}" >&2; exit 1; }
done
[[ -s "${public_key}" ]] || { echo "Release public key is missing: ${public_key}" >&2; exit 1; }

openssl dgst -sha256 -verify "${public_key}" \
  -signature "${artifact_dir}/deployment-bundle.sha256.sig" \
  "${artifact_dir}/deployment-bundle.sha256"
expected_sha="$(awk 'NF == 2 && $2 == "deployment-bundle.tar.gz" {print $1}' \
  "${artifact_dir}/deployment-bundle.sha256")"
[[ "${expected_sha}" =~ ^[0-9a-f]{64}$ ]] \
  || { echo "Signed bundle checksum evidence is malformed." >&2; exit 1; }
if command -v sha256sum >/dev/null 2>&1; then
  actual_sha="$(sha256sum "${artifact_dir}/deployment-bundle.tar.gz" | awk '{print $1}')"
else
  actual_sha="$(shasum -a 256 "${artifact_dir}/deployment-bundle.tar.gz" | awk '{print $1}')"
fi
[[ "${actual_sha}" == "${expected_sha}" ]] \
  || { echo "Deployment bundle checksum does not match signed evidence." >&2; exit 1; }
echo "Deployment bundle checksum is signed and matches the archive."
