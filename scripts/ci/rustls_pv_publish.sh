#!/usr/bin/env bash
# Build the rustls-platform-verifier JVM support component -- the AAR that
# matrix-rust-sdk reflects into at runtime -- from upstream source, align its
# toolchain with this repository, and publish it to the GitLab Maven package
# registry under rustls/rustls-platform-verifier.
#
# Upstream only builds this component as part of the
# rustls-platform-verifier-android crate; it never lands on Maven, so the app
# resolves it from our registry (settings.gradle.kts pins the "rustls" group
# to the GitLab Maven repo). This script makes that artifact reproducible from
# source instead of hand-uploaded.
#
# The build runs on the main repository's Gradle wrapper via `gradlew -p`
# (same Gradle/AGP/Kotlin as the app), against the upstream tree patched by
# scripts/ci/rustls_pv_toolchain.patch. The patch is a pure overlay: every
# hunk is anchored to upstream content, so an upstream layout change fails
# `git apply --check` loudly instead of silently producing a wrong artifact.
# When upstream moves, rebase the patch against the new tag and re-run.
#
# Environment:
#   RUSTLS_PV_VERSION    override the version (default: catalog [libraries])
#   RUSTLS_PV_FORCE=1    republish even when the registry already has it
#   RUSTLS_PV_DRY_RUN=1  build + contract check + POM only, no registry writes
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
TOOLKIT_DIR="${MAGISK_CI_TOOLKIT_DIR:-$(bash scripts/resolve_ci_toolkit.sh)}"
source "$TOOLKIT_DIR/ci/gitlab_api.sh"

CATALOG="gradle/libs.versions.toml"
PATCH_FILE="scripts/ci/rustls_pv_toolchain.patch"
UPSTREAM_URL="https://github.com/rustls/rustls-platform-verifier.git"
GROUP_ID="rustls"
ARTIFACT_ID="rustls-platform-verifier"
MODULE="rustls-platform-verifier"
AAR_NAME="rustls-platform-verifier-release.aar"

PROJECT_ID="${CI_PROJECT_ID:-84113188}"
DRY_RUN="${RUSTLS_PV_DRY_RUN:-0}"
FORCE="${RUSTLS_PV_FORCE:-0}"
WORK_DIR="$ROOT_DIR/.cache/rustls-platform-verifier"

die() {
  echo "ERROR: $*" >&2
  exit 1
}


catalog_version() {
  sed -nE "s/^$1[[:space:]]*=[[:space:]]*\"([^\"]+)\".*/\1/p" "$CATALOG" | head -n 1
}

VERSION="${RUSTLS_PV_VERSION:-}"
if [[ -z "$VERSION" ]]; then
  VERSION="$(sed -nE 's/^rustls-platform-verifier[[:space:]]*=.*version[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$CATALOG" | head -n 1)"
fi
[[ -n "$VERSION" ]] || die "cannot resolve rustls-platform-verifier version from $CATALOG (set RUSTLS_PV_VERSION to override)"

KOTLIN="$(catalog_version kotlin)"
AGP="$(catalog_version agp)"
BUILD_TOOLS="$(catalog_version buildTools)"
TARGET_SDK="$(catalog_version targetSdk)"
JAVA_BYTECODE="$(catalog_version javaBytecode)"
COMPILE_SDK="$(catalog_version compileSdk)"
COMPILE_SDK_MAJOR="${COMPILE_SDK%%.*}"
COMPILE_SDK_MINOR="${COMPILE_SDK#*.}"

for entry in "kotlin=$KOTLIN" "agp=$AGP" "buildTools=$BUILD_TOOLS" "targetSdk=$TARGET_SDK" \
  "javaBytecode=$JAVA_BYTECODE" "compileSdk=$COMPILE_SDK"; do
  [[ -n "${entry#*=}" ]] || die "cannot resolve ${entry%%=*} from $CATALOG"
done
if [[ "$COMPILE_SDK_MINOR" == "$COMPILE_SDK" ]]; then
  die "compileSdk '$COMPILE_SDK' is not MAJOR.MINOR; the toolchain patch needs a minor API level"
fi

POM_NAME="rustls-platform-verifier-$VERSION.pom"
MAVEN_URL="$(gitlab_api_base_url)/projects/$PROJECT_ID/packages/maven/$GROUP_ID/$ARTIFACT_ID/$VERSION"

echo "==> rustls-platform-verifier $VERSION (dry_run=$DRY_RUN force=$FORCE)"
echo "    toolchain: kotlin=$KOTLIN agp=$AGP buildTools=$BUILD_TOOLS targetSdk=$TARGET_SDK java=$JAVA_BYTECODE compileSdk=$COMPILE_SDK_MAJOR.$COMPILE_SDK_MINOR"

# Skip the build when the registry already serves this exact version; a manual
# run must always rebuild from the current sources.
if [[ "$DRY_RUN" != "1" && "$FORCE" != "1" ]]; then
  status="$(gitlab_api_curl "projects/$PROJECT_ID/packages/maven/$GROUP_ID/$ARTIFACT_ID/$VERSION/$POM_NAME" --output /dev/null --write-out '%{http_code}' || true)"
  if [[ "$status" == "200" ]]; then
    echo "==> $GROUP_ID:$ARTIFACT_ID:$VERSION already published; nothing to do (RUSTLS_PV_FORCE=1 to republish)"
    exit 0
  fi
fi

# Fetch the pinned upstream tag and overlay the toolchain patch.
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
CLONE_DIR="$WORK_DIR/upstream"

git clone --quiet --depth 1 --branch "v/$VERSION" "$UPSTREAM_URL" "$CLONE_DIR" \
  || die "cannot clone $UPSTREAM_URL at tag v/$VERSION"

sed -e "s/@KOTLIN@/$KOTLIN/g" \
  -e "s/@AGP@/$AGP/g" \
  -e "s/@BUILD_TOOLS@/$BUILD_TOOLS/g" \
  -e "s/@TARGET_SDK@/$TARGET_SDK/g" \
  -e "s/@JAVA_BYTECODE@/$JAVA_BYTECODE/g" \
  -e "s/@COMPILE_SDK_MAJOR@/$COMPILE_SDK_MAJOR/g" \
  -e "s/@COMPILE_SDK_MINOR@/$COMPILE_SDK_MINOR/g" \
  "$PATCH_FILE" > "$WORK_DIR/toolchain.patch"

git -C "$CLONE_DIR" apply --check "$WORK_DIR/toolchain.patch" \
  || die "toolchain patch does not apply to $ARTIFACT_ID v/$VERSION; rebase $PATCH_FILE against that tag"
git -C "$CLONE_DIR" apply "$WORK_DIR/toolchain.patch"
echo "==> toolchain patch applied to upstream v/$VERSION"

# `-p` keeps the wrapper's Gradle version while building the patched upstream
# tree; the Android SDK and the aarch64 AAPT2 override come from
# .magisk_android_base through the environment.
./gradlew --no-daemon --console=plain -p "$CLONE_DIR/android" ":$MODULE:assembleRelease"

AAR="$CLONE_DIR/android/$MODULE/build/outputs/aar/$AAR_NAME"
[[ -f "$AAR" ]] || die "release AAR not found at $AAR"

# Contract check: matrix-rust-sdk reflects into these exact symbols at runtime
# (the three classes, the JNI entry point, and the seven status codes), so a
# silent upstream rename or signature change must fail here, not in the app.
CHECK_DIR="$WORK_DIR/aar-check"
rm -rf "$CHECK_DIR"
mkdir -p "$CHECK_DIR"
unzip -qo "$AAR" classes.jar -d "$CHECK_DIR"
unzip -qo "$CHECK_DIR/classes.jar" -d "$CHECK_DIR"

if [[ ! -d "$CHECK_DIR/org/rustls/platformverifier" ]]; then
  die "package org.rustls.platformverifier is missing from $AAR_NAME"
fi
class_count="$(find "$CHECK_DIR/org/rustls/platformverifier" -name '*.class' | wc -l)"
[[ "$class_count" -eq 3 ]] || die "expected exactly 3 classes in $ARTIFACT_ID, found $class_count"

javap -p -cp "$CHECK_DIR" org.rustls.platformverifier.CertificateVerifier \
  | grep -q 'verifyCertificateChain(android.content.Context, java.lang.String, java.lang.String, java.lang.String\[\], byte\[\], long, byte\[\]\[\])' \
  || die "CertificateVerifier.verifyCertificateChain drifted from the JNI contract"

status_codes="$(javap -p -cp "$CHECK_DIR" org.rustls.platformverifier.StatusCode | grep -c 'public static final' || true)"
[[ "$status_codes" -eq 7 ]] || die "expected 7 StatusCode constants, found $status_codes"
echo "==> contract check passed (3 classes, JNI entry point, 7 StatusCode constants)"

# POM: the registry path and settings.gradle.kts pin group "rustls", while
# upstream's template ships "org.rustls".
POM_TEMPLATE="$CLONE_DIR/android-release-support/pom-template.xml"
[[ -f "$POM_TEMPLATE" ]] || die "POM template not found at $POM_TEMPLATE"
sed -e "s/\\\$VERSION/$VERSION/g" \
  -e "s|<groupId>org.rustls</groupId>|<groupId>$GROUP_ID</groupId>|" \
  "$POM_TEMPLATE" > "$WORK_DIR/$POM_NAME"

cp "$AAR" "$WORK_DIR/$ARTIFACT_ID-$VERSION.aar"

if [[ "$DRY_RUN" == "1" ]]; then
  echo "==> dry run: skipping registry writes"
  echo "    aar: $WORK_DIR/$ARTIFACT_ID-$VERSION.aar"
  echo "    pom: $WORK_DIR/$POM_NAME"
  exit 0
fi


for asset in "$WORK_DIR/$ARTIFACT_ID-$VERSION.aar" "$WORK_DIR/$POM_NAME"; do
  name="$(basename "$asset")"
  echo "==> PUT $name"
  status="$(gitlab_api_curl "projects/$PROJECT_ID/packages/maven/$GROUP_ID/$ARTIFACT_ID/$VERSION/$name" --output "$WORK_DIR/upload.json" --write-out '%{http_code}' \
    --request PUT --upload-file "$asset" || true)"
  case "$status" in
    200|201|409) ;;
    400)
      if ! grep -qiE 'already|taken|exist' "$WORK_DIR/upload.json"; then
        die "failed to upload $name (HTTP $status): $(cat "$WORK_DIR/upload.json")"
      fi
      ;;
    *)
      die "failed to upload $name (HTTP $status): $(cat "$WORK_DIR/upload.json")"
      ;;
  esac
done

verify_status="$(gitlab_api_curl "projects/$PROJECT_ID/packages/maven/$GROUP_ID/$ARTIFACT_ID/$VERSION/$POM_NAME" --output /dev/null --write-out '%{http_code}' || true)"
[[ "$verify_status" == "200" ]] || die "post-publish verification failed (HTTP $verify_status)"

echo "==> Published $GROUP_ID:$ARTIFACT_ID:$VERSION"
echo "    $MAVEN_URL/$ARTIFACT_ID-$VERSION.aar"
echo "    $MAVEN_URL/$POM_NAME"
