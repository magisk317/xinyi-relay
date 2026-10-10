#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"

gradle_args=()
if [[ "${SKIP_GOOGLE_SERVICES:-false}" == "true" ]]; then
  gradle_args+=("-PskipGoogleServices=true")
fi
bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" \
  --no-configuration-cache \
  "${gradle_args[@]}" \
  verifyEmbeddedSubmodules \
  verifyModuleBoundaries \
  verifyStructureBoundaries \
  :app:verifyBundledSmsCodeRules \
  :smscode-core:contract:test \
  :smscode-core:domain:test \
  :smscode-core:verification:test \
  :smscode-core:rule:test \
  :smscode-core:verification:detekt \
  :smscode-core:hook:lintDebug \
  :smscode-core:runtime:lintDebug \
  :magisk-ui-kit:compileAndroidMain :magisk-ui-kit:compileKotlinJvm \
  :magisk-xposed-kit:testDebugUnitTest \
  :magisk-xposed-kit:logging:testDebugUnitTest \
  :magisk-xposed-kit:diagnostics:testDebugUnitTest \
  :xpbridge:core:compileGithubDebugKotlin \
  :hook:entry:testGithubDebugUnitTest \
  :runtime:testGithubDebugUnitTest \
  :relay:sender:testGithubDebugUnitTest \
  :core:testGithubDebugUnitTest \
  :core:koverHtmlReportGithubDebug \
  :core:compileGithubDebugKotlin \
  :app:detekt

bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" \
  --no-configuration-cache \
  "${gradle_args[@]}" \
  :app:testGithubDebugUnitTest

bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" \
  --no-configuration-cache \
  "${gradle_args[@]}" \
  :app:lintReportGithubDebug
