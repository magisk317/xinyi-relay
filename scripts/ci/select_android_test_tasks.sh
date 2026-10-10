#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
toolkit_dir="${1:-${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}}"
paths_file="${2:-}"
mobile_test_tasks=(
  :mobile:feature:common:testGithubDebugUnitTest
  :mobile:feature:forward:testGithubDebugUnitTest
  :mobile:feature:overview:testGithubDebugUnitTest
  :mobile:feature:appconfig:testGithubDebugUnitTest
  :mobile:feature:record:testGithubDebugUnitTest
  :mobile:feature:settings:testGithubDebugUnitTest
  :mobile:feature:sender:testGithubDebugUnitTest
  :mobile:feature:verification:testGithubDebugUnitTest
  :mobile:ui:testGithubDebugUnitTest
)
core_test_tasks=(
  :smscode-core:contract:test
  :smscode-core:domain:test
  :smscode-core:verification:detekt
  :smscode-core:verification:test
  :smscode-core:rule:test
)
kit_test_tasks=(
  :magisk-ui-kit:testAndroidHostTest
  :magisk-ui-kit:jvmTest
  :magisk-ui-kit:billing:testDebugUnitTest
)
kmp_library_test_tasks=(
  :policy:jvmTest
  :relay:contract:jvmTest
  :relay:engine:jvmTest
  :relay:matrix-e2ee:jvmTest
  :relay:net:jvmTest
  :relay:sender:api:jvmTest
)
android_lib_test_tasks=(
  :relay:sender:testGithubDebugUnitTest
)
desktop_test_tasks=(
  :desktop:compileKotlinJvm
  :desktop:jvmTest
  :desktop:core:jvmTest
  :desktop:data:jvmTest
  verifyStructureBoundaries
)
full_tasks=(
  :app:testGithubDebugUnitTest
  :app:koverHtmlReportGithubDebug
  "${mobile_test_tasks[@]}"
  "${core_test_tasks[@]}"
  "${kit_test_tasks[@]}"
  "${kmp_library_test_tasks[@]}"
  "${android_lib_test_tasks[@]}"
  "${desktop_test_tasks[@]}"
)
if [[ -n "${CI_COMMIT_TAG:-}" || "${GITHUB_REF_TYPE:-}" == tag || "${CI_COMMIT_BRANCH:-}" == beta || "${CI_COMMIT_BRANCH:-}" == master || "${GITHUB_REF_NAME:-}" == beta || "${GITHUB_REF_NAME:-}" == master ]]; then printf '%s\n' "${full_tasks[@]}"; exit 0; fi
if [[ -z "$paths_file" ]]; then paths_file="$(mktemp)"; trap 'rm -f "$paths_file"' EXIT; bash "$toolkit_dir/ci/changed_paths.sh" "$paths_file"; fi
if [[ "$(sed -n '1p' "$paths_file")" == full ]]; then printf '%s\n' "${full_tasks[@]}"; exit 0; fi
declare -A selected=()
select_task() { selected["$1"]=1; }
select_app_bucket() {
  select_task :app:testGithubDebugUnitTest
  select_task :app:koverHtmlReportGithubDebug
  select_task verifyStructureBoundaries
  for task in "${mobile_test_tasks[@]}"; do select_task "$task"; done
}
while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  case "$path" in
    impact|.gitlab-ci.yml|.github/workflows/*|docs/*|README*|LICENSE*|CHANGELOG*|frontend/*|backend/*) continue ;;
    build.gradle*|settings.gradle*|gradle.properties|gradle/*|build-logic/*|.gitmodules|scripts/*|.magisk-ci-toolkit/*) printf '%s\n' "${full_tasks[@]}"; exit 0 ;;
    smscode/*)
      for task in "${core_test_tasks[@]}"; do select_task "$task"; done ;;
    magisk-ui-kit/*)
      for task in "${kit_test_tasks[@]}"; do select_task "$task"; done ;;
    desktop/*|modules/desktop/*)
      for task in "${desktop_test_tasks[@]}"; do select_task "$task"; done ;;
    modules/policy/*|modules/relay/contract/*|modules/relay/engine/*|modules/relay/matrix-e2ee/*|modules/relay/net/*|modules/relay/sender/api/*)
      select_app_bucket
      for task in "${kmp_library_test_tasks[@]}"; do select_task "$task"; done ;;
    modules/relay/sender/*)
      select_app_bucket
      for task in "${android_lib_test_tasks[@]}"; do select_task "$task"; done ;;
    app/*|modules/*|mobile/*|features/*|magisk-xposed-kit/*)
      select_app_bucket ;;
    *) printf '%s\n' "${full_tasks[@]}"; exit 0 ;;
  esac
done < <(sed -n '2,$p' "$paths_file")
if ((${#selected[@]} > 0)); then printf '%s\n' "${!selected[@]}" | sort; fi
