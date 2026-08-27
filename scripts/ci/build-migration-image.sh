#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
IMAGE_TAG="${WL_CHAT_MIGRATION_BUILD_IMAGE_TAG:-chat-backend-flyway:milestone-9}"
JDK_IMAGE="${WL_CHAT_TEMURIN_JDK_IMAGE:-}"
FLYWAY_BASE_IMAGE="${WL_CHAT_FLYWAY_BASE_IMAGE:-}"

for image in "${JDK_IMAGE}" "${FLYWAY_BASE_IMAGE}"; do
  if [[ "${image}" != *@sha256:* ]]; then
    echo "Migration builds require digest-pinned JDK and Flyway base images." >&2
    exit 1
  fi
done

docker build --pull \
  --file "${REPO_ROOT}/deploy/flyway/Dockerfile" \
  --build-arg "TEMURIN_JDK_IMAGE=${JDK_IMAGE}" \
  --build-arg "FLYWAY_BASE_IMAGE=${FLYWAY_BASE_IMAGE}" \
  --tag "${IMAGE_TAG}" "${REPO_ROOT}"

configured_user="$(docker image inspect --format '{{.Config.User}}' "${IMAGE_TAG}")"
if [[ "${configured_user}" == "" || "${configured_user}" == "0" || "${configured_user}" == "0:0" ]]; then
  echo "Migration image does not declare a non-root runtime user." >&2
  exit 1
fi
echo "Built SQL Server-only migration image ${IMAGE_TAG} with runtime user ${configured_user}."
