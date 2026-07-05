#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage:
  gitlab_desktop_package.sh <linux|macos> <artifact-suffix> <rust-target> <bundle-args> <bundle-dir>
EOF
}

if [[ $# -ne 5 ]]; then
  usage
  exit 2
fi

os_name=$1
artifact_suffix=$2
rust_target=$3
bundle_args=$4
bundle_dir=$5

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
desktop_dir="$root_dir/frontend/desktop"
artifact_dir="$root_dir/desktop-artifacts/$artifact_suffix"

install_node24() {
  if command -v node >/dev/null 2>&1 && [[ "$(node -p 'process.versions.node.split(".")[0]')" == "24" ]]; then
    return
  fi

  local platform arch archive node_base node_archive node_dir
  case "$(uname -s)" in
    Linux) platform="linux" ;;
    Darwin) platform="darwin" ;;
    *)
      echo "ERROR: unsupported desktop node platform: $(uname -s)" >&2
      exit 1
      ;;
  esac
  case "$(uname -m)" in
    x86_64|amd64) arch="x64" ;;
    aarch64|arm64) arch="arm64" ;;
    *)
      echo "ERROR: unsupported desktop node arch: $(uname -m)" >&2
      exit 1
      ;;
  esac

  node_base="https://nodejs.org/dist/${XINYI_NODE_RELEASE_CHANNEL:-latest-v24.x}"
  if [[ "$platform" == "linux" ]]; then
    archive="$(curl -fsSL "$node_base/SHASUMS256.txt" | awk -v p="node-v.*-${platform}-${arch}.tar.xz" '$2 ~ p { print $2; exit }')"
  else
    archive="$(curl -fsSL "$node_base/SHASUMS256.txt" | awk -v p="node-v.*-${platform}-${arch}.tar.gz" '$2 ~ p { print $2; exit }')"
  fi
  if [[ -z "$archive" ]]; then
    echo "ERROR: failed to resolve Node.js 24 archive for ${platform}-${arch}" >&2
    exit 1
  fi

  node_dir="$root_dir/.ci-node"
  rm -rf "$node_dir"
  mkdir -p "$node_dir"
  curl -fsSLo "$root_dir/$archive" "$node_base/$archive"
  tar -xf "$root_dir/$archive" -C "$node_dir" --strip-components=1
  rm -f "$root_dir/$archive"
  export PATH="$node_dir/bin:$PATH"
}

install_rust() {
  if ! command -v rustup >/dev/null 2>&1; then
    curl --proto '=https' --tlsv1.2 -fsS https://sh.rustup.rs \
      | sh -s -- -y --profile minimal --default-toolchain stable
  fi
  # shellcheck disable=SC1091
  source "$HOME/.cargo/env"
  rustup target add "$rust_target"
}

install_linux_dependencies() {
  if [[ "$os_name" != "linux" ]]; then
    return
  fi
  if ! command -v apt-get >/dev/null 2>&1; then
    return
  fi
  apt-get update
  apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    file \
    libayatana-appindicator3-dev \
    libgtk-3-dev \
    librsvg2-dev \
    libssl-dev \
    libwebkit2gtk-4.1-dev \
    libxdo-dev \
    pkg-config \
    wget \
    zip
  rm -rf /var/lib/apt/lists/*
}

sync_and_strip_desktop_version() {
  bash "$root_dir/scripts/release/sync_desktop_version.sh"
  node -e "const fs=require('fs'); const p='src-tauri/tauri.conf.json'; let c=fs.readFileSync(p,'utf8'); fs.writeFileSync(p, c.replace(/\"version\":\s*\"([0-9]+\.[0-9]+\.[0-9]+)-[a-zA-Z0-9]+\"/, '\"version\": \"\$1\"'));"
}

collect_artifacts() {
  rm -rf "$artifact_dir"
  mkdir -p "$artifact_dir"

  mapfile -d '' bundle_files < <(
    find "$root_dir/$bundle_dir" -type f \
      \( -name '*.AppImage' -o -name '*.deb' -o -name '*.rpm' -o -name '*.exe' -o -name '*.msix' -o -name '*.dmg' \) \
      -print0 | sort -z
  )
  for file in "${bundle_files[@]}"; do
    cp "$file" "$artifact_dir/"
  done

  if [[ "$os_name" == "macos" ]]; then
    while IFS= read -r -d '' app_dir; do
      local base_name
      base_name="$(basename "$app_dir")"
      if command -v ditto >/dev/null 2>&1; then
        ditto -c -k --sequesterRsrc --keepParent "$app_dir" "$artifact_dir/${base_name}.zip"
      else
        (cd "$(dirname "$app_dir")" && zip -qry "$artifact_dir/${base_name}.zip" "$base_name")
      fi
    done < <(find "$root_dir/$bundle_dir" -type d -name '*.app' -print0 | sort -z)
  fi

  if ! find "$artifact_dir" -type f | grep -q .; then
    echo "ERROR: no desktop artifacts found in $root_dir/$bundle_dir" >&2
    find "$root_dir/$bundle_dir" -maxdepth 4 -type f -print >&2 || true
    exit 1
  fi
}

install_linux_dependencies
install_node24
install_rust
corepack enable
corepack prepare "$(node -p "require('$desktop_dir/package.json').packageManager")" --activate

cd "$desktop_dir"
sync_and_strip_desktop_version
pnpm install --frozen-lockfile
pnpm build
export PKG_CONFIG="${PKG_CONFIG:-}"
if [[ "$os_name" == "linux" ]]; then
  export PKG_CONFIG="${PKG_CONFIG:-/usr/bin/pkg-config}"
  export TAURI_LINUX_AYATANA_APPINDICATOR="${TAURI_LINUX_AYATANA_APPINDICATOR:-1}"
fi
# shellcheck disable=SC2086
pnpm exec node ./scripts/with-system-pkg-config.mjs tauri build $bundle_args
collect_artifacts
