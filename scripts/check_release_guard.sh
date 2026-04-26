#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAX_LEN="${PLAY_WHATSNEW_MAX:-500}"
TAG_NAME="${1:-}"
RELEASE_REF_SCRIPT="$ROOT_DIR/scripts/release_ref.sh"
REQUIRED_LOCALES=(${PLAY_WHATSNEW_REQUIRED_LOCALES:-en-US zh-CN})
FASTLANE_REQUIRED_LOCALES=(${FASTLANE_REQUIRED_LOCALES:-en-US zh-CN})
FASTLANE_MIN_SCREENSHOTS="${FASTLANE_MIN_SCREENSHOTS:-1}"
ALLOW_NON_ASCII_COMMIT_SUBJECT="${ALLOW_NON_ASCII_COMMIT_SUBJECT:-false}"

if [[ ! "$MAX_LEN" =~ ^[0-9]+$ ]]; then
  echo "ERROR: PLAY_WHATSNEW_MAX must be an integer, got '$MAX_LEN'." >&2
  exit 2
fi
if [[ ! "$FASTLANE_MIN_SCREENSHOTS" =~ ^[0-9]+$ ]]; then
  echo "ERROR: FASTLANE_MIN_SCREENSHOTS must be an integer, got '$FASTLANE_MIN_SCREENSHOTS'." >&2
  exit 2
fi

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
if [[ ! -f "$VERSION_FILE" ]]; then
  echo "ERROR: Missing $VERSION_FILE" >&2
  exit 2
fi

VERSION_NAME="$(extract_toml_value "versionName" "$VERSION_FILE")"
VERSION_CODE="$(extract_toml_value "versionCode" "$VERSION_FILE")"

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
  echo "ERROR: Failed to parse versionName/versionCode from $VERSION_FILE" >&2
  exit 2
fi

release_kind="full"
release_component="all"
release_tag=""
release_version="v$VERSION_NAME"
release_title="$release_version"
release_requires_android_metadata="true"
release_is_component_only="false"

if [[ -n "$TAG_NAME" ]]; then
  eval "$(bash "$RELEASE_REF_SCRIPT" parse-tag "$TAG_NAME")"
fi

echo "Release guard"
echo "- versionName: $VERSION_NAME"
echo "- versionCode: $VERSION_CODE"
echo "- release kind: $release_kind"
echo "- release version: $release_version"
echo "- max whatsnew length: $MAX_LEN"
echo "- required locales: ${REQUIRED_LOCALES[*]}"
echo "- fastlane locales: ${FASTLANE_REQUIRED_LOCALES[*]}"
if (( FASTLANE_MIN_SCREENSHOTS == 0 )); then
  echo "- fastlane min screenshots: disabled (0)"
else
  echo "- fastlane min screenshots: $FASTLANE_MIN_SCREENSHOTS"
fi
echo "- commit subject ascii-only: $([[ "$ALLOW_NON_ASCII_COMMIT_SUBJECT" == "true" ]] && echo "disabled" || echo "enabled")"

FAIL=0

WHATSNEW_DIR="$ROOT_DIR/distribution/whatsnew"
if [[ "$release_requires_android_metadata" == "true" && ! -d "$WHATSNEW_DIR" ]]; then
  echo "ERROR: Missing $WHATSNEW_DIR" >&2
  exit 2
fi

if [[ "$release_requires_android_metadata" == "true" ]]; then
  for locale in "${REQUIRED_LOCALES[@]}"; do
    file="$WHATSNEW_DIR/whatsnew-$locale"
    if [[ ! -f "$file" ]]; then
      echo "FAIL: required whatsnew locale missing: $locale ($file)"
      FAIL=1
      continue
    fi

    count="$(wc -m < "$file" | tr -d '[:space:]')"
    if (( count == 0 )); then
      echo "FAIL: $locale is empty ($file)"
      FAIL=1
    elif (( count > MAX_LEN )); then
      echo "FAIL: $locale length=$count exceeds max=$MAX_LEN ($file)"
      FAIL=1
    else
      echo "PASS: $locale length=$count/$MAX_LEN"
    fi
  done
else
  echo "PASS: Android whatsnew checks skipped for release kind '$release_kind'"
fi

check_non_ascii_commit_subjects() {
  local commit_range=""
  local base_tag=""
  local checked=0
  local has_non_ascii=0
  local row=""
  local sha=""
  local subject=""
  local offenders=()

  base_tag="$(git -C "$ROOT_DIR" describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
  if [[ -n "$base_tag" ]]; then
    commit_range="$base_tag..HEAD"
  else
    commit_range="HEAD"
  fi

  while IFS=$'\t' read -r sha subject; do
    [[ -z "$sha" ]] && continue
    checked=$((checked + 1))
    if printf '%s' "$subject" | LC_ALL=C grep -q '[^ -~]'; then
      has_non_ascii=1
      offenders+=("$sha|$subject")
    fi
  done < <(git -C "$ROOT_DIR" log --no-merges --pretty=format:'%h%x09%s' "$commit_range")

  if (( checked == 0 )); then
    echo "PASS: commit subject check skipped (no commits in range: $commit_range)"
    return
  fi

  if (( has_non_ascii == 0 )); then
    echo "PASS: commit subjects are ASCII-only ($checked commits, range: $commit_range)"
    return
  fi

  echo "FAIL: non-ASCII commit subject detected (range: $commit_range)"
  for row in "${offenders[@]}"; do
    sha="${row%%|*}"
    subject="${row#*|}"
    echo " - $sha $subject"
  done
  FAIL=1
}

if [[ "$ALLOW_NON_ASCII_COMMIT_SUBJECT" == "true" ]]; then
  echo "PASS: commit subject ASCII guard disabled by ALLOW_NON_ASCII_COMMIT_SUBJECT=true"
else
  check_non_ascii_commit_subjects
fi

if [[ "$release_kind" == "full" || "$release_kind" == "mobile" ]]; then
  CHANGELOG_FILE="$ROOT_DIR/docs/CHANGELOG.md"
  if [[ ! -f "$CHANGELOG_FILE" ]]; then
    echo "FAIL: missing changelog file ($CHANGELOG_FILE)"
    FAIL=1
  elif grep -Fq "## [v$VERSION_NAME]" "$CHANGELOG_FILE"; then
    echo "PASS: changelog contains section for v$VERSION_NAME"
  else
    echo "FAIL: changelog section not found: ## [v$VERSION_NAME] in $CHANGELOG_FILE"
    FAIL=1
  fi
else
  echo "PASS: changelog guard skipped for release kind '$release_kind'"
fi

if [[ -n "$TAG_NAME" ]]; then
  expected_version="v$VERSION_NAME"
  if [[ "$release_version" != "$expected_version" ]]; then
    echo "FAIL: tag version mismatch. got '$TAG_NAME' -> '$release_version', expected '$expected_version' from versionName."
    FAIL=1
  else
    echo "PASS: tag version matches versionName ($TAG_NAME -> $release_version)"
  fi
fi

FASTLANE_SYNC_SCRIPT="$ROOT_DIR/scripts/sync_fastlane_metadata.sh"
if [[ "$release_requires_android_metadata" == "true" ]]; then
  if [[ ! -x "$FASTLANE_SYNC_SCRIPT" ]]; then
    echo "FAIL: missing executable fastlane sync script ($FASTLANE_SYNC_SCRIPT)"
    FAIL=1
  elif "$FASTLANE_SYNC_SCRIPT" --check; then
    echo "PASS: fastlane metadata sync check passed"
  else
    echo "FAIL: fastlane metadata is not synchronized"
    FAIL=1
  fi
else
  echo "PASS: fastlane metadata sync check skipped for release kind '$release_kind'"
fi

FASTLANE_META_DIR="$ROOT_DIR/fastlane/metadata/android"
if [[ "$release_requires_android_metadata" == "true" ]]; then
  for locale in "${FASTLANE_REQUIRED_LOCALES[@]}"; do
    locale_dir="$FASTLANE_META_DIR/$locale"
    required_file="$locale_dir/title.txt"
    if [[ ! -s "$required_file" ]]; then
      echo "FAIL: missing or empty Fastlane file ($required_file)"
      FAIL=1
    else
      echo "PASS: Fastlane file present ($required_file)"
    fi

    required_file="$locale_dir/short_description.txt"
    if [[ ! -s "$required_file" ]]; then
      echo "FAIL: missing or empty Fastlane file ($required_file)"
      FAIL=1
    else
      echo "PASS: Fastlane file present ($required_file)"
    fi

    required_file="$locale_dir/full_description.txt"
    if [[ ! -s "$required_file" ]]; then
      echo "FAIL: missing or empty Fastlane file ($required_file)"
      FAIL=1
    else
      echo "PASS: Fastlane file present ($required_file)"
    fi

    changelog_file="$locale_dir/changelogs/$VERSION_CODE.txt"
    if [[ ! -s "$changelog_file" ]]; then
      echo "FAIL: missing or empty Fastlane changelog ($changelog_file)"
      FAIL=1
    else
      echo "PASS: Fastlane changelog present ($changelog_file)"
    fi

    screenshots_dir="$locale_dir/images/phoneScreenshots"
    if [[ ! -d "$screenshots_dir" ]]; then
      echo "FAIL: missing Fastlane screenshots directory ($screenshots_dir)"
      FAIL=1
      continue
    fi

    screenshot_count="$(find "$screenshots_dir" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d '[:space:]')"
    if (( FASTLANE_MIN_SCREENSHOTS == 0 )); then
      echo "PASS: Fastlane screenshot count check disabled for $locale (found $screenshot_count)"
    elif (( screenshot_count < FASTLANE_MIN_SCREENSHOTS )); then
      echo "FAIL: Fastlane screenshots too few for $locale: $screenshot_count < $FASTLANE_MIN_SCREENSHOTS ($screenshots_dir)"
      FAIL=1
    else
      echo "PASS: Fastlane screenshots for $locale: $screenshot_count"
    fi
  done
else
  echo "PASS: Fastlane metadata checks skipped for release kind '$release_kind'"
fi

if (( FAIL != 0 )); then
  echo "Release guard failed." >&2
  exit 1
fi

echo "Release guard passed."
