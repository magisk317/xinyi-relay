#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
toolkit_dir="${1:-${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}}"
paths_file="${2:-}"
mobile_test_tasks=(
  :mobile:feature:common:testGithubNoE2eeDebugUnitTest
  :mobile:feature:forward:testGithubNoE2eeDebugUnitTest
  :mobile:feature:overview:testGithubNoE2eeDebugUnitTest
  :mobile:feature:appconfig:testGithubNoE2eeDebugUnitTest
  :mobile:feature:record:testGithubNoE2eeDebugUnitTest
  :mobile:feature:settings:testGithubNoE2eeDebugUnitTest
  :mobile:feature:sender:testGithubNoE2eeDebugUnitTest
  :mobile:feature:verification:testGithubNoE2eeDebugUnitTest
  :mobile:ui:testGithubNoE2eeDebugUnitTest
)
full_tasks=(
  :app:testGithubNoE2eeDebugUnitTest
  :app:koverHtmlReportGithubNoE2eeDebug
  verifyStructureBoundaries
  :app:testGithubWithE2eeDebugUnitTest
  :app:koverHtmlReportGithubWithE2eeDebug
  "${mobile_test_tasks[@]}"
)
if [[ -n "${CI_COMMIT_TAG:-}" || "${GITHUB_REF_TYPE:-}" == tag || "${CI_COMMIT_BRANCH:-}" == beta || "${CI_COMMIT_BRANCH:-}" == master || "${GITHUB_REF_NAME:-}" == beta || "${GITHUB_REF_NAME:-}" == master ]]; then printf '%s\n' "${full_tasks[@]}"; exit 0; fi
if [[ -z "$paths_file" ]]; then paths_file="$(mktemp)"; trap 'rm -f "$paths_file"' EXIT; bash "$toolkit_dir/ci/changed_paths.sh" "$paths_file"; fi
if [[ "$(sed -n '1p' "$paths_file")" == full ]]; then printf '%s\n' "${full_tasks[@]}"; exit 0; fi
declare -A selected=()
select_task() { selected["$1"]=1; }
while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  case "$path" in
    impact|.gitlab-ci.yml|.github/workflows/*|docs/*|README*|LICENSE*|CHANGELOG*|frontend/*|backend/*) continue ;;
    build.gradle*|settings.gradle*|gradle.properties|gradle/*|build-logic/*|.gitmodules|scripts/*|.magisk-ci-toolkit/*) printf '%s\n' "${full_tasks[@]}"; exit 0 ;;
    app/*|modules/*|mobile/*|features/*|smscode/*|magisk-ui-kit/*|magisk-xposed-kit/*)
      select_task :app:testGithubNoE2eeDebugUnitTest; select_task :app:koverHtmlReportGithubNoE2eeDebug
      select_task verifyStructureBoundaries; select_task :app:testGithubWithE2eeDebugUnitTest
      select_task :app:koverHtmlReportGithubWithE2eeDebug
      for task in "${mobile_test_tasks[@]}"; do select_task "$task"; done ;;
    *) printf '%s\n' "${full_tasks[@]}"; exit 0 ;;
  esac
done < <(sed -n '2,$p' "$paths_file")
if ((${#selected[@]} > 0)); then printf '%s\n' "${!selected[@]}" | sort; fi
