#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_BUILD="$ROOT_DIR/app/build.gradle.kts"
CORE_BUILD="$ROOT_DIR/core/build.gradle.kts"
MOBILE_UI_BUILD="$ROOT_DIR/mobile-ui/build.gradle.kts"
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
forbid_pattern "$APP_BUILD" 'implementation\(project\(":runtime"\)\)' \
  "app must not runtime-package :runtime directly"
forbid_pattern "$APP_BUILD" 'api\(project\(":runtime"\)\)' \
  "app must not expose :runtime directly"
forbid_pattern "$APP_BUILD" 'compileOnly\(project\(":runtime"\)\)' \
  "app must not compile against :runtime directly"
forbid_pattern "$APP_BUILD" 'implementation\(project\(":smscode-core:core"\)\)' \
  "app must not runtime-package :smscode-core:core directly"
forbid_pattern "$APP_BUILD" 'api\(project\(":smscode-core:core"\)\)' \
  "app must not expose :smscode-core:core directly"
require_pattern "$APP_BUILD" 'implementation\(project\(":hook-entry"\)\)' \
  "app must package :hook-entry for libxposed entrypoints"
forbid_pattern "$APP_BUILD" 'implementation\(project\(":smscode-core:smscode-xposed-core"\)\)' \
  "app must not package :smscode-core:smscode-xposed-core directly"

require_pattern "$CORE_BUILD" 'implementation\(project\(":runtime"\)\)' \
  "core must depend on :runtime as implementation"
forbid_pattern "$CORE_BUILD" 'api\(project\(":runtime"\)\)' \
  "core must not expose :runtime transitively"
require_pattern "$CORE_BUILD" 'implementation\(project\(":smscode-core:smscode-xposed-core"\)\)' \
  "core runtime bridge may depend directly on :smscode-core:smscode-xposed-core during the split"

forbid_pattern "$MOBILE_UI_BUILD" 'project\(":xpbridge-core"\)' \
  "mobile-ui must not depend on :xpbridge-core directly"
forbid_pattern "$MOBILE_UI_BUILD" 'project\(":smscode-core:smscode-verification-core"\)' \
  "mobile-ui must not depend on :smscode-core:smscode-verification-core directly"

require_pattern "$RUNTIME_BUILD" 'implementation\(project\(":smscode-core:smscode-domain"\)\)' \
  "runtime must depend on :smscode-core:smscode-domain"
forbid_pattern "$RUNTIME_BUILD" 'project\(":smscode-core:core"\)' \
  "runtime must stop depending on :smscode-core:core"

if [[ "${#violations[@]}" -ne 0 ]]; then
  printf 'Module boundary verification failed:\n' >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi

echo "Module boundary verification passed."
