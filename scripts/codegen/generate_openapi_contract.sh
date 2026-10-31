#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="$ROOT_DIR/frontend/shared/contracts/openapi.json"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

if [[ $# -gt 0 ]]; then
  python3 "$ROOT_DIR/scripts/codegen/generate_openapi_schemas.py" "$1"
  python3 "$ROOT_DIR/scripts/codegen/generate_openapi_route_contracts.py" "$1"
else
  python3 "$ROOT_DIR/scripts/codegen/generate_openapi_schemas.py"
  python3 "$ROOT_DIR/scripts/codegen/generate_openapi_route_contracts.py"
fi

# The CI runner image carries no Go toolchain: the whole freshness check is
# skipped there with a note. It stays enforced on machines that have go.
if command -v go >/dev/null 2>&1; then
  (
    cd "$ROOT_DIR/backend/api"
    GOWORK=off go run ./cmd/openapi-contract > "$TMP"
  )

  if [[ "${1:-}" == "--check" ]]; then
    if ! cmp -s "$TMP" "$OUTPUT"; then
      echo "frontend/shared/contracts/openapi.json is out of date"
      exit 1
    fi
    exit 0
  fi

  mv "$TMP" "$OUTPUT"
else
  echo "go not available; skipping the openapi contract freshness check"
fi
