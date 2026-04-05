#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK_DIR="${ROOT_DIR}/.gradle"
LOCK_FILE="${LOCK_DIR}/workspace-build.lock"

mkdir -p "${LOCK_DIR}"

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
exec bash "${ROOT_DIR}/gradlew" "$@"
