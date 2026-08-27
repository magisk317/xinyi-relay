# 系统架构与代码分层

最后更新：2026-08-27

## 总览

四端架构：`Android Agent + Backend + Web Frontend + Tauri Desktop`

- Android 端负责短信/通知/来电采集、验证码自动输入、Xposed hook 能力。
- 控制台能力由远程 Backend 提供，Web / Desktop 共用同一套 API。
- 部署策略：本地优先（Docker Compose），可扩展到公网。
- API 合同中心：`frontend/shared/contracts/openapi.json`；它现在由 backend `internal/http` 的 assembler 生成，但 auth/system/device-config/read-model schema 与 route metadata 已大部分先从 `relay/contract` 生成到 backend，再由 assembler 合并，`frontend/shared/contracts/console.generated.ts` 再由它生成，四端均有 drift test。

## Gradle 坐标与物理路径

下文模块名优先使用 **Gradle 逻辑坐标**（`:` 风格，叙述里常写成 `hook/entry`）。源码目录与坐标并不总是 1:1，对照如下：

| 叙述 / Gradle 坐标 | 物理目录 |
|---|---|
| `:app` / `app` | `app/` |
| `:hook:entry` / `hook/entry` | `modules/hook/entry/` |
| `:runtime` / `runtime` | `modules/runtime/` |
| `:core` / `core` | `modules/core/` |
| `:policy` / `policy` | `modules/policy/` |
| `:relay:android` / `relay/android` | `modules/relay/android/` |
| `:relay:contract` / `relay/contract` | `modules/relay/contract/` |
| `:relay:engine` / `relay/engine` | `modules/relay/engine/` |
| `:relay:engine:api` / `relay/engine/api` | `modules/relay/engine/api/` |
| `:relay:net` / `relay/net` | `modules/relay/net/` |
| `:relay:sender` / `relay/sender` | `modules/relay/sender/` |
| `:relay:sender:api` / `relay/sender/api` | `modules/relay/sender/api/` |
| `:relay:matrix-e2ee` / `relay/matrix-e2ee` | `modules/relay/matrix-e2ee/` |
| `:xpbridge:core` / `xpbridge/core` | `modules/xpbridge/core/` |
| `:xpbridge:android:api` / `xpbridge/android/api` | `modules/xpbridge/android/api/` |
| `:mobile:ui` / `mobile/ui` | `mobile/ui/` |
| `:mobile:feature:*` / `mobile/feature/*` | `mobile/feature/*/` |
| `:features:matrix_e2ee` | `features/matrix-e2ee/` |
| `:smscode-core:*` | `smscode/core/*`（Git 子模块） |

查找真实路径时以 `settings.gradle.kts` 的 `project(...).projectDir` 为准，不要假设“Gradle 名 = 仓库根下同名目录”。


## 代码库分层

### `app`

Android 应用壳。打包 `hook/entry`、`core`、`policy`、`mobile/ui`、feature 模块、`relay/android`、`xpbridge/core` 等。

- 允许：`core`、`policy`、`hook/entry`、`relay/android`、`mobile/ui`、`mobile/feature/*`、`xpbridge/core`、`smscode-core/verification`、`smscode-core/hook`
- 禁止：直接依赖 `runtime`（`verifyNoRuntimePipelineLeak` 源码级禁令）

### `hook/entry`

Xposed/libxposed 入口和 hook 调度。hook 侧负责采集/解析/拦截，不直接做 sender 选择、路由、结果落库。

- 允许：`runtime`、`relay/android`、`xpbridge/core`、`relay/contract`、`smscode-core/*`
- 禁止：`core`

### `xpbridge/core`

Xposed hook 侧 facade 与协调器。通过 `:xpbridge:android:api` 消费 runtime bridge，不拥有 runtime 实现。

- 允许：`xpbridge/android/api`、`relay/contract`、共享 hook/runtime contract
- 禁止：`runtime`、`relay/android`、`relay/engine` 实现、`smscode-core/domain`、Compose

### `xpbridge/android/api`

Xposed/runtime 之间的 Android API 边界，当前拥有 `XpSmsDispatchRuntimeBridge` 与 Noop。方法签名复用 `relay/contract` 中的平台中立 DTO；实现位于 `runtime`。

- 允许：Android SDK、`relay/contract`
- 禁止：`runtime`、`xpbridge/core`、`relay/android` 及 data/domain/platform 实现包

### `relay/contract`

项目级稳定 contract：常量、设置模型、repository 接口、远程同步接口、JSON codec、设备 mirror DTO、sender active schedule 真源。`RelayJson` 是业务 JSON 首选入口。Android 图标编码等平台实现归 `relay/android`；Xposed Android runtime bridge API 归 `xpbridge/android/api`。

### `relay/engine/api`

运行时/UI 共享的 engine API：事件模型、sender 模型、`SenderDispatcher`、`SenderSelector`、service 接口。上层模块优先依赖此模块，不直接依赖 `:relay:engine`。

### `relay/engine`

纯领域实现：`ForwardFilterEngine`、`NotifyRoutingResolver`。对外通过 `:relay:engine:api` 暴露。

### `relay/net`

共享 HTTP client。`RelayHttpClients.default` 配置 `callTimeout(30s)` 上限。

### `relay/sender`

sender 配置模型、发送实现、发送结果模型。通过 `SenderRuntimeInstaller` 装配到 `relay/engine/api` service registry。

- 允许：`relay/sender/api`、`relay/engine/api`、`relay/net`、`relay/contract`、`smscode-core/contract`
- 禁止：`relay/engine` 实现

### `relay/matrix-e2ee`

产品内 Matrix E2EE 唯一实现，持有 Matrix SDK sender、verification、room crypto state 和
对应测试。它通过 `relay/sender/api` 的 `MatrixE2eeHost` 端口使用 sender 侧的认证、明文回退、
格式化与日志策略，避免依赖 `relay/sender` 实现或任一打包入口。

- 允许：`relay/sender/api`、`relay/net`、Matrix SDK
- 禁止：`app`、`features/matrix-e2ee`、`relay/sender` 实现
- GitHub 仅由 `githubWithE2ee` 变体依赖；Play base 不依赖，由动态 feature 负责携带

### `relay/android`

Android 平台数据源与 adapter：Room、DataStore、PrefsReader、DBProvider、诊断、日志落地，以及应用图标解析/编码等 PackageManager/graphics 能力。

- 允许：`relay/engine/api`、`relay/sender`（implementation，不传递暴露）、`relay/contract`、`smscode-core/domain`、`smscode-core/runtime`
- 禁止：`relay/engine` 实现

### `runtime`

运行时装配、主 pipeline、repository 实现、远程 agent、调度、IPC adapter。

- 通过 `RuntimeDependencies` 接口解析依赖，不直接依赖 Koin
- 通过 `verifyNoComposeUiLeak` 禁止 Compose UI
- 不依赖 `relay/sender` 实现，sender 能力通过 `relay/engine/api` service 接口访问

子包：`bootstrap`（`RuntimeDependencies`）、`domain`（pipeline/filter/routing/recovery/schedule）、`data`（remote API/repository/backup）、`platform`（IPC/BroadcastReceiver/reminder）。

### `core`

应用侧 facade、initializer、Koin binding、系统能力协调层。`RuntimeGraph` 是 Koin 的类型化 facade，仅供 `:core` 内部使用。

- 允许：`runtime`（implementation，不传递暴露）、`policy`、`relay/engine/api`、`relay/android`、`relay/contract`、`magisk-ui-kit`、`smscode-core/*`
- 禁止：`xpbridge/core`、`webui` 包（`verifyNoWebUiLeak`）

### `policy`

标准模式 / 工作模式策略层：`WorkModeResolver`、`StandardModeFeatureGate`、权限与电池优化相关策略。从 `core` 拆出，供 app、core 与部分 feature 使用。

- Enhanced 与 Standard 是同一个 APK 的运行时能力状态，不是产品 flavor：检测到有效
  libxposed runtime/本次开机 hook 心跳时为 Enhanced，否则为 Standard。
- `Inactive` 只为未来显式全局停用保留；缺少 Xposed 或某一项电话权限不再禁用整个应用。
- Standard 权限按当前发行 manifest 求交并下沉到具体能力。Play 即使移除了电话权限，仍可
  以通知监听等合规能力运行 Standard 子集。
- libxposed service bind/died 后必须立即重算模式并协调 `StandardModeService` 与
  `CallStateMonitor`；安装/更新后的电话进程重启只允许在成功 bind 后触发。
- `StandardModeService` 使用 `remoteMessaging` FGS 与对应权限；不得退回 Android 15+ 受
  开机启动限制和累计时限约束的 `dataSync`。
- Standard MMS 的 Notification.ind metadata parser 随 APK 交付，禁止反射未打包的
  `com.google.android.mms` 隐藏实现。解析异常需记录结构化原因并安全降级。
- 通话结束的 CallLog 补全是有上限、可取消的延迟解析；已有直接/recent ingress 号码或无
  `READ_CALL_LOG` 时不得查询，模式切换/新通话需取消旧等待，最终事件只派发一次。

- Gradle：`:policy`；物理目录：`modules/policy/`
- 允许：Android/Kotlin 标准库与策略所需的轻量依赖（见 `modules/policy/build.gradle.kts`）
- 禁止：Compose UI、`relay/engine` 实现、直接依赖 hook 进程专用 API

### `mobile/feature/*`

业务功能组件化。`mobile:feature:common` 是共享 UI 工具层；**不**通过 `api()` 重导出 relay 依赖（`implementation` 即可）。各 feature 须显式声明自己真正用到的 `:relay:*` / `:core` / `:policy` / `:smscode-core:*`。

- Feature 模块可依赖：`mobile:feature:common`、`:core`、`:policy`、`:relay:engine:api`、`:relay:contract`、`:relay:android`、`:relay:sender:api`、`:magisk-ui-kit`、`:smscode-core:*`（按需）
- 业务 Feature 之间原则上不得相互依赖

### `mobile/ui`

UI 组装壳与协调层。原 2.4 万行 god module 已拆分为 `mobile:feature:*` 子模块，入度仅 1（只被 `app` 依赖）。

- 禁止：`runtime`、`xpbridge/core`、`relay/engine` 实现、`relay/sender` 实现（用 `relay/sender/api`）
- 顶层主界面使用 `magisk-ui-kit:MainTabScaffold` + typed `NavHost`：5 个主 tab 是独立
  destination，详情/配置页继续走 stack 路由；不再使用 `HorizontalPager` 作为主导航宿主。
- tab 转场、compact chrome inset 与双击刷新判定由 ui-kit 提供，父模块仍拥有 typed route、
  业务页、刷新信号和滚动 chrome 策略。

### `features/*`

Play Feature Delivery 动态下发模块（如 `features/matrix-e2ee`），隔离重型依赖。
Matrix dynamic feature 只负责 SDK 初始化及 provider 注册，业务实现来自
`:relay:matrix-e2ee`，不得在 feature 内复制 sender、verification 或 room state。

## 边界守护

```bash
./gradlew check  # 聚合 verifyModuleBoundaries + verifyStructureBoundaries + verifyEmbeddedSubmodules + verifyDependencyGovernance
bash scripts/checks/verify_shared_submodule_compat.sh  # 共享子模块兼容面
```

完整边界规则表见上文各模块的"允许/禁止"列表。守护脚本：`scripts/checks/verify_module_boundaries.sh`。

## 运行时主链

装配：`:runtime` 通过 `RuntimeDependencies`（service-locator）解析单例；`:core` 通过 `RuntimeGraph`（Koin facade）访问同一批实例。

处理顺序：采集原始事件 → `RelayEvent` → `EventGatekeeper`（gate）→ 记录写入 → 特殊提醒 → `SenderSelector`（选择/路由）→ `SenderDispatcher`（下发）→ `DispatchResultWriter`（结果写回）。

同步触发：`EventPipeline.finally` 是唯一 sync owner，通过 `messageSyncTrigger` 回调调用 `scheduleMessageTriggeredSync`。

## 配置访问

### 矩阵

| 配置域 | 唯一写入口 | UI 读取 | runtime 读取 |
|--------|-----------|---------|-------------|
| 模块总开关/显示模式 | `SettingsRepository.updateGeneralSettings()` | repository | `PrefsReader.isEnabled()` |
| 验证码功能 | `SettingsRepository.updateVerificationSettings()` | repository | `PrefsReader.verificationFeaturesEnabled()` |
| 转发功能 | `SettingsRepository.updateRelaySettings()` | repository | `PrefsReader.relayFeaturesEnabled()` |
| 特殊提醒 | `SettingsRepository.updateSpecialAlertSettings()` | repository | `PrefsReader.lowBatteryReminderEnabled()` |
| 记录设置 | `SettingsRepository.updateRecordSettings()` | repository | `PrefsReader.recordCodeSmsEnabled()` |
| 高级诊断 | `SettingsRepository.updateDiagnosticsSettings()` | repository | `PrefsReader.analyticsEnabled()` |
| IPC token | `SecurityInitializer` | 不暴露 | `PrefsReader.getIpcToken()` |

### 规则

- UI 优先 repository，不直接拼 pref key、不依赖 Provider URI。
- Web/Desktop 统一通过 Backend API 访问，不直接访问 Android 端数据层。
- 设备 mirror 的 clone / normalize 逻辑统一经由 `frontend/shared/configRoot.ts`，Web/Desktop 共享。
- `frontend/shared/contracts/configRoot.generated.ts` 由
  `scripts/codegen/generate_config_root_contract.py` 从
  `relay/contract/model/LocalConfigMirrorPayload.kt` 生成。
- OpenAPI schema 片段由 `scripts/codegen/generate_openapi_schemas.py` 从
  `relay/contract/remote/*Contracts.kt` 生成，再由 backend OpenAPI builder
  合并进最终 `openapi.json`。
- OpenAPI route metadata 由
  `scripts/codegen/generate_openapi_route_contracts.py` 从
  `relay/contract/remote/OpenApiRouteContracts.kt` 生成，再由 backend
  builder 合并进最终 `openapi.json`。
- sender 字段 schema 由 `scripts/codegen/generate_sender_schema_contract.py` 从 Kotlin 生成。

### Xposed 跨进程读取

`PrefsReader` 是唯一入口，读取链路：`remote_libxposed` → `default`。不扩展为 UI 通用配置 facade。

### 远程配置同步与冲突策略

- Android 本地配置是唯一真源，先本地 commit，再异步 push device mirror。
- Web / Desktop 不直接写 mirror，只向目标设备提交 `ConfigMutationBatch`
  命令。
- Backend mirror 与 pending command 均按设备隔离；pending command 只表示乐观状态，
  设备成功应用并 ack 前不得修改 mirror。
- 同一设备连续排队时，下一条命令以最新 pending `targetRevision` 为基线；首次绑定仅在
  本地 revision 为 `0` 且没有 dirty state 时允许导入远端初始 mirror。
- 命令基线落后时，Backend 返回 `stale_base_revision`；前端必须刷新目标
  设备 mirror 后重建 mutation，而不是套用旧的共享 snapshot merge 逻辑。
- 已被更新 mirror 越过的旧命令会变成 stale，不允许再覆盖设备上的更新本地
  revision。
- 活跃 wire 字段使用 `mirrorContent`，写操作只接受 typed mutation；`config_snapshots`
  与 SQLite `snapshot` 仅是一次性迁移或本地存储实现细节，不得恢复为共享配置真源。

### 多设备数据隔离

| 数据 | 隔离维度 | 说明 |
|------|---------|------|
| 记录（relay_records） | 按设备（`device_id`） | 每条记录由后端从 Bearer token 解析 device_id 写入，`(device_id, event_id)` 唯一索引保证幂等。两台手机同步到同一后端不会混杂。 |
| 设备配置镜像（device_config_mirrors） | 按设备 | Backend 只保存每台设备最新 mirror 与 revision，用于 Web/Desktop 读取和命令基线校验。 |
| 设备配置命令（device_config_commands） | 按设备 | Web/Desktop 写入的是排队命令；设备上线后按 revision 顺序拉取、应用并 ack。 |
| 设备配置审计（device_config_audit_logs） | 按设备 | 记录谁对哪台设备发了什么 mutation、设备何时应用、结果如何。 |
| 设备信息（devices） | 按设备 | 每台设备独立注册，独立 token。 |

## 后续演进

1. ~~共享配置编辑器 hook~~ — shared 目录无 React 依赖，hooks 无法提取到 shared；当前主路径已经切到“设备上下文 + typed mutation + 命令队列”，UI 层保持各端独立。
2. 收敛 Web / Desktop 功能 parity — SenderFieldEditor/SendersPage 仍有 UI 层重复，因设计系统差异保持各端独立。
3. 前端静态资源纳入远端镜像链。
4. 扩展 OpenAPI schema 覆盖新增 endpoint。
5. 桌面端独立运行：
   - ✅ SQLite Store（`sqlite_store.rs`）+ Store trait 抽象
   - ✅ pull/push 同步（`sync.rs`）+ Hybrid 自动定期同步（5 分钟）
   - ✅ 本地 HTTP server（`local_server.rs`，axum），Local/Hybrid 模式自动启动，绑定 `0.0.0.0:0`，端点：
     - `POST /api/v1/devices/register` — 设备注册
     - `POST /api/v1/heartbeat` — 心跳
     - `POST /api/v1/records/batch` — 记录上传
     - `GET /api/v1/devices/{id}/config` / `POST /api/v1/devices/{id}/config/commands` — 设备级配置 mirror 与命令队列
     - `GET /api/v1/devices`、`GET /api/v1/records`、`GET /api/v1/system/info`、`GET /healthz`
   - ✅ 冲突解决 UI — SyncResultCard 显示 local vs remote revision + 操作提示
   - ✅ 数据库导出（`desktop_export_database` 复制 SQLite 到 Downloads）
   - ✅ 数据库导入（`desktop_import_database` 替换并重新打开连接）
   - ✅ 本地模式概览页 — 显示 SQLite 统计（设备数、记录数、配置 revision）
   - ✅ push 同步 — 仅推送 config（records/devices 由 Android 设备直接上传后端，Desktop 是消费者无需反向推送，`RemoteStore::upsert_*` 故意 no-op）

## 重构历程

| 阶段 | 内容 | 状态 |
|------|------|------|
| P1 | JSON/协议边界收敛（`RelayJson` → `relay/contract`） | ✅ |
| P2 | 远程 API 合同收敛（OpenAPI 中心 + 四端 drift test） | ✅ |
| P3 | 模块边界治理（`verifyModuleBoundaries` 固化） | ✅ |
| P4 | Compose UI 大文件拆分 | ✅ |
| P5 | 日志系统收敛（`LogLevel`/`LogRoute`/`LogEvent`/`LogSink`） | ✅ |
| P6 | 桌面端独立运行（SQLite Store、双向配置同步、本地服务与管理 UI） | ✅ |

## 平台兼容性：Android 17 (API 37)

| 等级 | 问题 | 状态 |
|------|------|------|
| 🔴 严重 | `SMS_RECEIVED_ACTION` 对 OTP 施加 3 小时延迟。Xposed hook 可能不受限，需实测。 | 待验证 |
| ⚠️ 高 | 应用内存限制（基于设备 RAM）。 | 待验证 |
| ⚠️ 中 | PendingIntent mutability 显式声明。 | 已合规 |
| ⚠️ 中 | `MODE_WORLD_READABLE` 在 targetSdk 37 抛异常。 | 已合规 |
| ⚠️ 中 | 后台音频 API 调用静默失败。项目未使用后台音频。 | 无需修改 |
| ⚠️ 低 | 隐式 URI 授权限制（Android 18 预告）。 | 无需修改 |
| ⚠️ 低 | 每应用密钥库限制。 | 无需修改 |

## 已知技术债

1. `hook/entry`（11 处）和 `relay/android/DBProvider`（13 处）的 `runBlocking` 受 Android API 约束（Xposed 无协程基础设施、ContentProvider 同步接口），属可接受兼容。
2. 前端共享层是源码目录复用而非独立 package，新增共享逻辑需同步测试覆盖。
3. Android 17 OTP 广播延迟与内存限制待设备实测。

## 后续开发约束

1. 新运行时规则 → `runtime/domain` 或 `relay/engine`，不落 `app`/`mobile/ui`。
2. 新跨进程入口 → `runtime/platform`。
3. 新设置页 → `SettingsRepository` + contract snapshot/update。
4. 不新增 `legacy`/`forwarder` 目录（`verify_module_boundaries.sh` 守护）。
5. 新 sender → `relay/sender`，通过 `relay/engine/api` 暴露给 runtime。
6. `xpbridge/core` 新增内容优先 DTO/facade 签名，实现放 `runtime`/`relay/android`。
7. `:runtime` 新入口用 `RuntimeDependencies`，不绕过 Koin 边界。
8. 新远程 API 必须同步 OpenAPI + 四端 drift test。
9. sender schema 变更必须重新生成 `senderSchemas.json`。
