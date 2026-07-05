#!/usr/bin/env python3
"""Emit package toolchain values declared by package.json."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def load_package_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"{path} does not exist") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path} is not valid JSON: {exc}") from exc


def pnpm_version(package_json: dict[str, Any], package_path: Path) -> str:
    package_manager = package_json.get("packageManager", "")
    if not isinstance(package_manager, str) or not package_manager.startswith("pnpm@"):
        raise SystemExit(f"{package_path} must declare packageManager as pnpm@<version>")
    return package_manager.split("@", 1)[1]


def node_version(package_json: dict[str, Any], package_path: Path) -> str:
    engines = package_json.get("engines", {})
    version = engines.get("node") if isinstance(engines, dict) else None
    if not isinstance(version, str) or not version:
        raise SystemExit(f"{package_path} is missing engines.node")
    return version


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("package_json", type=Path)
    parser.add_argument(
        "--require-node",
        action="store_true",
        help="Require and emit engines.node as node_version.",
    )
    args = parser.parse_args()

    package_path = args.package_json
    package_json = load_package_json(package_path)

    if args.require_node:
        print(f"node_version={node_version(package_json, package_path)}")
    print(f"pnpm_version={pnpm_version(package_json, package_path)}")


if __name__ == "__main__":
    main()
