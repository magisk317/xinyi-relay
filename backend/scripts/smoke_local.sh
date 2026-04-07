#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
BASE_URL="${BASE_URL:-https://localhost:8443}"
USERNAME="${RELAY_SMOKE_USERNAME:-relay}"
PASSWORD="${RELAY_SMOKE_PASSWORD:-relay-pass}"
COOKIE_JAR="$(mktemp)"
SMOKE_IMAGE="${RELAY_SMOKE_API_IMAGE:-relay-backend-smoke:local}"

cleanup() {
  rm -f "$COOKIE_JAR"
  rm -f "$BACKEND_DIR/.env"
  (
    cd "$BACKEND_DIR"
    docker compose down >/dev/null 2>&1 || true
  )
}
trap cleanup EXIT

cd "$BACKEND_DIR"
cp -f .env.example .env
docker build -t "$SMOKE_IMAGE" api >/dev/null
RELAY_API_IMAGE="$SMOKE_IMAGE" RELAY_API_PULL_POLICY=never docker compose up -d postgres api caddy >/dev/null
sleep 8

echo "[smoke] backend health"
curl -kfsS "$BASE_URL/healthz" | jq .

echo "[smoke] bootstrap admin if needed"
curl -kfsS -X POST "$BASE_URL/api/v1/bootstrap/admin" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" >/tmp/relay_bootstrap.json || true

echo "[smoke] login"
curl -kfsS -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" >/tmp/relay_login.json
CSRF_TOKEN="$(jq -r '.csrfToken' /tmp/relay_login.json)"

echo "[smoke] create bind code"
curl -kfsS -b "$COOKIE_JAR" -H "X-CSRF-Token: $CSRF_TOKEN" -X POST \
  "$BASE_URL/api/v1/devices/bind-codes" >/tmp/relay_bind.json
BIND_CODE="$(jq -r '.code' /tmp/relay_bind.json)"

echo "[smoke] register device"
curl -kfsS -X POST "$BASE_URL/api/v1/agent/register" \
  -H 'Content-Type: application/json' \
  -d "{\"bindCode\":\"$BIND_CODE\",\"deviceName\":\"Smoke Device\",\"deviceModel\":\"CLI\",\"platform\":\"android\",\"appVersion\":\"0.0.4\"}" >/tmp/relay_register.json
DEVICE_TOKEN="$(jq -r '.deviceToken' /tmp/relay_register.json)"

echo "[smoke] heartbeat"
curl -kfsS -X POST "$BASE_URL/api/v1/agent/heartbeat" \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"appVersion":"0.0.4","localAddresses":["https://192.168.1.2:8443"],"capabilities":{"remoteConfig":true,"recordUpload":true}}' | jq .

echo "[smoke] round-trip config snapshot"
curl -kfsS -b "$COOKIE_JAR" "$BASE_URL/api/v1/config/snapshot" >/tmp/relay_snapshot.json
BASE_REVISION="$(jq -r '.revision' /tmp/relay_snapshot.json)"
curl -kfsS -b "$COOKIE_JAR" -H "X-CSRF-Token: $CSRF_TOKEN" -X PUT \
  "$BASE_URL/api/v1/config/snapshot" \
  -H 'Content-Type: application/json' \
  -d "{\"base_revision\":$BASE_REVISION,\"snapshot\":{\"senders\":[],\"appInfos\":[],\"rules\":[],\"smsCodeRules\":[],\"notifyRoutes\":[],\"forwardFilters\":[]}}" | jq .

echo "[smoke] config audit"
curl -kfsS -b "$COOKIE_JAR" "$BASE_URL/api/v1/config/audit" | jq .

echo "[smoke] done"
