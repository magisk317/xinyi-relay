#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage:
  gitlab_backend_container.sh build-arch <amd64|arm64>
  gitlab_backend_container.sh publish-manifests
  gitlab_backend_container.sh list-images
EOF
}

require_env() {
  local name=$1
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: ${name} is required" >&2
    exit 2
  fi
}

append_proxy_build_arg() {
  local -n tag_args_ref=$1
  local build_arg_name=$2
  shift 2

  local env_name value
  for env_name in "$@"; do
    value="${!env_name:-}"
    if [[ -n "$value" ]]; then
      tag_args_ref+=(--build-arg "${build_arg_name}=${value}")
      return 0
    fi
  done
}

release_tags() {
  local ref_type ref_name
  if [[ -n "${CI_COMMIT_TAG:-}" ]]; then
    ref_type="tag"
    ref_name="$CI_COMMIT_TAG"
  else
    ref_type="branch"
    ref_name="${CI_COMMIT_BRANCH:-${CI_COMMIT_REF_NAME:-}}"
  fi

  eval "$(bash scripts/release/release_ref.sh parse-ref "$ref_type" "$ref_name")"
  case "$release_kind" in
    beta)
      printf '%s\n' "beta"
      ;;
    full)
      printf '%s\n' "$release_version" "latest"
      ;;
    *)
      echo "ERROR: unsupported backend release kind: ${release_kind}" >&2
      exit 1
      ;;
  esac
}

gitlab_image() {
  require_env CI_REGISTRY_IMAGE
  printf '%s\n' "${BACKEND_GITLAB_IMAGE:-${CI_REGISTRY_IMAGE}/backend}"
}

dockerhub_image() {
  require_env DOCKERHUB_USERNAME
  require_env DOCKERHUB_TOKEN
  printf '%s\n' "${DOCKERHUB_IMAGE:-docker.io/${DOCKERHUB_USERNAME}/xinyi-relay-backend}"
}

docker_login_all() {
  require_env CI_REGISTRY
  require_env CI_REGISTRY_USER
  require_env CI_REGISTRY_PASSWORD
  require_env DOCKERHUB_USERNAME
  require_env DOCKERHUB_TOKEN

  printf '%s' "$CI_REGISTRY_PASSWORD" \
    | docker login "$CI_REGISTRY" --username "$CI_REGISTRY_USER" --password-stdin
  printf '%s' "$DOCKERHUB_TOKEN" \
    | docker login docker.io --username "$DOCKERHUB_USERNAME" --password-stdin
}

backend_arches() {
  local arches="${XINYI_BACKEND_ARCHES:-amd64}"
  local arch
  for arch in $arches; do
    case "$arch" in
      amd64|arm64) printf '%s\n' "$arch" ;;
      *)
        echo "ERROR: unsupported backend arch: ${arch}" >&2
        exit 2
        ;;
    esac
  done
}

create_builder() {
  BUILDX_BUILDER="xinyi-backend-${CI_JOB_ID:-$$}"
  docker buildx create --name "$BUILDX_BUILDER" --use
  trap 'docker buildx rm "$BUILDX_BUILDER" >/dev/null 2>&1 || true' EXIT
}

build_arch() {
  local arch=${1:-}
  case "$arch" in
    amd64|arm64) ;;
    *)
      usage
      exit 2
      ;;
  esac

  docker_login_all
  docker buildx version
  create_builder

  local gitlab_image dockerhub_image source_url revision ref_name
  gitlab_image="$(gitlab_image)"
  dockerhub_image="$(dockerhub_image)"
  source_url="${CI_PROJECT_URL:-https://gitlab.com/magisk3171/xinyi-relay}"
  revision="${CI_COMMIT_SHA:-unknown}"
  ref_name="${CI_COMMIT_REF_NAME:-${CI_COMMIT_TAG:-unknown}}"

  local tag_args=()
  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    tag_args+=(
      --tag "${gitlab_image}:${tag}-${arch}"
      --tag "${dockerhub_image}:${tag}-${arch}"
    )
  done < <(release_tags)

  if [[ ${#tag_args[@]} -eq 0 ]]; then
    echo "ERROR: no backend image tags resolved" >&2
    exit 1
  fi

  local build_args=(
    --build-arg "GOPROXY=${GOPROXY:-https://goproxy.cn,direct}"
  )
  append_proxy_build_arg build_args HTTP_PROXY RELAY_BUILD_HTTP_PROXY HTTP_PROXY
  append_proxy_build_arg build_args HTTPS_PROXY RELAY_BUILD_HTTPS_PROXY HTTPS_PROXY
  append_proxy_build_arg build_args ALL_PROXY RELAY_BUILD_ALL_PROXY ALL_PROXY
  append_proxy_build_arg build_args NO_PROXY RELAY_BUILD_NO_PROXY NO_PROXY
  append_proxy_build_arg build_args http_proxy RELAY_BUILD_HTTP_PROXY http_proxy HTTP_PROXY
  append_proxy_build_arg build_args https_proxy RELAY_BUILD_HTTPS_PROXY https_proxy HTTPS_PROXY
  append_proxy_build_arg build_args all_proxy RELAY_BUILD_ALL_PROXY all_proxy ALL_PROXY
  append_proxy_build_arg build_args no_proxy RELAY_BUILD_NO_PROXY no_proxy NO_PROXY

  docker buildx build \
    --platform "linux/${arch}" \
    --file backend/api/Dockerfile \
    --push \
    --provenance=false \
    "${build_args[@]}" \
    --label "org.opencontainers.image.title=xinyi-relay-backend" \
    --label "org.opencontainers.image.description=Remote backend for Xinyi Relay" \
    --label "org.opencontainers.image.source=${source_url}" \
    --label "org.opencontainers.image.revision=${revision}" \
    --label "org.opencontainers.image.ref.name=${ref_name}" \
    "${tag_args[@]}" \
    .
}

publish_manifests() {
  docker_login_all
  docker buildx version

  local gitlab_image dockerhub_image
  gitlab_image="$(gitlab_image)"
  dockerhub_image="$(dockerhub_image)"

  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    for image in "$gitlab_image" "$dockerhub_image"; do
      local image_sources=()
      while IFS= read -r arch; do
        [[ -z "$arch" ]] && continue
        image_sources+=("${image}:${tag}-${arch}")
      done < <(backend_arches)
      docker buildx imagetools create \
        --tag "${image}:${tag}" \
        "${image_sources[@]}"
      docker buildx imagetools inspect "${image}:${tag}"
    done
  done < <(release_tags)

  local signal_file="${MAGISK_TELEGRAM_IMAGE_PUBLISH_ENV_FILE:-telegram_images.env}"
  printf 'MAGISK_TELEGRAM_IMAGE_PUBLISH_OK=1\n' >"$signal_file"
  echo "Wrote publish success signal: $signal_file"
}


list_images() {
  local tag
  local gitlab
  gitlab="$(gitlab_image)"
  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    printf '%s:%s\n' "$gitlab" "$tag"
    if [[ -n "${DOCKERHUB_USERNAME:-}" && -n "${DOCKERHUB_TOKEN:-}" ]]; then
      printf '%s:%s\n' "$(dockerhub_image)" "$tag"
    elif [[ -n "${DOCKERHUB_IMAGE:-}" ]]; then
      printf '%s:%s\n' "$DOCKERHUB_IMAGE" "$tag"
    fi
  done < <(release_tags)
}

command=${1:-}
case "$command" in
  build-arch)
    build_arch "${2:-}"
    ;;
  publish-manifests)
    publish_manifests
    ;;
  list-images)
    list_images
    ;;
  *)
    usage
    exit 2
    ;;
esac
