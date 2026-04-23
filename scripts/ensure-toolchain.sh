#!/usr/bin/env bash

TOOLCHAIN_REQUIRED_JAVA_MAJOR=21
TOOLCHAIN_REQUIRED_MAVEN_MAJOR=3
TOOLCHAIN_REQUIRED_MAVEN_MINOR=9
TOOLCHAIN_MAVEN_VERSION=3.9.9

TOOLCHAIN_TOOLS_DIR=""
TOOLCHAIN_JAVA_HOME=""
TOOLCHAIN_JAVA_CMD=()
TOOLCHAIN_MVN_CMD=()

toolchain__java_bin_for_home() {
  local java_home="$1"

  if [[ -x "${java_home}/bin/java" ]]; then
    echo "${java_home}/bin/java"
    return 0
  fi

  if [[ -f "${java_home}/bin/java.exe" ]]; then
    echo "${java_home}/bin/java.exe"
    return 0
  fi

  return 1
}

toolchain__java_major_from_bin() {
  local java_bin="$1"
  local version_line

  version_line="$("${java_bin}" -version 2>&1 | head -n 1 || true)"
  if [[ "${version_line}" =~ \"([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  echo 0
}

toolchain__maven_version_from_bin() {
  local mvn_bin="$1"
  local version_line

  version_line="$("${mvn_bin}" -v 2>&1 | head -n 1 || true)"
  if [[ "${version_line}" =~ Apache[[:space:]]Maven[[:space:]]([0-9]+\.[0-9]+(\.[0-9]+)?) ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  echo ""
}

toolchain__maven_version_supported() {
  local version="$1"
  local major minor rest

  IFS='.' read -r major minor rest <<<"${version}"
  if [[ ! "${major}" =~ ^[0-9]+$ || ! "${minor}" =~ ^[0-9]+$ ]]; then
    return 1
  fi

  if (( major > TOOLCHAIN_REQUIRED_MAVEN_MAJOR )); then
    return 0
  fi
  if (( major < TOOLCHAIN_REQUIRED_MAVEN_MAJOR )); then
    return 1
  fi

  (( minor >= TOOLCHAIN_REQUIRED_MAVEN_MINOR ))
}

toolchain__detect_os() {
  case "$(uname -s)" in
    Linux*)
      echo "linux"
      ;;
    CYGWIN*|MINGW*|MSYS*)
      echo "windows"
      ;;
    *)
      return 1
      ;;
  esac
}

toolchain__detect_arch() {
  case "$(uname -m)" in
    x86_64|amd64)
      echo "x64"
      ;;
    aarch64|arm64)
      echo "aarch64"
      ;;
    *)
      return 1
      ;;
  esac
}

toolchain__download() {
  local url="$1"
  local output_file="$2"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 3 --retry-delay 2 -o "${output_file}" "${url}"
    return
  fi

  if command -v wget >/dev/null 2>&1; then
    wget -qO "${output_file}" "${url}"
    return
  fi

  echo "Error: neither curl nor wget is available for downloading dependencies." >&2
  return 1
}

toolchain__to_windows_path() {
  local input_path="$1"

  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "${input_path}"
  else
    echo "${input_path}"
  fi
}

toolchain__extract_zip() {
  local archive="$1"
  local destination="$2"

  if command -v unzip >/dev/null 2>&1; then
    unzip -oq "${archive}" -d "${destination}"
    return
  fi

  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command \
      "param([string]\$Archive,[string]\$Destination) Expand-Archive -LiteralPath \$Archive -DestinationPath \$Destination -Force" \
      "$(toolchain__to_windows_path "${archive}")" \
      "$(toolchain__to_windows_path "${destination}")"
    return
  fi

  echo "Error: unable to extract ZIP archive; install unzip or PowerShell." >&2
  return 1
}

toolchain__find_local_java_home() {
  local java_home java_bin java_major

  if [[ ! -d "${TOOLCHAIN_TOOLS_DIR}" ]]; then
    return 1
  fi

  while IFS= read -r java_home; do
    java_bin="$(toolchain__java_bin_for_home "${java_home}" || true)"
    if [[ -n "${java_bin}" ]]; then
      java_major="$(toolchain__java_major_from_bin "${java_bin}")"
      if (( java_major >= TOOLCHAIN_REQUIRED_JAVA_MAJOR )); then
        echo "${java_home}"
        return 0
      fi
    fi
  done < <(find "${TOOLCHAIN_TOOLS_DIR}" -maxdepth 1 -mindepth 1 -type d -name 'jdk-21*' 2>/dev/null | sort -r)

  return 1
}

toolchain__find_local_maven_bin() {
  local maven_dir maven_bin maven_version

  if [[ ! -d "${TOOLCHAIN_TOOLS_DIR}" ]]; then
    return 1
  fi

  while IFS= read -r maven_dir; do
    for maven_bin in "${maven_dir}/bin/mvn" "${maven_dir}/bin/mvn.cmd"; do
      if [[ -f "${maven_bin}" ]]; then
        maven_version="$(toolchain__maven_version_from_bin "${maven_bin}")"
        if toolchain__maven_version_supported "${maven_version}"; then
          echo "${maven_bin}"
          return 0
        fi
      fi
    done
  done < <(find "${TOOLCHAIN_TOOLS_DIR}" -maxdepth 1 -mindepth 1 -type d -name 'apache-maven-*' 2>/dev/null | sort -r)

  return 1
}

toolchain__install_java() {
  local os_name arch_name archive_ext archive_url downloads_dir archive_path stage_dir extracted_dir target_dir

  os_name="$(toolchain__detect_os)" || {
    echo "Error: unsupported OS for automatic Java installation. Supported OS: Linux and Windows." >&2
    return 1
  }

  arch_name="$(toolchain__detect_arch)" || {
    echo "Error: unsupported CPU architecture for automatic Java installation." >&2
    return 1
  }

  case "${os_name}" in
    windows)
      archive_ext="zip"
      ;;
    linux)
      archive_ext="tar.gz"
      ;;
    *)
      echo "Error: unsupported OS for automatic Java installation." >&2
      return 1
      ;;
  esac

  archive_url="https://api.adoptium.net/v3/binary/latest/${TOOLCHAIN_REQUIRED_JAVA_MAJOR}/ga/${os_name}/${arch_name}/jdk/hotspot/normal/eclipse?project=jdk"
  downloads_dir="${TOOLCHAIN_TOOLS_DIR}/.downloads"
  archive_path="${downloads_dir}/temurin-jdk${TOOLCHAIN_REQUIRED_JAVA_MAJOR}-${os_name}-${arch_name}.${archive_ext}"

  mkdir -p "${downloads_dir}"
  echo "Java 21+ not found. Downloading Temurin JDK ${TOOLCHAIN_REQUIRED_JAVA_MAJOR}..."
  toolchain__download "${archive_url}" "${archive_path}"

  stage_dir="${downloads_dir}/extract-java-${RANDOM}${RANDOM}"
  rm -rf "${stage_dir}"
  mkdir -p "${stage_dir}"

  if [[ "${archive_ext}" == "zip" ]]; then
    toolchain__extract_zip "${archive_path}" "${stage_dir}"
  else
    tar -xzf "${archive_path}" -C "${stage_dir}"
  fi

  extracted_dir="$(find "${stage_dir}" -maxdepth 1 -mindepth 1 -type d -name 'jdk-21*' 2>/dev/null | head -n 1 || true)"
  if [[ -z "${extracted_dir}" ]]; then
    echo "Error: downloaded Java archive did not contain a jdk-21* directory." >&2
    rm -rf "${stage_dir}"
    rm -f "${archive_path}"
    return 1
  fi

  target_dir="${TOOLCHAIN_TOOLS_DIR}/$(basename "${extracted_dir}")"
  if [[ -e "${target_dir}" ]]; then
    target_dir="${target_dir}-local-$(date +%s)"
  fi

  mv "${extracted_dir}" "${target_dir}"
  rm -rf "${stage_dir}"
  rm -f "${archive_path}"
}

toolchain__install_maven() {
  local os_name archive_ext archive_name archive_url downloads_dir archive_path stage_dir extracted_dir target_dir

  os_name="$(toolchain__detect_os)" || {
    echo "Error: unsupported OS for automatic Maven installation. Supported OS: Linux and Windows." >&2
    return 1
  }

  case "${os_name}" in
    windows)
      archive_ext="zip"
      ;;
    linux)
      archive_ext="tar.gz"
      ;;
    *)
      echo "Error: unsupported OS for automatic Maven installation." >&2
      return 1
      ;;
  esac

  archive_name="apache-maven-${TOOLCHAIN_MAVEN_VERSION}-bin.${archive_ext}"
  archive_url="https://archive.apache.org/dist/maven/maven-3/${TOOLCHAIN_MAVEN_VERSION}/binaries/${archive_name}"
  downloads_dir="${TOOLCHAIN_TOOLS_DIR}/.downloads"
  archive_path="${downloads_dir}/${archive_name}"

  mkdir -p "${downloads_dir}"
  echo "Maven 3.9+ not found. Downloading Apache Maven ${TOOLCHAIN_MAVEN_VERSION}..."
  toolchain__download "${archive_url}" "${archive_path}"

  stage_dir="${downloads_dir}/extract-maven-${RANDOM}${RANDOM}"
  rm -rf "${stage_dir}"
  mkdir -p "${stage_dir}"

  if [[ "${archive_ext}" == "zip" ]]; then
    toolchain__extract_zip "${archive_path}" "${stage_dir}"
  else
    tar -xzf "${archive_path}" -C "${stage_dir}"
  fi

  extracted_dir="$(find "${stage_dir}" -maxdepth 1 -mindepth 1 -type d -name 'apache-maven-*' 2>/dev/null | head -n 1 || true)"
  if [[ -z "${extracted_dir}" ]]; then
    echo "Error: downloaded Maven archive did not contain an apache-maven-* directory." >&2
    rm -rf "${stage_dir}"
    rm -f "${archive_path}"
    return 1
  fi

  target_dir="${TOOLCHAIN_TOOLS_DIR}/$(basename "${extracted_dir}")"
  if [[ -e "${target_dir}" ]]; then
    target_dir="${target_dir}-local-$(date +%s)"
  fi

  mv "${extracted_dir}" "${target_dir}"
  rm -rf "${stage_dir}"
  rm -f "${archive_path}"
}

ensure_toolchain() {
  local root="$1"
  local system_java_bin="" system_java_major=0
  local local_java_home=""
  local system_maven_bin="" system_maven_version=""
  local local_maven_bin=""

  TOOLCHAIN_TOOLS_DIR="${root}/tools"
  TOOLCHAIN_JAVA_HOME=""
  TOOLCHAIN_JAVA_CMD=()
  TOOLCHAIN_MVN_CMD=()

  mkdir -p "${TOOLCHAIN_TOOLS_DIR}"

  if command -v java >/dev/null 2>&1; then
    system_java_bin="$(command -v java)"
    system_java_major="$(toolchain__java_major_from_bin "${system_java_bin}")"
    if (( system_java_major >= TOOLCHAIN_REQUIRED_JAVA_MAJOR )); then
      TOOLCHAIN_JAVA_CMD=("${system_java_bin}")
    fi
  fi

  if [[ ${#TOOLCHAIN_JAVA_CMD[@]} -eq 0 ]]; then
    local_java_home="$(toolchain__find_local_java_home || true)"
    if [[ -z "${local_java_home}" ]]; then
      toolchain__install_java
      local_java_home="$(toolchain__find_local_java_home || true)"
    fi

    if [[ -z "${local_java_home}" ]]; then
      if [[ -n "${system_java_bin}" ]]; then
        echo "Error: Java 21+ required. Found Java ${system_java_major} at ${system_java_bin} but could not install a local JDK to ${TOOLCHAIN_TOOLS_DIR}." >&2
      else
        echo "Error: Java 21+ is required and local installation to ${TOOLCHAIN_TOOLS_DIR} failed." >&2
      fi
      return 1
    fi

    TOOLCHAIN_JAVA_HOME="${local_java_home}"
    export JAVA_HOME="${TOOLCHAIN_JAVA_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    TOOLCHAIN_JAVA_CMD=("$(toolchain__java_bin_for_home "${JAVA_HOME}")")
  fi

  if command -v mvn >/dev/null 2>&1; then
    system_maven_bin="$(command -v mvn)"
    system_maven_version="$(toolchain__maven_version_from_bin "${system_maven_bin}")"
    if toolchain__maven_version_supported "${system_maven_version}"; then
      TOOLCHAIN_MVN_CMD=("${system_maven_bin}")
    fi
  fi

  if [[ ${#TOOLCHAIN_MVN_CMD[@]} -eq 0 ]] && command -v mvn.cmd >/dev/null 2>&1; then
    system_maven_bin="$(command -v mvn.cmd)"
    system_maven_version="$(toolchain__maven_version_from_bin "${system_maven_bin}")"
    if toolchain__maven_version_supported "${system_maven_version}"; then
      TOOLCHAIN_MVN_CMD=("${system_maven_bin}")
    fi
  fi

  if [[ ${#TOOLCHAIN_MVN_CMD[@]} -eq 0 ]]; then
    local_maven_bin="$(toolchain__find_local_maven_bin || true)"
    if [[ -z "${local_maven_bin}" ]]; then
      toolchain__install_maven
      local_maven_bin="$(toolchain__find_local_maven_bin || true)"
    fi

    if [[ -z "${local_maven_bin}" ]]; then
      echo "Error: Maven 3.9+ is required and local installation to ${TOOLCHAIN_TOOLS_DIR} failed." >&2
      return 1
    fi

    TOOLCHAIN_MVN_CMD=("${local_maven_bin}")
  fi
}
