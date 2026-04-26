#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
FASTLANE_META_DIR="$ROOT_DIR/fastlane/metadata/android"
RELEASE_REF_SCRIPT="$ROOT_DIR/scripts/release_ref.sh"

working_tree_dirty() {
  if ! git -C "$ROOT_DIR" diff --quiet || ! git -C "$ROOT_DIR" diff --cached --quiet; then
    return 0
  fi
  [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]
}

count_sarif_results() {
  local sarif_file="$1"
  if command -v jq >/dev/null 2>&1; then
    jq '[.runs[]?.results[]?] | length' "$sarif_file"
  else
    # Fallback: count ruleId occurrences when jq is unavailable.
    grep -o '"ruleId"' "$sarif_file" | wc -l | tr -d '[:space:]'
  fi
}

ensure_no_staged_changes_for_auto_commit() {
  local label="$1"
  if ! git -C "$ROOT_DIR" diff --cached --quiet; then
    echo "ERROR: staged changes detected; refusing to auto-commit $label." >&2
    echo "Hint: commit or unstage existing changes before running release_tag.sh." >&2
    exit 1
  fi
}

run_webui_checks() {
  if ! command -v pnpm >/dev/null 2>&1; then
    echo "ERROR: pnpm is required for WebUI checks but was not found in PATH." >&2
    exit 1
  fi

  echo "Running WebUI checks (install/lint/typecheck/build)..."
  (
    cd "$ROOT_DIR"
    pnpm -C webui install --frozen-lockfile
    pnpm -C webui lint
    pnpm -C webui typecheck
    pnpm -C webui build
  )

  echo "WebUI checks passed."
}

run_sync_readme_badges() {
  local sync_script="$ROOT_DIR/scripts/sync_readme_badges.sh"
  if [[ ! -f "$sync_script" ]]; then
    echo "ERROR: missing script: $sync_script" >&2
    exit 1
  fi

  echo "Running README badge sync..."
  (
    cd "$ROOT_DIR"
    bash "$sync_script"
  )
  echo "README badge sync completed."
}

run_sync_fastlane_metadata() {
  local sync_script="$ROOT_DIR/scripts/sync_fastlane_metadata.sh"
  if [[ ! -f "$sync_script" ]]; then
    echo "ERROR: missing script: $sync_script" >&2
    exit 1
  fi

  echo "Running fastlane metadata sync..."
  (
    cd "$ROOT_DIR"
    bash "$sync_script"
  )
  echo "Fastlane metadata sync completed."
}

auto_commit_fastlane_metadata() {
  local fastlane_status

  if [[ "$INITIAL_WORKTREE_DIRTY" -eq 1 ]]; then
    echo "Working tree was already dirty at startup; skipping fastlane metadata auto-commit."
    return
  fi

  ensure_no_staged_changes_for_auto_commit "fastlane metadata"

  fastlane_status="$(git -C "$ROOT_DIR" status --porcelain -- "$FASTLANE_META_DIR")"
  if [[ -z "$fastlane_status" ]]; then
    echo "Fastlane metadata is already clean; no auto-commit needed."
    return
  fi

  echo "Fastlane metadata updated; committing changes..."
  git -C "$ROOT_DIR" add "$FASTLANE_META_DIR"
  if git -C "$ROOT_DIR" diff --cached --quiet; then
    echo "WARN: no staged fastlane metadata changes after add." >&2
    return
  fi
  git -C "$ROOT_DIR" commit -m "chore(release): sync fastlane metadata"
}

run_pre_push_checks() {
  echo "Running pre-push CI command..."
  (
    cd "$ROOT_DIR"
    bash scripts/with_workspace_gradle_lock.sh --warning-mode all \
      verifyModuleBoundaries \
      verifyStructureBoundaries \
      verifyEmbeddedSubmodules \
      assembleGithubApi101Debug \
      testGithubApi101DebugUnitTest \
      :runtime:verifyNoComposeUiLeak \
      :app:koverVerifyGithubApi101Debug \
      :app:koverHtmlReportGithubApi101Debug \
      -PbuildSplits
  )

  echo "Running pre-push Detekt command..."
  (
    cd "$ROOT_DIR"
    bash scripts/with_workspace_gradle_lock.sh detekt --continue
  )

  local sarif_files=(
    "$ROOT_DIR/app/build/reports/detekt/detekt.sarif"
    "$ROOT_DIR/core/build/reports/detekt/detekt.sarif"
    "$ROOT_DIR/runtime/build/reports/detekt/detekt.sarif"
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

VERSION_CODE="$(extract_toml_value "versionCode" "$VERSION_FILE")"
if [[ -z "$VERSION_CODE" ]]; then
  echo "ERROR: failed to parse versionCode from $VERSION_FILE" >&2
  exit 2
fi

RELEASE_TARGET="${1:-all}"
TAG_NAME="$(bash "$RELEASE_REF_SCRIPT" tag-for-target "$VERSION_NAME" "$RELEASE_TARGET")"
REMOTE_NAME="${RELEASE_REMOTE:-origin}"

current_branch="$(git -C "$ROOT_DIR" branch --show-current)"
if [[ -z "$current_branch" ]]; then
  echo "ERROR: detached HEAD is not supported for release_tag.sh" >&2
  exit 1
fi

INITIAL_WORKTREE_DIRTY=0
if working_tree_dirty; then
  INITIAL_WORKTREE_DIRTY=1
fi

ensure_fastlane_changelogs_ready() {
  local locales=(en-US zh-CN)
  local missing_files=()
  local locale
  local changelog_file
  for locale in "${locales[@]}"; do
    changelog_file="$FASTLANE_META_DIR/$locale/changelogs/$VERSION_CODE.txt"
    if [[ ! -s "$changelog_file" ]]; then
      missing_files+=("$changelog_file")
    fi
  done

  if [[ "${#missing_files[@]}" -ne 0 ]]; then
    echo "ERROR: missing or empty Fastlane changelog(s) for versionCode=$VERSION_CODE:" >&2
    printf ' - %s\n' "${missing_files[@]}" >&2
    echo "Hint: run scripts/sync_fastlane_metadata.sh and commit generated files." >&2
    exit 1
  fi
}

run_sync_readme_badges
if [[ "$RELEASE_TARGET" == "all" || "$RELEASE_TARGET" == "mobile" ]]; then
  run_sync_fastlane_metadata
  auto_commit_fastlane_metadata
  ensure_fastlane_changelogs_ready
else
  echo "Skipping fastlane metadata sync for release target: $RELEASE_TARGET"
fi
run_webui_checks
"$ROOT_DIR/scripts/check_release_guard.sh" "$TAG_NAME"
run_pre_push_checks

if working_tree_dirty; then
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
git -C "$ROOT_DIR" push --force-with-lease "$REMOTE_NAME" "$current_branch"
git -C "$ROOT_DIR" push "$REMOTE_NAME" "$TAG_NAME"

echo "Created and pushed tag: $TAG_NAME (branch: $current_branch, remote: $REMOTE_NAME)"
