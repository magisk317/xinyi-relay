#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"

count_sarif_results() {
  local sarif_file="$1"
  if command -v jq >/dev/null 2>&1; then
    jq '[.runs[]?.results[]?] | length' "$sarif_file"
  else
    # Fallback: count ruleId occurrences when jq is unavailable.
    grep -o '"ruleId"' "$sarif_file" | wc -l | tr -d '[:space:]'
  fi
}

run_pre_push_checks() {
  echo "Running pre-push CI command..."
  (
    cd "$ROOT_DIR"
    chmod +x gradlew
    ./gradlew --warning-mode all \
      assembleGithubDebug \
      testGithubDebugUnitTest \
      :app:koverVerifyGithubDebug \
      :app:koverHtmlReportGithubDebug \
      -PbuildSplits
  )

  echo "Running pre-push Detekt command..."
  (
    cd "$ROOT_DIR"
    ./gradlew detekt --continue
  )

  local sarif_files=(
    "$ROOT_DIR/app/build/reports/detekt/detekt.sarif"
    "$ROOT_DIR/core/build/reports/detekt/detekt.sarif"
    "$ROOT_DIR/storage/build/reports/detekt/detekt.sarif"
  )
  local found_report=0
  local total_findings=0
  local findings=0
  local sarif_file
  for sarif_file in "${sarif_files[@]}"; do
    if [[ -f "$sarif_file" ]]; then
      found_report=1
      findings="$(count_sarif_results "$sarif_file")"
      findings="${findings:-0}"
      total_findings=$((total_findings + findings))
      echo "Detekt findings: $findings ($sarif_file)"
    fi
  done

  if [[ "$found_report" -eq 0 ]]; then
    echo "ERROR: no Detekt SARIF reports found after detekt run." >&2
    exit 1
  fi

  if [[ "$total_findings" -ne 0 ]]; then
    echo "ERROR: Detekt findings must be 0 before push. total_findings=$total_findings" >&2
    exit 1
  fi

  echo "Pre-push checks passed: CI success and Detekt findings=0"
}

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

VERSION_NAME="$(extract_toml_value "versionName" "$VERSION_FILE")"
if [[ -z "$VERSION_NAME" ]]; then
  echo "ERROR: failed to parse versionName from $VERSION_FILE" >&2
  exit 2
fi

TAG_NAME="v$VERSION_NAME"
REMOTE_NAME="${RELEASE_REMOTE:-origin}"

current_branch="$(git -C "$ROOT_DIR" branch --show-current)"
if [[ -z "$current_branch" ]]; then
  echo "ERROR: detached HEAD is not supported for release_tag.sh" >&2
  exit 1
fi

detect_repo_slug() {
  local remote_url
  remote_url="$(git -C "$ROOT_DIR" remote get-url "$REMOTE_NAME" 2>/dev/null || true)"
  if [[ -z "$remote_url" ]]; then
    return 1
  fi
  if [[ "$remote_url" =~ ^https?://github.com/([^/]+/[^/.]+)(\.git)?$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  if [[ "$remote_url" =~ ^git@github.com:([^/]+/[^/.]+)(\.git)?$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  if [[ "$remote_url" =~ ^ssh://git@github.com/([^/]+/[^/.]+)(\.git)?$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

wait_for_workflow_completion() {
  local repo_slug="$1"
  local workflow_file="$2"
  local commit_sha="$3"
  local required="$4"
  local timeout_sec="${WORKFLOW_WAIT_TIMEOUT_SEC:-1800}"
  local poll_sec="${WORKFLOW_POLL_INTERVAL_SEC:-15}"
  local elapsed=0
  local run_id=""
  local status=""
  local conclusion=""

  while (( elapsed < timeout_sec )); do
    run_id="$(gh run list \
      --repo "$repo_slug" \
      --workflow "$workflow_file" \
      --event push \
      --commit "$commit_sha" \
      --json databaseId \
      --jq '.[0].databaseId // empty' 2>/dev/null || true)"

    if [[ -n "$run_id" ]]; then
      break
    fi
    sleep "$poll_sec"
    elapsed=$((elapsed + poll_sec))
  done

  if [[ -z "$run_id" ]]; then
    if [[ "$required" == "required" ]]; then
      echo "ERROR: workflow run not found in time ($workflow_file, commit=$commit_sha)" >&2
      return 1
    fi
    echo "WARN: optional workflow run not found ($workflow_file, commit=$commit_sha)"
    return 0
  fi

  elapsed=0
  while (( elapsed < timeout_sec )); do
    status="$(gh run view "$run_id" --repo "$repo_slug" --json status --jq '.status' 2>/dev/null || true)"
    conclusion="$(gh run view "$run_id" --repo "$repo_slug" --json conclusion --jq '.conclusion // empty' 2>/dev/null || true)"

    if [[ "$status" == "completed" ]]; then
      if [[ "$conclusion" == "success" ]]; then
        echo "PASS: workflow succeeded ($workflow_file, run_id=$run_id)"
        return 0
      fi
      if [[ "$required" == "required" ]]; then
        echo "ERROR: required workflow failed ($workflow_file, run_id=$run_id, conclusion=$conclusion)" >&2
        return 1
      fi
      echo "WARN: optional workflow concluded with $conclusion ($workflow_file, run_id=$run_id)"
      return 0
    fi

    sleep "$poll_sec"
    elapsed=$((elapsed + poll_sec))
  done

  if [[ "$required" == "required" ]]; then
    echo "ERROR: required workflow did not complete in time ($workflow_file, run_id=$run_id)" >&2
    return 1
  fi
  echo "WARN: optional workflow did not complete in time ($workflow_file, run_id=$run_id)"
  return 0
}

check_dual_release_workflows() {
  if ! command -v gh >/dev/null 2>&1; then
    echo "WARN: gh not found; skip remote workflow status checks."
    return 0
  fi
  local repo_slug
  repo_slug="$(detect_repo_slug)" || {
    echo "WARN: unable to detect GitHub repo slug from remote '$REMOTE_NAME'; skip workflow checks."
    return 0
  }
  local commit_sha
  commit_sha="$(git -C "$ROOT_DIR" rev-parse HEAD)"

  echo "Checking required workflow: build-apk.yml (newapi)"
  wait_for_workflow_completion "$repo_slug" "build-apk.yml" "$commit_sha" "required"

  echo "Checking optional workflow: build-apk-legacy.yml (legacy transition)"
  wait_for_workflow_completion "$repo_slug" "build-apk-legacy.yml" "$commit_sha" "optional"
}

"$ROOT_DIR/scripts/check_release_guard.sh" "$TAG_NAME"
run_pre_push_checks

if ! git -C "$ROOT_DIR" diff --quiet || ! git -C "$ROOT_DIR" diff --cached --quiet; then
  echo "ERROR: working tree is not clean. Commit/stash changes before tagging." >&2
  exit 1
fi

delete_local_tag_if_exists() {
  if git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/$TAG_NAME" >/dev/null; then
    local old_ref
    old_ref="$(git -C "$ROOT_DIR" rev-list -n 1 "$TAG_NAME" 2>/dev/null || true)"
    echo "WARN: local tag exists, deleting before retag: $TAG_NAME (${old_ref:-unknown})"
    git -C "$ROOT_DIR" tag -d "$TAG_NAME" >/dev/null
  fi
}

delete_remote_tag_if_exists() {
  local remote_output
  local remote_ref
  if ! remote_output="$(git -C "$ROOT_DIR" ls-remote --tags "$REMOTE_NAME" "refs/tags/$TAG_NAME")"; then
    echo "ERROR: failed to query remote tags from $REMOTE_NAME" >&2
    exit 1
  fi
  remote_ref="$(printf '%s\n' "$remote_output" | awk '{print $1}' | head -n1)"
  if [[ -n "$remote_ref" ]]; then
    echo "WARN: remote tag exists, deleting before retag: $TAG_NAME ($remote_ref)"
    if ! git -C "$ROOT_DIR" push "$REMOTE_NAME" ":refs/tags/$TAG_NAME"; then
      echo "ERROR: failed to delete remote tag $TAG_NAME from $REMOTE_NAME" >&2
      exit 1
    fi
  fi
}

delete_local_tag_if_exists
delete_remote_tag_if_exists

git -C "$ROOT_DIR" tag -a "$TAG_NAME" -m "$TAG_NAME"
git -C "$ROOT_DIR" push "$REMOTE_NAME" "$current_branch"
git -C "$ROOT_DIR" push "$REMOTE_NAME" "$TAG_NAME"
check_dual_release_workflows

echo "Created and pushed tag: $TAG_NAME (branch: $current_branch, remote: $REMOTE_NAME)"
