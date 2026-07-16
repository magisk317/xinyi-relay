#!/usr/bin/env python3
"""Generate backend OpenAPI schema fragments from Kotlin contract DTOs."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCES = [
    ROOT / "modules/relay/contract/src/main/java/io/github/magisk317/relay/contract/remote/AgentApiContracts.kt",
    ROOT / "modules/relay/contract/src/main/java/io/github/magisk317/relay/contract/remote/ConsoleDeviceConfigApiContracts.kt",
    ROOT / "modules/relay/contract/src/main/java/io/github/magisk317/relay/contract/remote/ConsoleApiContracts.kt",
    ROOT / "modules/relay/contract/src/main/java/io/github/magisk317/relay/contract/remote/ConsoleReadModelContracts.kt",
    ROOT / "modules/relay/contract/src/main/java/io/github/magisk317/relay/contract/remote/RealtimeApiContracts.kt",
]
OUTPUT = ROOT / "backend/api/internal/http/openapi_schemas.generated.json"

ROOT_SCHEMAS = {
    "JsonObject": None,
    "ErrorResponse": "ErrorResponse",
    "HealthResponse": "HealthResponse",
    "SystemInfoResponse": "SystemInfoResponse",
    "BootstrapAdminRequest": "BootstrapAdminRequest",
    "BootstrapAdminResponse": "BootstrapAdminResponse",
    "LoginRequest": "LoginRequest",
    "LoginResponse": "LoginResponse",
    "MeResponse": "MeResponse",
    "ChangePasswordRequest": "ChangePasswordRequest",
    "DesktopLoginPageRequest": "DesktopLoginPageRequest",
    "DesktopExchangeRequest": "DesktopExchangeRequest",
    "DesktopRefreshRequest": "DesktopRefreshRequest",
    "DesktopLogoutRequest": "DesktopLogoutRequest",
    "DesktopSessionResponse": "DesktopSessionResponse",
    "SimpleOKResponse": "SimpleOKResponse",
    "BindCodeResponse": "BindCodeResponse",
    "PatchDeviceRequest": "PatchDeviceRequest",
    "DeviceItem": "DeviceItem",
    "DevicesResponse": "DevicesResponse",
    "AgentRegisterRequest": "AgentRegisterRequest",
    "AgentRegisterResponse": "AgentRegisterResponse",
    "HeartbeatRequest": "HeartbeatRequest",
    "AgentConfigMirrorRequest": "AgentConfigMirrorRequest",
    "AgentConfigCommandsPullRequest": "AgentConfigCommandsPullRequest",
    "DeviceConfigCommandItem": "DeviceConfigCommandResponse",
    "DeviceConfigStateResponse": "DeviceConfigStateResponse",
    "AgentConfigCommandsPullResponse": "AgentConfigCommandsPullResponse",
    "AgentConfigCommandsAckRequest": "AgentConfigCommandsAckRequest",
    "DeviceConfigCommandRequest": "DeviceConfigCommandRequest",
    "DeviceConfigAuditLogItem": "DeviceConfigAuditLogItem",
    "DeviceConfigAuditLogsResponse": "DeviceConfigAuditLogsResponse",
    "RelayRecordWire": "RelayRecordWire",
    "RelayRecord": "RelayRecord",
    "RecordsResponse": "RecordsResponse",
    "RealtimeEvent": "RealtimeEvent",
    "RelayRecordsBatchRequest": "RelayRecordsBatchRequest",
    "RelayRecordsBatchResponse": "RelayRecordsBatchResponse",
}

REF_ALIASES = {
    "DeviceConfigCommandResponse": "DeviceConfigCommandItem",
    "DeviceConfigStateResponse": "DeviceConfigStateResponse",
}

REQUIRED_FIELD_OVERRIDES: dict[str, set[str]] = {
    "ErrorResponse": {
        "error",
    },
    "HealthResponse": {
        "ok",
        "service",
    },
    "SystemInfoResponse": {
        "service",
        "appEnv",
        "localBaseUrl",
        "publicBaseUrl",
        "databaseReady",
        "userCount",
        "time",
    },
    "BootstrapAdminResponse": {
        "ok",
        "userId",
        "username",
    },
    "LoginResponse": {
        "authenticated",
        "username",
        "csrfToken",
    },
    "DesktopSessionResponse": {
        "authenticated",
        "username",
        "accessToken",
        "refreshToken",
        "expiresAt",
        "refreshExpiresAt",
    },
    "SimpleOKResponse": {
        "ok",
    },
    "BindCodeResponse": {
        "code",
        "expiresAt",
    },
    "DeviceItem": {
        "id",
        "userId",
        "deviceName",
        "deviceModel",
        "platform",
        "appVersion",
        "displayName",
        "enabled",
        "localAddresses",
        "capabilities",
        "createdAt",
        "updatedAt",
    },
    "DevicesResponse": {
        "devices",
    },
    "AgentRegisterResponse": {
        "userId",
        "deviceId",
        "deviceToken",
    },
    "DeviceConfigCommandItem": {
        "id",
        "baseRevision",
        "targetRevision",
        "mutation",
        "summary",
        "actorType",
        "actorId",
        "status",
        "createdAt",
        "updatedAt",
    },
    "DeviceConfigStateResponse": {
        "deviceId",
        "revision",
        "pendingCommands",
        "updatedAt",
    },
    "DeviceConfigAuditLogItem": {
        "id",
        "deviceId",
        "revision",
        "eventType",
        "actorType",
        "actorId",
        "summary",
        "createdAt",
    },
    "DeviceConfigAuditLogsResponse": {
        "logs",
        "limit",
        "offset",
    },
    "AgentConfigCommandsPullResponse": {
        "deviceId",
        "revision",
        "pendingCommands",
        "updatedAt",
    },
    "RelayRecordsBatchResponse": {
        "inserted",
    },
    "RelayRecord": {
        "id",
        "deviceId",
        "recordType",
        "sender",
        "body",
        "smsCode",
        "packageName",
        "msgType",
        "callType",
        "occurredAt",
        "uploadedAt",
        "metadata",
    },
    "RecordsResponse": {
        "records",
        "limit",
        "offset",
    },
    "RealtimeEvent": {
        "type",
        "time",
    },
}

ARRAY_MAX_ITEMS: dict[tuple[str, str], int] = {
    ("RelayRecordsBatchRequest", "records"): 200,
}

REALTIME_EVENT_TYPES_RE = re.compile(
    r"RealtimeEventTypes\s*\{.*?val ALL:\s*List<String>\s*=\s*listOf\((.*?)\)",
    re.DOTALL,
)

PRIMITIVE_TYPES = {
    "String": {"type": "string"},
    "Boolean": {"type": "boolean"},
    "Int": {"type": "integer", "format": "int64"},
    "Long": {"type": "integer", "format": "int64"},
    "Float": {"type": "number"},
    "Double": {"type": "number"},
}

SPECIAL_TYPES = {
    "JsonObject": {"$ref": "#/components/schemas/JsonObject"},
}


@dataclass
class KotlinField:
    name: str
    type_name: str
    has_default: bool


@dataclass
class KotlinClass:
    name: str
    fields: list[KotlinField]


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


def strip_annotations(value: str) -> str:
    lines = []
    for line in value.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("@"):
            continue
        lines.append(stripped)
    return " ".join(lines)


def parse_field(raw_field: str) -> KotlinField | None:
    serial_name_match = re.search(r'@SerialName\("([^"]+)"\)', raw_field)
    normalized = strip_annotations(raw_field)
    match = re.search(r"\b(?:val|var)\s+(\w+)\s*:\s*([^=]+)", normalized)
    if not match:
        return None
    return KotlinField(
        name=serial_name_match.group(1) if serial_name_match else match.group(1),
        type_name=match.group(2).strip().rstrip(","),
        has_default="=" in normalized,
    )


def parse_kotlin_classes() -> dict[str, KotlinClass]:
    classes: dict[str, KotlinClass] = {}
    marker = "data class "
    for source_path in SOURCES:
        source = source_path.read_text(encoding="utf-8")
        index = 0
        while True:
            start = source.find(marker, index)
            if start < 0:
                break
            name_start = start + len(marker)
            name_match = re.match(r"(\w+)", source[name_start:])
            if not name_match:
                index = name_start
                continue
            class_name = name_match.group(1)
            after_name = source[name_start + len(class_name):]
            paren_offset = after_name.find("(")
            if paren_offset < 0:
                index = name_start + len(class_name)
                continue
            body_start = name_start + len(class_name) + paren_offset + 1
            depth = 1
            position = body_start
            while position < len(source) and depth:
                char = source[position]
                if char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
                position += 1
            body = source[body_start : position - 1]
            fields = [field for part in split_top_level(body) if (field := parse_field(part))]
            classes[class_name] = KotlinClass(name=class_name, fields=fields)
            index = position
    return classes


def parse_realtime_event_types() -> list[str]:
    source = SOURCES[-1].read_text(encoding="utf-8")
    match = REALTIME_EVENT_TYPES_RE.search(source)
    if not match:
        raise SystemExit("missing RealtimeEventTypes.ALL in RealtimeApiContracts.kt")
    return re.findall(r'"([^"]+)"', match.group(1))


def ref_name(type_name: str) -> str:
    return REF_ALIASES.get(type_name, type_name)


def make_nullable(schema: object) -> object:
    if not isinstance(schema, dict):
        return schema
    clone = dict(schema)
    current_type = clone.get("type")
    if isinstance(current_type, str):
        clone["type"] = [current_type, "null"]
        return clone
    if isinstance(current_type, list):
        if "null" not in current_type:
            clone["type"] = [*current_type, "null"]
        return clone
    if "$ref" in clone:
        return {"anyOf": [clone, {"type": "null"}]}
    return clone


def schema_for_type(type_name: str) -> object:
    nullable = type_name.endswith("?")
    base = type_name[:-1].strip() if nullable else type_name.strip()

    if base in PRIMITIVE_TYPES:
        schema: object = dict(PRIMITIVE_TYPES[base])
    elif base in SPECIAL_TYPES:
        schema = dict(SPECIAL_TYPES[base])
    elif base.startswith("List<") and base.endswith(">"):
        inner = base[5:-1].strip()
        schema = {
            "type": "array",
            "items": schema_for_type(inner),
        }
    elif base.startswith("Map<") and base.endswith(">"):
        parts = split_top_level(base[4:-1])
        value_type = parts[1].strip() if len(parts) == 2 else "JsonObject"
        schema = {
            "type": "object",
            "additionalProperties": schema_for_type(value_type),
        }
    else:
        schema = {"$ref": f"#/components/schemas/{ref_name(base)}"}

    return make_nullable(schema) if nullable else schema


def render_schema(schema_name: str, kotlin_class: KotlinClass) -> dict[str, object]:
    properties: dict[str, object] = {}
    required: list[str] = []
    override_required = REQUIRED_FIELD_OVERRIDES.get(schema_name, set())
    for field in kotlin_class.fields:
        properties[field.name] = schema_for_type(field.type_name)
        max_items = ARRAY_MAX_ITEMS.get((schema_name, field.name))
        if max_items is not None:
            properties[field.name]["maxItems"] = max_items
        if field.name in override_required or (not field.has_default and not field.type_name.endswith("?")):
            required.append(field.name)
    if schema_name == "RealtimeEvent":
        properties["type"] = {
            "type": "string",
            "enum": parse_realtime_event_types(),
        }
    schema: dict[str, object] = {
        "type": "object",
        "additionalProperties": False,
        "properties": properties,
    }
    if required:
        schema["required"] = sorted(required)
    return schema


def generate() -> str:
    classes = parse_kotlin_classes()
    rendered: dict[str, object] = {
        "JsonObject": {
            "type": "object",
            "additionalProperties": True,
        },
    }
    for schema_name, class_name in ROOT_SCHEMAS.items():
        if class_name is None:
            continue
        kotlin_class = classes.get(class_name)
        if kotlin_class is None:
            raise SystemExit(f"missing Kotlin data class {class_name} in {', '.join(str(path) for path in SOURCES)}")
        rendered[schema_name] = render_schema(schema_name, kotlin_class)
    return json.dumps(rendered, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail if backend/api/internal/http/openapi_schemas.generated.json is not up to date",
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
