#!/usr/bin/env bash
set -euo pipefail

# Refs:
# - https://github.com/libxposed/api/issues/47
# - https://github.com/libxposed/api/pull/51

API_REPO_URL="${API_REPO_URL:-https://github.com/libxposed/api.git}"
SERVICE_REPO_URL="${SERVICE_REPO_URL:-https://github.com/libxposed/service.git}"
API_DIR="${API_DIR:-/tmp/libxposed-api-20260307}"
SERVICE_DIR="${SERVICE_DIR:-/tmp/libxposed-service-20260307}"

clone_or_update() {
  local repo_url="$1"
  local repo_dir="$2"
  if [[ -d "$repo_dir/.git" ]]; then
    git -C "$repo_dir" pull --ff-only
  else
    rm -rf "$repo_dir"
    git clone "$repo_url" "$repo_dir"
  fi
}

patch_service_interface_namespace() {
  local interface_gradle="$SERVICE_DIR/interface/build.gradle.kts"
  if [[ ! -f "$interface_gradle" ]]; then
    echo "Missing file: $interface_gradle" >&2
    exit 1
  fi
  # Workaround for duplicate namespace between service/interface before upstream fix.
  if grep -q 'namespace = "io.github.libxposed.service"' "$interface_gradle"; then
    sed -i 's/namespace = "io.github.libxposed.service"/namespace = "io.github.libxposed.iface"/' "$interface_gradle"
    echo "Patched interface namespace to io.github.libxposed.iface"
  else
    echo "Interface namespace already patched"
  fi
}

echo "[1/3] Preparing libxposed/api in $API_DIR"
clone_or_update "$API_REPO_URL" "$API_DIR"

echo "[2/3] Preparing libxposed/service in $SERVICE_DIR"
clone_or_update "$SERVICE_REPO_URL" "$SERVICE_DIR"
patch_service_interface_namespace

echo "[3/3] Publishing to mavenLocal"
"$API_DIR/gradlew" -p "$API_DIR" \
  :api:publishApiPublicationToMavenLocal \
  -x :checks:compileKotlin \
  -x :api:javaDocReleaseGeneration \
  --no-daemon

"$SERVICE_DIR/gradlew" -p "$SERVICE_DIR" \
  :interface:publishInterfacePublicationToMavenLocal \
  :service:publishServicePublicationToMavenLocal \
  -x :interface:javaDocReleaseGeneration \
  -x :service:javaDocReleaseGeneration \
  --no-daemon

echo "Done. Local artifacts published:"
echo "  io.github.libxposed:api:100"
echo "  io.github.libxposed:interface:100"
echo "  io.github.libxposed:service:100-1.0.0"
