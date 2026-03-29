#!/usr/bin/env bash
set -euo pipefail

sdk_root=""
for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "/usr/local/lib/android/sdk"; do
  if [[ -n "$candidate" && -d "$candidate/platforms" ]]; then
    sdk_root="$candidate"
    break
  fi
done

if [[ -z "$sdk_root" ]]; then
  echo "Android SDK platforms directory not found, skip alias normalization."
  exit 0
fi

platforms_dir="$sdk_root/platforms"
shopt -s nullglob

created=0
for source in "$platforms_dir"/android-*.0; do
  target="${source%.0}"
  if [[ -e "$target" ]]; then
    continue
  fi
  ln -s "$(basename "$source")" "$target"
  echo "Created Android SDK alias: $target -> $(basename "$source")"
  created=1
done

if [[ "$created" -eq 0 ]]; then
  echo "Android SDK aliases already normalized in $platforms_dir"
fi
