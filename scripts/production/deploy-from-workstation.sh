#!/usr/bin/env bash
set -euo pipefail

artifact_dir="${1:-}"
target="${WL_CHAT_PRODUCTION_SSH_TARGET:-}"
remote_root="${WL_CHAT_PRODUCTION_REMOTE_ROOT:-C:/ProgramData/WLChat/deployment}"
wsl_root="${WL_CHAT_PRODUCTION_WSL_ROOT:-/mnt/c/ProgramData/WLChat/deployment}"
public_key="${WL_CHAT_RELEASE_PUBLIC_KEY:-}"
[[ -d "${artifact_dir}" ]] || { echo "Release artifact directory is required." >&2; exit 1; }
[[ "${target}" =~ ^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$ ]] \
  || { echo "WL_CHAT_PRODUCTION_SSH_TARGET must be a safe user@host value." >&2; exit 1; }
[[ "${remote_root}" =~ ^[A-Za-z]:/[A-Za-z0-9_./-]+$ ]] \
  || { echo "Unsafe production remote root." >&2; exit 1; }
[[ "${wsl_root}" =~ ^/[A-Za-z0-9_./-]+$ ]] || { echo "Unsafe production WSL root." >&2; exit 1; }

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
"${SCRIPT_DIR}/verify-release.sh" "${artifact_dir}/release-manifest.json" \
  "${artifact_dir}/release-manifest.sig" "${public_key}"
"${SCRIPT_DIR}/verify-release-bundle.sh" "${artifact_dir}" "${public_key}"

ssh "${target}" "powershell -NoProfile -NonInteractive -Command \"New-Item -ItemType Directory -Force -Path '${remote_root}' | Out-Null\""
scp "${artifact_dir}/deployment-bundle.tar.gz" "${artifact_dir}/deployment-bundle.sha256" \
  "${artifact_dir}/deployment-bundle.sha256.sig" \
  "${artifact_dir}/release-manifest.json" "${artifact_dir}/release-manifest.sig" \
  "${target}:${remote_root}/"
ssh "${target}" "powershell -NoProfile -NonInteractive -Command \"Set-Location '${remote_root}'; \$expected = ((Get-Content deployment-bundle.sha256).Trim() -split '\\s+')[0]; if ((Get-FileHash deployment-bundle.tar.gz -Algorithm SHA256).Hash.ToLower() -ne \$expected) { throw 'Bundle checksum mismatch after transfer' }; tar.exe -xzf deployment-bundle.tar.gz\""
ssh "${target}" "wsl.exe -- bash -lc \"cd '${wsl_root}' && export WL_CHAT_PRODUCTION_ENV_FILE='${wsl_root}/production.env' && ./scripts/production/deploy-production.sh --activate-candidate\""
echo "Verified release bundle transferred and production deployment completed."
