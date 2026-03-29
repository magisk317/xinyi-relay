#!/usr/bin/env bash
set -euo pipefail

TARGET_PLATFORM_ALIAS="${TARGET_PLATFORM_ALIAS:-android-37}"
SOURCE_PLATFORM_PACKAGE="${SOURCE_PLATFORM_PACKAGE:-platforms;android-37.0}"
SOURCE_PLATFORM_DIR="${SOURCE_PLATFORM_DIR:-android-37.0}"

sdk_root=""
for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "/usr/local/lib/android/sdk"; do
  if [[ -n "$candidate" && -d "$candidate" ]]; then
    sdk_root="$candidate"
    break
  fi
done

if [[ -z "$sdk_root" ]]; then
  echo "Android SDK root not found, skip alias normalization."
  exit 0
fi

platforms_dir="$sdk_root/platforms"
mkdir -p "$platforms_dir"

resolve_sdkmanager() {
  if command -v sdkmanager >/dev/null 2>&1; then
    command -v sdkmanager
    return 0
  fi
  local candidates=(
    "$sdk_root/cmdline-tools/latest/bin/sdkmanager"
    "$sdk_root/cmdline-tools/bin/sdkmanager"
    "$sdk_root/tools/bin/sdkmanager"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

source_path="$platforms_dir/$SOURCE_PLATFORM_DIR"
target_path="$platforms_dir/$TARGET_PLATFORM_ALIAS"

if [[ ! -d "$source_path" ]]; then
  sdkmanager_path="$(resolve_sdkmanager || true)"
  if [[ -z "$sdkmanager_path" ]]; then
    echo "sdkmanager not found and $source_path is missing, cannot normalize aliases." >&2
    exit 1
  fi
  echo "Installing missing Android SDK platform package: $SOURCE_PLATFORM_PACKAGE"
  { yes || true; } | "$sdkmanager_path" --sdk_root="$sdk_root" "$SOURCE_PLATFORM_PACKAGE" >/dev/null
fi

if [[ ! -d "$source_path" ]]; then
  echo "Expected Android platform directory still missing: $source_path" >&2
  exit 1
fi

if [[ -L "$target_path" || -e "$target_path" ]]; then
  echo "Android SDK alias already present: $target_path"
  exit 0
fi

ln -s "$SOURCE_PLATFORM_DIR" "$target_path"
echo "Created Android SDK alias: $target_path -> $SOURCE_PLATFORM_DIR"
