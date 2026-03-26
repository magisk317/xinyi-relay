#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path
from typing import Any

import tomllib


def load_catalog(toml_path: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    libs = tomllib.loads(toml_path.read_text())
    return libs.get("versions", {}), libs.get("libraries", {})


def resolve_alias(expr: str, versions: dict[str, Any], libraries: dict[str, Any]) -> dict[str, str] | None:
    alias = expr.strip()
    for prefix in ("catalog.", "libs."):
        if alias.startswith(prefix):
            alias = alias[len(prefix):]
            break
    else:
        return None

    alias = alias.replace(".", "-")
    lib = libraries.get(alias)
    if not lib:
        return None

    module = lib.get("module")
    version_ref = lib.get("version", {}).get("ref")
    version = versions.get(version_ref) if version_ref else None
    if not module or not version:
        return None

    group, artifact = module.split(":", 1)
    return {"group": group, "artifact": artifact, "version": version}


def parse_force_line(line: str, versions: dict[str, Any], libraries: dict[str, Any]) -> dict[str, str] | None:
    match = re.search(r"force\((.+)\)", line)
    if not match:
        return None

    expr = match.group(1).strip()
    resolved = resolve_alias(expr, versions, libraries)
    if resolved:
        return {**resolved, "line": line}

    if expr.startswith('"') or expr.startswith("'"):
        coord = expr.strip("\"'")
        parts = coord.split(":")
        if len(parts) >= 3:
            return {
                "group": parts[0],
                "artifact": parts[1],
                "version": ":".join(parts[2:]),
                "line": line,
            }

    return None


def command_read_forces(args: argparse.Namespace) -> None:
    versions, libraries = load_catalog(Path(args.toml_file))
    forces = []
    for line in Path(args.build_file).read_text().splitlines():
        resolved = parse_force_line(line, versions, libraries)
        if resolved:
            forces.append(resolved)

    Path(args.output).write_text(json.dumps(forces, indent=2) + "\n")
    print(f"Forced entries: {len(forces)}")


def command_strip_force_lines(args: argparse.Namespace) -> None:
    path = Path(args.build_file)
    original = path.read_text()
    lines = original.splitlines()
    new_lines = [line for line in lines if "force(" not in line]
    path.write_text("\n".join(new_lines) + ("\n" if original.endswith("\n") else ""))


def extract_resolved_version(text: str, group: str, artifact: str) -> str:
    pattern = re.compile(rf"{re.escape(group)}:{re.escape(artifact)}:([\w.\-]+)")
    match = pattern.search(text)
    return match.group(1) if match else ""


def command_resolve_natural(args: argparse.Namespace) -> None:
    forced = json.loads(Path(args.forced_json).read_text())
    results: dict[str, dict[str, dict[str, Any]]] = {}

    for item in forced:
        group = item["group"]
        artifact = item["artifact"]
        dep = f"{group}:{artifact}"
        results[dep] = {}

        for config in args.config:
            print(f"Resolving {dep} in {config}...")
            cmd = [
                args.gradlew,
                f"{args.project}:dependencyInsight",
                "--dependency",
                dep,
                "--configuration",
                config,
                "--console=plain",
            ]
            proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
            text = proc.stdout + "\n" + proc.stderr

            config_missing = (
                proc.returncode != 0 and "configuration" in text.lower() and "not found" in text.lower()
            )
            results[dep][config] = {
                "resolved": extract_resolved_version(text, group, artifact),
                "returncode": proc.returncode,
                "config_missing": config_missing,
            }

            if config_missing:
                print(f"  INFO: configuration {config} not found, skipping")
            elif not results[dep][config]["resolved"]:
                print(f"  WARN: no resolved version found for {dep} in {config}")

    Path(args.output).write_text(json.dumps(results, indent=2) + "\n")


def command_determine_removable(args: argparse.Namespace) -> None:
    forced = json.loads(Path(args.forced_json).read_text())
    natural = json.loads(Path(args.natural_json).read_text())
    removable: set[str] = set()

    for item in forced:
        dep = f"{item['group']}:{item['artifact']}"
        resolved_by_config = natural.get(dep, {})
        successful = [
            entry["resolved"]
            for entry in resolved_by_config.values()
            if not entry.get("config_missing") and entry.get("resolved")
        ]
        if not successful:
            continue
        if all(version == item["version"] for version in successful):
            removable.add(dep)

    Path(args.output).write_text(json.dumps(sorted(removable), indent=2) + "\n")
    print(f"Removable forces: {len(removable)}")


def load_alerts(alerts_path: Path) -> list[dict[str, Any]]:
    raw = json.loads(alerts_path.read_text())
    if isinstance(raw, list):
        return [item for item in raw if isinstance(item, dict)]
    if isinstance(raw, dict):
        message = raw.get("message")
        if message:
            print(f"WARN: dependabot alerts response is not a list: {message}")
        alerts = raw.get("alerts")
        if isinstance(alerts, list):
            return [item for item in alerts if isinstance(item, dict)]
    return []


def line_dep(expr: str, versions: dict[str, Any], libraries: dict[str, Any]) -> str | None:
    resolved = resolve_alias(expr, versions, libraries)
    if resolved:
        return f"{resolved['group']}:{resolved['artifact']}"
    if ":" in expr:
        parts = expr.split(":")
        if len(parts) >= 2:
            return f"{parts[0]}:{parts[1]}"
    return None


def command_apply_updates(args: argparse.Namespace) -> None:
    build_path = Path(args.build_file)
    versions, libraries = load_catalog(Path(args.toml_file))
    text = build_path.read_text().splitlines()

    removable = set(json.loads(Path(args.removable_json).read_text()))
    alerts = load_alerts(Path(args.alerts_json))
    security_forces: dict[str, str] = {}
    for alert in alerts:
        dep = alert.get("dependency", {}).get("package", {}).get("name")
        patched = alert.get("security_vulnerability", {}).get("first_patched_version", {}) or {}
        target = patched.get("identifier") or patched.get("version")
        if dep and target:
            security_forces[dep] = target

    filtered: list[str] = []
    for line in text:
        match = re.search(r"force\((.+)\)", line)
        if not match:
            filtered.append(line)
            continue

        expr = match.group(1).strip().strip("\"'")
        dep = line_dep(expr, versions, libraries)
        if dep and (dep in removable or dep in security_forces):
            continue
        filtered.append(line)

    begin = "            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)"
    end = "            // END AUTO FORCED DEPENDENCIES (managed by workflow)"
    try:
        start = filtered.index(begin)
        stop = filtered.index(end)
    except ValueError as exc:
        raise SystemExit("Auto force block not found in build.gradle.kts") from exc

    block_lines = [begin]
    for dep in sorted(security_forces):
        block_lines.append(f"            force(\"{dep}:{security_forces[dep]}\")")
    block_lines.append(end)

    new_text = filtered[:start] + block_lines + filtered[stop + 1 :]
    build_path.write_text("\n".join(new_text) + "\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    read_forces = subparsers.add_parser("read-forces")
    read_forces.add_argument("--build-file", required=True)
    read_forces.add_argument("--toml-file", required=True)
    read_forces.add_argument("--output", required=True)
    read_forces.set_defaults(func=command_read_forces)

    strip_force_lines = subparsers.add_parser("strip-force-lines")
    strip_force_lines.add_argument("--build-file", required=True)
    strip_force_lines.set_defaults(func=command_strip_force_lines)

    resolve_natural = subparsers.add_parser("resolve-natural")
    resolve_natural.add_argument("--gradlew", default="./gradlew")
    resolve_natural.add_argument("--project", default=":app")
    resolve_natural.add_argument("--forced-json", required=True)
    resolve_natural.add_argument("--output", required=True)
    resolve_natural.add_argument("--config", action="append", default=[])
    resolve_natural.set_defaults(func=command_resolve_natural)

    determine_removable = subparsers.add_parser("determine-removable")
    determine_removable.add_argument("--forced-json", required=True)
    determine_removable.add_argument("--natural-json", required=True)
    determine_removable.add_argument("--output", required=True)
    determine_removable.set_defaults(func=command_determine_removable)

    apply_updates = subparsers.add_parser("apply-updates")
    apply_updates.add_argument("--build-file", required=True)
    apply_updates.add_argument("--toml-file", required=True)
    apply_updates.add_argument("--removable-json", required=True)
    apply_updates.add_argument("--alerts-json", required=True)
    apply_updates.set_defaults(func=command_apply_updates)

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
