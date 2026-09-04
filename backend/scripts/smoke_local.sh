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

smoke_curl() {
  # The smoke target is an internal Docker service and must not go through the
  # runner's outbound proxy.
  curl -kfsS --noproxy '*' "$@"
}

append_proxy_build_args() {
  local -n build_args_ref=$1
  local build_arg_name=$2
  shift 2

  local env_name value
  for env_name in "$@"; do
    value="${!env_name:-}"
    if [[ -n "$value" ]]; then
      build_args_ref+=(--build-arg "${build_arg_name}=${value}")
      return 0
    fi
  done
}

network_use_mirror() {
  [[ "${MAGISK_LINUX_USE_MIRROR:-true}" == "true" ]]
}

go_proxy() {
  if network_use_mirror; then
    printf '%s\n' "${GOPROXY:-https://goproxy.cn,direct}"
  else
    printf '%s\n' "https://proxy.golang.org,direct"
  fi
}

bun_registry() {
  if network_use_mirror; then
    printf '%s\n' "${BUN_CONFIG_REGISTRY:-https://registry.npmmirror.com}"
  else
    printf '%s\n' "https://registry.npmjs.org"
  fi
}

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
docker_build_args=(
  --build-arg "GOPROXY=$(go_proxy)"
  --build-arg "BUN_CONFIG_REGISTRY=$(bun_registry)"
)
append_proxy_build_args docker_build_args HTTP_PROXY RELAY_BUILD_HTTP_PROXY HTTP_PROXY
append_proxy_build_args docker_build_args HTTPS_PROXY RELAY_BUILD_HTTPS_PROXY HTTPS_PROXY
append_proxy_build_args docker_build_args ALL_PROXY RELAY_BUILD_ALL_PROXY ALL_PROXY
append_proxy_build_args docker_build_args NO_PROXY RELAY_BUILD_NO_PROXY NO_PROXY
append_proxy_build_args docker_build_args http_proxy RELAY_BUILD_HTTP_PROXY http_proxy HTTP_PROXY
append_proxy_build_args docker_build_args https_proxy RELAY_BUILD_HTTPS_PROXY https_proxy HTTPS_PROXY
append_proxy_build_args docker_build_args all_proxy RELAY_BUILD_ALL_PROXY all_proxy ALL_PROXY
append_proxy_build_args docker_build_args no_proxy RELAY_BUILD_NO_PROXY no_proxy NO_PROXY

docker build \
  "${docker_build_args[@]}" \
  -t "$SMOKE_IMAGE" \
  -f api/Dockerfile \
  "$ROOT_DIR" >/dev/null
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
docker compose up -d api >/dev/null

echo "[smoke] backend health"
for attempt in $(seq 1 30); do
  if smoke_curl "$BASE_URL/healthz" >/tmp/relay_health.json 2>/tmp/relay_health.err; then
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
smoke_curl -X POST "$BASE_URL/api/v1/bootstrap/admin" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" >/tmp/relay_bootstrap.json || true

echo "[smoke] login"
smoke_curl -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" >/tmp/relay_login.json
CSRF_TOKEN="$(jq -er '.csrfToken | strings | select(length > 0)' /tmp/relay_login.json)"
SESSION_TOKEN="$(awk '$6 == "relay_session" { print $7; exit }' "$COOKIE_JAR")"
if [[ -z "$SESSION_TOKEN" ]]; then
  echo "Login did not return a relay session cookie" >&2
  exit 1
fi

# curl 8.22+ applies public-suffix validation when reloading Netscape cookie
# files. The CI service hostname is the single-label name "docker", which is
# treated as a public suffix by libpsl. Passing the extracted cookie explicitly
# keeps the smoke test compatible with both old and new curl versions.
WEB_COOKIE="relay_session=$SESSION_TOKEN"
web_curl() {
  smoke_curl -b "$WEB_COOKIE" "$@"
}

echo "[smoke] verify web session"
web_curl "$BASE_URL/api/v1/auth/me" >/tmp/relay_me.json
jq -e --arg username "$USERNAME" \
  '.authenticated == true and .username == $username and (.csrfToken | strings | length > 0)' \
  /tmp/relay_me.json >/dev/null

echo "[smoke] create bind code"
web_curl -H "X-CSRF-Token: $CSRF_TOKEN" -X POST \
  "$BASE_URL/api/v1/devices/bind-codes" >/tmp/relay_bind.json
BIND_CODE="$(jq -r '.code' /tmp/relay_bind.json)"

echo "[smoke] register device"
smoke_curl -X POST "$BASE_URL/api/v1/agent/register" \
  -H 'Content-Type: application/json' \
  -d "{\"bindCode\":\"$BIND_CODE\",\"deviceName\":\"Smoke Device\",\"deviceModel\":\"CLI\",\"platform\":\"android\",\"appVersion\":\"0.0.4\"}" >/tmp/relay_register.json
DEVICE_TOKEN="$(jq -r '.deviceToken' /tmp/relay_register.json)"
DEVICE_ID="$(jq -r '.deviceId' /tmp/relay_register.json)"

echo "[smoke] heartbeat"
smoke_curl -X POST "$BASE_URL/api/v1/agent/heartbeat" \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"appVersion":"0.0.4","localAddresses":["https://192.168.1.2:8443"],"capabilities":{"remoteConfig":true,"recordUpload":true}}' | jq .

echo "[smoke] fetch device config mirror"
web_curl "$BASE_URL/api/v1/devices/$DEVICE_ID/config" >/tmp/relay_device_config.json
BASE_REVISION="$(jq -r '.revision' /tmp/relay_device_config.json)"

echo "[smoke] queue device config command"
web_curl -H "X-CSRF-Token: $CSRF_TOKEN" -X POST \
  "$BASE_URL/api/v1/devices/$DEVICE_ID/config/commands" \
  -H 'Content-Type: application/json' \
  -d "{\"baseRevision\":$BASE_REVISION,\"summary\":\"smoke:update\",\"mutation\":{\"operations\":[{\"type\":\"replace_senders\",\"senders\":[]},{\"type\":\"replace_device_apps\",\"deviceId\":$DEVICE_ID,\"apps\":[]}]}}" >/tmp/relay_device_command.json
COMMAND_ID="$(jq -r '.id' /tmp/relay_device_command.json)"
TARGET_REVISION="$(jq -r '.targetRevision' /tmp/relay_device_command.json)"
cat /tmp/relay_device_command.json | jq .

echo "[smoke] agent pulls pending commands"
smoke_curl -X POST "$BASE_URL/api/v1/agent/config/commands:pull" \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"localRevision\":$BASE_REVISION}" >/tmp/relay_agent_pull.json
cat /tmp/relay_agent_pull.json | jq .

echo "[smoke] agent acknowledges command"
smoke_curl -X POST "$BASE_URL/api/v1/agent/config/commands:ack" \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"commandId\":$COMMAND_ID,\"status\":\"applied\",\"appliedRevision\":$TARGET_REVISION,\"failureReason\":\"\",\"mirrorContent\":{\"senders\":[],\"deviceAppInfos\":{\"$DEVICE_ID\":[]},\"rules\":[],\"smsCodeRules\":[],\"notifyRoutes\":[],\"forwardFilters\":[]}}" | jq .

echo "[smoke] device config audit"
web_curl "$BASE_URL/api/v1/devices/$DEVICE_ID/config/audit" | jq .

echo "[smoke] done"
