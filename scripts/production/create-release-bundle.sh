#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
app_image="${2:-}"
migration_image="${3:-}"
output_dir="${4:-}"
[[ "${version}" =~ ^v[0-9]+[.][0-9]+[.][0-9]+([.-][A-Za-z0-9.-]+)?$ ]] \
  || { echo "Version must be an annotated semantic version tag beginning with v." >&2; exit 1; }
[[ "${app_image}" =~ @sha256:[0-9a-f]{64}$ ]] || { echo "Application image must be digest pinned." >&2; exit 1; }
[[ "${migration_image}" =~ @sha256:[0-9a-f]{64}$ ]] || { echo "Migration image must be digest pinned." >&2; exit 1; }
[[ -n "${output_dir}" ]] || { echo "Output directory is required." >&2; exit 1; }

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
mkdir -p "${output_dir}/bundle"
commit="$(git -C "${REPO_ROOT}" rev-parse HEAD)"
schema="$(find "${REPO_ROOT}/scripts/database/flyway/wl_chat" -maxdepth 1 -type f \
  -name 'V*.sql' -print | sed -E 's#.*V([0-9]+)__.*#\1#' | sort -n | tail -n 1)"
minimum_compatible_schema="${WL_CHAT_MINIMUM_COMPATIBLE_SCHEMA:-${schema}}"
maximum_compatible_schema="${WL_CHAT_MAXIMUM_COMPATIBLE_SCHEMA:-${schema}}"
rollback_allowed="${WL_CHAT_ROLLBACK_ALLOWED:-false}"
[[ -n "${schema}" && "${minimum_compatible_schema}" =~ ^[0-9]+$ \
  && "${maximum_compatible_schema}" =~ ^[0-9]+$ ]] \
  || { echo "Release schema compatibility values must be numeric." >&2; exit 1; }
(( minimum_compatible_schema <= maximum_compatible_schema )) \
  || { echo "Minimum compatible schema cannot exceed maximum compatible schema." >&2; exit 1; }
[[ "${rollback_allowed}" == "true" || "${rollback_allowed}" == "false" ]] \
  || { echo "WL_CHAT_ROLLBACK_ALLOWED must be true or false." >&2; exit 1; }

cat > "${output_dir}/release-manifest.json" <<EOF
{
  "schemaVersion": 1,
  "releaseTag": "${version}",
  "sourceCommit": "${commit}",
  "applicationImage": "${app_image}",
  "migrationImage": "${migration_image}",
  "databaseSchemaTarget": "${schema}",
  "minimumCompatibleSchema": "${minimum_compatible_schema}",
  "maximumCompatibleSchema": "${maximum_compatible_schema}",
  "rollbackAllowed": ${rollback_allowed},
  "createdAtUtc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

cp -R "${REPO_ROOT}/deploy" "${output_dir}/bundle/deploy"
mkdir -p "${output_dir}/bundle/scripts"
cp -R "${REPO_ROOT}/scripts/production" "${output_dir}/bundle/scripts/production"
cp -R "${REPO_ROOT}/scripts/database" "${output_dir}/bundle/scripts/database"
cp -R "${REPO_ROOT}/scripts/deploy" "${output_dir}/bundle/scripts/deploy"
find "${output_dir}/bundle" -name 'hardened.env' -delete
find "${output_dir}/bundle" -name 'production.env' -delete
find "${output_dir}/bundle" -path '*/tls/generated/*' ! -name '.gitkeep' -delete
# Rebuild the secret-template directory exclusively from files tracked by Git. This keeps a manual
# invocation from packaging ignored host-local credentials that happen to live below deploy/.
rm -rf -- "${output_dir}/bundle/deploy/secrets"
mkdir -p "${output_dir}/bundle/deploy/secrets"
while IFS= read -r template; do
  cp "${REPO_ROOT}/${template}" "${output_dir}/bundle/deploy/secrets/$(basename -- "${template}")"
done < <(git -C "${REPO_ROOT}" ls-files --cached --others --exclude-standard \
  'deploy/secrets/*.example' 'deploy/secrets/README.md')
cp "${output_dir}/release-manifest.json" "${output_dir}/bundle/release-manifest.json"
tar -C "${output_dir}/bundle" -czf "${output_dir}/deployment-bundle.tar.gz" .
if command -v sha256sum >/dev/null 2>&1; then
  bundle_sha="$(sha256sum "${output_dir}/deployment-bundle.tar.gz" | awk '{print $1}')"
else
  bundle_sha="$(shasum -a 256 "${output_dir}/deployment-bundle.tar.gz" | awk '{print $1}')"
fi
printf '%s  %s\n' "${bundle_sha}" deployment-bundle.tar.gz \
  > "${output_dir}/deployment-bundle.sha256"

if [[ -n "${WL_CHAT_RELEASE_SIGNING_KEY:-}" ]]; then
  openssl dgst -sha256 -sign "${WL_CHAT_RELEASE_SIGNING_KEY}" \
    -out "${output_dir}/release-manifest.sig" "${output_dir}/release-manifest.json"
  openssl dgst -sha256 -sign "${WL_CHAT_RELEASE_SIGNING_KEY}" \
    -out "${output_dir}/deployment-bundle.sha256.sig" \
    "${output_dir}/deployment-bundle.sha256"
fi
echo "Release manifest and authenticated artifact-only deployment bundle created under ${output_dir}."
