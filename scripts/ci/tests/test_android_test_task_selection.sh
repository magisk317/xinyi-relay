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

assert_tasks_selected() {
  local scenario="$1"
  local selected="$2"
  shift 2
  local task
  for task in "$@"; do
    if ! grep -Fqx -- "$task" <<< "$selected"; then
      printf 'FAIL - %s does not select %s\n' "$scenario" "$task" >&2
      exit 1
    fi
  done
  printf 'ok   - %s selects its regression tests\n' "$scenario"
}

smscode_regression_tasks=(
  :smscode-core:hook:testDebugUnitTest
  :smscode-core:runtime:testDebugUnitTest
  :smscode-core:verification-runtime:testDebugUnitTest
)
kit_regression_tasks=(
  :magisk-xposed-kit:testDebugUnitTest
  :magisk-xposed-kit:contract:test
  :magisk-xposed-kit:logging:jvmTest
  :magisk-xposed-kit:diagnostics:testDebugUnitTest
  :magisk-xposed-kit:capability:testDebugUnitTest
  :magisk-xposed-kit:runtime:testDebugUnitTest
  :magisk-xposed-kit:permission:testDebugUnitTest
  :magisk-xposed-kit:service:testDebugUnitTest
  :magisk-xposed-kit:capture:testDebugUnitTest
  :magisk-xposed-kit:keepalive:jvmTest
)
assert_tasks_selected "beta shared SMS coverage" "$beta_selection" "${smscode_regression_tasks[@]}"
assert_tasks_selected "beta shared kit coverage" "$beta_selection" "${kit_regression_tasks[@]}"

for changed_path in smscode/core smscode/core/hook/src/main/kotlin/ProviderHook.kt; do
  selected="$(
    env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
      python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
        --paths-file <(printf 'impact\n%s\n' "$changed_path")
  )"
  assert_tasks_selected "$changed_path" "$selected" "${smscode_regression_tasks[@]}"
done
for changed_path in magisk-xposed-kit magisk-xposed-kit/src/main/kotlin/HookPermissions.kt; do
  selected="$(
    env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
      python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
        --paths-file <(printf 'impact\n%s\n' "$changed_path")
  )"
  assert_tasks_selected "$changed_path" "$selected" "${kit_regression_tasks[@]}"
done

forward_regression_tasks=(
  :magisk-forward-core:sender:api:jvmTest
  :magisk-forward-core:sender:testGithubDebugUnitTest
  :magisk-forward-core:sender:testPlayDebugUnitTest
  :magisk-forward-core:security:testGithubDebugUnitTest
  :magisk-forward-core:security:testPlayDebugUnitTest
)
assert_tasks_selected "beta sender coverage" "$beta_selection" "${forward_regression_tasks[@]}"
forward_selection="$(
  env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
    python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
      --paths-file <(printf 'impact\nforward/core\n')
)"
assert_tasks_selected "shared forward gitlink" "$forward_selection" "${forward_regression_tasks[@]}"

parent_runtime_tasks=(
  :runtime:testGithubDebugUnitTest
  :mobile:core:testGithubDebugUnitTest
  :mobile:feature:setup:testGithubDebugUnitTest
)
assert_tasks_selected "beta parent runtime coverage" "$beta_selection" "${parent_runtime_tasks[@]}"
runtime_selection="$(
  env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
    python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
      --paths-file <(printf 'impact\nmodules/runtime/src/main/java/RuntimeSignals.kt\n')
)"
assert_tasks_selected "runtime change" "$runtime_selection" "${parent_runtime_tasks[@]}"

ui_update_variant_tasks=(
  :magisk-ui-kit:update:testGithubDebugUnitTest
  :magisk-ui-kit:update:testPlayDebugUnitTest
)
assert_tasks_selected "beta update channel coverage" "$beta_selection" "${ui_update_variant_tasks[@]}"
for changed_path in magisk-ui-kit magisk-ui-kit/update/src/main/kotlin/UpdateSource.kt; do
  ui_selection="$(
    env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
      python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
        --paths-file <(printf 'impact\n%s\n' "$changed_path")
  )"
  assert_tasks_selected "$changed_path" "$ui_selection" "${ui_update_variant_tasks[@]}"
done

echo "all Android test-task selector checks passed"
