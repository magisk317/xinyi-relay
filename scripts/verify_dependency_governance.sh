#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/regex_helpers.sh"
ROOT_BUILD="$ROOT_DIR/build.gradle.kts"
ROOT_SETTINGS="$ROOT_DIR/settings.gradle.kts"
BUILD_LOGIC_SETTINGS="$ROOT_DIR/build-logic/settings.gradle.kts"
LEGACY_GOVERNANCE_SCRIPT="$ROOT_DIR/build-logic/src/main/kotlin/relay.dependency-governance.gradle.kts"
LEGACY_GOVERNANCE_SOURCE="$ROOT_DIR/build-logic/src/main/kotlin/RelayDependencyGovernance.kt"

fail() {
  echo "dependency governance violation: $*" >&2
  exit 1
}

for legacy_file in "$LEGACY_GOVERNANCE_SCRIPT" "$LEGACY_GOVERNANCE_SOURCE"; do
  [[ ! -e "$legacy_file" ]] \
    || fail "$(realpath --relative-to="$ROOT_DIR" "$legacy_file") must be removed after localizing dependency governance"
done

if regex_quiet 'id\("relay\.dependency-governance"\)' "$ROOT_BUILD"; then
  fail "root build must not apply relay.dependency-governance"
fi

for file in "$ROOT_SETTINGS" "$BUILD_LOGIC_SETTINGS"; do
  regex_quiet 'RepositoriesMode\.FAIL_ON_PROJECT_REPOS' "$file" \
    || fail "$(realpath --relative-to="$ROOT_DIR" "$file") must reject project-level repositories"
done

regex_quiet 'includeGroupByRegex\("com\\\\\.github\\\\\.\.\*"\)' "$ROOT_SETTINGS" \
  || fail "JitPack must be filtered to com.github.* groups"
regex_quiet 'snapshotsOnly\(\)' "$ROOT_SETTINGS" \
  || fail "Sonatype snapshot repository must be snapshots-only"

if regex_lines 'maven\("https://jitpack\.io"\)|maven\("https://s01\.oss\.sonatype\.org' "$ROOT_BUILD"; then
  fail "root build must not carry project repositories"
fi

managed_begin_count="$(regex_matches 'BEGIN AUTO FORCED DEPENDENCIES \(managed by workflow\)' "$ROOT_BUILD" | wc -l | tr -d ' ')"
[[ "$managed_begin_count" == "2" ]] \
  || fail "root build must keep managed force blocks in both buildscript and allprojects"

managed_end_count="$(regex_matches 'END AUTO FORCED DEPENDENCIES \(managed by workflow\)' "$ROOT_BUILD" | wc -l | tr -d ' ')"
[[ "$managed_end_count" == "2" ]] \
  || fail "root build must keep matching managed force block endings"

for entry in \
  'gson:::force\("com\.google\.code\.gson:gson:[^"]+"\)' \
  'guava:::force\("com\.google\.guava:guava:[^"]+"\)' \
  'netty-codec:::force\("io\.netty:netty-codec:[^"]+"\)' \
  'commons-lang3:::force\("org\.apache\.commons:commons-lang3:[^"]+"\)' \
  'jose4j:::force\("org\.bitbucket\.b_c:jose4j:[^"]+"\)' \
  'bouncycastle:::force\("org\.bouncycastle:bcpkix-jdk18on:[^"]+"\)' \
  'jdom2:::force\("org\.jdom:jdom2:[^"]+"\)'
do
  name="${entry%%:::*}"
  pattern="${entry#*:::}"
  regex_quiet "$pattern" "$ROOT_BUILD" \
    || fail "root build must keep localized force rule for $name"
done

for asm_artifact in asm asm-commons asm-tree asm-analysis asm-util; do
  regex_quiet "force\\(\"org\\.ow2\\.asm:${asm_artifact}:[^\"]+\"\\)" "$ROOT_BUILD" \
    || fail "root build must keep custom migration override for ${asm_artifact}"
done

regex_quiet 'force\("org\.jetbrains\.kotlin:kotlin-metadata-jvm:\$forcedKotlinVersion"\)' "$ROOT_BUILD" \
  || fail "root build must keep custom migration override for kotlin-metadata-jvm"

if regex_lines '"3\.18\.0"|"4\.5\.13"' "$ROOT_BUILD"; then
  fail "stale forced dependency versions must not reappear"
fi
