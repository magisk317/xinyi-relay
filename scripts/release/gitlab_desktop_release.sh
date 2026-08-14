#!/usr/bin/env bash
set -euo pipefail

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

require_env() {
  local name=$1
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: ${name} is required" >&2
    exit 2
  fi
}

gitlab_curl() {
  if [[ -n "${GITLAB_RELEASE_TOKEN:-}" ]]; then
    curl --silent --show-error --location --header "PRIVATE-TOKEN: ${GITLAB_RELEASE_TOKEN}" "$@"
  else
    curl --silent --show-error --location --header "JOB-TOKEN: ${CI_JOB_TOKEN}" "$@"
  fi
}

require_env CI_API_V4_URL
require_env CI_PROJECT_ID
require_env CI_PROJECT_URL
require_env CI_COMMIT_TAG
require_env CI_COMMIT_SHA
require_env CI_JOB_TOKEN

eval "$(bash scripts/release/release_ref.sh parse-ref tag "$CI_COMMIT_TAG")"

asset_root="${XINYI_DESKTOP_RELEASE_ASSET_ROOT:-desktop-artifacts}"
package_name="${XINYI_DESKTOP_RELEASE_PACKAGE_NAME:-desktop-release}"
tag_name="$CI_COMMIT_TAG"
release_name="${XINYI_DESKTOP_RELEASE_NAME:-$release_title}"
release_description="${XINYI_DESKTOP_RELEASE_DESCRIPTION:-Desktop release assets for ${tag_name}.}"

if [[ ! -d "$asset_root" ]]; then
  echo "ERROR: desktop artifact root not found: $asset_root" >&2
  exit 1
fi

mapfile -d '' source_files < <(
  find "$asset_root" -type f \
    \( -name '*.AppImage' -o -name '*.deb' -o -name '*.rpm' -o -name '*.exe' -o -name '*.msix' -o -name '*.msi' -o -name '*.dmg' -o -name '*.zip' \) \
    -print0 | sort -z
)
if [[ ${#source_files[@]} -eq 0 ]]; then
  echo "ERROR: no desktop release files found under $asset_root" >&2
  find "$asset_root" -type f -print >&2 || true
  exit 1
fi

encoded_project="$(urlencode "$CI_PROJECT_ID")"
encoded_tag="$(urlencode "$tag_name")"
encoded_package="$(urlencode "$package_name")"
release_url="${CI_API_V4_URL}/projects/${encoded_project}/releases/${encoded_tag}"
create_url="${CI_API_V4_URL}/projects/${encoded_project}/releases"

create_payload="$(mktemp)"
links_tsv="$(mktemp)"
trap 'rm -f "$create_payload" "$links_tsv"' EXIT

python3 - "$release_name" "$tag_name" "$CI_COMMIT_SHA" "$release_description" > "$create_payload" <<'PY'
import json
import sys

name, tag_name, ref, description = sys.argv[1:5]
print(json.dumps({
    "name": name,
    "tag_name": tag_name,
    "ref": ref,
    "description": description,
}))
PY

status="$(gitlab_curl --output /tmp/gitlab-desktop-release-get.json --write-out "%{http_code}" "$release_url" || true)"
case "$status" in
  200) ;;
  404)
    create_status="$(
      gitlab_curl --output /tmp/gitlab-desktop-release-create.json --write-out "%{http_code}" \
        --request POST \
        --header "Content-Type: application/json" \
        --data @"$create_payload" \
        "$create_url" || true
    )"
    case "$create_status" in
      200|201|409) ;;
      *)
        echo "ERROR: failed to create GitLab release $tag_name (HTTP $create_status)" >&2
        cat /tmp/gitlab-desktop-release-create.json >&2 || true
        exit 1
        ;;
    esac
    ;;
  *)
    echo "ERROR: failed to inspect GitLab release $tag_name (HTTP $status)" >&2
    cat /tmp/gitlab-desktop-release-get.json >&2 || true
    exit 1
    ;;
esac

: > "$links_tsv"
declare -A used_names=()
for asset_path in "${source_files[@]}"; do
  platform="$(basename "$(dirname "$asset_path")")"
  base_name="$(basename "$asset_path")"
  asset_name="${platform}-${base_name}"
  if [[ -n "${used_names[$asset_name]:-}" ]]; then
    asset_name="${platform}-$(date +%s)-${base_name}"
  fi
  used_names[$asset_name]=1

  encoded_asset="$(urlencode "$asset_name")"
  package_url="${CI_API_V4_URL}/projects/${encoded_project}/packages/generic/${encoded_package}/${encoded_tag}/${encoded_asset}"
  download_url="${CI_PROJECT_URL}/-/packages/generic/${package_name}/${tag_name}/${asset_name}"

  upload_status="$(
    gitlab_curl --output /tmp/gitlab-desktop-package-upload.json --write-out "%{http_code}" \
      --request PUT \
      --upload-file "$asset_path" \
      "$package_url" || true
  )"
  case "$upload_status" in
    200|201|409) ;;
    400)
      if ! grep -qiE 'already|taken|exist' /tmp/gitlab-desktop-package-upload.json; then
        echo "ERROR: failed to upload $asset_name (HTTP $upload_status)" >&2
        cat /tmp/gitlab-desktop-package-upload.json >&2 || true
        exit 1
      fi
      ;;
    *)
      echo "ERROR: failed to upload $asset_name (HTTP $upload_status)" >&2
      cat /tmp/gitlab-desktop-package-upload.json >&2 || true
      exit 1
      ;;
  esac

  printf '%s\t%s\t%s\t%s\n' "$asset_name" "$download_url" "/desktop/$asset_name" "package" >> "$links_tsv"
done

links_url="${release_url}/assets/links"
while IFS=$'\t' read -r link_name link_url direct_asset_path link_type; do
  link_status="$(
    gitlab_curl --output /tmp/gitlab-desktop-release-link.json --write-out "%{http_code}" \
      --request POST \
      --data-urlencode "name=${link_name}" \
      --data-urlencode "url=${link_url}" \
      --data-urlencode "direct_asset_path=${direct_asset_path}" \
      --data "link_type=${link_type}" \
      "$links_url" || true
  )"
  case "$link_status" in
    201|409) ;;
    *)
      echo "ERROR: failed to create release link $link_name (HTTP $link_status)" >&2
      cat /tmp/gitlab-desktop-release-link.json >&2 || true
      exit 1
      ;;
  esac
done < "$links_tsv"

echo "Published desktop release assets for $tag_name"
