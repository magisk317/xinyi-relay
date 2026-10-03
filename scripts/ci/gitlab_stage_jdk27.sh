#!/usr/bin/env bash
# Stages JDK 27 for the KMP desktop packaging jobs.
#
# The desktop modules are pure JVM, so a packaging job needs a JDK and nothing
# else: no Android SDK, no Node, no Rust. The only JDK staging that exists on
# these runners lives in the toolkit's .magisk_android_base anchor, and it drags
# the Android SDK, ANDROID_HOME and aapt2 workarounds along with it, so the same
# Adoptium fetch is restated here for a track that needs none of that.
#
# Source this file rather than executing it: the point of staging is to hand
# JAVA_HOME and PATH back to the caller, and a child process cannot do that. The
# body is a function so that a direct `bash scripts/ci/gitlab_stage_jdk27.sh` run
# still works -- it stages the JDK, it just cannot hand it back.
#
# Stages into the repository tree so that concurrent jobs on one runner share a
# single download behind a lock instead of racing each other. Keep the directory
# in .gitignore: it is a build dependency, never a source file.

_stage_jdk27_main() {
  local script_dir jdk_dir jdk_os jdk_arch jdk_tmp

  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  jdk_dir="${XINYI_JDK_DIR:-$script_dir/../../.jdk-27}"

  if [ -x "$jdk_dir/bin/javac" ]; then
    export JAVA_HOME="$jdk_dir"
    export PATH="$JAVA_HOME/bin:$PATH"
    java -version
    return 0
  fi

  # Hotspot rather than a JRE: jpackage needs jlink, which only a full JDK carries.
  case "$(uname -s)" in
    Linux) jdk_os=linux ;;
    Darwin) jdk_os=mac ;;
    *)
      echo "ERROR: unsupported JDK platform: $(uname -s)" >&2
      return 1
      ;;
  esac
  case "$(uname -m)" in
    x86_64|amd64) jdk_arch=x64 ;;
    aarch64|arm64) jdk_arch=aarch64 ;;
    *)
      echo "ERROR: unsupported JDK architecture: $(uname -m)" >&2
      return 1
      ;;
  esac

  # The lock is a sibling of the JDK directory, not inside it, so the rm -rf below
  # cannot take a concurrent job's lock with it. flock is optional: a runner
  # without it still stages, it just may download twice.
  exec 9>"$jdk_dir.lock"
  flock 9 2>/dev/null || true

  if [ ! -x "$jdk_dir/bin/javac" ]; then
    jdk_tmp="$(mktemp -d)"
    curl -fsSL --retry 3 \
      "https://api.adoptium.net/v3/binary/latest/27/ga/${jdk_os}/${jdk_arch}/jdk/hotspot/normal/eclipse" \
      -o "$jdk_tmp/jdk.tar.gz"
    rm -rf "$jdk_dir"
    mkdir -p "$jdk_dir"
    tar -xzf "$jdk_tmp/jdk.tar.gz" -C "$jdk_dir" --strip-components=1
    rm -rf "$jdk_tmp"
  fi

  export JAVA_HOME="$jdk_dir"
  export PATH="$JAVA_HOME/bin:$PATH"
  java -version
}

_stage_jdk27_main
