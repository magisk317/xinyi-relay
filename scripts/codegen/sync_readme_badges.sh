#!/usr/bin/env bash
set -euo pipefail

# Sync README badges from libs.versions.toml + gradle-wrapper.properties.
# Badge helpers are single-sourced from magisk-ci-toolkit/codegen/badges.sh;
# this wrapper only declares the xinyi-relay-specific badge subset + values.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
# shellcheck source=/dev/null
source "$TOOLKIT_DIR/codegen/badges.sh"

TOML="gradle/libs.versions.toml"
WRAPPER_PROPS="gradle/wrapper/gradle-wrapper.properties"

kotlin="$(read_toml_value kotlin "$TOML")"
java="$(read_toml_value java "$TOML")"
compose="$(read_toml_value compose-bom-alpha "$TOML")"
agp="$(read_toml_value agp "$TOML")"
min_sdk="$(read_toml_value minSdk "$TOML")"
target_sdk="$(read_toml_value targetSdk "$TOML")"
xposed="$(read_toml_value libxposed-api "$TOML")"
gradle_ver="$(read_gradle_version "$WRAPPER_PROPS")"

# Xposed API badge shows the major API level (e.g. 102.0.0 -> 102).
xposed_level="${xposed%%.*}"

# --- tech stack (shared subset, identical order across all three repos) ---
tech="$(tech_badge Kotlin "$kotlin" 7F52FF kotlin https://kotlinlang.org)"
tech="$tech $(tech_badge Java "${java}+" E76F00 openjdk https://openjdk.org)"
tech="$tech $(tech_badge "Jetpack Compose" "BOM ${compose}" 4285F4 android https://developer.android.com/jetpack/compose)"
tech="$tech $(tech_badge Gradle "$gradle_ver" 02303A gradle https://gradle.org)"
tech="$tech $(tech_badge AGP "$agp" 3DDC84 gradle https://developer.android.com/studio/releases/gradle-plugin)"
tech="$tech $(tech_badge "Min SDK" "$min_sdk" brightgreen android https://developer.android.com/about/versions)"
tech="$tech $(tech_badge "Target SDK" "$target_sdk" blue android https://developer.android.com/about/versions)"
tech="$tech $(tech_badge "Xposed API" "$xposed_level" orange '' https://github.com/libxposed/api)"
tech="$tech $(tech_badge Telegram Group 2CA5E0 telegram https://t.me/+NR2QaQ4dlEgxYmNl)"

# --- platform (GitLab flavour: link-wrapped, flat-square where shields-based) ---
plat="$(tech_badge GitLab "magisk3171/xinyi-relay" FC6D26 gitlab https://gitlab.com/magisk3171/xinyi-relay)"
plat="$plat [![CI](https://img.shields.io/gitlab/pipeline-status/magisk3171%2Fxinyi-relay?branch=beta&style=flat-square&logo=gitlab&label=CI)](https://gitlab.com/magisk3171/xinyi-relay/-/pipelines?ref=beta)"
plat="$plat [![Latest Release](https://img.shields.io/gitlab/v/release/magisk3171%2Fxinyi-relay?include_prereleases&style=flat-square&logo=gitlab)](https://gitlab.com/magisk3171/xinyi-relay/-/releases)"
plat="$plat $(tech_badge License GPL-3.0 blue '' LICENSE)"

for readme in README.md README-EN.md; do
  replace_block "$readme" platform "$plat"
  replace_block "$readme" tech "$tech"
done

echo "xinyi-relay README badges synced."
