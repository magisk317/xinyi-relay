# Scripts

Repository automation is kept here so CI workflows stay focused on triggers,
permissions, and job wiring.

- `checks/`: project-specific boundary and architecture checks.
- `ci/`: backend, desktop, plugin, and release-channel drivers owned by xinyi-relay.
- `codegen/`: OpenAPI and contract generators owned by this repository.
- `config/`: project task-selection, compatibility, dual-track, and release-policy configuration.
- `release/`: xinyi-specific release metadata and wrapper scripts.
- `utils/`: project helper libraries.

Shared task selection, dual-track analysis, compatibility execution, API retry,
and release primitives live in `magisk-ci-toolkit`; do not add consumer copies.
All toolkit includes and runtime resolvers use one full 40-character commit SHA.
