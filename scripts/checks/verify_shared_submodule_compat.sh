#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"

gradle_args=()
if [[ "${SKIP_GOOGLE_SERVICES:-false}" == "true" ]]; then
  gradle_args+=("-PskipGoogleServices=true")
fi
if [[ "${ALLOW_INCOMPATIBLE_DEBUG_SIGNING:-false}" == "true" ]]; then
  gradle_args+=("-PallowIncompatibleDebugSigning=true")
fi

bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" \
  --no-configuration-cache \
  "${gradle_args[@]}" \
  verifyEmbeddedSubmodules \
  verifyModuleBoundaries \
  :smscode-core:domain:testDebugUnitTest \
  :smscode-core:verification:detekt \
  :smscode-core:hook:lintDebug \
  :smscode-core:runtime:lintDebug \
  :magisk-ui-kit:compileDebugKotlin \
  :xpbridge:core:compileGithubNoE2eeDebugKotlin \
  :core:testGithubNoE2eeDebugUnitTest \
  :core:compileGithubNoE2eeDebugKotlin \
  :app:lintGithubNoE2eeDebug \
  :app:testGithubNoE2eeDebugUnitTest \
  :app:detekt
