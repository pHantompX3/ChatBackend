#!/usr/bin/env bash
set -euo pipefail

manifest="${1:-}"
signature="${2:-}"
public_key="${3:-}"
for file in "${manifest}" "${signature}" "${public_key}"; do
  [[ -s "${file}" ]] || { echo "Required release evidence missing: ${file}" >&2; exit 1; }
done
openssl dgst -sha256 -verify "${public_key}" -signature "${signature}" "${manifest}"

for field in applicationImage migrationImage; do
  value="$(sed -n "s/.*\"${field}\": \"\([^\"]*\)\".*/\1/p" "${manifest}")"
  [[ "${value}" =~ @sha256:[0-9a-f]{64}$ ]] \
    || { echo "${field} is not digest pinned." >&2; exit 1; }
done
rollback="$(sed -n 's/.*"rollbackAllowed": \([^,]*\).*/\1/p' "${manifest}" | tr -d ' ')"
[[ "${rollback}" == "true" || "${rollback}" == "false" ]] \
  || { echo "Manifest lacks an explicit rollbackAllowed decision." >&2; exit 1; }
echo "Release signature, image digests, and rollback decision are valid."

