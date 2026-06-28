#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="${ROOT_DIR}/scripts/_toolkit"

# Source the toolkit's modular release tag
export MAGISK_RELEASE_GUARD_SCRIPT="${SCRIPT_DIR}/check_release_guard.sh"
source "${TOOLKIT_DIR}/release/release_tag.sh"

# xinyi-relay specific configuration
FASTLANE_META_DIR="$ROOT_DIR/fastlane/metadata/android"
RELEASE_REF_SCRIPT="$ROOT_DIR/scripts/release/release_ref.sh"

# xinyi-relay specific: run WebUI checks
run_pre_push_checks() {
  run_common_gradle_checks "$ROOT_DIR" \
    :app:testGithubWithE2eeDebugUnitTest \
    :app:assembleGithubWithE2eeDebug
  run_detekt_sarif_check "$ROOT_DIR"
  run_webui_checks "$ROOT_DIR"
  run_fastlane_sync "$ROOT_DIR"
}

# Run the release tag with xinyi-relay configuration
release_tag "$ROOT_DIR"
