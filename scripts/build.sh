#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT}/scripts/ensure-toolchain.sh"
ensure_toolchain "${ROOT}"

echo "Building access-unpack..."
"${TOOLCHAIN_MVN_CMD[@]}" -DskipTests package "$@"
echo "Build complete."
echo "Artifacts:"
find "${ROOT}"/access-unpack-* -maxdepth 2 -path '*/target/*.jar' | sort
