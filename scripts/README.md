# Scripts

Repository automation is intentionally kept in this directory so CI workflows
stay focused on triggers, permissions, and job wiring.

## Layout

- `checks/`: repository boundary and architecture verification checks (`verify_*.sh`). Prefer extending an existing entry over adding another one-off gate.
- `codegen/`: code generators and documentation sync helpers (e.g., `generate_sender_schema_contract.py`, `sync_readme_badges.sh`).
- `release/`: release metadata, tag, and version helpers (`check_release_guard.sh`, `release_ref.sh`, `release_tag.sh`, `bump_version_code.sh`, `sync_*.sh`).
- `security/`: dependency security automation (`manage_dependabot_alerts.py`, `janitor_security_fixes.sh`, etc.), including tests under `security/tests/`.
- `utils/`: shared helper utilities (e.g., regex, formatters) used across various scripts.

Gradle invocations stay direct (`./gradlew`) in callers; keep orchestration
there instead of adding workspace-lock wrappers.

When adding new automation, first reuse an existing script or Gradle task. Add a
new top-level script only when it owns a distinct workflow that cannot fit the
current release, dependency, sync, or verification buckets.
