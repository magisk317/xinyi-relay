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

# Verify desktop version aligns with VERSION_NAME (防呆门禁)
check_desktop_version() {
  local root_dir="$1"
  local expected_version="$2"

  local desktop_build="${root_dir}/desktop/build.gradle.kts"
  if [[ ! -f "$desktop_build" ]]; then
    echo "FAIL: desktop build file missing: $desktop_build" >&2
    return 1
  fi

  if ! grep -q 'libs\.versions\.versionName\.get()' "$desktop_build" || \
     ! grep -q 'packageVersion = desktopVersion' "$desktop_build"; then
    echo "FAIL: desktop build script must wire packageVersion to libs.versions.versionName" >&2
    return 1
  fi

  echo "PASS: desktop package version derived from libs.versions.versionName ($expected_version)"
}

# Verify backend version aligns with VERSION_NAME (防呆门禁)
check_backend_version() {
  local root_dir="$1"
  local expected_version="$2"
  local fail=0

  # 1. OpenAPI Go contract definition
  local go_contract="${root_dir}/backend/api/internal/http/openapi_contract.go"
  if [[ ! -f "$go_contract" ]]; then
    echo "FAIL: backend OpenAPI contract missing: $go_contract" >&2
    fail=1
  else
    local go_ver
    go_ver="$(sed -nE 's/.*"version":[[:space:]]*"([^"]+)".*/\1/p' "$go_contract" | head -n1)"
    if [[ "$go_ver" != "$expected_version" ]]; then
      echo "FAIL: backend openapi_contract.go version mismatch: got '$go_ver', expected '$expected_version'" >&2
      fail=1
    else
      echo "PASS: backend openapi_contract.go version matches ($go_ver)"
    fi
  fi

  # 2. Shared OpenAPI contract JSON
  local json_contract="${root_dir}/frontend/shared/contracts/openapi.json"
  if [[ ! -f "$json_contract" ]]; then
    echo "FAIL: shared openapi.json contract missing: $json_contract" >&2
    fail=1
  else
    local json_ver
    json_ver="$(python3 -c "import json, sys; print(json.load(open(sys.argv[1]))['info']['version'])" "$json_contract" 2>/dev/null || true)"
    if [[ "$json_ver" != "$expected_version" ]]; then
      echo "FAIL: shared openapi.json version mismatch: got '$json_ver', expected '$expected_version'" >&2
      fail=1
    else
      echo "PASS: shared openapi.json version matches ($json_ver)"
    fi
  fi

  # 3. WebUI package.json
  local webui_pkg="${root_dir}/frontend/webui/package.json"
  if [[ ! -f "$webui_pkg" ]]; then
    echo "FAIL: webui package.json missing: $webui_pkg" >&2
    fail=1
  else
    local pkg_ver
    pkg_ver="$(python3 -c "import json, sys; print(json.load(open(sys.argv[1]))['version'])" "$webui_pkg" 2>/dev/null || true)"
    if [[ "$pkg_ver" != "$expected_version" ]]; then
      echo "FAIL: webui package.json version mismatch: got '$pkg_ver', expected '$expected_version'" >&2
      fail=1
    else
      echo "PASS: webui package.json version matches ($pkg_ver)"
    fi
  fi

  if (( fail != 0 )); then
    return 1
  fi
}

# Run the base release guard
echo "Release guard (xinyi-relay)"
check_version_extraction "$ROOT_DIR"
check_commit_subjects "$ROOT_DIR" "$ALLOW_NON_ASCII_COMMIT_SUBJECT"
check_changelog_section "$ROOT_DIR" "$VERSION_NAME"
check_tag_matches_version "$TAG_NAME" "$VERSION_NAME"
check_desktop_version "$ROOT_DIR" "$VERSION_NAME"
check_backend_version "$ROOT_DIR" "$VERSION_NAME"

python3 - "$ROOT_DIR/app/src/main/AndroidManifest.xml" "$ROOT_DIR/app/src/play/AndroidManifest.xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

android_ns = "{http://schemas.android.com/apk/res/android}"
tools_ns = "{http://schemas.android.com/tools}"
main_manifest, play_manifest = map(ET.parse, sys.argv[1:])


def foreground_service_types(service):
    """Returns the normalized values of a service's foregroundServiceType.

    android:foregroundServiceType is a multi-value attribute whose tokens are
    pipe-separated (commas are tolerated to stay aligned with the contract test).
    """
    raw = service.get(android_ns + "foregroundServiceType") or ""
    return {token.strip() for token in re.split(r"[|,]", raw)}


main_permissions = {
    element.get(android_ns + "name")
    for element in main_manifest.getroot().findall("uses-permission")
}
if {"android.permission.FOREGROUND_SERVICE"} - main_permissions:
    raise SystemExit("FAIL: main manifest lost the base foreground-service permission")

# 4452221a dropped android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING from the
# main manifest: once the standard (non-Xposed) relay channel was retired and
# StandardModeService deleted by 6fa158b9, the permission no longer had a service
# to grant. Instead of pinning the permission unconditionally, this guard keeps the
# same contract as RelayManifestContractTest: the permission must track the
# declared service types, so declaring the permission without a remoteMessaging
# foreground service is a failure too.
remote_messaging_permission = "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"
if remote_messaging_permission in main_permissions:
    has_remote_messaging_service = any(
        "remoteMessaging" in foreground_service_types(service)
        for service in main_manifest.getroot().findall("application/service")
    )
    if not has_remote_messaging_service:
        raise SystemExit(
            "FAIL: main manifest declares FOREGROUND_SERVICE_REMOTE_MESSAGING "
            "without any service whose android:foregroundServiceType includes "
            "remoteMessaging"
        )

# Keep negative checks so the retired service cannot silently reappear.
main_services = [
    element for element in main_manifest.getroot().findall("application/service")
    if element.get(android_ns + "name") == "io.github.magisk317.relay.service.StandardModeService"
]
if main_services:
    raise SystemExit("FAIL: retired StandardModeService must not be declared in the main manifest")

play_root = play_manifest.getroot()
play_services = [
    element for element in play_root.findall("application/service")
    if element.get(android_ns + "name") == "io.github.magisk317.relay.service.StandardModeService"
]
if play_services:
    raise SystemExit("FAIL: retired StandardModeService must not be declared in the Play manifest")
PY
echo "PASS: foreground-service manifest contract (StandardModeService retired in 6fa158b9)"

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
