#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: $name is required" >&2
    exit 2
  fi
}

require_env CI_COMMIT_TAG

eval "$(bash scripts/release/release_ref.sh parse-ref tag "$CI_COMMIT_TAG")"

asset_root="${XINYI_DESKTOP_RELEASE_ASSET_ROOT:-desktop-artifacts}"
prepared_asset_dir="${XINYI_DESKTOP_RELEASE_PREPARED_ASSET_DIR:-desktop-release-assets}"
notes_file="${MAGISK_RELEASE_NOTES_FILE:-desktop-release-notes.md}"
package_name="${XINYI_DESKTOP_RELEASE_PACKAGE_NAME:-desktop-release}"
release_name="${XINYI_DESKTOP_RELEASE_NAME:-$release_title}"
release_description="${XINYI_DESKTOP_RELEASE_DESCRIPTION:-Desktop release assets for ${CI_COMMIT_TAG}.}"

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

rm -rf "$prepared_asset_dir"
mkdir -p "$prepared_asset_dir"
declare -A used_names=()
for source_file in "${source_files[@]}"; do
  platform="$(basename "$(dirname "$source_file")")"
  asset_name="${platform}-$(basename "$source_file")"
  if [[ -n "${used_names[$asset_name]:-}" ]]; then
    echo "ERROR: duplicate desktop release asset name: $asset_name" >&2
    exit 1
  fi
  used_names[$asset_name]=1
  cp "$source_file" "$prepared_asset_dir/$asset_name"
done

printf '%s\n' "$release_description" > "$notes_file"
MAGISK_RELEASE_NOTES_FILE="$notes_file" \
MAGISK_RELEASE_NAME="$release_name" \
MAGISK_GITLAB_RELEASE_PACKAGE_NAME="$package_name" \
MAGISK_GITLAB_RELEASE_PREPARED_ASSET_DIR="$prepared_asset_dir" \
MAGISK_GITLAB_RELEASE_DIRECT_ASSET_PREFIX="/desktop" \
  bash "$MAGISK_CI_TOOLKIT_DIR/release/gitlab_release.sh"
