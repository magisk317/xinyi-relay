#!/usr/bin/env python3
"""Generate the shared sender schema contract from Kotlin source."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
SENDER_TYPE_SOURCE = (
    ROOT
    / "relay/engine/api/src/main/java/io/github/magisk317/relay/engine/sender/SenderType.kt"
)
SCHEMA_SOURCE = (
    ROOT
    / "relay/sender/api/src/main/java/io/github/magisk317/relay/sender/SenderSettingSchema.kt"
)
OUTPUT = ROOT / "shared/contracts/senderSchemas.json"


def read_sender_types() -> dict[str, int]:
    source = SENDER_TYPE_SOURCE.read_text(encoding="utf-8")
    return {
        name: int(value)
        for name, value in re.findall(r"const\s+val\s+([A-Z0-9_]+)\s*=\s*(\d+)", source)
    }


def matching_call_bodies(source: str, function_name: str) -> Iterable[str]:
    marker = f"{function_name}("
    index = 0
    while True:
        start = source.find(marker, index)
        if start < 0:
            return
        body_start = start + len(marker)
        depth = 1
        position = body_start
        while position < len(source) and depth:
            char = source[position]
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            position += 1
        if depth != 0:
            raise ValueError(f"Unbalanced call to {function_name} near offset {start}")
        yield source[body_start : position - 1]
        index = position


def parse_field(body: str) -> dict[str, object]:
    strings = re.findall(r'"([^"]*)"', body)
    if not strings:
        raise ValueError(f"Unable to parse field name from: {body}")
    field_type = "TEXT"
    type_match = re.search(r"SenderSettingFieldType\.([A-Z_]+)", body)
    if type_match:
        field_type = type_match.group(1)
    aliases_match = re.search(r"aliases\s*=\s*arrayOf\(([^)]*)\)", body)
    options_match = re.search(r"options\s*=\s*arrayOf\(([^)]*)\)", body)
    default_match = re.search(r"defaultValue\s*=\s*\"([^\"]*)\"", body)

    if aliases_match:
        aliases = re.findall(r'"([^"]*)"', aliases_match.group(1))
    else:
        positional_body = re.sub(r"aliases\s*=\s*arrayOf\([^)]*\)", "", body)
        positional_body = re.sub(r"options\s*=\s*arrayOf\([^)]*\)", "", positional_body)
        positional_body = re.sub(r"defaultValue\s*=\s*\"[^\"]*\"", "", positional_body)
        aliases = re.findall(r'"([^"]*)"', positional_body)[1:]

    field: dict[str, object] = {
        "name": strings[0],
        "type": field_type,
        "aliases": aliases,
        "requiredForEnable": bool(re.search(r"requiredForEnable\s*=\s*true", body)),
    }
    if default_match:
        field["defaultValue"] = default_match.group(1)
    if options_match:
        field["options"] = [
            {"value": value}
            for value in re.findall(r'"([^"]*)"', options_match.group(1))
        ]
    return field


def generate_contract() -> list[dict[str, object]]:
    sender_types = read_sender_types()
    source = SCHEMA_SOURCE.read_text(encoding="utf-8")
    schemas: list[dict[str, object]] = []
    for schema_body in matching_call_bodies(source, "schema"):
        type_match = re.search(r"SenderType\.([A-Z0-9_]+)", schema_body)
        if not type_match:
            continue
        type_name = type_match.group(1)
        if type_name not in sender_types:
            raise ValueError(f"Unknown SenderType.{type_name}")
        fields = [parse_field(body) for body in matching_call_bodies(schema_body, "field")]
        schemas.append(
            {
                "senderType": sender_types[type_name],
                "fields": fields,
            }
        )
    return schemas


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if shared/contracts/senderSchemas.json is not up to date",
    )
    args = parser.parse_args()

    encoded = json.dumps(generate_contract(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        existing = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
        if existing != encoded:
            print(f"{OUTPUT.relative_to(ROOT)} is out of date")
            return 1
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(encoded, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
