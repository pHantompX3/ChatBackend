#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
TRIVY_IMAGE="${WL_CHAT_TRIVY_IMAGE:-}"
APP_IMAGE="${WL_CHAT_SCAN_IMAGE:-}"
MIGRATION_IMAGE="${WL_CHAT_SCAN_MIGRATION_IMAGE:-}"
REPORT_DIR="${REPO_ROOT}/security-reports"
SCAN_INPUT="$(mktemp -d)"
IMAGE_ARCHIVE="${SCAN_INPUT}/application-image.tar"
CACHE_DIR="${REPORT_DIR}/.trivy-cache"
IGNORE_FILE="${REPO_ROOT}/.trivyignore.yaml"
cleanup() {
  rm -rf -- "${SCAN_INPUT}"
}
trap cleanup EXIT

if [[ "${TRIVY_IMAGE}" != *@sha256:* ]]; then
  echo "WL_CHAT_TRIVY_IMAGE must be a reviewed digest-pinned image." >&2
  exit 1
fi
if [[ -z "${APP_IMAGE}" ]]; then
  echo "WL_CHAT_SCAN_IMAGE is required." >&2
  exit 1
fi
mkdir -p "${REPORT_DIR}" "${CACHE_DIR}"
if [[ ! -f "${IGNORE_FILE}" ]]; then
  echo "The reviewed Trivy finding-disposition file is missing: ${IGNORE_FILE}" >&2
  exit 1
fi

# Never mount the working tree into a third-party scanner container. Stage only files that Git would
# allow into a commit so ignored local secrets, certificates, backups, and build output stay outside
# the scanner boundary while untracked implementation files are still checked before commit.
git -C "${REPO_ROOT}" ls-files --cached --others --exclude-standard -z \
  | tar -C "${REPO_ROOT}" --null -T - -cf - \
  | tar -C "${SCAN_INPUT}" -xf -

docker run --rm -v "${SCAN_INPUT}:/workspace:ro" -v "${REPORT_DIR}:/reports" \
  -v "${IGNORE_FILE}:/scan-policy/.trivyignore.yaml:ro" \
  -v "${CACHE_DIR}:/root/.cache/trivy" \
  "${TRIVY_IMAGE}" fs --scanners vuln,misconfig,secret \
  --offline-scan --ignorefile /scan-policy/.trivyignore.yaml \
  --severity HIGH,CRITICAL --exit-code 1 --format json --output /reports/filesystem.json /workspace

docker image save --output "${IMAGE_ARCHIVE}" "${APP_IMAGE}"
docker run --rm -v "${IMAGE_ARCHIVE}:/scan/application-image.tar:ro" \
  -v "${IGNORE_FILE}:/scan-policy/.trivyignore.yaml:ro" \
  -v "${REPORT_DIR}:/reports" -v "${CACHE_DIR}:/root/.cache/trivy" \
  "${TRIVY_IMAGE}" image --scanners vuln,secret \
  --offline-scan --ignorefile /scan-policy/.trivyignore.yaml \
  --severity HIGH,CRITICAL --exit-code 1 --format json --output /reports/image.json \
  --input /scan/application-image.tar

if [[ -n "${MIGRATION_IMAGE}" ]]; then
  migration_archive="${SCAN_INPUT}/migration-image.tar"
  docker image save --output "${migration_archive}" "${MIGRATION_IMAGE}"
  docker run --rm -v "${migration_archive}:/scan/migration-image.tar:ro" \
    -v "${IGNORE_FILE}:/scan-policy/.trivyignore.yaml:ro" \
    -v "${REPORT_DIR}:/reports" -v "${CACHE_DIR}:/root/.cache/trivy" \
    "${TRIVY_IMAGE}" image --scanners vuln,secret \
    --offline-scan --ignorefile /scan-policy/.trivyignore.yaml \
    --severity HIGH,CRITICAL --exit-code 1 --format json \
    --output /reports/migration-image.json --input /scan/migration-image.tar
fi

if [[ -n "${WL_CHAT_SCAN_INFRA_IMAGES:-}" ]]; then
  IFS=',' read -r -a infrastructure_images <<<"${WL_CHAT_SCAN_INFRA_IMAGES}"
  index=0
  infrastructure_scan_failed=false
  for image in "${infrastructure_images[@]}"; do
    image="${image//[[:space:]]/}"
    if [[ -z "${image}" ]]; then
      continue
    fi
    if [[ "${image}" != *@sha256:* ]]; then
      echo "Infrastructure scan image must be digest pinned: ${image}" >&2
      exit 1
    fi
    index="$((index + 1))"
    if ! docker run --rm -v "${REPORT_DIR}:/reports" -v "${CACHE_DIR}:/root/.cache/trivy" \
      -v "${IGNORE_FILE}:/scan-policy/.trivyignore.yaml:ro" \
      "${TRIVY_IMAGE}" image --scanners vuln,secret \
      --offline-scan --ignorefile /scan-policy/.trivyignore.yaml \
      --severity HIGH,CRITICAL --exit-code 1 --format json \
      --output "/reports/infrastructure-${index}.json" "${image}"; then
      infrastructure_scan_failed=true
    fi
  done
  if [[ "${infrastructure_scan_failed}" == "true" ]]; then
    echo "One or more infrastructure image scans reported High/Critical findings." >&2
    exit 1
  fi
fi

echo "Filesystem, configuration, secret, and image scans passed."
