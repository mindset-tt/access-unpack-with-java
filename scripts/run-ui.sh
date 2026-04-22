#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MVN="${ROOT}/.tools/apache-maven-3.9.9/bin/mvn"
JAVA_HOME_CANDIDATE="$(find "${ROOT}/.tools" -maxdepth 1 -type d -name 'jdk-21*' | head -n 1)"

if [[ -n "${JAVA_HOME_CANDIDATE}" ]]; then
  export JAVA_HOME="${JAVA_HOME_CANDIDATE}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

echo "Launching access-unpack UI..."
"${MVN}" -q -f "${ROOT}/pom.xml" -pl access-unpack-ui -am -DskipTests package >/dev/null
CP_FILE="$(mktemp)"
trap 'rm -f "${CP_FILE}"' EXIT
"${MVN}" -q -f "${ROOT}/access-unpack-ui/pom.xml" dependency:build-classpath \
  -Dmdep.includeScope=runtime \
  -Dmdep.outputFile="${CP_FILE}" >/dev/null
CLASSPATH="${ROOT}/access-unpack-ui/target/classes:${ROOT}/access-unpack-core/target/classes:$(cat "${CP_FILE}")"
exec java -cp "${CLASSPATH}" com.access.unpack.ui.AccessUnpackUi
