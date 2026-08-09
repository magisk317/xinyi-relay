#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
selector="$(cd "$script_dir/../../.." && pwd)/scripts/ci/select_android_test_tasks.sh"
tmp_dir="$(mktemp -d)"; trap 'rm -rf "$tmp_dir"' EXIT
assert_contains() { grep -Fx -- "$1" "$2" >/dev/null || { echo "missing $1" >&2; exit 1; }; }
assert_empty() { [[ ! -s "$1" ]] || { echo "expected empty output" >&2; exit 1; }; }
printf 'impact\nmodules/core/src/main.kt\n' > "$tmp_dir/source"
bash "$selector" /dev/null "$tmp_dir/source" > "$tmp_dir/out"
assert_contains ':app:testGithubNoE2eeDebugUnitTest' "$tmp_dir/out"; assert_contains ':app:testGithubWithE2eeDebugUnitTest' "$tmp_dir/out"
printf 'impact\nfrontend/webui/src/App.tsx\n' > "$tmp_dir/web"
bash "$selector" /dev/null "$tmp_dir/web" > "$tmp_dir/out"; assert_empty "$tmp_dir/out"
printf 'full\n' > "$tmp_dir/full"
bash "$selector" /dev/null "$tmp_dir/full" > "$tmp_dir/out"; assert_contains 'verifyStructureBoundaries' "$tmp_dir/out"
echo 'xinyi selector tests passed'
