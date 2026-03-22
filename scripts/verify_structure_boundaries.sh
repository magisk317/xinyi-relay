#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_SRC="$ROOT_DIR/app/src/main/java/io/github/magisk317/relay"
RUNTIME_SRC="$ROOT_DIR/runtime/src/main/java/io/github/magisk317/relay"

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

expect_only_files "$APP_SRC/web" "WebUiManager.kt"

expect_no_kotlin_files "$APP_SRC/feature"
expect_no_kotlin_files "$RUNTIME_SRC/feature"
expect_no_kotlin_files "$RUNTIME_SRC/forwarder"

forbid_imports_in_dir() {
  local dir="$1"
  shift
  local -a patterns=("$@")
  [[ -d "$dir" ]] || return 0
  local file
  while IFS= read -r file; do
    local pattern
    for pattern in "${patterns[@]}"; do
      if rg -n "$pattern" "$file" >/dev/null; then
        violations+=("$(realpath --relative-to="$ROOT_DIR" "$file") imports forbidden lower-layer symbols")
        break
      fi
    done
  done < <(find "$dir" -type f -name '*.kt' | sort)
}

forbid_imports_in_dir "$APP_SRC/app" \
  '^import io\.github\.magisk317\.relay\.bootstrap\.' \
  '^import io\.github\.magisk317\.relay\.data\.' \
  '^import io\.github\.magisk317\.relay\.domain\.' \
  '^import io\.github\.magisk317\.relay\.platform\.' \
  '^import io\.github\.magisk317\.relay\.legacy\.' \
  '^import io\.github\.magisk317\.relay\.model\.' \
  '^import io\.github\.magisk317\.relay\.common\.(constant|utils)\.' \
  '^import io\.github\.magisk317\.smscode\.core\.'

forbid_imports_in_dir "$APP_SRC/receiver" \
  '^import io\.github\.magisk317\.relay\.bootstrap\.' \
  '^import io\.github\.magisk317\.relay\.data\.' \
  '^import io\.github\.magisk317\.relay\.domain\.' \
  '^import io\.github\.magisk317\.relay\.platform\.' \
  '^import io\.github\.magisk317\.relay\.legacy\.' \
  '^import io\.github\.magisk317\.relay\.model\.' \
  '^import io\.github\.magisk317\.relay\.common\.(constant|utils)\.' \
  '^import io\.github\.magisk317\.smscode\.core\.'

forbid_imports_in_dir "$APP_SRC/service" \
  '^import io\.github\.magisk317\.relay\.bootstrap\.' \
  '^import io\.github\.magisk317\.relay\.data\.' \
  '^import io\.github\.magisk317\.relay\.domain\.' \
  '^import io\.github\.magisk317\.relay\.platform\.' \
  '^import io\.github\.magisk317\.relay\.legacy\.' \
  '^import io\.github\.magisk317\.relay\.model\.' \
  '^import io\.github\.magisk317\.relay\.common\.(constant|utils)\.' \
  '^import io\.github\.magisk317\.smscode\.core\.'

forbid_imports_in_dir "$APP_SRC/web" \
  '^import io\.github\.magisk317\.relay\.bootstrap\.' \
  '^import io\.github\.magisk317\.relay\.data\.' \
  '^import io\.github\.magisk317\.relay\.domain\.' \
  '^import io\.github\.magisk317\.relay\.platform\.' \
  '^import io\.github\.magisk317\.relay\.legacy\.' \
  '^import io\.github\.magisk317\.relay\.model\.' \
  '^import io\.github\.magisk317\.relay\.common\.(constant|utils)\.' \
  '^import io\.github\.magisk317\.smscode\.core\.'

if [[ "${#violations[@]}" -ne 0 ]]; then
  printf 'Structure boundary verification failed:\n' >&2
  printf ' - %s\n' "${violations[@]}" >&2
  exit 1
fi

echo "Structure boundary verification passed."
