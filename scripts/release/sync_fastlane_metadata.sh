#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
FASTLANE_ROOT="$ROOT_DIR/fastlane/metadata/android"
MODE="write"
STATUS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/release/sync_fastlane_metadata.sh [--check]

Options:
  --check   Validate metadata is synchronized, do not modify files.
EOF
}

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

fail() {
  local message="$1"
  echo "ERROR: $message" >&2
  STATUS=1
}

if [[ $# -gt 1 ]]; then
  usage
  exit 2
fi

if [[ $# -eq 1 ]]; then
  case "$1" in
    --check)
      MODE="check"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: unknown option '$1'" >&2
      usage
      exit 2
      ;;
  esac
fi

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "ERROR: missing version file: $VERSION_FILE" >&2
  exit 2
fi

VERSION_CODE="$(extract_toml_value "versionCode" "$VERSION_FILE")"
if [[ -z "$VERSION_CODE" ]]; then
  echo "ERROR: failed to parse versionCode from $VERSION_FILE" >&2
  exit 2
fi

sync_locale() {
  local locale="$1"
  local whatsnew_source="$2"
  local art_source="$3"
  local locale_root="$FASTLANE_ROOT/$locale"
  local changelog_target="$locale_root/changelogs/$VERSION_CODE.txt"
  local screenshots_dir="$locale_root/images/phoneScreenshots"

  if [[ ! -f "$whatsnew_source" ]]; then
    fail "missing source file: $whatsnew_source"
    return
  fi
  if [[ ! -d "$art_source" ]]; then
    fail "missing screenshot source directory: $art_source"
    return
  fi

  local files=()
  mapfile -t files < <(find "$art_source" -maxdepth 1 -type f -name '*.png' | sort)
  if [[ ${#files[@]} -eq 0 ]]; then
    fail "no PNG screenshots found in $art_source"
    return
  fi

  if [[ "$MODE" == "write" ]]; then
    mkdir -p "$locale_root/changelogs" "$screenshots_dir"
    cp "$whatsnew_source" "$changelog_target"
    find "$screenshots_dir" -maxdepth 1 -type f -name '*.png' -delete
    local i=1
    local src=""
    for src in "${files[@]}"; do
      cp "$src" "$screenshots_dir/${i}.png"
      i=$((i + 1))
    done
    echo "SYNC: $locale changelog and ${#files[@]} screenshots"
    return
  fi

  if [[ ! -f "$changelog_target" ]]; then
    fail "missing target changelog: $changelog_target"
  elif ! cmp -s "$whatsnew_source" "$changelog_target"; then
    fail "changelog out of sync for $locale: $changelog_target"
  fi

  if [[ ! -d "$screenshots_dir" ]]; then
    fail "missing target screenshot directory: $screenshots_dir"
    return
  fi

  local j=1
  local src_file=""
  for src_file in "${files[@]}"; do
    local target_file="$screenshots_dir/${j}.png"
    if [[ ! -f "$target_file" ]]; then
      fail "missing screenshot: $target_file"
    elif ! cmp -s "$src_file" "$target_file"; then
      fail "screenshot out of sync for $locale: $target_file"
    fi
    j=$((j + 1))
  done

  local expected_count="${#files[@]}"
  local actual_count
  actual_count="$(find "$screenshots_dir" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d '[:space:]')"
  if [[ "$actual_count" != "$expected_count" ]]; then
    fail "unexpected screenshot count for $locale: expected=$expected_count actual=$actual_count"
  else
    echo "CHECK: $locale changelog and screenshots are synchronized"
  fi
}

sync_locale \
  "en-US" \
  "$ROOT_DIR/distribution/whatsnew/whatsnew-en-US" \
  "$ROOT_DIR/docs/assets/common"

sync_locale \
  "zh-CN" \
  "$ROOT_DIR/distribution/whatsnew/whatsnew-zh-CN" \
  "$ROOT_DIR/docs/assets/common"

if (( STATUS != 0 )); then
  exit 1
fi

if [[ "$MODE" == "write" ]]; then
  echo "Fastlane metadata synchronized for versionCode=$VERSION_CODE"
else
  echo "Fastlane metadata check passed for versionCode=$VERSION_CODE"
fi
# Fold the metadata this script just generated back into the previous commit
# when that commit is the release commit and the only dirty paths are the ones
# owned by this script. Pushed history is rewritten (amend + force push -- the
# release flow force-pushes the branch anyway); unpushed history is amended in
# place. Any other situation leaves the working tree untouched.
fold_back_release_metadata() {
  if [[ "$MODE" != "write" || "${FASTLANE_SYNC_FOLD_BACK:-1}" != "1" || "$STATUS" -ne 0 ]]; then
    return 0
  fi
  if ! git -C "$ROOT_DIR" rev-parse --git-dir >/dev/null 2>&1; then
    return 0
  fi

  local head_subject
  head_subject="$(git -C "$ROOT_DIR" log -1 --format=%s 2>/dev/null || true)"
  if [[ ! "$head_subject" =~ ^[a-z]+\(release\): ]]; then
    echo "FOLD-BACK: HEAD is not a release commit, skipping ($head_subject)"
    return 0
  fi

  # Paths this script may have written, relative to the repo root.
  local -a owned=()
  local locale rel_changelog png rel_png
  for locale in en-US zh-CN; do
    rel_changelog="fastlane/metadata/android/$locale/changelogs/$VERSION_CODE.txt"
    owned+=("$rel_changelog")
    while IFS= read -r png; do
      [[ -n "$png" ]] || continue
      rel_png="${png#$ROOT_DIR/}"
      owned+=("$rel_png")
    done < <(find "$FASTLANE_ROOT/$locale/images/phoneScreenshots" -maxdepth 1 -type f -name '*.png' 2>/dev/null | sort)
  done

  # Every dirty path must be owned by this script; anything else (version
  # bumps, hand edits) belongs to a different commit.
  local -a dirty=()
  local status_line status_path owned_path is_owned path
  while IFS= read -r status_line; do
    [[ -n "$status_line" ]] || continue
    status_path="${status_line:3}"
    if [[ "$status_path" == *" -> "* ]]; then
      echo "FOLD-BACK: rename detected, skipping ($status_path)"
      return 0
    fi
    dirty+=("$status_path")
  done < <(git -C "$ROOT_DIR" status --porcelain)

  for path in ${dirty[@]+"${dirty[@]}"}; do
    is_owned=false
    for owned_path in "${owned[@]}"; do
      if [[ "$path" == "$owned_path" ]]; then
        is_owned=true
        break
      fi
    done
    if [[ "$is_owned" != "true" ]]; then
      echo "FOLD-BACK: unowned change present, skipping ($path)"
      return 0
    fi
  done

  if [[ "${#dirty[@]}" -eq 0 ]]; then
    echo "FOLD-BACK: nothing to fold back"
    return 0
  fi

  # Decide push behavior BEFORE amending: the amend rewrites HEAD, so the
  # ancestry check must run against the old commit.
  local pushed=false upstream
  upstream="$(git -C "$ROOT_DIR" rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null || true)"
  if [[ -n "$upstream" ]] && git -C "$ROOT_DIR" merge-base --is-ancestor HEAD "$upstream" 2>/dev/null; then
    pushed=true
  fi

  if ! git -C "$ROOT_DIR" add -- "${owned[@]}" || ! git -C "$ROOT_DIR" commit --amend --no-edit >/dev/null; then
    echo "FOLD-BACK: amend failed, leaving the working tree as is" >&2
    return 0
  fi
  echo "FOLD-BACK: amended generated metadata into: $head_subject"

  if [[ "$pushed" == "true" ]]; then
    echo "FOLD-BACK: release commit was pushed; rewriting remote history"
    if ! git -C "$ROOT_DIR" push --force-with-lease -o ci.skip; then
      echo "FOLD-BACK: force push failed -- resolve manually (git push --force-with-lease)" >&2
    fi
  fi
}

fold_back_release_metadata
