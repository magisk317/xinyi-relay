#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="${ROOT_DIR}/scripts/_toolkit"

# Source the toolkit's modular release guard
source "${TOOLKIT_DIR}/release/check_release_guard.sh"

# xinyi-relay specific configuration
MAX_LEN="${PLAY_WHATSNEW_MAX:-500}"
TAG_NAME="${1:-}"
REQUIRED_LOCALES=(${PLAY_WHATSNEW_REQUIRED_LOCALES:-en-US zh-CN})
FASTLANE_REQUIRED_LOCALES=(${FASTLANE_REQUIRED_LOCALES:-en-US zh-CN})
FASTLANE_MIN_SCREENSHOTS="${FASTLANE_MIN_SCREENSHOTS:-1}"
ALLOW_NON_ASCII_COMMIT_SUBJECT="${ALLOW_NON_ASCII_COMMIT_SUBJECT:-false}"

# Validate numeric inputs
if [[ ! "$MAX_LEN" =~ ^[0-9]+$ ]]; then
  echo "ERROR: PLAY_WHATSNEW_MAX must be an integer, got '$MAX_LEN'." >&2
  exit 2
fi
if [[ ! "$FASTLANE_MIN_SCREENSHOTS" =~ ^[0-9]+$ ]]; then
  echo "ERROR: FASTLANE_MIN_SCREENSHOTS must be an integer, got '$FASTLANE_MIN_SCREENSHOTS'." >&2
  exit 2
fi

# Run the base release guard
echo "Release guard (xinyi-relay)"
check_version_extraction "$ROOT_DIR"
check_commit_subjects "$ROOT_DIR" "$ALLOW_NON_ASCII_COMMIT_SUBJECT"
check_changelog_section "$ROOT_DIR" "$VERSION_NAME"
check_tag_matches_version "$TAG_NAME" "$VERSION_NAME"

# xinyi-relay specific: check whatsnew locales
MAX_WHATSNEW_LEN="$MAX_LEN" check_whatsnew_locales "$ROOT_DIR" "$VERSION_NAME" "${REQUIRED_LOCALES[@]}"

# xinyi-relay specific: check fastlane metadata
FASTLANE_MIN_SCREENSHOTS="$FASTLANE_MIN_SCREENSHOTS" check_fastlane_metadata "$ROOT_DIR" "${FASTLANE_REQUIRED_LOCALES[@]}"

if (( $? != 0 )); then
  echo "Release guard failed." >&2
  exit 1
fi

echo "Release guard passed."
