# Xinyi Relay 全栈彻底重构跟进文档

最后更新：2026-07-07

## 目标

- 将 `xinyi-relay` 重构为以 Android 本地为唯一真源的设备中心模型。
- WebUI / Desktop 只负责设备管理、命令下发、状态观察，不再直接写共享 cloud snapshot。
- Backend 从“共享配置仓库”降级为“设备 mirror + command queue + audit”。
- Android UI 同步切向轻量壳层、弱动画、弱 blur/haze、重计算前移。

## 最终形态

- Android 侧新增 `LocalConfigRepository`，统一导出本地配置快照、revision、dirty state。
- `RemoteSyncRepository` 重构为 `ConfigSyncCoordinator`，只负责：
  - 推送本地 mirror
  - 拉取配置命令
  - ack 应用结果
  - 上报 records / device state
- Backend 提供：
  - `GET /api/v1/devices/{id}/config`
  - `POST /api/v1/devices/{id}/config/commands`
  - `POST /api/v1/agent/config/mirror`
  - `POST /api/v1/agent/config/commands:pull`
  - `POST /api/v1/agent/config/commands:ack`
- WebUI / Desktop 统一切为：
  - `selectedDevice`
  - `deviceConfigMirror`
  - `pendingCommands`
  - typed mutation forms
- 公开 wire contract 中设备配置内容字段统一使用 `mirrorContent`，不再把 per-device mirror payload 暴露成 `snapshot`。
- Android 顶层壳层固定为 `Overview / Apps / Records / Advanced / Settings` 五个一级页，去掉基于 section index 的横向转场和默认 haze-heavy 视觉路径。

## 当前代码状态

### 已落地的脚手架

- Backend 已开始引入 device-config 数据模型脚手架：
  - `device_config_mirrors`
  - `device_config_commands`
  - `device_config_audit_logs`
- Backend 已补充一套初版 store / handler / HTTP DTO：
  - `backend/api/internal/store/device_config.go`
  - `backend/api/internal/http/handlers_device_config.go`
  - `backend/api/internal/http/types.go`
- WebUI 已开始切向统一设备上下文：
  - `frontend/webui/src/deviceConfig.tsx`
  - `AppsPage` / `SendersPage` / `AnalyticsPage` / `SettingsPage` 初步改为读设备 mirror 与 pending commands
  - `AdvancedPage` / `RecordsPage` 已开始复用统一设备上下文中的设备清单与选中设备状态，不再各自维护一套 page-local device store
  - `OverviewPage` / `AnalyticsPage` 也已开始复用统一设备上下文中的设备清单状态，进一步收敛 page-local device loading
  - 旧的 page-local `useConfigSnapshotEditor` 已移出实际主路径
  - shared TS console client 已不再对活动前端路径暴露旧 snapshot 读写方法
  - 设备配置 OpenAPI / TS / Kotlin / Go wire DTO 已把配置内容字段统一为 `mirrorContent`；旧 `snapshot` 字段不再出现在活跃公开读写契约中
  - `SendersPage` 的默认编辑流已收敛为结构化字段；控制台侧不再默认暴露 sender `jsonSetting` 的高级 JSON 回退入口
  - `AppsPage` / `SendersPage` 已开始发送 typed config mutation（`replace_device_apps` / `replace_senders`），而不是继续把常规编辑全部包成整份 `replace_root`
  - shared console mutation 类型已删除 active `replace_root` 分支；WebUI/Desktop pending command 预览、Android agent 应用器、backend command enqueue 现在都只接受 typed mutation，旧 whole-root mutation 会被拒绝或 ack 失败
  - backend 命令队列已从“单条 pending 限制”切到“每设备多条有序 pending command”；WebUI / Desktop 的 React device-config store 现在通过共享 helper 按最大 pending `targetRevision` 计算下一条命令的 `baseRevision`，不再信任 pending 数组最后一项
  - backend device-config 写事务现在在 command enqueue、mirror upsert、command ack 三条路径顶部获取 per-device advisory lock；ack 更新同时带 `status = pending` 原子条件和 `RowsAffected() == 1` 校验，重复 ack 不再能二次推进 mirror
  - unsupported agent ack status 已映射为 400 类客户端错误，不再走 500
  - WebUI / Desktop 共享 `deriveEffectiveConfigRoot` 现在会先按 `targetRevision` 排序 pending command，再做预览应用；未知或不支持的 mutation 预览会被跳过，不再导致整页 render 崩溃
  - Desktop hook 已补 focused 测试覆盖乱序 pending 预览和未知 mutation 预览容错，避免再次回到 page-local reduce 实现
  - backend store 多设备测试已覆盖：legacy snapshot 会初始化同一用户下每台设备的独立 mirror，一台设备本地 mirror 推进只 stale 自己的 pending command，第二台设备仍按自己的 revision 继续排队，不会被第一台覆盖
  - OpenAPI schema / route metadata 已从 `relay/contract/remote/*` Kotlin 合同生成到 backend fragment，再由 backend assembler 输出 `frontend/shared/contracts/openapi.json`
  - Android agent `commands:pull` 响应已拥有显式 `AgentConfigCommandsPullResponse` Kotlin DTO，不再靠 `DeviceConfigStateResponse` 生成别名支撑 wire contract
  - `console.generated.ts` 由 `openapi.json` 生成，nullable `JsonObject` 现在保留为 `JsonObject | null`，不再退化成 `unknown`
  - backend / Kotlin / WebUI / Desktop 合同测试已覆盖 route request/response/parameter、schema `$ref` 可解析、operationId 唯一、path parameter 与 path template 匹配
- Android 已打通第一段本地真源链路：
  - 新增 `LocalConfigRepository` / `LocalConfigRepositoryImpl`
  - 本地 revision / dirty state 已从绑定状态中分离
  - `RemoteAgentRepository` 已开始围绕 local mirror export/import 与新 mirror/command API 工作
  - `RemoteAgentRepository` 已抽出可注入的 `RemoteAgentApi` 边界，生产路径继续使用 OkHttp `RemoteApiClient`，单测可以直接驱动真实同步协调器逻辑
  - `RemoteAgentRepository` 内部重复的本地 mirror / app catalog 装配器已删除；同步协调器现在只通过 `LocalConfigRepository.exportMirror()` 读取 Android 本地真源，app catalog digest 也从 mirror 的 `deviceAppInfos` 计算，不再在同步层额外扫描包管理器
  - `RemoteAgentRepositoryTest` 已覆盖首次绑定/首次升级决策矩阵：仅当本地 revision 为 `0` 且 dirty state 为 clean 时导入 backend 初始 mirror；本地 dirty 或已有 revision 时保持 Android 本地 mirror 权威
  - `RemoteAgentRepositoryTest` 已覆盖设备命令 ack 行为：typed mutation 成功应用后 ack 新 revision，unsupported whole-root mutation ack failed 且不覆盖本地 mirror
  - `LocalConfigRepository.applyMirror()` 已在真正写入前拒绝 dirty 本地状态与 stale incoming revision；`RemoteAgentRepository` 拉取到这类命令时 ack failed，失败原因分别为 `local_dirty` / `stale_local_revision`
  - 远端 agent token 过期状态现在保持为 `token_expired` 与友好错误文案，不再被外层通用 `onFailure { setSyncState("error") }` 覆盖
  - token 过期状态已有 push / pull 两条路径的对等单测覆盖
  - `LocalConfigMirrorPayload` 已迁入 `relay/contract` 作为 config-root 真源；runtime 侧旧 payload 文件已删除，改为合同消费者 + 映射层
  - `SenderActiveSchedule` 真源已迁入 `relay/contract`；`relay/engine/api` 仅保留兼容 facade，避免大范围调用点一次性改包
  - `MainScreen` 顶层 section 横向切页动画与 compact bottom bar haze 包装已去除
  - 顶层 `HazeState` 传递链已从 `MainActivity -> NavGraph -> MainScreen` 主路径删除
  - `OverviewScreen` 已移除 `delay(1500)` 式整页轮询刷新
  - `OverviewScreen` 的运行时环境查询已开始收敛为单一 runtime snapshot 状态，而不是在 Composable 顶层分散触发多组 `produceState`
  - `OverviewViewModel` / `OverviewUiState` 已接管首页卡片顺序、启用状态与图表设置状态
  - `OverviewViewModel` 已继续接管编辑模式、加卡片面板、拖拽态、状态诊断开关与电池优化提示状态，首页壳层本地状态进一步收缩
  - `CodeRecordViewModel` 已接管记录 tab 拆分、验证码去重与列表 query state 预装配
  - `CodeRecordScreen` 已改为直接消费 `RecordQueryState` 的当前 tab 列表，删除整份记录列表作为 `AnimatedContent` target 的壳层动画，并为 tab 拆分/验证码去重补充单测
  - `CodeRecordViewModel` 现已接管默认短信/拨号包解析、记录页 app label 解析与记录图标预取；`CodeRecordScreen` / blacklist hit 列表项只消费已解析的环境状态和 bitmap，不再在组合期调用系统包解析
  - `pendingMutations` / `lastAppliedConfigRevision` 等旧同步语义已开始改为 `pendingLocalChanges` / `localConfigRevision`
  - Android 本机 `DataStore -> Xposed RemotePrefs` 发布重试标记已从 `remoteSyncPending` 改名为 `remotePrefsPublishPending`，避免与云端配置同步语义混淆
  - `AppConfigViewModel` 已继续收敛到 `AppConfigQueryState -> AppConfigUiState` 的单一 query pipeline，过滤/排序/分页/system-app/usage stats 已不再靠散落的隐式缓存拼装
  - `AppConfigViewModel` 现已预取当前 Apps 列表图标，Apps 列表项使用 stable key 并只渲染 `Bitmap` 或 placeholder，不再由 item 组合期启动图标加载链路
  - 主工程旧 `AppIconImage` 组合期加载入口与 `AppIconLoader` label 反查 / 全表扫描 fallback 已删除；`AppIconCache` 只接受明确 package name，并继续使用 `LruCache + bounded concurrency + package/uid/sourceDir key`
  - `ForwardFilterViewModel` 已接管 App forward filter 页标题 app label 解析；`AppForwardFilterScreen` 只消费稳定 header state
  - Android 主壳层相关模块里已清除未使用的 `haze` / `blur` 依赖残留，版本 catalog 和 `magisk-ui-kit` 中未使用的 haze 依赖也已删除，避免“视觉策略已删但模块边界仍保留旧库”的半清理状态
  - `verify_module_boundaries.sh` 已新增防回归检查：禁止 Haze import / haze catalog alias / `SubcomposeAsyncImage` / `AppIconLoader` / 旧 `AppIconImage` 组合期入口回到移动端主源码
  - `MainScreen` 已从“一级页直接作为内部 NavHost 目的地”切到 `MainTabsRoute + pager shell + secondary stack` 结构，一级页不再依赖内部 NavHost 页面切换
  - `:benchmark:macro` 已加入工程，覆盖冷启动、一级 tab 切换、Apps 列表滚动、Records 列表滚动、Senders 打开/滚动；`MainActivity` 同时接入 debug-only JankStats 诊断 hook
  - 最新活跃路径审计确认：旧 `/api/v1/config/snapshot`、`loadNormalizedConfigSnapshot` / `saveNormalizedConfigSnapshot`、page-local snapshot editor、active `replace_root` 应用路径、Haze import、组合期 Coil app icon loader 均未出现在当前 Android/WebUI/Desktop/backend 主路径；剩余命中集中在文档、拒绝测试、迁移表、本地 SQLite 实现细节或未接入主路径的 `magisk-ui-kit` surface 包
  - 最新 Senders/Filters 审计确认：已检查的二级页热路径没有组合期 package-manager 全表扫描；剩余工作更偏向继续扩大 assembler 覆盖和性能验收，而不是已知的本地包解析残留 bug
- Desktop 已完成第一段设备中心迁移：
  - 新的 device-config Tauri 命令已接通
  - `ConfigPage` 不再默认提供 raw JSON snapshot 编辑
  - `AppsPage` / `SendersPage` 已改为读取设备 mirror 并通过 typed command 提交变更
  - `RecordsPage` / `AnalyticsPage` 已开始复用统一 desktop device-config hook 中的设备清单与选中设备状态，不再各自维护一套 page-local device store
  - shared desktop device-config hook 已放宽到 local mode 也可稳定提供设备清单，从而减少各页对 page-local device fallback 的依赖
  - `DevicesPage` / `OverviewPage` 也已开始复用统一 desktop device/device-config context 做设备刷新，不再各自维护独立设备获取状态
  - `OverviewPage` / `AnalyticsPage` 已不再从桌面 UI 层读取共享 snapshot revision
  - Desktop local mode 现在也通过统一 device-config hook 读取本地 SQLite device mirror / pending command，不再在 React 层把 config 降级为空
  - Desktop Tauri local bridge / local server 对前端输出 `mirrorContent`，内部 SQLite `snapshot` 列名仅作为本地存储实现细节保留
  - desktop Rust 的 local store / sync / local server 已开始围绕 device mirror 和 pending command 工作
  - Desktop SQLite local mode 已补齐 backend 同款 pending revision 语义：连续本地命令按上一条 pending target revision 继续排队，而不是被未变的 mirror revision 误判为冲突
  - Desktop React device-config hook 已补齐同款 pending revision 语义：即使 pending command 数组乱序，也会按最高 target revision 继续向 Tauri / backend 排队
  - Desktop SQLite migration 测试已覆盖 legacy `config_snapshots` 初始化到每台本地设备，避免只初始化第一台设备的隐性回归

### 尚未完成的关键缺口

- `openapi.json` 的主真源已迁到 Kotlin contract + 生成脚本；backend 现在主要负责 fragment assembly。剩余工作不再是 OpenAPI 所有权迁移，而是继续把新增 API 严格纳入同一生成链和测试链。
- auth/system/device-config/read-model/realtime/agent 的 schema 与全部 route metadata 已迁到 `relay/contract/remote/*` 生成链；`JsonObject` 等基础 schema 也纳入生成物，不再由 Go builder 手写。
- shared TS contract 已退出手写主路径：`console.generated.ts` 现由 `openapi.json` 生成，并已修正 nullable `JsonObject` 类型退化问题。
- 核心配置根 TS 类型已退出手写主路径：`configRoot.generated.ts` 现已从 contract-owned Kotlin `LocalConfigMirrorPayload` 生成；剩余工作不再是 config-root / OpenAPI 所有权，而是防止新增契约绕过生成链。
- Android agent request/response DTO 已从 `runtime` 迁入 `relay/contract`，runtime 网络层退回为合同消费者。
- Desktop 的活跃 UI / Tauri wire contract 已切到 device mirror / command queue；旧 `config_snapshots` 仅作为一次性迁移来源，SQLite `snapshot` 列名保留为存储实现细节。
- Android 仍然保留若干兼容桥：
  - `RemoteSyncRepository` 类型本身已删除，活跃远端配置同步入口已收敛到 `ConfigSyncCoordinator` / `RemoteAgentRepository` 的 mirror-command 语义
  - `LocalConfigMirror` 已替换核心远端快照类型名；剩余 `Snapshot*` 多为配置根片段 DTO 或 settings slice 命名，不再表示“云端共享快照是真源”
- Android `SettingsRepository` / `ConfigRepository` 已收敛为本地 mutation 标记，不再承担远端同步副作用；剩余工作主要是继续清理命名和兼容路径。
- Android agent DTO 已迁入 `relay/contract/remote/AgentApiContracts.kt`，runtime 网络层开始只消费共享合同。
- Android 壳层、haze 默认路径、App 图标缓存、Overview/Apps/Records 关键 query assembler 已部分落地；Apps/Records/Forward filter 的主列表与标题热路径已不再在 Composable item 期做包管理器解析，主工程旧组合期图标加载入口也已删除；剩余主要是继续扩大 Senders/Deny/Filters 等二级页的 assembler 覆盖与性能验收。
- Android 首次同步/迁移语义已从“实现可见但缺少证明”推进到单测覆盖；剩余迁移风险主要集中在更宽的端到端升级场景和性能验收，而不是同步协调器的基础决策分支。
- 合并前必须保持旧共享 snapshot 默认写路径与 raw JSON 默认编辑流不可回归。
- active `replace_root` 写入/预览/应用路径已删除；剩余 `config_snapshots` 命名仅作为 backend/desktop 一次性迁移来源，设备 mirror payload 的公开字段名为 `mirrorContent`。

## 固定实施顺序

### 1. Contract 与 Backend 设备配置模型

- 固定设备级 config API 与 wire schema。
- 完成 `openapi.json`、TS contract、Go DTO、Kotlin DTO 的一致性。
- Backend 命令队列需覆盖：
  - stale base revision 拒绝
  - mirror 推送后旧 pending command stale 化
  - ack success / failure
  - legacy `config_snapshots` -> per-device mirror 初始化

### 2. Android 本地真源与同步协调器

- 引入 `LocalConfigRepository`：
  - 聚合 `prefs + Room + senders/rules/app config + templates + overview/settings slices`
  - 维护 `LocalConfigMirror / LocalConfigRevision / LocalDirtyState`
- `ConfigSyncCoordinator` 替换旧云端覆盖本地策略：
  - 本地改动先 commit 本地，再异步 push mirror
  - 远端只下发命令，不再直接下发 canonical snapshot 覆盖本地
- 首次升级规则固定：
  - 本地已有 revision 或 dirty：本地覆盖 backend mirror
  - 首次绑定且本地无历史：导入 backend 初始 mirror 一次

### 3. WebUI / Desktop 统一设备上下文

- 用统一 client store 替换 page-local `loadNormalizedConfigSnapshot / saveNormalizedConfigSnapshot / cloneConfigRoot`
- 默认不再暴露 raw JSON snapshot 编辑
- 所有写入改为 `ConfigMutationBatch`
- 页面只消费：
  - `selectedDevice`
  - `deviceConfigMirror`
  - `pendingCommands`
  - records / analytics read model

### 4. Android UI 壳层与重计算前移

- 顶层导航去横滑 section 动画
- 删除默认 `hazeEffect + forceInvalidateOnPreDraw`
- Apps / Records / Overview 改 query/assembler 模式
- App 图标链路改 `AppIconCache`
- 列表项组合期不得再触发包管理器扫描、字符串重拼接、全页重算

### 5. 最终清理

- 删除旧 `/api/v1/config/snapshot` 共享写语义
- 删除 Android settings/config repo 中所有远端同步副作用
- 删除 WebUI / Desktop raw snapshot 默认编辑入口
- 删除移动端 blur/haze 用户设置与旧壳层依赖

## 文档同步要求

- 每完成一个子系统切换，必须同步更新仓库内文档。
- 以 `docs/` 为主，不把“真实实现方式”只留在提交历史里。
- 至少保持以下文档持续更新：
  - 新配置模型与数据流
  - Backend API / queue / mirror 语义
  - Android 本地真源与首次升级规则
  - WebUI / Desktop 设备上下文与 pending command 行为

## 验收基线

- 老设备升级后本地配置不丢。
- WebUI 选中设备提交修改后，设备上线可以应用并回写 mirror。
- 第二台设备不会再被第一台设备的配置自动覆盖。
- Android 一级页切换无全屏横滑壳层动画。
- Apps / Records 列表滚动无明显 blur 重绘抖动峰值。

## 最近验证记录

- `go test ./internal/http ./internal/store`
- `go test -count=1 ./internal/store` in `backend/api`
- `bash scripts/codegen/generate_openapi_contract.sh --check && python3 scripts/codegen/generate_console_contract_from_openapi.py --check`
- `pnpm typecheck && pnpm test -- ConfigMutations ConsoleOpenApiContract ConsoleApiClient` in `frontend/webui`
- `pnpm test -- DeviceConfigCommands` in `frontend/webui`
- `pnpm test -- ConsoleOpenApiContract DesktopApi useDesktopDeviceConfig` in `frontend/desktop`
- `pnpm test -- useDesktopDeviceConfig` in `frontend/desktop`
- `pnpm typecheck` in `frontend/webui` and `frontend/desktop`
- `cargo test` in `frontend/desktop/src-tauri`
- `cargo test device_config` and `cargo test legacy_config_snapshot` in `frontend/desktop/src-tauri`
- `./gradlew --no-daemon :relay:contract:testPlayDebugUnitTest :runtime:testPlayDebugUnitTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- `./gradlew --no-daemon :runtime:testGithubNoE2eeDebugUnitTest :relay:contract:testGithubNoE2eeDebugUnitTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- `./gradlew --no-daemon :runtime:testPlayDebugUnitTest --tests io.github.magisk317.relay.data.repository.RemoteAgentRepositoryTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- `./gradlew --no-daemon :runtime:testPlayDebugUnitTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- `./gradlew --no-daemon :relay:android:compilePlayDebugKotlin :core:compilePlayDebugKotlin -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- `./gradlew --no-daemon :mobile:feature:common:compilePlayDebugKotlin :mobile:feature:appconfig:testPlayDebugUnitTest :mobile:feature:record:testPlayDebugUnitTest -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- `./gradlew --no-daemon :benchmark:macro:assembleGithubNoE2eeDebug -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- `./gradlew --no-daemon :app:compileGithubNoE2eeDebugKotlin -PskipGoogleServices=true -PallowIncompatibleDebugSigning=true`
- `bash scripts/checks/verify_module_boundaries.sh`

## 最终本地提交

- 父仓库 `d4b56ce0 refactor(config): adopt device-centered command sync`
- 父仓库 `9b88eea5 refactor(android): lighten shell and add perf guards`
- submodule `magisk-ui-kit` 本地提交 `c02703d refactor(ui): remove haze chrome dependencies`
- 当前父仓库状态（2026-07-15 复核）：`beta` 本地 ahead 7 / behind 0，工作区干净（除文档修订）；未 push。历史压缩后为 7 笔本地提交。
