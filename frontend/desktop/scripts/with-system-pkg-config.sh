#!/usr/bin/env bash
set -euo pipefail

if [[ "${OSTYPE:-}" == linux* ]] && [[ -x /usr/bin/pkg-config ]]; then
  SYSTEM_PC_PATH="$(
    /usr/bin/pkg-config --variable pc_path pkg-config 2>/dev/null || true
  )"

  export TAURI_LINUX_AYATANA_APPINDICATOR="${TAURI_LINUX_AYATANA_APPINDICATOR:-1}"
  export PATH="/usr/bin:${PATH}"
  export PKG_CONFIG="/usr/bin/pkg-config"

  if [[ -n "${SYSTEM_PC_PATH}" ]]; then
    if [[ -n "${PKG_CONFIG_PATH:-}" ]]; then
      export PKG_CONFIG_PATH="${SYSTEM_PC_PATH}:${PKG_CONFIG_PATH}"
    else
      export PKG_CONFIG_PATH="${SYSTEM_PC_PATH}"
    fi
  fi
fi

exec "$@"
