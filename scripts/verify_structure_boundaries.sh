#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_SRC="$ROOT_DIR/app/src/main/java/io/github/magisk317/relay"

violations=()

expect_only_files() {
  local dir="$1"
  shift
  local -a expected=("$@")
  local -a actual=()

  if [[ -d "$dir" ]]; then
    while IFS= read -r file; do
      actual+=("$(basename "$file")")
    done < <(find "$dir" -maxdepth 1 -type f | sort)
  fi

  local expected_joined actual_joined
  expected_joined="$(printf '%s\n' "${expected[@]}" | sort | tr '\n' '|' )"
  actual_joined="$(printf '%s\n' "${actual[@]}" | sort | tr '\n' '|' )"

  if [[ "$expected_joined" != "$actual_joined" ]]; then
    violations+=("$(basename "$dir") must contain only: ${expected[*]} (actual: ${actual[*]:-<empty>})")
  fi
}

expect_no_kotlin_files() {
  local dir="$1"
  if [[ -d "$dir" ]] && find "$dir" -type f \( -name '*.kt' -o -name '*.java' \) | grep -q .; then
    violations+=("$(realpath --relative-to="$ROOT_DIR" "$dir") must not contain Kotlin/Java sources")
  fi
}

expect_only_files \
  "$APP_SRC/web" \
  "WebUiAssetHandler.kt" \
  "WebUiManager.kt" \
  "WebUiServer.kt" \
  "WebUiTlsManager.kt"

expect_no_kotlin_files "$APP_SRC/feature"

if [[ "${#violations[@]}" -ne 0 ]]; then
  printf 'Structure boundary verification failed:\n' >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi

echo "Structure boundary verification passed."
