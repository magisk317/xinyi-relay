#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

gradle_args=()
if [[ "${SKIP_GOOGLE_SERVICES:-false}" == "true" ]]; then
  gradle_args+=("-PskipGoogleServices=true")
fi

bash scripts/with_workspace_gradle_lock.sh \
  "${gradle_args[@]}" \
  :smscode-core:smscode-domain:testDebugUnitTest \
  :magisk-ui-kit:compileDebugScreenshotTestKotlin \
  :xpbridge-core:compileGithubApi101DebugKotlin \
  :core:testGithubApi101DebugUnitTest \
  :core:compileGithubApi101DebugKotlin \
  :app:check
