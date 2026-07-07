#!/usr/bin/env python3
"""Generate backend OpenAPI route fragments from Kotlin route contracts."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "modules/relay/contract/src/main/java/io/github/magisk317/relay/contract/remote/OpenApiRouteContracts.kt"
OUTPUT = ROOT / "backend/api/internal/http/openapi_routes.generated.json"

PRIMITIVE_SCHEMAS: dict[str, dict[str, str]] = {
    "String": {"type": "string"},
    "Int": {"type": "integer", "format": "int64"},
    "Long": {"type": "integer", "format": "int64"},
    "Boolean": {"type": "boolean"},
}


def split_top_level(value: str, delimiter: str = ",") -> list[str]:
    parts: list[str] = []
    current: list[str] = []
    depth_round = depth_angle = depth_square = depth_curly = 0
    in_string = False
    escape = False

    for char in value:
        current.append(char)
        if in_string:
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == '"':
                in_string = False
            continue

        if char == '"':
            in_string = True
        elif char == "(":
            depth_round += 1
        elif char == ")":
            depth_round -= 1
        elif char == "<":
            depth_angle += 1
        elif char == ">":
            depth_angle -= 1
        elif char == "[":
            depth_square += 1
        elif char == "]":
            depth_square -= 1
        elif char == "{":
            depth_curly += 1
        elif char == "}":
            depth_curly -= 1
        elif (
            char == delimiter
            and depth_round == 0
            and depth_angle == 0
            and depth_square == 0
            and depth_curly == 0
        ):
            part = "".join(current[:-1]).strip()
            if part:
                parts.append(part)
            current = []

    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def find_constructor_calls(source: str, constructor_name: str) -> list[str]:
    calls: list[str] = []
    marker = f"{constructor_name}("
    index = 0
    while True:
        start = source.find(marker, index)
        if start < 0:
            break
        body_start = start + len(marker)
        depth = 1
        position = body_start
        in_string = False
        escape = False
        while position < len(source) and depth:
            char = source[position]
            if in_string:
                if escape:
                    escape = False
                elif char == "\\":
                    escape = True
                elif char == '"':
                    in_string = False
            else:
                if char == '"':
                    in_string = True
                elif char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
            position += 1
        calls.append(source[body_start : position - 1])
        index = position
    return calls


def parse_named_args(call_body: str) -> dict[str, str]:
    args: dict[str, str] = {}
    for part in split_top_level(call_body):
        if "=" not in part:
            continue
        name, raw_value = part.split("=", maxsplit=1)
        args[name.strip()] = raw_value.strip()
    return args


def parse_string(raw_value: str) -> str | None:
    raw_value = raw_value.strip()
    if raw_value == "null":
        return None
    if len(raw_value) >= 2 and raw_value[0] == '"' and raw_value[-1] == '"':
        return json.loads(raw_value)
    return raw_value


def parse_bool(raw_value: str, default: bool) -> bool:
    raw_value = raw_value.strip()
    if raw_value == "true":
        return True
    if raw_value == "false":
        return False
    return default


def list_body(raw_value: str) -> str:
    raw_value = raw_value.strip()
    if raw_value == "emptyList()":
        return ""
    if raw_value.startswith("listOf(") and raw_value.endswith(")"):
        return raw_value[len("listOf(") : -1]
    return raw_value


def schema_for_name(schema_name: str) -> dict[str, Any]:
    primitive = PRIMITIVE_SCHEMAS.get(schema_name)
    if primitive is not None:
        return dict(primitive)
    return {"$ref": f"#/components/schemas/{schema_name}"}


def parse_parameters(raw_value: str | None) -> list[dict[str, Any]]:
    if raw_value is None:
        return []
    parameters: list[dict[str, Any]] = []
    for call_body in find_constructor_calls(list_body(raw_value), "OpenApiRouteParameter"):
        args = parse_named_args(call_body)
        name = parse_string(args.get("name", '"unknown"')) or "unknown"
        location = parse_string(args.get("location", '"query"')) or "query"
        required = parse_bool(args.get("required", "true"), default=True)
        schema = parse_string(args.get("schema", '"String"')) or "String"
        parameters.append(
            {
                "name": name,
                "in": location,
                "required": required,
                "schema": schema_for_name(schema),
            },
        )
    return parameters


def parse_responses(raw_value: str | None) -> dict[str, dict[str, Any]]:
    if raw_value is None:
        return {
            "200": {
                "description": "OK",
            },
        }
    responses: dict[str, dict[str, Any]] = {}
    for call_body in find_constructor_calls(list_body(raw_value), "OpenApiRouteResponse"):
        args = parse_named_args(call_body)
        status = parse_string(args.get("status", '"200"')) or "200"
        schema = parse_string(args.get("schema", "null"))
        description = parse_string(args.get("description", '""')) or default_response_description(status)
        content_type = parse_string(args.get("contentType", '"application/json"')) or "application/json"
        response: dict[str, Any] = {"description": description}
        if schema is not None:
            response["content"] = {
                content_type: {
                    "schema": schema_for_name(schema),
                },
            }
        responses[status] = response
    return responses


def default_response_description(status: str) -> str:
    return {
        "101": "Switching Protocols",
        "200": "OK",
        "201": "Created",
        "303": "See Other",
    }.get(status, "Response")


def parse_routes(source: str) -> list[dict[str, Any]]:
    routes_source = source[source.find("ALL_ROUTES") :]
    routes: list[dict[str, Any]] = []
    for call_body in find_constructor_calls(routes_source, "OpenApiRouteContract"):
        args = parse_named_args(call_body)
        path = parse_string(args["path"])
        method = parse_string(args["method"])
        operation_id = parse_string(args["operationId"])
        if path is None or method is None or operation_id is None:
            raise SystemExit(f"invalid OpenApiRouteContract: {call_body}")

        route: dict[str, Any] = {
            "path": path,
            "method": method,
            "operationId": operation_id,
            "responses": parse_responses(args.get("responses")),
        }
        request_schema = parse_string(args.get("requestSchema", "null"))
        if request_schema is not None:
            request_content_type = parse_string(args.get("requestContentType", '"application/json"')) or "application/json"
            request_required = parse_bool(args.get("requestRequired", "true"), default=True)
            route["requestBody"] = {
                "required": request_required,
                "content": {
                    request_content_type: {
                        "schema": schema_for_name(request_schema),
                    },
                },
            }
        parameters = parse_parameters(args.get("parameters"))
        if parameters:
            route["parameters"] = parameters
        routes.append(route)
    return routes


def generate() -> str:
    source = SOURCE.read_text(encoding="utf-8")
    routes: dict[str, dict[str, dict[str, Any]]] = {}
    for route in parse_routes(source):
        operation: dict[str, Any] = {
            "operationId": route["operationId"],
            "responses": route["responses"],
        }
        if "requestBody" in route:
            operation["requestBody"] = route["requestBody"]
        if "parameters" in route:
            operation["parameters"] = route["parameters"]
        routes.setdefault(route["path"], {})[route["method"]] = operation
    return json.dumps(routes, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if backend/api/internal/http/openapi_routes.generated.json is not up to date",
    )
    args = parser.parse_args()

    rendered = generate()
    if args.check:
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
        if current != rendered:
            print(f"{OUTPUT.relative_to(ROOT)} is out of date")
            return 1
        return 0

    OUTPUT.write_text(rendered, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
