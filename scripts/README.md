# Scripts

Repository automation is intentionally kept in this directory so GitHub Actions
workflows stay focused on triggers, permissions, and job wiring.

## Layout

- `ci/`: small helpers used by GitHub Actions jobs.
- `tests/`: tests for Python automation scripts.
- `check_release_guard.sh`, `release_ref.sh`, `release_tag.sh`,
  `bump_version_code.sh`: release metadata, tag, and version helpers.
- `manage_dependabot_alerts.py`, `manage_dependency_forces.py`,
  `janitor_security_fixes.sh`, `reconcile_dependabot_alerts.sh`,
  `validate_dependabot_graph.sh`: dependency security automation.
- `verify_*.sh`, `generate_sender_schema_contract.py`: repository boundary and
  generated contract checks. Prefer extending an existing `verify_*.sh` entry
  over adding another one-off gate.
- `sync_*.sh`: metadata and generated documentation synchronization.
- `with_workspace_gradle_lock.sh`: serializes Gradle invocations that share the
  workspace.

When adding new automation, first reuse an existing script or Gradle task. Add a
new top-level script only when it owns a distinct workflow that cannot fit the
current release, dependency, sync, or verification buckets.
