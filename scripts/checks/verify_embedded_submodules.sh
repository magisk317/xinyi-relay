#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/utils/regex_helpers.sh"
SUBMODULE_DIR="$ROOT_DIR/smscode/core"
SUBMODULE_SETTINGS="$SUBMODULE_DIR/settings.gradle.kts"
SUBMODULE_BUILD="$SUBMODULE_DIR/build.gradle.kts"
PARENT_SETTINGS="$ROOT_DIR/settings.gradle.kts"
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

# smscode-core is consumed the same way magisk-xposed-kit is: the parent build declares
# each module and remaps its projectDir. It must not carry a settings file of its own,
# otherwise it regrows into a parallel root build with its own plugin and AGP versions.
if [[ -f "$SUBMODULE_SETTINGS" ]]; then
  violations+=("smscode-core/settings.gradle.kts must stay absent; the parent build owns module wiring")
fi
for module in hook rule domain contract runtime verification; do
  require_pattern "$PARENT_SETTINGS" "\":smscode-core:$module\"" \
    "settings.gradle.kts must include :smscode-core:$module"
  require_pattern "$PARENT_SETTINGS" "project\(\":smscode-core:$module\"\)\.projectDir" \
    "settings.gradle.kts must remap :smscode-core:$module to smscode/core/$module"
done
require_pattern "$PARENT_SETTINGS" 'project\(":smscode-core"\)\.projectDir' \
  "settings.gradle.kts must remap the :smscode-core root to smscode/core"
require_pattern "$PARENT_SETTINGS" 'project\(":magisk-xposed-kit:logging"\)\.projectDir' \
  "settings.gradle.kts must remap :magisk-xposed-kit:logging"
forbid_pattern "$PARENT_SETTINGS" 'include\(":smscode-core:core"\)|include\(":smscode-core:app"\)' \
  "settings.gradle.kts must not resurrect the removed :core or add an :app module under smscode-core"

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
