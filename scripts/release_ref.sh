#!/usr/bin/env bash
set -euo pipefail

emit_shell_var() {
  local name="$1"
  local value="$2"
  printf "%s=%q\n" "$name" "$value"
}

emit_release_metadata() {
  emit_shell_var "release_kind" "$release_kind"
  emit_shell_var "release_component" "$release_component"
  emit_shell_var "release_tag" "$release_tag"
  emit_shell_var "release_version" "$release_version"
  emit_shell_var "release_title" "$release_title"
  emit_shell_var "release_requires_android_metadata" "$release_requires_android_metadata"
  emit_shell_var "release_is_component_only" "$release_is_component_only"
}

parse_tag() {
  local tag_name="$1"

  case "$tag_name" in
    v[0-9]*.[0-9]*.[0-9]*)
      release_kind="full"
      release_component="all"
      release_tag="$tag_name"
      release_version="$tag_name"
      release_title="$tag_name"
      release_requires_android_metadata="true"
      release_is_component_only="false"
      ;;
    mobile-v[0-9]*.[0-9]*.[0-9]*)
      release_kind="mobile"
      release_component="mobile"
      release_tag="$tag_name"
      release_version="${tag_name#mobile-}"
      release_title="Mobile ${release_version}"
      release_requires_android_metadata="true"
      release_is_component_only="true"
      ;;
    desktop-v[0-9]*.[0-9]*.[0-9]*)
      release_kind="desktop"
      release_component="desktop"
      release_tag="$tag_name"
      release_version="${tag_name#desktop-}"
      release_title="Desktop ${release_version}"
      release_requires_android_metadata="false"
      release_is_component_only="true"
      ;;
    backend-v[0-9]*.[0-9]*.[0-9]*)
      release_kind="backend"
      release_component="backend"
      release_tag="$tag_name"
      release_version="${tag_name#backend-}"
      release_title="Backend ${release_version}"
      release_requires_android_metadata="false"
      release_is_component_only="true"
      ;;
    *)
      echo "Unsupported release tag: $tag_name" >&2
      return 1
      ;;
  esac
}

parse_ref() {
  local ref_type="$1"
  local ref_name="$2"

  case "$ref_type" in
    tag)
      parse_tag "$ref_name"
      ;;
    branch)
      if [[ "$ref_name" != "beta" ]]; then
        echo "Unsupported branch release ref: $ref_name" >&2
        return 1
      fi
      release_kind="beta"
      release_component="backend"
      release_tag="$ref_name"
      release_version=""
      release_title="beta"
      release_requires_android_metadata="false"
      release_is_component_only="true"
      ;;
    *)
      echo "Unsupported ref type: $ref_type" >&2
      return 1
      ;;
  esac
}

tag_for_target() {
  local version_name="$1"
  local target="${2:-all}"

  case "$target" in
    all)
      printf "v%s\n" "$version_name"
      ;;
    mobile)
      printf "mobile-v%s\n" "$version_name"
      ;;
    desktop)
      printf "desktop-v%s\n" "$version_name"
      ;;
    backend)
      printf "backend-v%s\n" "$version_name"
      ;;
    *)
      echo "Unsupported release target: $target" >&2
      return 1
      ;;
  esac
}

command="${1:-}"
case "$command" in
  parse-ref)
    parse_ref "${2:-}" "${3:-}"
    emit_release_metadata
    ;;
  parse-tag)
    parse_tag "${2:-}"
    emit_release_metadata
    ;;
  tag-for-target)
    tag_for_target "${2:-}" "${3:-all}"
    ;;
  *)
    cat >&2 <<'EOF'
Usage:
  release_ref.sh parse-ref <ref_type> <ref_name>
  release_ref.sh parse-tag <tag_name>
  release_ref.sh tag-for-target <version_name> <all|mobile|desktop|backend>
EOF
    exit 1
    ;;
esac
