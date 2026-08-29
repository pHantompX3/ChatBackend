#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
IMAGE_TAG="${WL_CHAT_BUILD_IMAGE_TAG:-chat-backend:milestone-9}"
JDK_IMAGE="${WL_CHAT_TEMURIN_JDK_IMAGE:-}"
JRE_IMAGE="${WL_CHAT_TEMURIN_JRE_IMAGE:-}"

for image in "${JDK_IMAGE}" "${JRE_IMAGE}"; do
  if [[ "${image}" != *@sha256:* ]]; then
    echo "Hardened builds require digest-pinned WL_CHAT_TEMURIN_JDK_IMAGE and WL_CHAT_TEMURIN_JRE_IMAGE." >&2
    exit 1
  fi
done

version="$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "${REPO_ROOT}/pom.xml" | head -n 1)"
revision="$(git -C "${REPO_ROOT}" rev-parse HEAD)"
build_date="${SOURCE_DATE_EPOCH:-$(date +%s)}"
created_at="$(date -u -r "${build_date}" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null \
  || date -u -d "@${build_date}" +%Y-%m-%dT%H:%M:%SZ)"

docker build --pull \
  --build-arg "TEMURIN_JDK_IMAGE=${JDK_IMAGE}" \
  --build-arg "TEMURIN_JRE_IMAGE=${JRE_IMAGE}" \
  --build-arg "APP_VERSION=${version}" \
  --build-arg "VCS_REF=${revision}" \
  --build-arg "BUILD_DATE=${created_at}" \
  -t "${IMAGE_TAG}" "${REPO_ROOT}"

configured_user="$(docker image inspect --format '{{.Config.User}}' "${IMAGE_TAG}")"
if [[ "${configured_user}" == "" || "${configured_user}" == "0" || "${configured_user}" == "0:0" ]]; then
  echo "Application image does not declare a non-root runtime user." >&2
  exit 1
fi
docker image inspect --format '{{json .Config.Labels}}' "${IMAGE_TAG}"
echo "Built ${IMAGE_TAG} with runtime user ${configured_user}."
