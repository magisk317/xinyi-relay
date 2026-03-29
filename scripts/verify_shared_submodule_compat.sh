#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

bash scripts/ensure_android_sdk_platform_alias.sh

./gradlew \
  :smscode-core:smscode-domain:testDebugUnitTest \
  :magisk-ui-kit:validateDebugScreenshotTest \
  :webui-core:testGithubApi101DebugUnitTest \
  :webui-core:compileGithubApi101DebugKotlin \
  :xpbridge-core:compileGithubApi101DebugKotlin \
  :core:testGithubApi101DebugUnitTest \
  :core:compileGithubApi101DebugKotlin \
  :app:check
