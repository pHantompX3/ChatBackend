#!/usr/bin/env bash
set -euo pipefail

echo "[production-trigger] The legacy production-branch trigger is retired."
echo "[production-trigger] Production candidates are created only from annotated semantic-version tags by .github/workflows/production-release.yml."
echo "[production-trigger] No remote deployment was executed."
