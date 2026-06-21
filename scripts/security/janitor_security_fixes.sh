#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

ALERTS_JSON="/tmp/dependabot-alerts.json"
REMOVABLE_JSON="/tmp/removable.json"

echo "Fetching open Dependabot alerts..."
if ! gh api --paginate "/repos/{owner}/{repo}/dependabot/alerts?state=open" 2>/dev/null \
  | jq -s 'flatten' > "$ALERTS_JSON"; then
  echo '[]' > "$ALERTS_JSON"
fi
echo '[]' > "$REMOVABLE_JSON"

echo "Applying security force updates for Maven..."
python3 scripts/security/manage_dependency_forces.py apply-updates \
  --build-file build.gradle.kts \
  --toml-file gradle/libs.versions.toml \
  --removable-json "$REMOVABLE_JSON" \
  --alerts-json "$ALERTS_JSON"

echo "Applying security updates for NPM/PNPM..."
for dir in frontend/webui frontend/desktop; do
  if [ -f "$dir/package.json" ]; then
    echo "Running pnpm audit --fix in $dir..."
    python3 scripts/security/clear_pnpm_overrides.py "$dir/package.json"
    (cd "$dir" && pnpm install --lockfile-only)
    (cd "$dir" && pnpm audit --fix || true)
    (cd "$dir" && pnpm install --lockfile-only)
  fi
done

echo "Applying security updates for Rust/Cargo..."
if [ -d "frontend/desktop/src-tauri" ]; then
  if ! command -v cargo-audit &> /dev/null; then
      echo "Installing cargo-audit..."
      cargo install cargo-audit --features=fix
  fi
  (cd frontend/desktop/src-tauri && cargo audit fix || true)
  (cd frontend/desktop/src-tauri && cargo update)
fi

if git diff --quiet -- build.gradle.kts gradle/libs.versions.toml frontend/webui/package.json frontend/desktop/package.json frontend/webui/pnpm-lock.yaml frontend/desktop/pnpm-lock.yaml frontend/desktop/src-tauri/Cargo.toml frontend/desktop/src-tauri/Cargo.lock; then
  echo "No security changes generated. Skipping build validation."
  exit 0
fi

echo "Security entries changed. Running build/test validation before opening PR..."
./gradlew --no-daemon \
  --warning-mode all \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  -PbuildSplits \
  -Pkotlin.incremental=false

echo "Janitor validation passed."
