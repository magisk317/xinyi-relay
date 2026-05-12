#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

REPO="${GITHUB_REPOSITORY:-}"
if [[ -z "${REPO}" ]]; then
  REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

ALERTS_JSON="${TMP_DIR}/dependabot-alerts.json"
SUMMARY_JSON="${TMP_DIR}/dependabot-reconciliation.json"

gh api \
  --paginate \
  -H "Accept: application/vnd.github+json" \
  "/repos/${REPO}/dependabot/alerts?state=open&per_page=100" \
  | jq -s '[.[][]]' \
  > "${ALERTS_JSON}"

python3 scripts/manage_dependabot_alerts.py reconcile \
  --alerts-json "${ALERTS_JSON}" \
  --workspace-root . \
  --build-file build.gradle.kts \
  --toml-file gradle/libs.versions.toml \
  --output "${SUMMARY_JSON}"
