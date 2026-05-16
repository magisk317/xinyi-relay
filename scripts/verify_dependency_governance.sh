#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_BUILD="$ROOT_DIR/build.gradle.kts"
ROOT_SETTINGS="$ROOT_DIR/settings.gradle.kts"
BUILD_LOGIC_BUILD="$ROOT_DIR/build-logic/build.gradle.kts"
BUILD_LOGIC_SETTINGS="$ROOT_DIR/build-logic/settings.gradle.kts"
GOVERNANCE_PLUGIN="$ROOT_DIR/build-logic/src/main/kotlin/RelayDependencyGovernance.kt"

fail() {
  echo "dependency governance violation: $*" >&2
  exit 1
}

rg -q 'id\("relay\.dependency-governance"\)' "$ROOT_BUILD" \
  || fail "root build must apply relay.dependency-governance"

for file in "$ROOT_SETTINGS" "$BUILD_LOGIC_SETTINGS"; do
  rg -q 'RepositoriesMode\.FAIL_ON_PROJECT_REPOS' "$file" \
    || fail "$(realpath --relative-to="$ROOT_DIR" "$file") must reject project-level repositories"
done

rg -q 'includeGroupByRegex\("com\\\\\.github\\\\\.\.\*"\)' "$ROOT_SETTINGS" \
  || fail "JitPack must be filtered to com.github.* groups"
rg -q 'snapshotsOnly\(\)' "$ROOT_SETTINGS" \
  || fail "Sonatype snapshot repository must be snapshots-only"

if rg -n 'BEGIN AUTO FORCED DEPENDENCIES|resolutionStrategy\s*\{|maven\("https://jitpack\.io"\)|maven\("https://s01\.oss\.sonatype\.org' "$ROOT_BUILD"; then
  fail "root build must not carry inline force rules or project repositories"
fi

for alias in gson guava netty-codec netty-runtime commons-lang3 httpclient jose4j bouncycastle jdom2; do
  rg -q "RelayForcedDependency\\(.*\"$alias\"" "$GOVERNANCE_PLUGIN" \
    || fail "governance plugin must declare $alias as a catalog-backed force"
done

rg -q 'catalogVersionOrNull\(forcedDependency\.versionAlias\)' "$GOVERNANCE_PLUGIN" \
  || fail "governance plugin must resolve forced versions through the catalog alias"

if rg -n '"3\.18\.0"|"4\.5\.13"' "$BUILD_LOGIC_BUILD" "$GOVERNANCE_PLUGIN" "$ROOT_BUILD"; then
  fail "stale forced dependency versions must not reappear"
fi

rg -q 'forcedDependency\("org\.apache\.commons", "commons-lang3", "commons-lang3", "3\.20\.0"\)' "$BUILD_LOGIC_BUILD" \
  || fail "build-logic commons-lang3 force must be catalog-backed"
rg -q 'forcedDependency\("org\.apache\.httpcomponents", "httpclient", "httpclient", "4\.5\.14"\)' "$BUILD_LOGIC_BUILD" \
  || fail "build-logic httpclient force must be catalog-backed"
