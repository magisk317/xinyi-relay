#!/usr/bin/env bash
# Emit the Maven version listing (maven-metadata.xml) for a coordinate that
# this repository publishes with raw PUTs instead of Gradle's maven-publish
# plugin.
#
# Why this exists: in the Maven repository layout the only thing that
# advertises which versions a coordinate has is
# <group>/<artifact>/maven-metadata.xml. A `curl PUT` of a POM and an AAR
# does not create it, so scripts/ci/rustls_pv_publish.sh used to leave the
# registry holding a resolvable artifact with an empty version listing.
# Consumers that pin the exact version (gradle/libs.versions.toml) never
# notice, but anything that enumerates versions does: Gradle dynamic versions
# such as "0.7.+" or "latest.release", and Renovate's maven datasource, which
# reads that one file and reports `no-result` when it is missing.
#
# Usage: rustls_pv_metadata.sh <groupId> <artifactId> <currentVersion> [knownVersion...]
# The document goes to stdout. The shape is byte-for-byte the one Gradle's
# maven-publish uploads, so the file is indistinguishable from a
# plugin-generated one (see scripts/ci/tests/test_rustls_pv_metadata.sh).
#
# Ordering is numeric per dot-separated component, so 0.7.1 < 0.9.0 < 0.10.0.
# Upstream rustls-platform-verifier only ships plain x.y.z releases, and no
# prerelease ordering (0.8.0-rc.1 < 0.8.0) is implemented; revisit this if
# that axis ever grows prereleases.
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: $(basename "$0") <groupId> <artifactId> <currentVersion> [knownVersion...]" >&2
  exit 2
fi

group_id="$1"
artifact_id="$2"
current_version="$3"
shift 3

version_pattern='^[A-Za-z0-9._+-]+$'
[[ "$group_id" =~ $version_pattern ]] || { echo "ERROR: invalid groupId '$group_id'" >&2; exit 2; }
[[ "$artifact_id" =~ $version_pattern ]] || { echo "ERROR: invalid artifactId '$artifact_id'" >&2; exit 2; }
[[ "$current_version" =~ $version_pattern ]] || { echo "ERROR: invalid version '$current_version'" >&2; exit 2; }
for candidate in "$@"; do
  [[ -n "$candidate" ]] || continue
  [[ "$candidate" =~ $version_pattern ]] || { echo "ERROR: invalid version '$candidate'" >&2; exit 2; }
done

python3 - "$group_id" "$artifact_id" "$current_version" "$@" <<'PY'
import datetime
import re
import sys
import xml.sax.saxutils as sax

group_id, artifact_id, current_version, *known = sys.argv[1:]
versions = sorted({current_version, *(v for v in known if v)},
                  key=lambda v: [
                      (0, int(m.group(1)), m.group(2)) if (m := re.match(r'^(\d+)(.*)$', part))
                      else (1, 0, part)
                      for part in v.split('.')
                  ])
latest = versions[-1]
stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
quote = sax.escape

lines = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<metadata>',
    f'  <groupId>{quote(group_id)}</groupId>',
    f'  <artifactId>{quote(artifact_id)}</artifactId>',
    '  <versioning>',
    f'    <latest>{quote(latest)}</latest>',
    f'    <release>{quote(latest)}</release>',
    '    <versions>',
    *(f'      <version>{quote(v)}</version>' for v in versions),
    '    </versions>',
    f'    <lastUpdated>{stamp}</lastUpdated>',
    '  </versioning>',
    '</metadata>',
]
sys.stdout.write('\n'.join(lines) + '\n')
PY
