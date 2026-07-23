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
  verifyStructureBoundaries \
  :app:verifyBundledSmsCodeRules \
  :smscode-core:domain:testDebugUnitTest \
  :smscode-core:verification:testDebugUnitTest \
  :smscode-core:verification:detekt \
  :smscode-core:hook:lintDebug \
  :smscode-core:runtime:lintDebug \
  :magisk-ui-kit:compileDebugKotlin \
  :magisk-xposed-kit:testDebugUnitTest \
  :magisk-xposed-kit:logging:testDebugUnitTest \
  :magisk-xposed-kit:diagnostics:testDebugUnitTest \
  :xpbridge:core:compileGithubNoE2eeDebugKotlin \
  :hook:entry:testGithubNoE2eeDebugUnitTest \
  :runtime:testGithubNoE2eeDebugUnitTest \
  :relay:sender:testGithubNoE2eeDebugUnitTest \
  :core:testGithubNoE2eeDebugUnitTest \
  :core:koverHtmlReportGithubNoE2eeDebug \
  :core:compileGithubNoE2eeDebugKotlin \
  :app:detekt

bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" \
  --no-configuration-cache \
  "${gradle_args[@]}" \
  :app:testGithubNoE2eeDebugUnitTest

bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" \
  --no-configuration-cache \
  "${gradle_args[@]}" \
  :app:lintReportGithubNoE2eeDebug
