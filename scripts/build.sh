#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MVN="${ROOT}/.tools/apache-maven-3.9.9/bin/mvn"
JAVA_HOME_CANDIDATE="$(find "${ROOT}/.tools" -maxdepth 1 -type d -name 'jdk-21*' | head -n 1)"

if [[ -n "${JAVA_HOME_CANDIDATE}" ]]; then
  export JAVA_HOME="${JAVA_HOME_CANDIDATE}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

echo "Building access-unpack..."
"${MVN}" -DskipTests package "$@"
echo "Build complete."
echo "Artifacts:"
find "${ROOT}"/access-unpack-* -maxdepth 2 -path '*/target/*.jar' | sort
