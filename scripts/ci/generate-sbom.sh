#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

./mvnw --batch-mode --no-transfer-progress -DskipTests cyclonedx:makeAggregateBom
test -s target/bom.json
test -s target/bom.xml
echo "CycloneDX SBOMs generated at target/bom.json and target/bom.xml."
