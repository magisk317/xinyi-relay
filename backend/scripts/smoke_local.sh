#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
BASE_URL="${BASE_URL:-http://localhost:8088}"
USERNAME="${RELAY_SMOKE_USERNAME:-relay}"
PASSWORD="${RELAY_SMOKE_PASSWORD:-relay-pass}"
COOKIE_JAR="$(mktemp)"
POSTGRES_DATA_DIR="$(mktemp -d)"
SMOKE_IMAGE="${RELAY_SMOKE_API_IMAGE:-relay-backend-smoke:local}"

prepare_postgres_data_dir() {
  chmod 0777 "$POSTGRES_DATA_DIR"
}

remove_postgres_data_dir() {
  chmod -R 0777 "$POSTGRES_DATA_DIR" >/dev/null 2>&1 || true
  if command -v sudo >/dev/null 2>&1; then
    sudo rm -rf "$POSTGRES_DATA_DIR" >/dev/null 2>&1 || rm -rf "$POSTGRES_DATA_DIR" >/dev/null 2>&1 || true
  else
    rm -rf "$POSTGRES_DATA_DIR" >/dev/null 2>&1 || true
  fi
}

cleanup() {
  rm -f "$COOKIE_JAR"
  rm -f "$BACKEND_DIR/.env"
  (
    cd "$BACKEND_DIR"
    docker compose down >/dev/null 2>&1 || true
  )
  remove_postgres_data_dir
}
trap cleanup EXIT

cd "$BACKEND_DIR"
cp -f .env.example .env
# Caddy 在容器内需要监听所有接口，否则 Docker 端口映射无法到达
sed -i 's/^RELAY_HTTP_HOST=.*/RELAY_HTTP_HOST=0.0.0.0/' .env
set -a
# shellcheck disable=SC1091
. ./.env
set +a
prepare_postgres_data_dir
docker build -t "$SMOKE_IMAGE" api >/dev/null
RELAY_API_IMAGE="$SMOKE_IMAGE" \
RELAY_API_PULL_POLICY=never \
RELAY_POSTGRES_DATA_DIR="$POSTGRES_DATA_DIR" \
docker compose up -d postgres >/dev/null

for attempt in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/tmp/relay_pg_ready.log 2>/tmp/relay_pg_ready.err; then
    cat /tmp/relay_pg_ready.log
    break
  fi

  if [[ "$attempt" -eq 30 ]]; then
    echo "Postgres readiness check failed after ${attempt} attempts" >&2
    cat /tmp/relay_pg_ready.err >&2 || true
    docker compose logs postgres >&2 || true
    exit 1
  fi

  sleep 2
done

RELAY_API_IMAGE="$SMOKE_IMAGE" \
RELAY_API_PULL_POLICY=never \
RELAY_POSTGRES_DATA_DIR="$POSTGRES_DATA_DIR" \
docker compose up -d api caddy >/dev/null

echo "[smoke] backend health"
for attempt in $(seq 1 30); do
  if curl -kfsS "$BASE_URL/healthz" >/tmp/relay_health.json 2>/tmp/relay_health.err; then
    cat /tmp/relay_health.json | jq .
    break
  fi

  if [[ "$attempt" -eq 30 ]]; then
    echo "Backend health check failed after ${attempt} attempts" >&2
    echo "--- curl error ---" >&2
    cat /tmp/relay_health.err >&2 || true
    echo "--- api logs ---" >&2
    docker compose logs --tail=50 api >&2 || true
    echo "--- caddy logs ---" >&2
    docker compose logs --tail=30 caddy >&2 || true
    echo "--- postgres logs ---" >&2
    docker compose logs --tail=20 postgres >&2 || true
    exit 1
  fi

  sleep 2
done

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
