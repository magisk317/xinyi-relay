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

PACKAGE_NAME="xinyi-e2ee-plugin"
APK_OUTPUT_DIR="features/matrix-e2ee-plugin/build/outputs/apk"

SDK_VERSION="$(sed -nE 's/^matrix-sdk-android[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' gradle/libs.versions.toml)"
if [[ -z "$SDK_VERSION" ]]; then
  echo "ERROR: cannot resolve matrix-sdk-android version from gradle/libs.versions.toml" >&2
  exit 2
fi

API_BASE="${CI_API_V4_URL:-https://gitlab.com/api/v4}"
PROJECT_ID="${CI_PROJECT_ID:-84113188}"

gitlab_curl_args() {
  if [[ -n "${CI_JOB_TOKEN:-}" ]]; then
    printf '%s\n' --header "JOB-TOKEN: $CI_JOB_TOKEN"
  elif [[ -n "${GITLAB_TOKEN:-}" ]]; then
    printf '%s\n' --header "PRIVATE-TOKEN: $GITLAB_TOKEN"
  elif [[ -n "${GITLAB_DEPLOY_TOKEN:-}" ]]; then
    printf '%s\n' --header "DEPLOY-TOKEN: $GITLAB_DEPLOY_TOKEN"
  else
    echo "ERROR: set CI_JOB_TOKEN, GITLAB_TOKEN, or GITLAB_DEPLOY_TOKEN" >&2
    exit 2
  fi
}

echo "==> Building E2EE plugin APKs (matrix-sdk $SDK_VERSION)"
./gradlew :features:matrix_e2ee_plugin:assembleRelease --console=plain

mapfile -t apk_files < <(find "$APK_OUTPUT_DIR" -type f -name '*.apk' | sort)
if [[ ${#apk_files[@]} -eq 0 ]]; then
  echo "ERROR: no plugin APKs found under $APK_OUTPUT_DIR" >&2
  find "$APK_OUTPUT_DIR" -type f -print >&2 || true
  exit 1
fi

auth_args=()
while IFS= read -r line; do auth_args+=("$line"); done < <(gitlab_curl_args)

for apk in "${apk_files[@]}"; do
  name="$(basename "$apk")"
  sha_file="$apk.sha256"
  sha256sum "$apk" | awk '{print $1}' > "$sha_file"

  for asset in "$apk" "$sha_file"; do
    asset_name="$(basename "$asset")"
    url="$API_BASE/projects/$PROJECT_ID/packages/generic/$PACKAGE_NAME/$SDK_VERSION/$asset_name"
    echo "==> PUT $asset_name"
    status="$(curl --silent --output /tmp/e2ee-plugin-upload.json --write-out '%{http_code}' \
      --request PUT --upload-file "$asset" "${auth_args[@]}" "$url" || true)"
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
