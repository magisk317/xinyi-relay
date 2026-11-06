#!/usr/bin/env bash
# Regression test for scripts/ci/rustls_pv_metadata.sh and its wiring into
# scripts/ci/rustls_pv_publish.sh.
#
# What this pins down:
#   1. the document shape Gradle's maven-publish uploads, byte for byte apart
#      from the timestamp, because Renovate's maven datasource and Gradle
#      dynamic versions both parse exactly that structure;
#   2. numeric ordering (0.7.1 < 0.9.0 < 0.10.0) and <latest>/<release>
#      pointing at the newest entry, not the newest by string;
#   3. that the current version is always in the listing even when the
#      registry did not report it, and duplicates collapse;
#   4. that a malformed version or a missing argument fails loudly instead of
#      publishing a listing that hides artifacts;
#   5. that the publish script really calls the generator and the backfill
#      path -- a listing nobody uploads fixes nothing -- and that a dry run
#      still writes nothing to the registry.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
generator="$root_dir/scripts/ci/rustls_pv_metadata.sh"
publisher="$root_dir/scripts/ci/rustls_pv_publish.sh"
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

expect_fail() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "FAIL - $label (command succeeded, expected a non-zero exit)"
    failures=$((failures + 1))
  else
    echo "ok   - $label"
  fi
}

mask_stamp() {
  sed -E 's#^    <lastUpdated>[0-9]{14}</lastUpdated>$#    <lastUpdated>MASKED</lastUpdated>#' "$1"
}

has_stamp() {
  grep -qE '^    <lastUpdated>[0-9]{14}</lastUpdated>$' "$1"
}

[[ -f "$generator" ]] || { echo "ERROR: $generator is missing" >&2; exit 1; }
[[ -x "$generator" ]] || { echo "ERROR: $generator is not executable" >&2; exit 1; }

# --- 1. the Gradle-generated shape ------------------------------------------
cat > "$tmp_dir/fixture.xml" <<'FIXTURE'
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>rustls</groupId>
  <artifactId>rustls-platform-verifier</artifactId>
  <versioning>
    <latest>0.7.1</latest>
    <release>0.7.1</release>
    <versions>
      <version>0.7.0</version>
      <version>0.7.1</version>
    </versions>
    <lastUpdated>MASKED</lastUpdated>
  </versioning>
</metadata>
FIXTURE

bash "$generator" rustls rustls-platform-verifier 0.7.1 0.7.0 > "$tmp_dir/shape.xml"
check "lastUpdated is a 14-digit UTC stamp" has_stamp "$tmp_dir/shape.xml"
check "document matches the maven-publish shape" \
  diff -u "$tmp_dir/fixture.xml" <(mask_stamp "$tmp_dir/shape.xml")

# --- 2. ordering and latest -------------------------------------------------
bash "$generator" rustls rustls-platform-verifier 0.7.1 0.10.0 0.9.0 > "$tmp_dir/order.xml"
check "versions sort numerically, not lexicographically" diff -u - \
  <(grep -oE '<version>[^<]+' "$tmp_dir/order.xml") <<'EXPECT'
<version>0.7.1
<version>0.9.0
<version>0.10.0
EXPECT
check "latest points at 0.10.0" grep -q '<latest>0.10.0</latest>' "$tmp_dir/order.xml"
check "release points at 0.10.0" grep -q '<release>0.10.0</release>' "$tmp_dir/order.xml"
check "document parses as XML" python3 -c '
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
raise SystemExit(0 if root.tag == "metadata" else 1)' "$tmp_dir/order.xml"

# --- 3. current version is always listed, duplicates collapse --------------
bash "$generator" rustls rustls-platform-verifier 0.8.0 0.8.0 0.7.1 0.8.0 > "$tmp_dir/dedupe.xml"
check "duplicate versions collapse" \
  test "$(grep -c '<version>0.8.0</version>' "$tmp_dir/dedupe.xml")" -eq 1
check "a version the registry did not report still lands in the listing" \
  grep -q '<version>0.8.0</version>' "$tmp_dir/dedupe.xml"

bash "$generator" rustls rustls-platform-verifier 1.0.0 > "$tmp_dir/single.xml"
check "no known versions yields a one-entry listing" \
  grep -q '<version>1.0.0</version>' "$tmp_dir/single.xml"

# --- 4. fail loudly ---------------------------------------------------------
expect_fail "rejects an empty groupId" bash "$generator" "" rustls-platform-verifier 0.7.1
expect_fail "rejects a current version carrying markup" \
  bash "$generator" rustls rustls-platform-verifier '0.7.1<script>'
expect_fail "rejects a known version carrying markup" \
  bash "$generator" rustls rustls-platform-verifier 0.7.1 '0.8 <bad>'
expect_fail "rejects a missing current version" bash "$generator" rustls rustls-platform-verifier
expect_fail "rejects a blank current version" bash "$generator" rustls rustls-platform-verifier ''

# --- 5. the publish script is wired, and dry runs stay clean ---------------
check "publish script resolves the generator path" \
  grep -q 'METADATA_SCRIPT=' "$publisher"
check "publish script uploads the listing" \
  grep -qF 'put_asset "$METADATA_ENDPOINT"' "$publisher"
check "publish script verifies the listing after upload" \
  grep -qF 'does not list $VERSION after upload' "$publisher"
check "the already-published path backfills a missing listing" \
  grep -qF 'artifact is published but not listed' "$publisher"

dry_run_block="$(sed -n '/if \[\[ "\$DRY_RUN" == "1" \]\]; then/,/^fi$/p' "$publisher")"
[[ -n "$dry_run_block" ]] || { echo "ERROR: dry-run block not found in $publisher" >&2; exit 1; }
if printf '%s' "$dry_run_block" | grep -qE 'put_asset|publish_metadata'; then
  echo "FAIL - dry run performs no registry writes"
  failures=$((failures + 1))
else
  echo "ok   - dry run performs no registry writes"
fi

if (( failures > 0 )); then
  echo "ERROR: $failures check(s) failed" >&2
  exit 1
fi
echo "all rustls_pv_metadata checks passed"
