# Scripts

Repository automation is intentionally kept in this directory so CI workflows
stay focused on triggers, permissions, and job wiring.

## Layout

- `checks/`: repository boundary and architecture verification checks (`verify_*.sh`). Prefer extending an existing entry over adding another one-off gate.
- `codegen/`: code generators and documentation sync helpers (e.g., `generate_openapi_contract.sh`, `generate_openapi_schemas.py`, `generate_openapi_route_contracts.py`, `generate_sender_schema_contract.py`, `generate_console_contract_from_openapi.py`, `generate_config_root_contract.py`, `sync_readme_badges.sh`). `generate_config_root_contract.py` reads contract-owned mirror DTOs from `modules/relay/contract`, not runtime-local transport structs; `generate_openapi_schemas.py` covers the contract-owned console/agent/read-model/realtime schema DTOs before backend OpenAPI assembly; and `generate_openapi_route_contracts.py` does the same for contract-owned OpenAPI route metadata. The backend OpenAPI assembler now merges these generated artifacts instead of reflecting active schemas from Go structs.
- `release/`: release metadata, tag, and version helpers (`check_release_guard.sh`, `release_ref.sh`, `release_tag.sh`, `bump_version_code.sh`, `sync_*.sh`).
- `utils/`: shared helper utilities (e.g., regex, formatters) used across various scripts.

Gradle invocations stay in callers. For CI or network-sensitive automation, use
the shared `gradle/run_gradle_with_retry.sh` from `magisk-ci-toolkit` instead of adding one-off
wrappers around `./gradlew`.

When adding new automation, first reuse an existing script or Gradle task. Add a
new top-level script only when it owns a distinct workflow that cannot fit the
current release, dependency, sync, or verification buckets.
