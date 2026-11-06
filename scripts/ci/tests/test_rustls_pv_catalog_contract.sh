#!/usr/bin/env bash
# Cross-file contract for the rustls-platform-verifier coordinate.
#
# Four places describe the same string and none of them imports another:
#   gradle/libs.versions.toml        the only line that decides the version;
#   scripts/ci/rustls_pv_publish.sh  sed-extracts that version, to clone the
#                                    matching upstream tag and to name the
#                                    artifact it PUTs;
#   .gitlab/renovate.json            the regex custom manager that proposes the
#                                    next version, and the registry URL;
#   settings.gradle.kts              routes group "rustls" to that registry.
# When two of them drift, the failure surfaces far away: Renovate proposes a
# version the publish script cannot clone, or the lookup points at a registry
# that holds nothing.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
toml="$root_dir/gradle/libs.versions.toml"
publisher="$root_dir/scripts/ci/rustls_pv_publish.sh"
renovate_cfg="$root_dir/.gitlab/renovate.json"
settings="$root_dir/settings.gradle.kts"

failures=0
check() {
  local label="$1"
  shift
  if "$@"; then
    echo "ok   - $label"
  else
    echo "FAIL - $label"
    failures=$((failures + 1))
  fi
}

for path in "$toml" "$publisher" "$renovate_cfg" "$settings"; do
  [[ -f "$path" ]] || { echo "ERROR: $path is missing" >&2; exit 1; }
done

# 1. the Renovate regex and the publish script's sed must read the same version
#    out of the same line.
check "the two readers of the catalog line agree" python3 - "$renovate_cfg" "$toml" "$publisher" <<'PY'
import json
import re
import subprocess
import sys

cfg_path, toml_path, publisher_path = sys.argv[1:4]

config = json.load(open(cfg_path))
managers = [
    manager
    for manager in config.get("customManagers", [])
    if "rustls:rustls-platform-verifier" in " ".join(manager.get("matchStrings", []))
]
if len(managers) != 1:
    raise SystemExit(f"expected exactly one rustls custom manager, found {len(managers)}")

pattern = managers[0]["matchStrings"][0].replace("(?<currentValue>", "(?P<currentValue>")
line = next(
    candidate for candidate in open(toml_path) if candidate.startswith("rustls-platform-verifier =")
)
match = re.search(pattern, line)
if not match:
    raise SystemExit("the renovate regex no longer matches the catalog line")
regex_value = match.group("currentValue")

extract = next(
    statement
    for statement in open(publisher_path)
    if "rustls-platform-verifier" in statement and "sed -nE" in statement
)
sed_expression = re.search(r"'(.*)'", extract).group(1)
sed_value = subprocess.run(
    ["sed", "-nE", sed_expression, toml_path], capture_output=True, text=True, check=False
).stdout.strip()

if not regex_value or regex_value != sed_value:
    raise SystemExit(f"regex sees {regex_value!r} but the publish script sees {sed_value!r}")
print(f"    both read version {regex_value}")
PY

# 2. one registry id in all three places. settings.gradle.kts declares two
#    private registries, so the rustls one is taken from the block that
#    includeGroup()s the group rather than from the first match in the file.
registry_from_cfg="$(grep -oE 'projects/[0-9]+' "$renovate_cfg" | head -n 1 | sed 's#projects/##')"
registry_from_settings="$(grep -B10 'includeGroup("rustls")' "$settings" |
  grep -oE 'projects/[0-9]+' | tail -n 1 | sed 's#projects/##')"
publisher_id="$(grep -oE 'CI_PROJECT_ID:-[0-9]+' "$publisher" | head -n 1 | sed 's/.*-//')"

same_registry() {
  [[ -n "$registry_from_cfg" ]] || return 1
  [[ -n "$registry_from_settings" ]] || return 1
  [[ "$registry_from_cfg" == "$registry_from_settings" ]]
}
same_publisher_id() {
  [[ -n "$publisher_id" ]] || return 1
  [[ "$publisher_id" == "$registry_from_settings" ]]
}
echo "    registry id: renovate=$registry_from_cfg settings=$registry_from_settings publisher=$publisher_id"
check "renovate.json points at the registry settings.gradle.kts routes to" same_registry
check "the publish script's fallback project id is the same registry" same_publisher_id

# 3. the upstream ref the custom manager extracts is the ref the script clones.
check "the publish script clones v/<catalog version>" \
  grep -qF -- '--branch "v/$VERSION"' "$publisher"

if (( failures > 0 )); then
  echo "ERROR: $failures check(s) failed" >&2
  exit 1
fi
echo "all rustls_pv catalog contract checks passed"
