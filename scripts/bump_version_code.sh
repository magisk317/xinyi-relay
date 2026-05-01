#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
CHANGELOG_FILE="$ROOT_DIR/docs/CHANGELOG.md"
WHATSNEW_EN_FILE="$ROOT_DIR/distribution/whatsnew/whatsnew-en-US"
SYNC_FASTLANE_SCRIPT="$ROOT_DIR/scripts/sync_fastlane_metadata.sh"

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

replace_first_in_file() {
  local file="$1"
  local from="$2"
  local to="$3"
  python3 - "$file" "$from" "$to" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
old = sys.argv[2]
new = sys.argv[3]
text = path.read_text()
if old not in text:
    raise SystemExit(f"pattern not found in {path}: {old}")
path.write_text(text.replace(old, new, 1))
PY
}

current_code="$(extract_toml_value "versionCode" "$VERSION_FILE")"
if [[ -z "$current_code" ]]; then
  echo "ERROR: failed to parse versionCode from $VERSION_FILE" >&2
  exit 1
fi

next_code="${1:-$((current_code + 1))}"
if ! [[ "$next_code" =~ ^[0-9]+$ ]]; then
  echo "ERROR: next versionCode must be numeric, got: $next_code" >&2
  exit 1
fi

if [[ "$next_code" -le "$current_code" ]]; then
  echo "ERROR: next versionCode ($next_code) must be greater than current ($current_code)" >&2
  exit 1
fi

replace_first_in_file "$VERSION_FILE" "versionCode = \"$current_code\"" "versionCode = \"$next_code\""
replace_first_in_file "$CHANGELOG_FILE" "versionCode $current_code" "versionCode $next_code"
replace_first_in_file "$WHATSNEW_EN_FILE" "($current_code)" "($next_code)"

bash "$SYNC_FASTLANE_SCRIPT"

echo "Bumped versionCode: $current_code -> $next_code"
