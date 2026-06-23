# 系统架构与代码分层

最后更新：2026-06-23

## 总览

四端架构：`Android Agent + Backend + Web Frontend + Tauri Desktop`

- Android 端负责短信/通知/来电采集、验证码自动输入、Xposed hook 能力。
- 控制台能力由远程 Backend 提供，Web / Desktop 共用同一套 API。
- 部署策略：本地优先（Docker Compose），可扩展到公网。
- API 合同中心：`frontend/shared/contracts/openapi.json`，四端均有 drift test。

## 代码库分层

### `app`

Android 应用壳。打包 `hook/entry`、`core`、`mobile/ui`、`relay/android`、`xpbridge/core`。

- 允许：`core`、`hook/entry`、`relay/android`、`mobile/ui`、`xpbridge/core`、`smscode-core/verification`、`smscode-core/hook`
- 禁止：直接依赖 `runtime`（`verifyNoRuntimePipelineLeak` 源码级禁令）

### `hook/entry`

Xposed/libxposed 入口和 hook 调度。hook 侧负责采集/解析/拦截，不直接做 sender 选择、路由、结果落库。

- 允许：`runtime`、`relay/android`、`xpbridge/core`、`relay/contract`、`smscode-core/*`
- 禁止：`core`

### `xpbridge/core`

Xposed/runtime 之间的 facade、DTO 和桥接接口。各 facade 收敛至 `relay/contract` 合同，实现放 `runtime` / `relay/android`。

- 禁止：`runtime`、`relay/android`、`relay/engine` 实现、`smscode-core/domain`、Compose

### `relay/contract`

项目级稳定 contract：常量、设置模型、repository 接口、远程同步接口、JSON codec。`RelayJson` 是业务 JSON 首选入口。

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

### `relay/android`

Android 平台数据源：Room、DataStore、PrefsReader、DBProvider、诊断、日志落地。

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

- 允许：`runtime`（implementation，不传递暴露）、`relay/engine/api`、`relay/android`、`relay/contract`、`magisk-ui-kit`、`smscode-core/*`
- 禁止：`xpbridge/core`、`webui` 包（`verifyNoWebUiLeak`）

### `mobile/feature/*`

业务功能组件化。`mobile:feature:common` 是共享 UI 工具层，通过 `api()` 传递暴露 `:relay:android`、`:relay:engine:api`、`:relay:contract`。

- Feature 模块可依赖 `mobile:feature:common`、`core`、`relay:engine:api`
- 业务 Feature 之间原则上不得相互依赖

### `mobile/ui`

UI 组装壳与协调层。原 2.4 万行 god module 已拆分为 `mobile:feature:*` 子模块，入度仅 1（只被 `app` 依赖）。

- 禁止：`runtime`、`xpbridge/core`、`relay/engine` 实现、`relay/sender` 实现（用 `relay/sender/api`）

### `features/*`

Play Feature Delivery 动态下发模块（如 `features/matrix-e2ee`），隔离重型依赖。

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
- 配置快照编辑逻辑统一经由 `frontend/shared/configSnapshot.ts`，Web/Desktop 共享。
- sender 字段 schema 由 `scripts/codegen/generate_sender_schema_contract.py` 从 Kotlin 生成。

### Xposed 跨进程读取

`PrefsReader` 是唯一入口，读取链路：`remote_libxposed` → `default`。不扩展为 UI 通用配置 facade。

### 远程配置冲突策略

push 409 时：应用云端 snapshot 作为新 base，保留 `pendingMutations` 不清零，状态设为 `dirty` 触发下一次 push。本地修改不会被静默丢弃。

### 多设备数据隔离

| 数据 | 隔离维度 | 说明 |
|------|---------|------|
| 记录（relay_records） | 按设备（`device_id`） | 每条记录由后端从 Bearer token 解析 device_id 写入，`(device_id, event_id)` 唯一索引保证幂等。两台手机同步到同一后端不会混杂。 |
| 配置快照（config_snapshots） | 按用户（`user_id`） | 所有设备共享同一配置链，乐观锁（`base_revision`）处理并发写入。`deviceAppInfos` 字段按 device_id 分区，各设备只读取自己的条目。 |
| 设备信息（devices） | 按设备 | 每台设备独立注册，独立 token。 |

## 后续演进

1. ~~共享配置编辑器 hook~~ — shared 目录无 React 依赖，hooks 无法提取到 shared；sender 业务逻辑已提取到 `shared/senderDefaults.ts`，UI 层保持各端独立。
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
     - `GET/PUT /api/v1/config/snapshot` — 配置读写（乐观锁冲突检测）
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
| P6 | 桌面端独立运行（SQLite Store + pull 同步已实现，push + UI 待完成） | 🔵 |

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

详细分析：`docs/xinyi-relay-android17-impact.md`

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
