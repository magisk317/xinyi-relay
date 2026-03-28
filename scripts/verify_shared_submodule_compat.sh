#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SIBLING_REPO="${1:-}"

cd "$ROOT_DIR"

compare_submodule_sha() {
    local path="$1"
    local self_sha sibling_sha
    self_sha="$(git submodule status -- "$path" | awk '{print $1}' | tr -d ' +-')"
    sibling_sha="$(git -C "$SIBLING_REPO" submodule status -- "$path" | awk '{print $1}' | tr -d ' +-')"
    if [[ -z "$self_sha" || -z "$sibling_sha" ]]; then
        echo "Missing submodule SHA for $path" >&2
        exit 1
    fi
    if [[ "$self_sha" != "$sibling_sha" ]]; then
        echo "Submodule SHA mismatch for $path: self=$self_sha sibling=$sibling_sha" >&2
        exit 1
    fi
}

if [[ -n "$SIBLING_REPO" ]]; then
    compare_submodule_sha "build-logic"
    compare_submodule_sha "magisk-ui-kit"
    compare_submodule_sha "smscode-core"
fi

./gradlew \
  :smscode-core:smscode-domain:testDebugUnitTest \
  :magisk-ui-kit:validateDebugScreenshotTest \
  :webui-core:testGithubApi101DebugUnitTest \
  :webui-core:compileGithubApi101DebugKotlin \
  :xpbridge-core:compileGithubApi101DebugKotlin \
  :core:testGithubApi101DebugUnitTest \
  :core:compileGithubApi101DebugKotlin \
  :app:check
