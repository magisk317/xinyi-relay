#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_DIR="${ROOT_DIR}/.gradle"

if [[ ! -d "${GRADLE_DIR}" ]]; then
  echo "No local .gradle directory found at ${GRADLE_DIR}"
  exit 0
fi

cd "${GRADLE_DIR}"

mapfile -t versions < <(find . -maxdepth 1 -mindepth 1 -type d -printf '%f\n' | grep -E '^[0-9]' | sort)

if [[ ${#versions[@]} -le 1 ]]; then
  echo "No stale Gradle version caches to clean."
  exit 0
fi

latest="${versions[-1]}"

for version in "${versions[@]}"; do
  if [[ "${version}" == "${latest}" ]]; then
    continue
  fi
  echo "Cleaning up old Gradle cache: ${version}"
  rm -rf "${version}"
done
