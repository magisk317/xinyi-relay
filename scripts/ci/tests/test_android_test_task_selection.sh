#!/usr/bin/env bash
# Regression guard for Android test task selection from android-test-tasks.json.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
config="$root_dir/scripts/config/android-test-tasks.json"
toolkit_dir="${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}"
selector="$toolkit_dir/ci/select_gradle_tasks.py"

[[ -f "$config" ]] || { echo "ERROR: $config is missing" >&2; exit 1; }
[[ -f "$selector" ]] || {
  echo "ERROR: $selector is missing; set MAGISK_CI_TOOLKIT_DIR to the toolkit checkout" >&2
  exit 1
}

expected_full_tasks="$(python3 -c 'import json,sys; print("\n".join(json.load(open(sys.argv[1], encoding="utf-8"))["full_tasks"]))' "$config")"

root_check_dependencies=(
  verifyModuleBoundaries
  verifyStructureBoundaries
  verifyEmbeddedSubmodules
  verifyDependencyGovernance
  verifyNoGlobalWarningMute
  verifyGodFileLimits
)

assert_full_tasks() {
  local scenario="$1"
  local selected="$2"
  if [[ "$selected" != "$expected_full_tasks" ]]; then
    printf 'FAIL - %s did not select exactly full_tasks\nExpected:\n%s\nActual:\n%s\n' \
      "$scenario" "$expected_full_tasks" "$selected" >&2
    exit 1
  fi
  printf 'ok   - %s selects exactly full_tasks\n' "$scenario"
}

assert_root_check_dependencies() {
  local scenario="$1"
  local selected="$2"
  local task
  for task in "${root_check_dependencies[@]}"; do
    if ! grep -Fqx -- "$task" <<< "$selected"; then
      printf 'FAIL - %s does not select root check dependency %s\n' "$scenario" "$task" >&2
      exit 1
    fi
  done
  printf 'ok   - %s selects all root check dependencies\n' "$scenario"
}

full_selection="$(
  env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
    python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
      --paths-file <(printf 'full\n')
)"
assert_full_tasks "full impact selection" "$full_selection"
assert_root_check_dependencies "full impact selection" "$full_selection"

beta_selection="$(
  env -u CI_COMMIT_TAG -u GITHUB_REF_TYPE -u GITHUB_REF_NAME CI_COMMIT_BRANCH=beta \
    python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config"
)"
assert_full_tasks "beta branch selection" "$beta_selection"
assert_root_check_dependencies "beta branch selection" "$beta_selection"

scripts_config_selection="$(
  env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
    python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
      --paths-file <(printf 'impact\nscripts/config/android-test-tasks.json\n')
)"
assert_full_tasks "scripts/config change selection" "$scripts_config_selection"
assert_root_check_dependencies "scripts/config change selection" "$scripts_config_selection"

echo "all Android test-task selector checks passed"
