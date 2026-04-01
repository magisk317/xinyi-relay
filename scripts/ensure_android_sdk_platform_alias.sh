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

if [[ -e "$target_path" && ! -L "$target_path" ]]; then
  echo "Android SDK alias already present: $target_path"
  exit 0
fi

rm -rf "$target_path"
cp -a "$source_path" "$target_path"

source_properties="$target_path/source.properties"
package_xml="$target_path/package.xml"
target_api="${TARGET_PLATFORM_ALIAS#android-}"

if [[ -f "$source_properties" ]]; then
  sed -i "s/^AndroidVersion\\.ApiLevel=.*/AndroidVersion.ApiLevel=$target_api/" "$source_properties"
fi

if [[ -f "$package_xml" ]]; then
  sed -i "s#path=\"platforms;${SOURCE_PLATFORM_DIR}\"#path=\"platforms;${TARGET_PLATFORM_ALIAS}\"#" "$package_xml"
  sed -i "s#<api-level>${SOURCE_PLATFORM_DIR#android-}</api-level>#<api-level>$target_api</api-level>#" "$package_xml"
  sed -i "s#<display-name>Android SDK Platform ${SOURCE_PLATFORM_DIR#android-}</display-name>#<display-name>Android SDK Platform $target_api</display-name>#" "$package_xml"
fi

echo "Created Android SDK compatibility platform: $target_path"
