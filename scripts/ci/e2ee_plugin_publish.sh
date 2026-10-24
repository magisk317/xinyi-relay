#!/usr/bin/env bash
# Build the Matrix E2EE plugin APKs (one per ABI), write .sha256 sidecars, and
# PUT them to the GitLab generic package registry under
# xinyi-e2ee-plugin/<matrix-sdk-version>/.
#
# Cadence is independent of the main APK: this script is safe to run on every
# upstream matrix-rust-sdk bump (the scheduled e2ee-plugin:sync job calls it
# only when Maven Central actually moved). Uploads are idempotent (409 = already
# published).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
TOOLKIT_DIR="${MAGISK_CI_TOOLKIT_DIR:-$(bash scripts/resolve_ci_toolkit.sh)}"
source "$TOOLKIT_DIR/ci/gitlab_api.sh"

PACKAGE_NAME="xinyi-e2ee-plugin"
APK_OUTPUT_DIR="features/matrix-e2ee-plugin/build/outputs/apk"

SDK_VERSION="$(sed -nE 's/^matrix-sdk-android[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' gradle/libs.versions.toml)"
if [[ -z "$SDK_VERSION" ]]; then
  echo "ERROR: cannot resolve matrix-sdk-android version from gradle/libs.versions.toml" >&2
  exit 2
fi

PROJECT_ID="${CI_PROJECT_ID:-84113188}"


echo "==> Building E2EE plugin APKs (matrix-sdk $SDK_VERSION)"
./gradlew :features:matrix_e2ee_plugin:assembleRelease --console=plain

mapfile -t apk_files < <(find "$APK_OUTPUT_DIR" -type f -name '*.apk' | sort)
if [[ ${#apk_files[@]} -eq 0 ]]; then
  echo "ERROR: no plugin APKs found under $APK_OUTPUT_DIR" >&2
  find "$APK_OUTPUT_DIR" -type f -print >&2 || true
  exit 1
fi


for apk in "${apk_files[@]}"; do
  name="$(basename "$apk")"
  sha_file="$apk.sha256"
  sha256sum "$apk" | awk '{print $1}' > "$sha_file"

  for asset in "$apk" "$sha_file"; do
    asset_name="$(basename "$asset")"
    endpoint="projects/$PROJECT_ID/packages/generic/$PACKAGE_NAME/$SDK_VERSION/$asset_name"
    echo "==> PUT $asset_name"
    status="$(gitlab_api_curl "$endpoint" --output /tmp/e2ee-plugin-upload.json --write-out '%{http_code}' \
      --request PUT --upload-file "$asset" || true)"
    case "$status" in
      200|201|409) ;;
      400)
        if ! grep -qiE 'already|taken|exist' /tmp/e2ee-plugin-upload.json; then
          echo "ERROR: failed to upload $asset_name (HTTP $status)" >&2
          cat /tmp/e2ee-plugin-upload.json >&2 || true
          exit 1
        fi
        ;;
      *)
        echo "ERROR: failed to upload $asset_name (HTTP $status)" >&2
        cat /tmp/e2ee-plugin-upload.json >&2 || true
        exit 1
        ;;
    esac
  done
done

echo "==> Published xinyi-e2ee-plugin/$SDK_VERSION:"
for apk in "${apk_files[@]}"; do
  echo "    $(basename "$apk")"
done
