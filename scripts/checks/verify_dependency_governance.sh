#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/utils/regex_helpers.sh"
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

if regex_lines 'maven\("https://jitpack\.io"\)|maven\("https://s01\.oss\.sonatype\.org' "$ROOT_BUILD"; then
  fail "root build must not carry project repositories"
fi

for asm_artifact in asm asm-commons asm-tree asm-analysis asm-util; do
  regex_quiet "force\\(\"org\\.ow2\\.asm:${asm_artifact}:[^\"]+\"\\)" "$ROOT_BUILD" \
    || fail "root build must keep custom migration override for ${asm_artifact}"
done

# Hand-written force rules are limited to the ASM family (Java 27 bytecode, buildscript
# classpath). Security-motivated forces are only allowed inside the block the
# dependency-force workflow manages (BEGIN/END AUTO FORCED DEPENDENCIES).
if awk '/BEGIN AUTO FORCED DEPENDENCIES/ { skip = 1 }
        !skip && /force\(/ && !/org\.ow2\.asm/ { found = 1 }
        /END AUTO FORCED DEPENDENCIES/ { skip = 0 }
        END { exit !found }' "$ROOT_BUILD"; then
  fail "root build must only hand-force the ASM family; security forces belong in the managed block"
fi
