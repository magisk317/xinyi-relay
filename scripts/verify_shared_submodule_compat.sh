#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

gradle_args=()
if [[ "${SKIP_GOOGLE_SERVICES:-false}" == "true" ]]; then
  gradle_args+=("-PskipGoogleServices=true")
fi
if [[ "${ALLOW_INCOMPATIBLE_DEBUG_SIGNING:-false}" == "true" ]]; then
  gradle_args+=("-PallowIncompatibleDebugSigning=true")
fi

bash scripts/with_workspace_gradle_lock.sh \
  "${gradle_args[@]}" \
  :smscode-core:smscode-domain:testDebugUnitTest \
  :magisk-ui-kit:compileDebugScreenshotTestKotlin \
  :xpbridge-core:compileGithubDebugKotlin \
  :core:testGithubDebugUnitTest \
  :core:compileGithubDebugKotlin \
  :app:check
