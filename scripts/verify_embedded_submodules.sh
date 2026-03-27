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

require_pattern "$SUBMODULE_SETTINGS" 'include\(":smscode-xposed-core"\)' \
  "smscode-core/settings.gradle.kts must include :smscode-xposed-core"
require_pattern "$SUBMODULE_SETTINGS" 'include\(":smscode-domain"\)' \
  "smscode-core/settings.gradle.kts must include :smscode-domain"
require_pattern "$SUBMODULE_SETTINGS" 'include\(":smscode-verification-core"\)' \
  "smscode-core/settings.gradle.kts must include :smscode-verification-core"
forbid_pattern "$SUBMODULE_SETTINGS" 'include\(":core"\)' \
  "smscode-core/settings.gradle.kts must not include the removed legacy :core module"
forbid_pattern "$SUBMODULE_SETTINGS" 'include\(":app"\)|include\(":runtime"\)' \
  "smscode-core/settings.gradle.kts must stay a shared-library workspace without standalone app/runtime modules"

forbid_pattern "$SUBMODULE_BUILD" 'dependencies\s*\{' \
  "smscode-core/build.gradle.kts must stay a minimal embedded-root stub without root dependencies"
forbid_pattern "$SUBMODULE_BUILD" 'subprojects\s*\{|allprojects\s*\{' \
  "smscode-core/build.gradle.kts must not carry standalone root project orchestration"

if [[ -d "$SUBMODULE_DIR/core" ]]; then
  violations+=("smscode-core/core must be removed after the xposed-core split")
fi

if [[ "${#violations[@]}" -ne 0 ]]; then
  printf 'Embedded submodule verification failed:\n' >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi

echo "Embedded submodule verification passed."
