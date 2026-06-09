#!/usr/bin/env bash
set -euo pipefail

# Ensures desktop version files match gradle/libs.versions.toml.
# If already in sync, exits silently (no file writes).
# If out of sync, patches the files and prints a warning.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
PACKAGE_JSON="$ROOT_DIR/desktop/package.json"
CARGO_TOML="$ROOT_DIR/desktop/src-tauri/Cargo.toml"
TAURI_CONF="$ROOT_DIR/desktop/src-tauri/tauri.conf.json"

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

current_version() {
  local file="$1"
  sed -nE 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' "$file" | head -n1
}

current_cargo_version() {
  sed -nE 's/^version[[:space:]]*=[[:space:]]*"([^"]+)"/\1/p' "$CARGO_TOML" | head -n1
}

replace_once() {
  local file="$1"
  local pattern="$2"
  local replacement="$3"

  python3 - "$file" "$pattern" "$replacement" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
pattern = sys.argv[2]
replacement = sys.argv[3]
text = path.read_text()
next_text, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
if count != 1:
    print(f"ERROR: failed to update version in {path}", file=sys.stderr)
    sys.exit(1)
path.write_text(next_text)
PY
}

VERSION="$(extract_toml_value "versionName" "$VERSION_FILE")"
if [[ -z "$VERSION" ]]; then
  echo "ERROR: failed to parse versionName from $VERSION_FILE" >&2
  exit 1
fi

pkg_ver="$(current_version "$PACKAGE_JSON")"
tauri_ver="$(current_version "$TAURI_CONF")"
cargo_ver="$(current_cargo_version)"

if [[ "$pkg_ver" == "$VERSION" && "$tauri_ver" == "$VERSION" && "$cargo_ver" == "$VERSION" ]]; then
  exit 0
fi

echo "WARNING: desktop version mismatch (expected $VERSION, got pkg=$pkg_ver cargo=$cargo_ver tauri=$tauri_ver), auto-syncing..." >&2

# package.json: "version": "x.y.z"
replace_once "$PACKAGE_JSON" '"version"\s*:\s*"[^"]*"' "\"version\": \"$VERSION\""

# Cargo.toml: version = "x.y.z"  (under [package])
replace_once "$CARGO_TOML" '^version\s*=\s*"[^"]*"' "version = \"$VERSION\""

# tauri.conf.json: "version": "x.y.z"
replace_once "$TAURI_CONF" '"version"\s*:\s*"[^"]*"' "\"version\": \"$VERSION\""

echo "Desktop version synced to $VERSION" >&2
