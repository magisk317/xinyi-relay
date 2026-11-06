#!/usr/bin/env bash
# Guard for the rustls-platform-verifier publish ordering in .gitlab-ci.yml.
#
# The invariant: the catalog line `rustls:rustls-platform-verifier` is the only
# thing that decides which AAR version exists, so the job that publishes it has
# to finish before any job that resolves the app classpath. The four build jobs
# share stage build with it, so GitLab would start them in parallel; the test
# anchor and detekt declare explicit needs, which drops stage ordering
# altogether. Either way the ordering has to be written down, and a need nobody
# declared is invisible until a build fails with "Could not find
# rustls:rustls-platform-verifier:<new version>".
#
# It also pins the two rule changes that make an automated bump mergeable: the
# publish job runs on renovate branches and it no longer forces a dry run, so a
# merge request resolves against an artifact that its own pipeline published.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
ci_file="$root_dir/.gitlab-ci.yml"

# Jobs that resolve the app classpath: the four that build APK/AAB, the shared
# Android-test anchor its children extend, and detekt (compile classpath).
app_jobs=(
  android:build
  android:release:apk
  android:release:aab
  android:ci:aab
  .xinyi_android_test
  android:detekt
)
publish_job="rustls-platform-verifier:publish"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

failures=0
check() {
  local label="$1"
  shift
  if "$@"; then
    echo "ok   - $label"
  else
    echo "FAIL - $label"
    failures=$((failures + 1))
  fi
}

# job_block <name> -- print the YAML lines of one top-level job.
job_block() {
  awk -v name="$1:" '
    $0 == name { inside = 1; print; next }
    inside && /^[^[:space:]#]/ { inside = 0 }
    inside { print }
  ' "$ci_file"
}

[[ -f "$ci_file" ]] || { echo "ERROR: $ci_file not found" >&2; exit 1; }

job_block "$publish_job" > "$tmp_dir/publish.yml"
[[ -s "$tmp_dir/publish.yml" ]] || { echo "ERROR: job $publish_job is gone from $ci_file" >&2; exit 1; }

# 1. the guard itself would notice a vanished job list.
count=0
for job in "${app_jobs[@]}"; do
  block="$(job_block "$job")"
  [[ -n "$block" ]] || { echo "FAIL - $job is gone from $ci_file; the list is stale" >&2; failures=$((failures + 1)); continue; }
  count=$((count + 1))
done
if (( count == 0 )); then
  echo "ERROR: no app job matched; this guard is checking nothing" >&2
  exit 1
fi
echo "ok   - all ${#app_jobs[@]} app jobs still present ($count found)"

# 2. every app job waits for the publish job, without demanding its artifacts.
needs_present() {
  awk '
    index($0, "job: rustls-platform-verifier:publish") { found = 1; window = 3; next }
    found && window-- > 0 {
      if ($0 ~ /optional: true/) opt = 1
      if ($0 ~ /artifacts: false/) art = 1
    }
    END { exit !(found && opt && art) }
  ' "$1"
}

for job in "${app_jobs[@]}"; do
  job_block "$job" > "$tmp_dir/$job.yml"
  check "$job needs $publish_job (optional, no artifacts)" needs_present "$tmp_dir/$job.yml"
done

# 3. the publish job keeps running on every pipeline that can change the catalog.
check "publish job still triggers on catalog, script, patch and CI changes" \
  awk '/^  rules:/{r=1} r && /gradle\/libs\.versions\.toml/{a=1} r && /scripts\/ci\/rustls_pv_publish\.sh/{b=1} r && /scripts\/ci\/rustls_pv_toolchain\.patch/{c=1} r && /\.gitlab-ci\.yml/{d=1} END{exit ! (a && b && c && d)}' \
  "$tmp_dir/publish.yml"
check "publish job no longer excludes renovate branches" \
  bash -c '! grep -q "renovate" "$1"' _ "$tmp_dir/publish.yml"
check "publish job no longer forces a dry run" \
  bash -c '! grep -q "RUSTLS_PV_DRY_RUN" "$1"' _ "$tmp_dir/publish.yml"
check "publish job runs on schedule" \
  grep -q 'CI_PIPELINE_SOURCE == "schedule"' "$tmp_dir/publish.yml"
check "publish job stays in stage build" \
  grep -q '^  stage: build$' "$tmp_dir/publish.yml"

if (( failures > 0 )); then
  echo "ERROR: $failures check(s) failed" >&2
  exit 1
fi
echo "all rustls_pv CI wiring checks passed"
