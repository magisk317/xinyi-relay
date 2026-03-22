#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_BUILD="$ROOT_DIR/app/build.gradle.kts"
CORE_BUILD="$ROOT_DIR/core/build.gradle.kts"
RUNTIME_BUILD="$ROOT_DIR/runtime/build.gradle.kts"

violations=()

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! rg -q "$pattern" "$file"; then
    violations+=("$message")
  fi
}

forbid_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if rg -q "$pattern" "$file"; then
    violations+=("$message")
  fi
}

require_pattern "$APP_BUILD" 'implementation\(project\(":core"\)\)' \
  "app must depend directly on :core"
forbid_pattern "$APP_BUILD" 'project\(":runtime"\)' \
  "app must not depend directly on :runtime"
forbid_pattern "$APP_BUILD" 'project\(":smscode-core:core"\)' \
  "app must not depend directly on :smscode-core:core"

require_pattern "$CORE_BUILD" 'api\(project\(":runtime"\)\)' \
  "core must expose :runtime transitively"
forbid_pattern "$CORE_BUILD" 'project\(":smscode-core:core"\)' \
  "core must not depend directly on :smscode-core:core"

require_pattern "$RUNTIME_BUILD" 'api\(project\(":smscode-core:core"\)\)' \
  "runtime must expose :smscode-core:core transitively"

if [[ "${#violations[@]}" -ne 0 ]]; then
  printf 'Module boundary verification failed:\n' >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi

echo "Module boundary verification passed."
