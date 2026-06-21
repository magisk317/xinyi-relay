#!/usr/bin/env python3
"""Dependabot alert helpers for ecosystems that need more than Gradle forces."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tomllib
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import manage_dependency_forces as gradle_forces


@dataclass
class CargoUpdate:
    alert: int | None
    manifest: str
    package: str
    current: str
    target: str
    status: str
    reason: str = ""


def load_json(path: Path) -> Any:
    raw = json.loads(path.read_text())
    if isinstance(raw, list):
        return raw
    if isinstance(raw, dict) and isinstance(raw.get("alerts"), list):
        return raw["alerts"]
    return raw


def load_alerts(path: Path) -> list[dict[str, Any]]:
    raw = load_json(path)
    if not isinstance(raw, list):
        return []
    return [item for item in raw if isinstance(item, dict)]


def open_alerts(alerts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [alert for alert in alerts if (alert.get("state") or "").lower() == "open"]


def package_info(alert: dict[str, Any]) -> tuple[str, str]:
    package = alert.get("dependency", {}).get("package", {})
    return (package.get("ecosystem") or "").lower(), package.get("name") or ""


def patched_version(alert: dict[str, Any]) -> str:
    patched = (alert.get("security_vulnerability") or {}).get("first_patched_version") or {}
    return patched.get("identifier") or patched.get("version") or ""


def vulnerable_range(alert: dict[str, Any]) -> str:
    return (alert.get("security_vulnerability") or {}).get("vulnerable_version_range") or ""


def version_key(version: str) -> list[tuple[int, int | str]]:
    parts = re.findall(r"\d+|[A-Za-z]+", version)
    key: list[tuple[int, int | str]] = []
    for part in parts:
        if part.isdigit():
            key.append((0, int(part)))
        else:
            key.append((1, part.lower()))
    return key


def version_gte(left: str, right: str) -> bool:
    return version_key(left) >= version_key(right)


def version_cmp(left: str, right: str) -> int:
    left_key = version_key(left)
    right_key = version_key(right)
    if left_key == right_key:
        return 0
    return -1 if left_key < right_key else 1


def version_in_range(version: str, version_range: str) -> bool | None:
    if not version_range.strip():
        return None

    saw_clause = False
    for raw_clause in version_range.split(","):
        clause = raw_clause.strip()
        if not clause:
            continue
        match = re.match(r"([><=!]+)\s*(.+)", clause)
        if not match:
            return None
        op, target = match.group(1), match.group(2).strip()
        cmp_result = version_cmp(version, target)
        saw_clause = True

        if op == ">=" and cmp_result < 0:
            return False
        if op == ">" and cmp_result <= 0:
            return False
        if op == "<=" and cmp_result > 0:
            return False
        if op == "<" and cmp_result >= 0:
            return False
        if op in ("=", "==") and cmp_result != 0:
            return False
        if op == "!=" and cmp_result == 0:
            return False
        if op not in (">=", ">", "<=", "<", "=", "==", "!="):
            return None

    return True if saw_clause else None


def cargo_manifest_for_alert(root: Path, manifest_path: str) -> Path | None:
    path = root / manifest_path
    if path.name == "Cargo.toml":
        return path
    if path.name == "Cargo.lock":
        candidate = path.with_name("Cargo.toml")
        return candidate if candidate.exists() else None
    return None


def cargo_lock_for_manifest(manifest_path: Path) -> Path:
    return manifest_path.with_name("Cargo.lock")


def read_cargo_lock_versions(lock_path: Path, package_name: str) -> list[str]:
    if not lock_path.exists():
        return []
    data = tomllib.loads(lock_path.read_text())
    versions = [
        item.get("version")
        for item in data.get("package", [])
        if isinstance(item, dict) and item.get("name") == package_name and item.get("version")
    ]
    return sorted(set(str(version) for version in versions), key=version_key)


def collect_cargo_update_plan(root: Path, alerts: list[dict[str, Any]]) -> list[CargoUpdate]:
    planned: dict[tuple[str, str, str], CargoUpdate] = {}
    skipped: list[CargoUpdate] = []

    for alert in open_alerts(alerts):
        ecosystem, package_name = package_info(alert)
        if ecosystem != "rust":
            continue
        target = patched_version(alert)
        manifest_ref = alert.get("dependency", {}).get("manifest_path") or ""
        manifest_path = cargo_manifest_for_alert(root, manifest_ref)
        if not target or manifest_path is None:
            skipped.append(
                CargoUpdate(
                    alert=alert.get("number"),
                    manifest=manifest_ref,
                    package=package_name,
                    current="",
                    target=target,
                    status="skipped",
                    reason="missing patched version or Cargo.toml",
                )
            )
            continue

        versions = read_cargo_lock_versions(cargo_lock_for_manifest(manifest_path), package_name)
        matched = False
        for current in versions:
            in_range = version_in_range(current, vulnerable_range(alert))
            if in_range is not True:
                continue
            matched = True
            key = (str(manifest_path.relative_to(root)), package_name, current)
            existing = planned.get(key)
            if existing is None or version_gte(target, existing.target):
                planned[key] = CargoUpdate(
                    alert=alert.get("number"),
                    manifest=key[0],
                    package=package_name,
                    current=current,
                    target=target,
                    status="planned",
                )
        if not matched:
            skipped.append(
                CargoUpdate(
                    alert=alert.get("number"),
                    manifest=str(manifest_path.relative_to(root)),
                    package=package_name,
                    current="",
                    target=target,
                    status="skipped",
                    reason="no locked vulnerable version matched the alert range",
                )
            )

    return sorted(
        [*planned.values(), *skipped],
        key=lambda item: (item.manifest, item.package, item.current, item.target),
    )


def run_cargo_update(root: Path, update: CargoUpdate) -> CargoUpdate:
    manifest = root / update.manifest
    spec = f"{update.package}@{update.current}"
    cmd = [
        "cargo",
        "update",
        "--manifest-path",
        str(manifest),
        "-p",
        spec,
        "--precise",
        update.target,
    ]
    print(f"Updating Cargo lock: {spec} -> {update.target} ({update.manifest})", flush=True)
    proc = subprocess.run(cmd, cwd=root, capture_output=True, text=True, check=False)
    if proc.stdout:
        print(proc.stdout, end="")
    if proc.stderr:
        print(proc.stderr, end="", file=sys.stderr)
    if proc.returncode == 0:
        update.status = "updated"
        update.reason = ""
    else:
        update.status = "unresolved"
        update.reason = (proc.stderr or proc.stdout or f"cargo update exited {proc.returncode}").strip()
    return update


def command_apply_cargo_updates(args: argparse.Namespace) -> None:
    root = Path(args.workspace_root).resolve()
    alerts = load_alerts(Path(args.alerts_json))
    results: list[CargoUpdate] = []
    for update in collect_cargo_update_plan(root, alerts):
        if update.status == "planned":
            update = run_cargo_update(root, update)
        results.append(update)

    output = {
        "updated": [asdict(item) for item in results if item.status == "updated"],
        "unresolved": [asdict(item) for item in results if item.status == "unresolved"],
        "skipped": [asdict(item) for item in results if item.status == "skipped"],
    }
    Path(args.output).write_text(json.dumps(output, indent=2) + "\n")
    print(
        "Cargo alert updates: "
        f"updated={len(output['updated'])}, "
        f"unresolved={len(output['unresolved'])}, "
        f"skipped={len(output['skipped'])}"
    )


def read_gradle_force_map(build_file: Path, toml_file: Path) -> dict[str, str]:
    versions, libraries = gradle_forces.load_catalog(toml_file)
    lines = build_file.read_text().splitlines()
    result: dict[str, str] = {}
    for start, end in gradle_forces.iter_managed_blocks(lines):
        for line in lines[start + 1 : end]:
            parsed = gradle_forces.parse_force_line(line, versions, libraries)
            if not parsed:
                continue
            dep = f"{parsed['group']}:{parsed['artifact']}"
            current = result.get(dep)
            if current is None or version_gte(parsed["version"], current):
                result[dep] = parsed["version"]
    return result


def summarize_maven_alert(alert: dict[str, Any], force_map: dict[str, str]) -> dict[str, Any]:
    _, package_name = package_info(alert)
    target = patched_version(alert)
    forced = force_map.get(package_name, "")
    covered = bool(target and forced and version_gte(forced, target))
    return {
        "number": alert.get("number"),
        "ecosystem": "maven",
        "package": package_name,
        "manifest": alert.get("dependency", {}).get("manifest_path") or "",
        "patched": target,
        "forced": forced,
        "status": "covered" if covered else "uncovered",
        "reason": "" if covered else "no force rule at or above patched version",
    }


def summarize_rust_alert(root: Path, alert: dict[str, Any]) -> dict[str, Any]:
    _, package_name = package_info(alert)
    target = patched_version(alert)
    manifest_ref = alert.get("dependency", {}).get("manifest_path") or ""
    manifest = cargo_manifest_for_alert(root, manifest_ref)
    if manifest is None:
        return {
            "number": alert.get("number"),
            "ecosystem": "rust",
            "package": package_name,
            "manifest": manifest_ref,
            "patched": target,
            "status": "uncovered",
            "reason": "Cargo.toml not found for alert manifest",
            "vulnerable_versions": [],
        }

    versions = read_cargo_lock_versions(cargo_lock_for_manifest(manifest), package_name)
    vulnerable_versions = [
        version
        for version in versions
        if version_in_range(version, vulnerable_range(alert)) is True
    ]
    return {
        "number": alert.get("number"),
        "ecosystem": "rust",
        "package": package_name,
        "manifest": str(manifest.relative_to(root)),
        "patched": target,
        "status": "covered" if not vulnerable_versions else "uncovered",
        "reason": "" if not vulnerable_versions else "locked vulnerable version remains",
        "vulnerable_versions": vulnerable_versions,
    }


def reconcile_alerts(
    root: Path,
    alerts: list[dict[str, Any]],
    build_file: Path,
    toml_file: Path,
) -> dict[str, Any]:
    force_map = read_gradle_force_map(build_file, toml_file)
    details: list[dict[str, Any]] = []

    for alert in open_alerts(alerts):
        ecosystem, package_name = package_info(alert)
        if ecosystem == "maven":
            details.append(summarize_maven_alert(alert, force_map))
        elif ecosystem == "rust":
            details.append(summarize_rust_alert(root, alert))
        else:
            details.append(
                {
                    "number": alert.get("number"),
                    "ecosystem": ecosystem,
                    "package": package_name,
                    "manifest": alert.get("dependency", {}).get("manifest_path") or "",
                    "patched": patched_version(alert),
                    "status": "uncovered",
                    "reason": "unsupported ecosystem",
                }
            )

    covered = sum(1 for item in details if item["status"] == "covered")
    uncovered = sum(1 for item in details if item["status"] != "covered")
    return {
        "total": len(details),
        "covered": covered,
        "uncovered": uncovered,
        "details": sorted(
            details,
            key=lambda item: (
                item.get("status") != "uncovered",
                item.get("ecosystem") or "",
                item.get("package") or "",
                item.get("number") or 0,
            ),
        ),
    }


def write_reconciliation_summary(summary: dict[str, Any]) -> None:
    for item in summary["details"]:
        prefix = "COVERED" if item["status"] == "covered" else "UNRESOLVED"
        extra = ""
        if item.get("ecosystem") == "maven":
            extra = f" forced={item.get('forced') or 'n/a'} patched={item.get('patched') or 'n/a'}"
        elif item.get("ecosystem") == "rust":
            versions = ",".join(item.get("vulnerable_versions") or [])
            extra = f" vulnerable={versions or 'none'} patched={item.get('patched') or 'n/a'}"
        reason = item.get("reason")
        if reason:
            extra = f"{extra} reason={reason}"
        print(
            f"{prefix} #{item.get('number')} {item.get('ecosystem')} "
            f"{item.get('package')} ({item.get('manifest')}){extra}"
        )

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a") as handle:
            handle.write("## Dependabot alert reconciliation\n\n")
            handle.write(f"- Open alerts scanned: {summary['total']}\n")
            handle.write(f"- Covered: {summary['covered']}\n")
            handle.write(f"- Unresolved: {summary['uncovered']}\n")


def command_reconcile(args: argparse.Namespace) -> None:
    root = Path(args.workspace_root).resolve()
    summary = reconcile_alerts(
        root,
        load_alerts(Path(args.alerts_json)),
        root / args.build_file,
        root / args.toml_file,
    )
    Path(args.output).write_text(json.dumps(summary, indent=2) + "\n")
    write_reconciliation_summary(summary)


def snapshot_packages(snapshot_path: Path) -> dict[str, set[str]]:
    data = json.loads(snapshot_path.read_text())
    packages: dict[str, set[str]] = {}

    if isinstance(data.get("manifests"), dict):
        for manifest in data["manifests"].values():
            resolved = manifest.get("resolved") or {}
            if not isinstance(resolved, dict):
                continue
            for coord in resolved:
                if not isinstance(coord, str) or coord.count(":") < 2:
                    continue
                group, artifact, version = coord.split(":", 2)
                packages.setdefault(f"{group}:{artifact}", set()).add(version)
    elif isinstance(data.get("sbom", {}).get("packages"), list):
        for package in data["sbom"]["packages"]:
            name = package.get("name")
            version = package.get("versionInfo")
            if name and version:
                packages.setdefault(name, set()).add(version)
    else:
        raise SystemExit(f"Unsupported dependency graph snapshot format: {snapshot_path}")

    return packages


def command_validate_graph(args: argparse.Namespace) -> None:
    packages = snapshot_packages(Path(args.snapshot))
    alerts = load_alerts(Path(args.alerts_json))
    vulnerable_matches: list[dict[str, Any]] = []
    scanned = 0
    matched = 0

    for alert in open_alerts(alerts):
        ecosystem, package_name = package_info(alert)
        if ecosystem != "maven":
            continue
        scanned += 1
        versions = sorted(packages.get(package_name, set()), key=version_key)
        if not versions:
            continue
        matched += 1
        for version in versions:
            if version_in_range(version, vulnerable_range(alert)) is True:
                vulnerable_matches.append(
                    {
                        "number": alert.get("number"),
                        "package": package_name,
                        "version": version,
                        "severity": (alert.get("security_vulnerability") or {}).get("severity"),
                        "manifest": alert.get("dependency", {}).get("manifest_path") or "",
                    }
                )

    for item in vulnerable_matches:
        print(
            "VULNERABLE_GRAPH "
            f"#{item['number']} {item['package']}:{item['version']} "
            f"({item['severity']}, manifest={item['manifest']})"
        )

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a") as handle:
            handle.write("## Dependabot graph validation\n\n")
            handle.write(f"- Maven open alerts scanned: {scanned}\n")
            handle.write(f"- Alerts with package entries in graph: {matched}\n")
            handle.write(f"- Vulnerable graph entries: {len(vulnerable_matches)}\n")

    if vulnerable_matches:
        raise SystemExit("Dependency graph still contains versions covered by open Dependabot alerts.")
    print("Dependency graph has no package versions matching open Maven Dependabot alert ranges.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    apply_cargo = subparsers.add_parser("apply-cargo-updates")
    apply_cargo.add_argument("--alerts-json", required=True)
    apply_cargo.add_argument("--workspace-root", default=".")
    apply_cargo.add_argument("--output", required=True)
    apply_cargo.set_defaults(func=command_apply_cargo_updates)

    reconcile = subparsers.add_parser("reconcile")
    reconcile.add_argument("--alerts-json", required=True)
    reconcile.add_argument("--workspace-root", default=".")
    reconcile.add_argument("--build-file", default="build.gradle.kts")
    reconcile.add_argument("--toml-file", default="gradle/libs.versions.toml")
    reconcile.add_argument("--output", required=True)
    reconcile.set_defaults(func=command_reconcile)

    validate_graph = subparsers.add_parser("validate-graph")
    validate_graph.add_argument("--alerts-json", required=True)
    validate_graph.add_argument("--snapshot", required=True)
    validate_graph.set_defaults(func=command_validate_graph)

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
