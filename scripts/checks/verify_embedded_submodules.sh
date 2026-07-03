#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/utils/regex_helpers.sh"
SUBMODULE_DIR="$ROOT_DIR/smscode/core"
SUBMODULE_SETTINGS="$SUBMODULE_DIR/settings.gradle.kts"
SUBMODULE_BUILD="$SUBMODULE_DIR/build.gradle.kts"
RULES_DIR="$ROOT_DIR/smscode/rules"

violations=()

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! regex_quiet "$pattern" "$file"; then
    violations+=("$message")
  fi
}

forbid_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if regex_quiet "$pattern" "$file"; then
    violations+=("$message")
  fi
}

require_pattern "$SUBMODULE_SETTINGS" 'include\(":magisk-xposed-kit"\)' \
  "smscode-core/settings.gradle.kts must include :magisk-xposed-kit"
require_pattern "$SUBMODULE_SETTINGS" 'include\(":hook"\)' \
  "smscode-core/settings.gradle.kts must include :hook"
require_pattern "$SUBMODULE_SETTINGS" 'include\(":rule"\)' \
  "smscode-core/settings.gradle.kts must include :rule"
require_pattern "$SUBMODULE_SETTINGS" 'include\(":domain"\)' \
  "smscode-core/settings.gradle.kts must include :domain"
require_pattern "$SUBMODULE_SETTINGS" 'include\(":contract"\)' \
  "smscode-core/settings.gradle.kts must include :contract"
require_pattern "$SUBMODULE_SETTINGS" 'include\(":runtime"\)' \
  "smscode-core/settings.gradle.kts must include :runtime"
require_pattern "$SUBMODULE_SETTINGS" 'include\(":verification"\)' \
  "smscode-core/settings.gradle.kts must include :verification"
forbid_pattern "$SUBMODULE_SETTINGS" 'include\(":core"\)' \
  "smscode-core/settings.gradle.kts must not include the removed legacy :core module"
forbid_pattern "$SUBMODULE_SETTINGS" 'include\(":app"\)' \
  "smscode-core/settings.gradle.kts must stay a shared-library workspace without a standalone app module"

forbid_pattern "$SUBMODULE_BUILD" 'dependencies\s*\{' \
  "smscode-core/build.gradle.kts must stay a minimal embedded-root stub without root dependencies"
forbid_pattern "$SUBMODULE_BUILD" 'subprojects\s*\{|allprojects\s*\{' \
  "smscode-core/build.gradle.kts must not carry standalone root project orchestration"

if [[ -d "$SUBMODULE_DIR/core" ]]; then
  violations+=("smscode-core/core must be removed after the xposed split")
fi

if [[ ! -f "$RULES_DIR/_meta/rules-index.json" ]]; then
  violations+=("smscode-rules must provide _meta/rules-index.json")
fi
if [[ ! -d "$RULES_DIR/rules" ]]; then
  violations+=("smscode-rules must provide rules/")
fi
if [[ -f "$RULES_DIR/settings.gradle.kts" || -f "$RULES_DIR/build.gradle.kts" ]]; then
  violations+=("smscode-rules must stay a content-only submodule, not a Gradle module")
fi

if [[ "${#violations[@]}" -ne 0 ]]; then
  printf 'Embedded submodule verification failed:\n' >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi

echo "Embedded submodule verification passed."
