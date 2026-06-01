#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "$1 is required" >&2
    exit 1
  fi
}

require_tool gh
require_tool jq
require_tool python3

export PYTHONDONTWRITEBYTECODE=1

DEPENDENCY_FORCE_CONFIGS="${DEPENDENCY_FORCE_CONFIGS:-githubDebugRuntimeClasspath,playDebugRuntimeClasspath,githubReleaseRuntimeClasspath,playReleaseRuntimeClasspath}"
DEPENDENCY_FORCE_HISTORICAL_ALERT_COOLDOWN_HOURS="${DEPENDENCY_FORCE_HISTORICAL_ALERT_COOLDOWN_HOURS:-168}"

TMP_DIR="$(mktemp -d)"
RESTORE_BUILD_FILE=0
trap 'if [[ "${RESTORE_BUILD_FILE}" == "1" && -f "${TMP_DIR}/build.gradle.kts.original" ]]; then cp "${TMP_DIR}/build.gradle.kts.original" build.gradle.kts; fi; rm -rf "${TMP_DIR}"' EXIT

ALERTS_JSON="${TMP_DIR}/dependabot-alerts.json"
ALERTS_NDJSON="${TMP_DIR}/dependabot-alerts.ndjson"
FORCED_JSON="${TMP_DIR}/forced.json"
NATURAL_JSON="${TMP_DIR}/natural.json"
REMOVABLE_JSON="${TMP_DIR}/removable.json"
SECURITY_NATURAL_JSON="${TMP_DIR}/security-natural.json"
CARGO_RESULTS_JSON="${TMP_DIR}/cargo-results.json"
RECONCILIATION_JSON="${TMP_DIR}/reconciliation.json"

echo "Fetching Dependabot alert history..."
REPO="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
: > "${ALERTS_NDJSON}"
ALERTS_DISABLED=0
for state in open fixed dismissed auto_dismissed; do
  if ! gh api \
    --paginate \
    -H "Accept: application/vnd.github+json" \
    "repos/${REPO}/dependabot/alerts?state=${state}&per_page=100" \
    > "${TMP_DIR}/dependabot-alerts-${state}.json" \
    2> "${TMP_DIR}/dependabot-alerts-${state}.err"; then
    if grep -qi "Dependabot alerts are disabled" "${TMP_DIR}/dependabot-alerts-${state}.err"; then
      echo "Dependabot alerts are disabled for this repository; continuing with an empty alert set."
      ALERTS_DISABLED=1
      break
    fi
    cat "${TMP_DIR}/dependabot-alerts-${state}.err" >&2
    exit 1
  fi
  jq -c '.[]' "${TMP_DIR}/dependabot-alerts-${state}.json" >> "${ALERTS_NDJSON}"
done
if [[ "${ALERTS_DISABLED}" == "1" ]]; then
  echo "[]" > "${ALERTS_JSON}"
else
  jq -s 'unique_by(.number) | sort_by(.number)' "${ALERTS_NDJSON}" > "${ALERTS_JSON}"
fi

echo "Reading current Gradle force rules..."
python3 scripts/manage_dependency_forces.py read-forces \
  --build-file build.gradle.kts \
  --toml-file gradle/libs.versions.toml \
  --output "${FORCED_JSON}"

cp build.gradle.kts "${TMP_DIR}/build.gradle.kts.original"
RESTORE_BUILD_FILE=1

echo "Resolving natural Gradle dependency versions..."
python3 scripts/manage_dependency_forces.py strip-force-lines --build-file build.gradle.kts
gradle_args=()
IFS=',' read -ra configs <<< "${DEPENDENCY_FORCE_CONFIGS}"
for config in "${configs[@]}"; do
  gradle_args+=(--config "${config}")
done
python3 scripts/manage_dependency_forces.py resolve-natural \
  --forced-json "${FORCED_JSON}" \
  --output "${NATURAL_JSON}" \
  --include-build-environment \
  "${gradle_args[@]}"

cp "${TMP_DIR}/build.gradle.kts.original" build.gradle.kts
RESTORE_BUILD_FILE=0

echo "Determining removable Gradle force rules..."
python3 scripts/manage_dependency_forces.py determine-removable \
  --forced-json "${FORCED_JSON}" \
  --natural-json "${NATURAL_JSON}" \
  --alerts-json "${ALERTS_JSON}" \
  --historical-alert-cooldown-hours "${DEPENDENCY_FORCE_HISTORICAL_ALERT_COOLDOWN_HOURS}" \
  --output "${REMOVABLE_JSON}"

echo "Resolving natural versions for open Maven security alerts..."
python3 scripts/manage_dependency_forces.py resolve-alert-natural \
  --alerts-json "${ALERTS_JSON}" \
  --output "${SECURITY_NATURAL_JSON}" \
  "${gradle_args[@]}"

echo "Applying Gradle security force updates..."
python3 scripts/manage_dependency_forces.py apply-updates \
  --build-file build.gradle.kts \
  --toml-file gradle/libs.versions.toml \
  --removable-json "${REMOVABLE_JSON}" \
  --alerts-json "${ALERTS_JSON}" \
  --natural-json "${SECURITY_NATURAL_JSON}"

if jq -e '.[] | select((.state == "open") and ((.dependency.package.ecosystem | ascii_downcase) == "rust"))' "${ALERTS_JSON}" >/dev/null; then
  require_tool cargo
  echo "Applying Cargo lockfile security updates..."
  python3 scripts/manage_dependabot_alerts.py apply-cargo-updates \
    --alerts-json "${ALERTS_JSON}" \
    --workspace-root . \
    --output "${CARGO_RESULTS_JSON}"
else
  echo '{"updated":[],"unresolved":[],"skipped":[]}' > "${CARGO_RESULTS_JSON}"
fi

echo "Reconciling open Dependabot alerts..."
python3 scripts/manage_dependabot_alerts.py reconcile \
  --alerts-json "${ALERTS_JSON}" \
  --workspace-root . \
  --build-file build.gradle.kts \
  --toml-file gradle/libs.versions.toml \
  --output "${RECONCILIATION_JSON}"

echo "Security janitor finished."
find scripts -type d -name __pycache__ -prune -exec rm -rf {} +
