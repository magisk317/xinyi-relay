#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK_DIR="${ROOT_DIR}/.gradle"
LOCK_FILE="${LOCK_DIR}/workspace-build.lock"
IGNORE_SUBMODULE_LOCKFILES=false
SUBMODULES="${GRADLE_LOCKLESS_SUBMODULES:-${PARENT_GRADLE_LOCKLESS_SUBMODULES:-magisk-ui-kit smscode-core}}"

declare -a GRADLE_ARGS=()
declare -a RESTORE_PAIRS=()
declare -A ORIGINAL_LOCKFILES=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --ignore-submodule-lockfiles)
      IGNORE_SUBMODULE_LOCKFILES=true
      shift
      ;;
    --)
      shift
      GRADLE_ARGS+=("$@")
      break
      ;;
    *)
      GRADLE_ARGS+=("$1")
      shift
      ;;
  esac
done

if [ "${#GRADLE_ARGS[@]}" -eq 0 ]; then
  echo "Usage: $0 [--ignore-submodule-lockfiles] <gradle-args...>" >&2
  exit 64
fi

mkdir -p "${LOCK_DIR}"

restore_lockfiles() {
  local pair lockfile backup submodule
  for pair in "${RESTORE_PAIRS[@]}"; do
    IFS=$'\t' read -r lockfile backup <<< "${pair}"
    if [ -e "${backup}" ]; then
      rm -f "${lockfile}"
      mv "${backup}" "${lockfile}"
    fi
  done

  for submodule in ${SUBMODULES}; do
    [ -d "${ROOT_DIR}/${submodule}" ] || continue

    while IFS= read -r -d '' lockfile; do
      if [[ -z "${ORIGINAL_LOCKFILES["${lockfile}"]:-}" ]]; then
        rm -f "${lockfile}"
      fi
    done < <(
      find "${ROOT_DIR}/${submodule}" \
        \( -name 'gradle.lockfile' -o -name 'settings-gradle.lockfile' \) \
        -type f \
        -print0
    )
  done
}

hide_lockfiles() {
  local submodule lockfile backup
  for submodule in ${SUBMODULES}; do
    [ -d "${ROOT_DIR}/${submodule}" ] || continue

    while IFS= read -r -d '' lockfile; do
      backup="$(mktemp "${lockfile}.parent-build.XXXXXX")"
      ORIGINAL_LOCKFILES["${lockfile}"]=1
      mv "${lockfile}" "${backup}"
      RESTORE_PAIRS+=("${lockfile}"$'\t'"${backup}")
    done < <(
      find "${ROOT_DIR}/${submodule}" \
        \( -name 'gradle.lockfile' -o -name 'settings-gradle.lockfile' \) \
        -type f \
        -print0
    )
  done
}

if command -v flock >/dev/null 2>&1; then
  exec 9>"${LOCK_FILE}"
  if ! flock -n 9; then
    echo "Another Gradle build is already running in this workspace; waiting for the workspace build lock..." >&2
    flock 9
  fi
else
  echo "flock is not available; running Gradle without a workspace lock." >&2
fi

cd "${ROOT_DIR}"

if ${IGNORE_SUBMODULE_LOCKFILES}; then
  trap restore_lockfiles EXIT
  hide_lockfiles
  bash "${ROOT_DIR}/gradlew" "${GRADLE_ARGS[@]}"
else
  exec bash "${ROOT_DIR}/gradlew" "${GRADLE_ARGS[@]}"
fi
