#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"

# Source the toolkit's modular release guard
source "${TOOLKIT_DIR}/release/check_release_guard.sh"

# xinyi-relay specific configuration
MAX_LEN="${PLAY_WHATSNEW_MAX:-500}"
if [[ -n "${1:-}" ]] && [[ -d "$1" ]]; then
  TAG_NAME="${2:-}"
else
  TAG_NAME="${1:-}"
fi
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

python3 - "$ROOT_DIR/app/src/main/AndroidManifest.xml" "$ROOT_DIR/app/src/play/AndroidManifest.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

android_ns = "{http://schemas.android.com/apk/res/android}"
tools_ns = "{http://schemas.android.com/tools}"
main_manifest, play_manifest = map(ET.parse, sys.argv[1:])

main_permissions = {
    element.get(android_ns + "name")
    for element in main_manifest.getroot().findall("uses-permission")
}
if {
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
} - main_permissions:
    raise SystemExit("FAIL: main manifest lost the remote-messaging foreground-service permissions")

main_services = [
    element for element in main_manifest.getroot().findall("application/service")
    if element.get(android_ns + "name") == "io.github.magisk317.relay.service.StandardModeService"
]
if len(main_services) != 1 or main_services[0].get(android_ns + "foregroundServiceType") != "remoteMessaging":
    raise SystemExit("FAIL: main manifest must declare StandardModeService as remoteMessaging")

play_root = play_manifest.getroot()
play_permissions = [
    element for element in play_root.findall("uses-permission")
    if element.get(android_ns + "name") in {
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
    }
]
if len(play_permissions) != 2 or any(
    element.get(tools_ns + "node") != "remove" for element in play_permissions
):
    raise SystemExit("FAIL: Play manifest must explicitly remove both foreground-service permissions")

play_services = [
    element for element in play_root.findall("application/service")
    if element.get(android_ns + "name") == "io.github.magisk317.relay.service.StandardModeService"
]
if len(play_services) != 1 or play_services[0].get(tools_ns + "node") != "remove":
    raise SystemExit("FAIL: Play manifest must explicitly remove StandardModeService")
PY
echo "PASS: Play foreground-service manifest contract"

# xinyi-relay specific: check whatsnew locales
whatsnew_dir="${ROOT_DIR}/distribution/whatsnew"
fail=0
for locale in "${REQUIRED_LOCALES[@]}"; do
  file="$whatsnew_dir/whatsnew-$locale"
  if [[ ! -f "$file" ]]; then
    echo "FAIL: required whatsnew locale missing: $locale ($file)"
    fail=1
    continue
  fi
  count="$(wc -m < "$file" | tr -d '[:space:]')"
  if (( count == 0 )); then
    echo "FAIL: $locale is empty ($file)"
    fail=1
  elif (( count > MAX_LEN )); then
    echo "FAIL: $locale length=$count exceeds max=$MAX_LEN ($file)"
    fail=1
  else
    echo "PASS: $locale length=$count/$MAX_LEN"
  fi
done
if (( fail != 0 )); then
  echo "Release guard failed." >&2
  exit 1
fi

# xinyi-relay specific: check fastlane metadata
FASTLANE_MIN_SCREENSHOTS="$FASTLANE_MIN_SCREENSHOTS" check_fastlane_metadata "$ROOT_DIR" "${FASTLANE_REQUIRED_LOCALES[@]}"

if (( $? != 0 )); then
  echo "Release guard failed." >&2
  exit 1
fi

echo "Release guard passed."
