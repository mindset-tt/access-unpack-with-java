#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT}/scripts/ensure-toolchain.sh"
ensure_toolchain "${ROOT}"

echo "Launching access-unpack UI..."
CP_FILE="$(mktemp)"
trap 'rm -f "${CP_FILE}"' EXIT
"${TOOLCHAIN_MVN_CMD[@]}" -q -f "${ROOT}/pom.xml" -pl access-unpack-ui -am -DskipTests package dependency:build-classpath \
  -Dmdep.includeScope=runtime \
  -Dmdep.outputFile="${CP_FILE}" >/dev/null

CP_SEP=':'
case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*)
    CP_SEP=';'
    ;;
esac

to_java_cp_path() {
  local candidate="$1"
  if [[ "${CP_SEP}" == ";" ]] && command -v cygpath >/dev/null 2>&1; then
    cygpath -w "${candidate}"
  else
    echo "${candidate}"
  fi
}

UI_CLASSES="$(to_java_cp_path "${ROOT}/access-unpack-ui/target/classes")"
CORE_CLASSES="$(to_java_cp_path "${ROOT}/access-unpack-core/target/classes")"
CLASSPATH="${UI_CLASSES}${CP_SEP}${CORE_CLASSES}${CP_SEP}$(cat "${CP_FILE}")"
exec "${TOOLCHAIN_JAVA_CMD[@]}" -cp "${CLASSPATH}" com.access.unpack.ui.AccessUnpackUi
