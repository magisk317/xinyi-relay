#!/usr/bin/env bash
# Kotlin 2.5 flags every discarded result. These repos used to mute the two diagnostics
# build-wide with -Xwarning-level in the root build, which also hid the next accidental
# discard. Tolerated failures must stay explicit at the call site (`.onFailure { ... }`),
# and the only file-scoped exemption allowed is the frozen vendor tree, which carries its
# own reason comment.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
status=0

muted=$(git ls-files -z '*.gradle.kts' | grep -z -v '^vendor/build.gradle.kts$' | xargs -0 -r grep -l -- '-Xwarning-level=RETURN_VALUE_NOT_USED' || true)
if [ -n "${muted}" ]; then
  printf '::error::%s mutes RETURN_VALUE_NOT_USED build-wide; convert the call sites instead\n' "${muted}"
  status=1
fi
# The one allowed mute is the frozen vendor module, and only when it says why.
if grep -q -- '-Xwarning-level=RETURN_VALUE_NOT_USED' vendor/build.gradle.kts 2>/dev/null; then
  grep -q 'scoped to this module' vendor/build.gradle.kts || {
    printf '::error::vendor/build.gradle.kts mutes the diagnostic without its reason comment\n'; status=1; }
fi

while IFS= read -r file; do
  [ -n "${file}" ] || continue
  case "${file}" in
    vendor/*) ;;
    *)
      printf '::error::%s carries a file-scoped RETURN_VALUE_NOT_USED exemption outside the frozen vendor tree\n' "${file}"
      status=1
      ;;
  esac
done < <(git ls-files -z '*.kt' | xargs -0 -r grep -l -- '@file:Suppress("RETURN_VALUE_NOT_USED"' || true)

if [ "${status}" -eq 0 ]; then
  printf 'no build-wide or stray RETURN_VALUE_NOT_USED mute: ok\n'
fi
exit "${status}"
