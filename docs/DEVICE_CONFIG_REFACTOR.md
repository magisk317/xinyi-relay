# Device-Centered Config Refactor

This document tracks the target architecture for the config refactor that moves
Xinyi Relay away from the legacy shared `/api/v1/config/snapshot` model.

## Target model

- Android local storage is the single source of truth.
- Backend stores:
  - a per-device mirrored config document
  - a command queue for pending mutations
  - audit logs for queued/applied/failed/stale commands
- WebUI and Desktop never write the mirror directly. They submit
  `ConfigMutationBatch` commands for a selected device.

## API surface

Console-facing:

- `GET /api/v1/devices/{id}/config`
- `POST /api/v1/devices/{id}/config/commands`
- `GET /api/v1/devices/{id}/config/audit`

Agent-facing:

- `POST /api/v1/agent/config/mirror`
- `POST /api/v1/agent/config/commands:pull`
- `POST /api/v1/agent/config/commands:ack`

Device config wire payloads expose the mirrored config JSON as `mirrorContent`.
The older `snapshot` name is not part of the active public read/write contract;
it is limited to migration tables, SQLite column names, or internal config
fragment terminology.

Legacy compatibility:

- the old `/api/v1/config/snapshot` public route has been retired from the
  active contract; any remaining snapshot semantics are migration-only residue.

## Current implementation status

Backend scaffolding is in progress:

- migration `0003_device_config_queue.sql`
- `device_config_mirrors`
- `device_config_commands`
- `device_config_audit_logs`
- HTTP handlers under `backend/api/internal/http/handlers_device_config.go`

WebUI scaffolding is in progress:

- unified device config context in `frontend/webui/src/deviceConfig.tsx`
- device-aware `AppsPage`, `SendersPage`, `AnalyticsPage`, and `SettingsPage`
- `AdvancedPage` and `RecordsPage` now also reuse the shared WebUI device
  context for device inventory / selected-device state instead of maintaining
  separate page-local device lists
- `OverviewPage` and `AnalyticsPage` now also reuse the shared WebUI device
  context for device inventory state; device and config reads are less
  fragmented across pages
- the previous page-local snapshot editor hook has been removed from active use
- the shared TS console client no longer exposes the old snapshot read/write
  methods to the active WebUI path
- device config OpenAPI schemas, generated TS contracts, Go HTTP DTOs, and
  Android agent DTOs now use `mirrorContent` for the mirrored config payload,
  removing the last active public `snapshot` field from the mirror API
- WebUI/Desktop sender editors no longer expose an “advanced JSON” fallback for
  sender `jsonSetting`; sender config now defaults to typed/structured fields,
  with unsupported sender types pushed back to device-side editing instead of
  raw JSON fallback in the console
- WebUI/Desktop `Apps` and `Senders` pages now queue typed config mutations
  (`replace_device_apps` / `replace_senders`) instead of always packaging a
  whole-root `replace_root` command for these routine edits
- shared console types, WebUI/Desktop pending-command previews, Android agent
  mutation application, and backend command enqueue now only accept typed
  mutation operations; legacy whole-root `replace_root` commands are rejected
  instead of being applied as a compatibility path
- desktop local-mode store/sync examples and tests now also use typed sender
  mutations as the default command shape, instead of treating whole-root
  replacement as the primary sample path
- desktop local mode now reads device config through the same device-context
  hook as remote mode; Tauri selects the local SQLite mirror/command queue
  underneath instead of returning an empty config shell in React
- desktop Tauri local commands and the local server now serialize device config
  content as `mirrorContent`; the Rust/SQLite internals may still call the
  stored JSON `snapshot` because that is the local table column, not the public
  wire contract
- Android `Records` now consumes `RecordQueryState` for tab slices and code
  deduplication in the ViewModel/query layer; the full-list `AnimatedContent`
  wrapper has been removed from the record list shell
- Android `Records` and blacklist-hit rows now get default SMS/dialer packages,
  package labels, and icon bitmap preloads from `CodeRecordViewModel`; list
  rows render a bitmap or placeholder and no longer start package-manager
  resolution while composing each row
- Android `Apps` now preloads visible app icons from `AppConfigViewModel` and
  uses stable list keys; app rows render preloaded bitmaps or placeholders
  instead of starting icon loads from each item composition
- The main app no longer keeps the old `AppIconImage` composition-time loader
  or the `AppIconLoader` label lookup fallback. `AppIconCache` is now
  package-name only, with LRU caching, bounded concurrency, and
  package/uid/sourceDir cache keys; hot rows render `AppIconBitmapImage`
  from ViewModel-prefetched bitmaps.
- Android app forward-filter detail pages now resolve the app label through
  `ForwardFilterViewModel` header state, keeping `PackageManager` access out of
  the Composable title path
- backend queue semantics now allow multiple ordered pending commands per
  device; clients advance `baseRevision` against the latest pending target
  revision instead of being blocked by a single-command queue
- backend device-config write paths now take a per-device transaction advisory
  lock before command enqueue, mirror upsert, or command ack, so read-then-write
  revision math is serialized without blocking other devices
- backend command ack updates now atomically require `status = pending` and
  check `RowsAffected() == 1`; duplicate ack attempts are rejected before they
  can advance the mirror twice
- unsupported agent command ack statuses are mapped to a client error instead
  of falling through as a generic server failure
- WebUI/Desktop React device-config stores now use a shared pending-command
  helper that computes the next `baseRevision` from the maximum pending
  `targetRevision`, so unsorted pending arrays cannot make the console queue a
  command against an older revision
- WebUI/Desktop effective config previews now also sort pending commands by
  `targetRevision` before applying them, and skip unsupported preview mutations
  instead of crashing the page render path
- the desktop device-config hook has focused coverage for both unsorted pending
  preview order and unsupported preview mutation tolerance
- backend store tests now also prove multi-device isolation: legacy snapshots
  initialize each known device independently, local mirror updates on one
  device stale only that device's pending commands, and a second device can
  continue queuing from its own revision without being overwritten
- shared config-root helpers now live under `frontend/shared/configRoot.ts`
  instead of the older `configSnapshot.ts` naming
- `configRoot.generated.ts` is now generated from Kotlin config models rooted at
  contract-owned `relay/contract/model/LocalConfigMirrorPayload.kt`, so the TS
  config-root structure is no longer hand-maintained in TypeScript or runtime
  DTO files
- `SenderActiveSchedule` and its evaluator/normalizer now also live in
  `relay/contract`; `relay/engine/api` keeps a compatibility facade so existing
  engine/runtime/UI imports do not have to move in the same patch
- Android agent request/response DTOs now live in `relay/contract`, and
  runtime-side networking consumes those shared Kotlin contracts
- `AgentConfigCommandsPullResponse` is now an explicit Kotlin contract DTO,
  not an OpenAPI-only alias of `DeviceConfigStateResponse`; Android runtime
  deserializes the pull endpoint against that agent-facing response type.
- Android agent OpenAPI schema fragments are now also generated from
  `relay/contract/remote/AgentApiContracts.kt` into the backend tree before the
  final OpenAPI document is built, so the backend no longer hand-defines those
  schema objects as the primary source
- OpenAPI route metadata is now also generated from
  `relay/contract/remote/OpenApiRouteContracts.kt` into the backend tree before
  final OpenAPI assembly, so the backend builder no longer hand-owns path
  metadata either
- most console auth/system/device-config/read-model schemas now also come from
  Kotlin contract models under `relay/contract/remote/*`, so backend
  reflection is no longer the primary schema authoring path for those DTOs
- the backend OpenAPI builder no longer relies on its previous reflection-based
  schema synthesis path for active schemas; it now mainly assembles generated
  contract fragments plus a tiny fixed `JsonObject` helper schema
- backend-generated `openapi.json`, OpenAPI-derived `console.generated.ts`, and
  contract-owned agent DTOs now form one contract chain instead of three
  unrelated hand-maintained layers
- `console.generated.ts` preserves nullable JSON object schemas as
  `JsonObject | null`; route/schema tests now also verify route `$ref`
  resolution, operationId uniqueness, and path-template parameter drift.

Android migration is partially in place:

- `LocalConfigRepository` contract and `LocalConfigRepositoryImpl`
- local revision / dirty-state prefs decoupled from binding state
- `RemoteAgentRepository` now builds around local mirror export/import and the
  new mirror/command API surface
- `RemoteAgentRepository` now depends on a small `RemoteAgentApi` boundary, so
  sync semantics can be tested without a live backend while production still
  uses the same OkHttp client implementation
- `RemoteAgentRepository` no longer owns a duplicate local mirror or installed
  app catalog assembler. It consumes `LocalConfigRepository.exportMirror()` as
  the Android source of truth, and derives the app catalog digest from the
  exported mirror's `deviceAppInfos` for the selected device.
- Android agent sync tests now prove the first-bind bootstrap matrix: a remote
  mirror is imported only when local revision is `0` and local dirty state is
  clean; dirty local state or any existing local revision keeps the Android
  mirror authoritative instead of accepting a remote overwrite.
- Android agent sync tests now also prove command ack behavior: supported typed
  commands apply through `LocalConfigRepository.applyMirror()` and ack the new
  revision, while unsupported whole-root mutations ack failure without
  clobbering the local mirror.
- `LocalConfigRepository.applyMirror()` now refuses remote command imports when
  the local config is dirty or the incoming revision is stale; the Android
  agent acks those pulled commands as `local_dirty` / `stale_local_revision`
  failures instead of overwriting local state
- Android remote-agent token expiry is preserved as `token_expired`; the generic
  failure path no longer immediately overwrites that state with `error`
- Android runtime tests cover the token-expired preservation on both mirror push
  and command pull paths
- Android local dirty-state and remote-agent status naming has started moving
  away from `pendingMutations` / `lastAppliedConfigRevision` toward explicit
  local-change and local-revision terminology
- Android's local `DataStore -> Xposed RemotePrefs` retry flag is named
  `remotePrefsPublishPending`, so this local preference bridge is not confused
  with cloud/device-config sync state.
- Android `Apps` page now consumes a single `AppConfigUiState` instead of
  stitching together multiple page-level flows in the Composable shell
- Android `AppConfigViewModel` now builds that screen from an explicit
  `AppConfigQueryState` + assembler pipeline, so filtering, sorting, paging,
  system-app exclusion, and usage-stat ordering no longer depend on ad-hoc
  mutable screen caches
- Android `OverviewScreen` runtime environment queries are now being pulled into
  a single runtime snapshot state instead of multiple top-level `produceState`
  calls
- Android `OverviewViewModel` now also owns edit-mode, add-sheet, drag, status
  diagnostics, and battery-optimization shell state; the screen shell is
  narrower and mainly keeps donation / QR dialogs plus context-bound side
  effects
- the old Android haze/blur library dependencies have now been removed from
  `mobile/ui`, `overview`, `appconfig`, `record`, and `relayconfig`; the
  version catalog and `magisk-ui-kit` no longer declare unused haze aliases, and
  the remaining shell path no longer carries those visual dependencies as
  inactive residue
- `verify_module_boundaries.sh` now blocks Haze imports, haze catalog aliases,
  `SubcomposeAsyncImage`, `AppIconLoader`, and the old composition-time
  `AppIconImage` entrypoint from returning to active mobile source
- `MainScreen` no longer uses primary NavHost destinations for
  `Overview / Apps / Records / Advanced / Settings`; those top-level sections
  now render through a fixed pager shell route, while secondary pages stay on
  the stack navigation path
- Android `OverviewViewModel` now owns card-order / enabled-card / chart
  settings state so the screen shell no longer duplicates those values locally
- top-level `MainScreen` no longer uses section-index-driven horizontal page
  animations or the compact bottom-bar haze wrapper

Desktop migration is partially in place:

- device-config Tauri commands are wired through `desktopApi`
- `ConfigPage`, `AppsPage`, and `SendersPage` now read per-device mirror state
  and queue typed commands instead of directly editing a shared raw snapshot
- `ConfigPage` now also reads selected-device config through the shared desktop
  device-config hook instead of maintaining a separate page-local config/device
  loading flow
- desktop `RecordsPage` and `AnalyticsPage` now also reuse the shared desktop
  device-config hook for device inventory / selected-device state instead of
  maintaining separate page-local device lists
- desktop local-mode and remote-mode device pages now tolerate the shared
  device-config hook even when no remote config mirror is available, reducing
  page-specific fallback state
- desktop `DevicesPage` and `OverviewPage` now also reuse the shared desktop
  device/device-config context for inventory refresh instead of maintaining
  separate page-local device fetch state
- `OverviewPage` and `AnalyticsPage` now rely on the selected-device mirror /
  revision state instead of the removed shared snapshot endpoint
- local sqlite sync, Tauri local server, and Rust local/remote stores now use
  per-device mirrors and pending command queues
- legacy local `config_snapshots` rows are now treated as one-time migration
  input only; runtime reads and writes no longer fall back to the shared
  snapshot tables
- desktop local SQLite mode now matches backend pending-command revision
  semantics: a second local command can be queued against the first pending
  command's target revision, and pending queues remain scoped per device
- desktop local migration tests now prove that a legacy `config_snapshots` row
  seeds a mirror for every existing local device, not just the first device
- Android `SettingsRepository` mutation helpers now describe local-pref publish /
  mutation tracking only, instead of implying remote snapshot sync
- Android `OverviewScreen` no longer polls the entire page on a `delay(1500)`
  loop; diagnostics now refresh on enter/resume
- Android records tab splitting and code deduplication now happen in
  `CodeRecordViewModel` before Compose renders the active tab
- Android Apps/Records list icon loading now uses ViewModel-owned prefetch over
  `AppIconCache`; the hot rows consume stable UI state instead of invoking the
  icon lookup chain from item composition
- Current active-path audit: legacy `/api/v1/config/snapshot`,
  `loadNormalizedConfigSnapshot` / `saveNormalizedConfigSnapshot`,
  page-local config snapshot editor hooks, active `replace_root` application,
  Haze imports, and composition-time Coil app icon loaders are absent from the
  active Android/WebUI/Desktop/backend paths. Remaining mentions are docs,
  rejection tests, migration tables, local SQLite implementation details, or
  the unused `magisk-ui-kit` surface package.
- Current Senders/Filters audit: these secondary pages no longer have
  composition-time package-manager scans in the inspected hot paths; the
  remaining work is broader assembler coverage and performance acceptance, not
  an obvious local package-resolution bug in those screens.
- Macrobenchmark/JankStats acceptance scaffolding is now present: `:benchmark:macro`
  covers cold startup, top-level tab switching, Apps scrolling, Records
  scrolling, and Senders open/scroll, while `MainActivity` installs a debug-only
  JankStats hook for frame diagnostics

## Verification

Recent broad checks:

- backend HTTP/store tests: `go test ./internal/http ./internal/store`
- OpenAPI/codegen drift checks: `scripts/codegen/generate_openapi_contract.sh --check`
  plus `generate_console_contract_from_openapi.py --check`
- WebUI type/tests: `pnpm typecheck && pnpm test -- ConfigMutations ConsoleOpenApiContract ConsoleApiClient`
- WebUI focused pending-command helper tests:
  `pnpm test -- DeviceConfigCommands` in `frontend/webui`
- Desktop contract/local-mode tests: `pnpm test -- ConsoleOpenApiContract DesktopApi useDesktopDeviceConfig`
- Desktop focused device-config hook tests:
  `pnpm test -- useDesktopDeviceConfig` in `frontend/desktop`
- WebUI/Desktop type checks:
  `pnpm typecheck` in `frontend/webui` and `frontend/desktop`
- Desktop Rust local store/sync tests: `cargo test` in `frontend/desktop/src-tauri`
- Desktop Rust focused device-config tests:
  `cargo test device_config` and `cargo test legacy_config_snapshot` in
  `frontend/desktop/src-tauri`
- backend store focused device-config tests:
  `go test -count=1 ./internal/store` in `backend/api`
- Android contract/runtime tests:
  `./gradlew --no-daemon :relay:contract:testPlayDebugUnitTest :runtime:testPlayDebugUnitTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- Android runtime sync/local-source tests:
  `./gradlew --no-daemon :runtime:testPlayDebugUnitTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- Android runtime/contract focused dirty-state sync tests:
  `./gradlew --no-daemon :runtime:testGithubNoE2eeDebugUnitTest :relay:contract:testGithubNoE2eeDebugUnitTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- Android focused agent sync tests:
  `./gradlew --no-daemon :runtime:testPlayDebugUnitTest --tests io.github.magisk317.relay.data.repository.RemoteAgentRepositoryTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- Android RemotePrefs bridge compile check:
  `./gradlew --no-daemon :relay:android:compilePlayDebugKotlin :core:compilePlayDebugKotlin -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- Android icon/query hot-path tests:
  `./gradlew --no-daemon :mobile:feature:common:compilePlayDebugKotlin :mobile:feature:appconfig:testPlayDebugUnitTest :mobile:feature:record:testPlayDebugUnitTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- module boundary check: `bash scripts/checks/verify_module_boundaries.sh`

## Rules that must hold after migration

- Local Android mutations always commit locally before the backend mirror is updated.
- Backend mirror revisions advance per device; they are never shared across devices.
- Pending commands are optimistic client state only; they do not mutate the mirror
  until the device applies them.
- Pending command base revisions advance against the newest pending target
  revision for that same device; another device's mirror or queue cannot move
  the selected device's base revision.
- A command with an outdated base revision must be rejected with
  `stale_base_revision`.
- If the device pushes a newer local revision before an older pending command is
  applied, that command must become stale instead of overriding the device.

## Documentation upkeep

When code changes land, update this file together with any user-facing or
cross-component behavior change:

- wire contract changes
- command queue semantics
- migration/bootstrap rules
- removal of legacy snapshot paths
- whether shared `console.ts` / `openapi.json` are still hand-maintained or
  have actually moved to Kotlin-generated source of truth
- current state: `configRoot.generated.ts` now comes from contract-owned Kotlin
  DTOs, OpenAPI schemas for agent/device-config/auth/system/read-model/realtime
  are generated from `relay/contract`, OpenAPI route metadata is generated from
  `relay/contract`, `openapi.json` is assembled by the backend contract
  assembler, `console.generated.ts` is generated from `openapi.json`, and
  agent pull-command responses are represented by an explicit Kotlin DTO
