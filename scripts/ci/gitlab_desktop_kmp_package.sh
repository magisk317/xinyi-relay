#!/usr/bin/env bash
set -euo pipefail

# Packages the KMP desktop track for the build host and collects the artifacts
# into the layout scripts/release/gitlab_desktop_release.sh already reads, so
# both desktop tracks reach a release through the one publish job.

usage() {
  cat >&2 <<'EOF'
Usage:
  gitlab_desktop_kmp_package.sh <artifact-suffix>
EOF
}

if [[ $# -ne 1 ]]; then
  usage
  exit 2
fi

artifact_suffix=$1
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
artifact_dir="$root_dir/desktop-artifacts/$artifact_suffix"
# binaries/main/<format>/ holds one directory per package format -- deb, rpm,
# app-image, exe, msi, dmg, pkg. The jlink runtime image sits at binaries/main/app
# and is pruned below, because it is build output and not a release artifact. The
# uber jar is a sibling of binaries that the plugin does not place under it.
collect_roots=(
  "$root_dir/desktop/build/compose/binaries/main"
  "$root_dir/desktop/build/compose/jars"
)

# Strip a pre-release suffix from versionName: the Debian revision a suffix
# turns into is exactly what the Tauri track strips for the same reason.
desktop_version="$(sed -nE 's/^versionName[[:space:]]*=[[:space:]]"([^"]+)"/\1/p' "$root_dir/gradle/libs.versions.toml" | head -n1)"
if [[ -z "$desktop_version" ]]; then
  echo "ERROR: failed to parse versionName from gradle/libs.versions.toml" >&2
  exit 1
fi
desktop_version="${desktop_version%%-*}"

# No JDK staging here: the job image is eclipse-temurin:<major>_<build>-jdk-resolute,
# which already exports JAVA_HOME=/opt/java/openjdk and ships javac, jlink and
# jpackage. Staging a second JDK into the checkout would shadow it -- see the
# toolkit AGENTS.md "CI Java Runtime" section before reintroducing one.

cd "$root_dir"
chmod +x ./gradlew

# MAGISK_GRADLE_ARGS arrives from CI as one space-separated string, so it is split
# into arguments here rather than expanded inline into the command below.
magisk_gradle_args="${MAGISK_GRADLE_ARGS:---warning-mode all}"
read -r -a extra_gradle_args <<< "$magisk_gradle_args"

gradle_cmd=(
  ./gradlew
  :desktop:packageDistributionForCurrentOS
  :desktop:packageUberJarForCurrentOS
  "-PdesktopVersion=$desktop_version"
  ${extra_gradle_args[@]+"${extra_gradle_args[@]}"}
  --no-daemon
)
if ! "${gradle_cmd[@]}"; then
  echo "Gradle packaging failed once, retrying after stopping the daemon" >&2
  ./gradlew --stop
  "${gradle_cmd[@]}"
fi

collect_artifacts() {
  rm -rf "$artifact_dir"
  mkdir -p "$artifact_dir"

  local found=0 root file
  for root in "${collect_roots[@]}"; do
    [[ -d "$root" ]] || continue
    while IFS= read -r -d '' file; do
      cp "$file" "$artifact_dir/"
      found=1
    done < <(
      find "$root" -type d -name app -prune -o -type f \
        \( -name '*.deb' -o -name '*.rpm' -o -name '*.AppImage' -o -name '*.exe' \
           -o -name '*.msi' -o -name '*.dmg' -o -name '*.pkg' -o -name '*.jar' \) \
        -print0
    )
  done

  if [[ $found -eq 0 ]]; then
    echo "ERROR: no KMP desktop artifacts found under ${collect_roots[*]}" >&2
    find "${collect_roots[@]}" -maxdepth 3 -type f -print 2>/dev/null >&2 || true
    exit 1
  fi
}
collect_artifacts
