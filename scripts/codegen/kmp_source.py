#!/usr/bin/env python3
"""Locate a source file inside a Kotlin multiplatform module.

KMP modules keep their sources under `src/<sourceSet>/{kotlin,java}/<package>/`,
so a hardcoded `src/main/java/...` path breaks the moment a file moves into
`commonMain` or `androidMain`. Resolution therefore searches every source set
and prefers the platform-neutral one, which is where the shared contract models
live.
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# A contract that exists in more than one source set must resolve to the
# platform-neutral copy, otherwise the generators emit an Android-shaped schema.
PREFERRED_SOURCE_SETS = ("commonMain", "main", "jvmMain", "androidMain")


def resolve_source(module: str, package: str, file_name: str) -> Path:
    module_root = ROOT / module
    if not module_root.is_dir():
        raise SystemExit(f"cannot find module directory {module_root}")

    # `src/*/*/` covers both the `kotlin/` and the legacy `java/` source roots.
    matches = sorted(module_root.glob(f"src/*/*/{package}/{file_name}"))
    if not matches:
        raise SystemExit(
            f"cannot find {file_name} ({package}) under any source set of {module_root}"
        )

    for source_set in PREFERRED_SOURCE_SETS:
        for match in matches:
            if match.parents[2].name == source_set:
                return match
    return matches[0]
