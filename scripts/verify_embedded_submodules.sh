#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUBMODULE_DIR="$ROOT_DIR/smscode-core"
SUBMODULE_SETTINGS="$SUBMODULE_DIR/settings.gradle.kts"
SUBMODULE_BUILD="$SUBMODULE_DIR/build.gradle.kts"

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

require_pattern "$SUBMODULE_SETTINGS" 'include\(":core"\)' \
  "smscode-core/settings.gradle.kts must keep only the embedded :core include"
forbid_pattern "$SUBMODULE_SETTINGS" 'include\(":app"\)|include\(":runtime"\)|include\(":core",' \
  "smscode-core/settings.gradle.kts must not grow standalone app/runtime includes"

forbid_pattern "$SUBMODULE_BUILD" 'dependencies\s*\{' \
  "smscode-core/build.gradle.kts must stay a minimal embedded-root stub without root dependencies"
forbid_pattern "$SUBMODULE_BUILD" 'subprojects\s*\{|allprojects\s*\{' \
  "smscode-core/build.gradle.kts must not carry standalone root project orchestration"

if [[ "${#violations[@]}" -ne 0 ]]; then
  printf 'Embedded submodule verification failed:\n' >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi

echo "Embedded submodule verification passed."
