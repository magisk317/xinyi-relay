#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
export MAGISK_RELEASE_POLICY_FILE="$ROOT_DIR/scripts/config/release-policy.json"

# Accept both "<tag>" (CI invocation) and "<root-dir> <tag>" (toolkit entry
# point convention).
if [[ -n "${1:-}" ]] && [[ -d "$1" ]]; then
  TAG_NAME="${2:-}"
else
  TAG_NAME="${1:-}"
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
    echo "FAIL: webui package.json version missing: $webui_pkg" >&2
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

# Enforce the foreground-service manifest contract in CI before a release.
check_manifest_contract() {
  local root_dir="$1"

  python3 - "$root_dir/app/src/main/AndroidManifest.xml" "$root_dir/app/src/play/AndroidManifest.xml" <<'PY'
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
# to grant. Instead of pinning the permission unconditionally, this guard keeps
# the same contract as RelayManifestContractTest: the permission must track the
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
}

# The GitHub APK pins matrix-sdk-android and downloads the matching plugin APK at
# runtime. A tag that ships before e2ee-plugin:sync finishes would leave that
# pin without an artifact, so require the exact SDK version (all ABI APKs and
# sha256 sidecars) to exist in the registry before the guard passes.
check_e2ee_plugin_published() {
  local root_dir="$1"
  local sdk_version packages_json package_id files_json missing

  sdk_version="$(sed -nE 's/^matrix-sdk-android[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' \
    "$root_dir/gradle/libs.versions.toml" | head -n1)"
  if [[ -z "$sdk_version" ]]; then
    echo "FAIL: cannot resolve matrix-sdk-android from gradle/libs.versions.toml" >&2
    return 1
  fi

  # Local guard runs have no job token; the tag pipeline is the enforcement point.
  if [[ -z "${CI_JOB_TOKEN:-}" || -z "${CI_API_V4_URL:-}" || -z "${CI_PROJECT_ID:-}" ]]; then
    echo "SKIP: E2EE plugin availability (no GitLab CI job token)"
    return 0
  fi

  if ! packages_json="$(curl --silent --show-error --fail \
    --header "JOB-TOKEN: $CI_JOB_TOKEN" \
    "$CI_API_V4_URL/projects/$CI_PROJECT_ID/packages?package_name=xinyi-e2ee-plugin&per_page=100")"; then
    echo "FAIL: cannot query the GitLab package registry for xinyi-e2ee-plugin" >&2
    return 1
  fi

  package_id="$(python3 -c '
import json
import sys

version = sys.argv[1]
for package in json.load(sys.stdin):
    if package.get("name") == "xinyi-e2ee-plugin" and package.get("version") == version:
        print(package.get("id", ""))
        break
' "$sdk_version" <<<"$packages_json")"

  if [[ -z "$package_id" ]]; then
    echo "FAIL: xinyi-e2ee-plugin $sdk_version is not published; run e2ee-plugin:sync before tagging" >&2
    return 1
  fi

  if ! files_json="$(curl --silent --show-error --fail \
    --header "JOB-TOKEN: $CI_JOB_TOKEN" \
    "$CI_API_V4_URL/projects/$CI_PROJECT_ID/packages/$package_id/package_files?per_page=100")"; then
    echo "FAIL: cannot list xinyi-e2ee-plugin $sdk_version package files" >&2
    return 1
  fi

  missing="$(python3 -c '
import json
import sys

version = sys.argv[1]
abis = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
expected = {
    f"xinyi-e2ee-plugin_v{version}_{abi}{suffix}"
    for abi in abis
    for suffix in (".apk", ".apk.sha256")
}
names = {entry.get("file_name", "") for entry in json.load(sys.stdin)}
print("\n".join(sorted(expected - names)))
' "$sdk_version" <<<"$files_json")"

  if [[ -n "$missing" ]]; then
    echo "FAIL: xinyi-e2ee-plugin $sdk_version is missing artifacts:" >&2
    printf '%s\n' "$missing" >&2
    return 1
  fi

  echo "PASS: xinyi-e2ee-plugin $sdk_version has all ABI APKs and sha256 sidecars"
}


# The base checks, the Play metadata checks and the project_checks dispatch all
# live in the toolkit; scripts/config/release-policy.json drives what runs.
# shellcheck source=check_release_guard.sh
source "${TOOLKIT_DIR}/release/check_release_guard.sh"
check_release_guard "$ROOT_DIR" "$TAG_NAME"
