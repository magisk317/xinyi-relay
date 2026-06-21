# 系统架构与代码分层

最后更新：2026-06-21

本文档描述信驿 Relay 当前正式采用的远程架构：

`Android Agent + Backend + Web Frontend + Tauri Desktop`

## 总览

- Android 端继续负责短信、通知、来电、自动输入、Xposed/LSPosed 相关能力。
- 本地嵌入式 WebUI 已退出主运行链，避免 Android 端自带 HTTPS 服务的稳定性问题。
- 控制台能力改为远程 Backend 提供，Web 和 Desktop 共用同一套 API。
- 部署策略优先本地，可在需要时平滑扩展到公网。
- 远程 API 合同以 `shared/contracts/openapi.json` 为中心，Backend / Android Agent / Desktop / Web 均已有 drift test。

## 角色划分

### Android Agent

- 采集短信、应用通知、来电等事件。
- 执行验证码自动输入与本地交互。
- 本地缓存待上报记录与配置快照。
- 与 Backend 建立安全连接。
- 上报心跳、记录与配置快照。
- 接收远程配置并下发到现有 runtime / Xposed 链路。

边界：

- Android Agent 不直接服务 Web / Desktop 静态资源。
- Android Agent 不把本地 Room、Provider、DataStore 暴露给远程控制台。
- 远程配置落地必须先合并本地完整配置，再写入本地 repository / prefs 链路。

### Backend

- 用户 / 设备 / Token 管理。
- 配置存储、历史记录存储、审计日志。
- 实时推送（WebSocket）。
- Web / Desktop 共用的管理 API。
- 为本地部署与公网部署提供统一入口。

边界：

- Backend 不进入 Android Gradle 构建图。
- Backend 的路由和 DTO 字段必须与 `shared/contracts/openapi.json` 保持一致。
- 新 endpoint 必须同步 OpenAPI schema、Backend test、Android/TS DTO 或类型测试。

### Web Frontend

- 主控制台。
- 管理发送器、规则、设备、记录、概览和设置。
- 复用 `shared/contracts/console.ts` 中的远程类型。
- 复用 `shared/configSnapshot.ts` 中的 config snapshot normalize / clone / conflict helper，减少 Web / Desktop
  配置编辑基础逻辑漂移。
- 通过 lint、typecheck、build 与 OpenAPI contract test 保证基础一致性。

后续重点：

- 继续抽出共享配置编辑器和 sender 表单布局元数据，减少 Web / Desktop 双份实现；API client、认证状态模型
  和 sender 字段 schema 已有共享入口。

### Tauri Desktop

- 复用远程控制台能力。
- 增加桌面集成功能：托盘、通知、导出、诊断入口。
- 使用 `shared/contracts/console.ts` 与 `shared/contracts/openapi.json` 做类型/合同测试。

边界：

- Desktop 只保留桌面运行时 adapter、Tauri command、窗口/托盘/系统集成。
- 业务 API 类型和配置编辑模型优先与 Web 共享。

## 部署模式

### 本地优先

- Docker Compose 在局域网主机运行 Backend。
- Android Agent / Web / Desktop 优先连接局域网地址。
- 证书先使用内网自签 / 内部 CA。
- Android Agent 通过安装内部 CA 到“用户证书”来信任本地 HTTPS；App 已显式允许 `user` trust anchors。

### 保留公网能力

- 后续增加域名与公网入口。
- 保持同一套 API 与数据模型。
- Android Agent 可按“本地地址优先，公网地址兜底”的策略连接。

## 技术选择

### Backend

- Go。
- PostgreSQL。
- Caddy。
- Docker Compose。

理由：

- Go 适合做单二进制 API 服务，部署简单。
- PostgreSQL 适合设备、配置、记录、审计这类结构化数据。
- Caddy 更适合本地优先并保留公网扩展能力。
- Docker Compose 便于 NAS / 迷你主机 / 本地开发统一部署。

### Web / Desktop

- Web：React / Vite / TypeScript。
- Desktop：Tauri + React / Vite / TypeScript。
- 共享合同：`shared/contracts/openapi.json`、`shared/contracts/console.ts`。

理由：

- Web 和 Desktop 可以共享主要控制台模型。
- Tauri 保留轻量桌面壳和系统集成，不引入 Electron。
- TypeScript 合同让前端和 OpenAPI 字段 drift 更容易被 CI 捕捉。

## 当前实现状态

- `backend/` 已提供正式的本地优先 Compose 部署入口。
- Backend 已包含设备绑定、配置快照、记录上报、配置审计与实时事件。
- Web 控制台已接入设备、记录、发送器、应用与设置视图。
- Tauri Desktop 已提供桌面壳、托盘与内嵌控制台能力。
- Android Agent 已包含远程绑定、心跳、配置同步与记录上报链路。
- `runtime/data/remote/RemoteApiClient.kt` 已把 HTTP 调用从 `RemoteAgentRepository` 中抽出。

## 合同与漂移测试

已落地：

- Backend：`backend/api/internal/http/openapi_contract_test.go` 校验路由和 DTO 字段。
- Android Agent：`runtime/src/test/.../RemoteApiDtosContractTest.kt` 校验 agent wire DTO 字段。
- Desktop：`frontend/desktop/src/test/ConsoleOpenApiContract.test.ts` 校验共享 TypeScript 类型。
- Web：`frontend/webui/src/__tests__/ConsoleOpenApiContract.test.ts` 校验共享 TypeScript 类型，并在 CI 中执行 lint/typecheck/test/build。

后续要求：

- 新增 API 时同时更新 `shared/contracts/openapi.json`、Backend DTO test、Android DTO test、Desktop/Web 类型测试。
- OpenAPI 只能描述已实现或同一变更中落地的 endpoint，不提前写“未来接口”。

## 对现有工程的影响

- 当前 Android Gradle 工程保持不变，不将 Backend 纳入 Android 构建图。
- `frontend/webui/` 作为远程控制台前端保留。
- Android 主运行链已停止启动旧内嵌 WebUI 服务。
- Android 端远程同步继续落在 `runtime`，由 `RuntimeGraph` 装配。

## 后续演进

1. 继续抽共享配置编辑器和 sender 表单布局元数据；React hook 需要先有明确前端 package / workspace
   边界，不直接放仓库根 `shared/`。
2. 继续收敛 Web / Desktop 与手机端的功能 parity。
3. 逐步把前端静态资源也纳入远端镜像链，减少宿主机对本地 `frontend/webui/dist` 的依赖。
4. 扩展 OpenAPI schema 覆盖新增 endpoint，并把合同测试纳入对应 CI。

---

# 代码库分层与模块架构

最后更新：2026-06-21

## 当前分层

### `app`

- Android 应用壳：manifest、Application、入口 Activity、service、receiver、初始化启动。
- 负责把 `hook/entry`、`core`、`mobile/ui`、`relay/android`、`xpbridge/core` 等模块打包进 APK。
- 安装 app 进程需要的平台桥接实现，例如 `AndroidNotificationPlatformBridge`。
- 允许保留 app-process adapter：`CodeNotificationReceiver`、auto-input accessibility/result、force-stop recovery wakeup、低电量提醒 receiver。
- 不直接承载转发主编排、sender 选择、路由、结果落库。
- 不负责嵌入式 WebUI 服务启动、TLS 或静态资源打包链路。

### `hook/entry`

- Xposed/libxposed 入口、hook 调度和 hook 进程所需最小资源。
- 主入口：`io.github.magisk317.relay.xp.LibXposedEntry`。
- 不直接依赖 `core`。
- 可安装 Xposed facade 所需的 runtime / relay/android 桥接实现；不要把业务编排逻辑直接写入 hook 入口。
- 独立于 UI 页面、应用初始化或 repository 装配。
- hook 侧负责采集、解析、拦截和桥接委托；sender 选择、路由、记录写回由 runtime 主链。

### `xpbridge/core`

- Xposed/runtime 之间的兼容 facade、DTO、typealias 和桥接接口。
- notification facade 收敛至 `relay/contract` 的 `NotificationPlatformBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- SMS parser / blacklist facade 收敛至 `relay/contract` 的 `SmsRuntimeBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- clipboard facade 收敛至 `relay/contract` 的 `ClipboardPlatformBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- app config facade 收敛至 `relay/contract` 的 `XpAppConfigRuntimeBridge` 合同，具体 runtime 实现由 `runtime` 提供。
- record/export facade 收敛至 `relay/contract` 的 `XpRecordRuntimeBridge` / `XpSmsRecord` 合同，具体 runtime 实现由 `runtime` 提供。
- diagnostics facade 收敛至 `relay/contract` 的 `XpDiagnosticsRuntimeBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- prefs facade 收敛至 `relay/contract` 的 `XpPrefsRuntimeBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- SMS dispatch facade 收敛至 `relay/contract` 的 `XpSmsDispatchRuntimeBridge` / `XpPreparedSmsHookDispatch` / `XpForwardPayload` 合同，具体 runtime 实现由 `runtime` 提供。
- 固化的禁止项：不依赖 `runtime`、`relay/android`、`relay/engine` 实现模块、`smscode-core/domain` 或 Compose runtime。
- 新增桥接模型优先放 `relay/contract` 或 `relay/engine/api`；新增桥接实现优先放 `runtime` / `relay/android`。

### `relay/contract`

- 项目级稳定 contract：常量、设置模型、repository 接口、远程同步接口、JSON codec、Prefs bridge model、Xposed dispatch model。
- `RelayJson` 是业务 JSON 的首选入口。
- 新增跨模块 DTO 或配置模型时优先考虑这里；不要把 UI-only 或平台-only 类型塞进 contract。

### `relay/engine/api`

- 运行时、Android、UI 共享的 engine API。
- 承载事件模型、sender 模型、repository/service 接口、调度表达式工具等稳定 API。
- `mobile/ui`、`relay/android`、`relay/sender` 等上层或平台模块优先依赖 `:relay:engine:api`，不直接依赖 `:relay:engine` 实现。
- `SenderDispatcher` 和 sender runtime service 接口在这里；`runtime` 消费这些 API，不直接 import sender 实现包。

### `relay/engine`

- 纯领域实现与可复用算法。
- 当前承载过滤、通知路由、sender 选择等实现。
- 对外通过 `:relay:engine:api` 暴露稳定类型。

### `relay/net`

- 共享 HTTP client 与轻量网络 helper。
- `relay/sender`、`runtime` 等需要发起网络请求的模块依赖它。
- 独立于 sender 配置、路由、过滤或运行时编排逻辑。
- 普通网络调用优先使用 `RelayHttpClients`；特殊场景可以显式构造 client，但需要说明原因。

### `relay/sender`

- sender 配置模型、配置 JSON codec、发送实现、发送结果模型和 sender 日志入口。
- 不依赖 `relay/engine` 实现模块，依赖 `relay/engine/api` 与 `relay/net`。
- 提供默认 `SenderDispatcher`、sender 配置修复与定时短信发送实现，并通过 `SenderRuntimeInstaller` 装配到 `relay/engine/api` 的 service registry。

### `relay/android`

- Android 平台数据源、Room、DataStore、PrefsReader、DBProvider、诊断、日志落地、系统信息 provider。
- 提供 app/hook 可安装的平台桥接实现，例如 notification channel / delivery diagnostics adapter、SMS parser / blacklist adapter、clipboard adapter、Xposed diagnostics adapter。
- `RelayLogger` / `XLog` / `RuntimeLogStore` 的 Android 落地在这里；新增 app 侧日志优先使用
  `RelayLogger` 的显式 `LogRoute` API，`XLog` 继续作为旧调用兼容入口。
- `PrefsReader` 是 Xposed/runtime 跨进程读取的首选入口，不扩展为 UI 通用配置 facade。

### `runtime`

- 运行时装配、主 pipeline、repository 实现、远程 agent、调度、IPC adapter。
- `bootstrap`
  - 运行时装配入口：`RuntimeGraph`。
- `domain`
  - 主模型与主管线：`EventPipeline`、`EventGatekeeper`、`RoutingResolver`、`DispatchExecutor`、`DispatchResultWriter`。
  - 恢复与调度：`RootDbCatchupEngine`、`ScheduledTaskExecutor`、`RuntimeSettingsCache`。
- `data`
  - 远程 API client / DTO、repository、备份导入导出。
- `platform`
  - Android IPC / BroadcastReceiver 适配层、低电量提醒调度入口。
- 项目无 `runtime/legacy` 或 `runtime/forwarder` 目录；不要以兼容名义复原这些旧落点。

### `core`

- 应用侧 facade、initializer、Koin binding、系统能力协调层。
- 通话监听、特殊提醒、启动初始化、安全初始化、更新与备份外观层当前在这里。
- 短期继续承接 `RuntimeGraph` 到 UI/Koin 的桥接，避免 UI 层直接触达 runtime 实现包。
- Compose 页面和 UI ViewModel 主体在 `mobile/feature/*`。

### `mobile/feature/*`

- 业务功能组件化：包含独立的 Compose 页面、UI ViewModel 和特定于该特性的 UI helper（如 `common`, `settings`, `rule`, `overview` 等）。
- 依赖约束：Feature 模块可以依赖 `mobile:feature:common`、`core` 和 `relay:engine:api` 等基础设施，但业务 Feature 之间原则上不得相互依赖。

### `mobile/ui`

- 作为 **UI 组装壳与协调层**。
- 负责组装各个 `:mobile:feature:*` 提供的 Compose 页面、全局导航架构，以及跨页面的顶层交互编排。
- 不直接依赖 `runtime` / `xpbridge/core` / `relay/engine` 实现模块。
- 运行时能力经由 `core` 的 UI-facing facade、`relay/contract` 或 `relay/engine/api` 访问。
- 首页概览页包含 `OverviewChartCard`、`OverviewInfoCards` 与 `OverviewCardEditing`，
  `OverviewScreen` 负责页面状态装配、卡片路由和顶层交互编排。
- 设置首页包含 `SettingsHomeSections`、`SettingsDisplayCoordinator`、`SettingsBackupCoordinator`、
  `SettingsRuntimeLogCoordinator`、`SettingsHomeDialogs` 与 `SettingsBackupUi`；`SettingsExperienceScreens`
  负责页面状态装配、分组路由和顶层设置写入回调。
- sender 列表页包含 `SenderConfigCards`、`SenderTypeDialog`、`SenderGeneralDialogs` 与
  `SenderTemplateDialogs`，顶部配置入口、添加类型、通用/优先级配置和短信/通知/来电模板编辑
  独立于 `SenderListScreen` 主编排文件里。
- sender 列表项与删除撤销倒计时 Snackbar 位于 `SenderCard` 与 `SenderUndoSnackbar`；
  `SenderListScreen` 负责列表状态、拖拽排序、删除恢复动作和顶层对话框编排。
- Compose sender 表单统一接入 `SchemaSenderConfigForm`，字段默认值和选项来自
  `SenderSettingSchemas` / `SenderSettingDraft`；表单文件负责可见字段标签、少量兼容归一化和
  Bark 加密密钥生成等通道专属 UI。
- 新增 API 子模块优先收进所属目录，例如 `relay/engine/api`；避免在项目根目录继续增加多词模块目录。

### `features/*` (动态特性分层)

- 包含基于 Play Feature Delivery 的动态下发特性模块（例如 `features/matrix-e2ee`）。
- 该层级专门用于隔离需要重型依赖（如 Matrix Rust SDK）或仅部分用户需要的可选功能，防止主 APK 体积膨胀。
- 构建侧按需配合对应的 Source Set 或 Flavor 变体进行构建和条件编译。

### 根目录布局

- 自有模块根目录优先使用单词目录，复合语义使用嵌套目录表达，例如 `mobile/ui`、`relay/engine`、`xpbridge/core`。
- 允许保留多词根目录的仅限外部子模块边界，例如 `build-logic`、`magisk-ui-kit`、`smscode-core`、`smscode-rules`。
- Kotlin package 与 Android namespace 不随物理目录重排自动改名，避免目录治理扩大为 API 迁移。

## 边界守护

落地的守护入口：

```bash
./gradlew check
./gradlew verifyModuleBoundaries verifyStructureBoundaries verifyDependencyGovernance
./gradlew verifyEmbeddedSubmodules
SKIP_GOOGLE_SERVICES=true ALLOW_INCOMPATIBLE_DEBUG_SIGNING=true bash scripts/checks/verify_shared_submodule_compat.sh
```

根项目 `check` 聚合了 `verifyModuleBoundaries`、`verifyStructureBoundaries`、
`verifyEmbeddedSubmodules` 和 `verifyDependencyGovernance`，常规检查会覆盖模块边界、结构边界、
嵌入子模块和依赖治理。

`verify_shared_submodule_compat.sh` 额外固定共享子模块兼容面：嵌入子模块结构、模块边界、
`smscode-core` domain 单测、verification detekt、hook/runtime/xposed lint，以及 app/core 的常规检查。

当前守护重点：

| 边界 | 当前规则 |
| --- | --- |
| `app` | 必须依赖 `core` 和 `hook/entry`；可依赖 `relay/android` 安装平台 adapter；不得直接依赖 `runtime` |
| `hook/entry` | 不得依赖 `core` |
| `mobile/ui` | 不得依赖 `runtime`、`xpbridge/core`、`relay/engine` 实现模块 |
| `relay/android` | 依赖 `relay/engine/api`，不得依赖 `relay/engine` 实现模块 |
| `relay/sender` | 依赖 `relay/engine/api` 和 `relay/net`，不得依赖 `relay/engine` 实现模块 |
| `xpbridge/core` | 不得依赖 `runtime`、`relay/android`、`relay/engine` 实现模块、`smscode-core/domain` 或 Compose |
| `runtime` | 不直接依赖 Koin、Compose UI 或 `relay/sender` 实现包；sender 发送能力通过 `relay/engine/api` service 接口访问 |

仍需补强的边界：

- Web / Desktop 共享前端层尚未形成独立包，当前主要共享 `shared/contracts` 类型。

## 运行时主链

运行时依赖的装配规则：

- `runtime` 内部通过 `RuntimeGraph` 按需懒加载 `AppDatabase`、repository、pipeline 与 formatter。
- `ForwardReceiver`、`CustomMessageReceiver`、调度 worker/receiver 等运行时入口直接消费 `RuntimeGraph`。
- Koin 在 `core/app` 层复用这套实例，不作为 runtime 运行时的唯一所有者。

统一处理顺序：

1. 来源组件采集原始事件。
2. 标准化为 `RelayEvent`。
3. `EventGatekeeper` 做模块总开关、类型级开关、来源级 gate。
4. 记录判定与记录写入。
5. 特殊提醒副作用。
6. `SenderSelector` 做 sender 选择与路由。
7. `SenderDispatcher` 下发。
8. `DispatchResultWriter` 写回结果与统计。

约束：

- 主流程判断基于 `RelayEvent`。
- `MsgInfo` 仅作为 sender 下发载荷。
- 新系统事件也应优先进入 `RelayEvent -> EventPipeline`。
- sender adapter 不回填到旧 `forwarder/*` 结构。

运行时组件落点：

- 主管线：`runtime/domain/pipeline`。
- 过滤 / 路由 / 恢复：`runtime/domain/filter`、`runtime/domain/routing`、`runtime/domain/recovery` 或 `relay/engine` 中的纯领域实现。
- 设备与环境：`runtime/domain/system`、`relay/android/platform/metadata`。
- 调度：`runtime/domain/schedule`、`runtime/platform/reminder`。
- DB DAO / converter：`relay/android/data/db/dao`、`relay/android/data/db/ext`。
- 备份与导入导出：`runtime/data/backup`。
- sender 发送实现：`relay/sender`。

## 配置访问规则

### 应用内 UI

- 优先使用 repository。
- 不直接拼 pref key。
- 不直接依赖 Provider URI。

### Web / Desktop 控制台

- 统一通过 Backend API 访问配置、记录与设备状态。
- 不直接访问 Android 端 repository、Provider 或 DataStore。
- 浏览器控制台的 API 方法表统一经由 `shared/consoleApiClient.ts` 维护；Web 侧实现 fetch / CSRF /
  cookie / timeout transport adapter，Desktop 侧继续由 Tauri runtime 暴露本地 adapter。
- Desktop 远程 API 页面同样使用 `shared/consoleApiClient.ts` 的方法形状；Tauri runtime adapter 负责把共享
  endpoint 映射到本地 command，并需要覆盖 OpenAPI 中声明的 endpoint。
- 认证会话响应统一经由 `shared/consoleSession.ts` 归一化，避免各端重复解释 Web login / me 响应与
  Desktop bootstrap session 状态。
- 配置快照 clone / normalize / conflict / load / save 纯逻辑统一经由 `shared/configSnapshot.ts` 维护；
  Web / Desktop 的 React hook 负责状态 ownership 和 runtime adapter 调用。
- sender 结构化配置字段合同来自 `shared/contracts/senderSchemas.json`，由
  `scripts/codegen/generate_sender_schema_contract.py` 从 Kotlin `SenderSettingSchemas` 生成；Web / Desktop 在该合同上补充 UI label、布局和控件类型。
- sender 字段默认值和枚举选项优先写入 `SenderSettingSchemas`，当前覆盖 Telegram / Pushplus / Bark /
  Webhook / Dingtalk / Dingtalk Inner / WeCom Robot / WeCom App / Feishu / Feishu App / Socket 的跨端选项，
  Email、Gotify、Ntfy、SMS 等通道的默认值，并同步生成到 `shared/contracts/senderSchemas.json`；
  Android Compose 表单新增 schema-driven 字段时补充展示标签和运行时 adapter。

### Xposed / 跨进程运行时

- 继续使用 `PrefsReader`。
- 读取链路固定为：
  - `remote_libxposed`
  - `default`
- `PrefsReader` 负责 source-chain 解析，不扩展为 UI 通用配置 facade。
- 运行时工具箱（如 `SmsBlacklistUtils`）仅限 runtime/Xposed 使用。
- 应用进程内 runtime 热路径优先复用 `RuntimeSettingsCache`，避免分散的 `runBlocking + PreferenceDataSource`。

### 兼容期允许保留

- `AppPreferencesDataStore`
- `DBProvider`

但它们不应再作为新页面或新业务逻辑的首选入口。

## 配置访问矩阵

本文档中的矩阵用于约束主要配置项的唯一写入口、Native UI 读取入口与 runtime 读取入口，避免再次出现多套事实来源。

| 配置域 | 唯一写入口 | UI 读取入口 | runtime 读取入口 |
| --- | --- | --- | --- |
| 模块总开关、显示模式 | `SettingsRepository.get/updateGeneralSettings()` | Native 设置页 | `PrefsReader.isEnabled()` |
| 验证码功能 | `SettingsRepository.get/updateVerificationSettings()` | Native 设置页 | `PrefsReader.verificationFeaturesEnabled()` 及相关验证码 getter |
| 转发功能 | `SettingsRepository.get/updateRelaySettings()` | Native 设置页 | `PrefsReader.relayFeaturesEnabled()`、消息类型 getter |
| 特殊提醒 | `SettingsRepository.get/updateSpecialAlertSettings()` | Native 高级页 | `PrefsReader.lowBatteryReminderEnabled()` 等 |
| 记录设置 | `SettingsRepository.get/updateRecordSettings()` | 记录页设置面板 | `PrefsReader.recordCodeSmsEnabled()` 等 |
| 高级诊断 | `SettingsRepository.get/updateDiagnosticsSettings()` | Native 高级页 | `PrefsReader.analyticsEnabled()` 等 |
| IPC token | `SecurityInitializer` | 不直接暴露 | `PrefsReader.getIpcToken()` |

### 验证码规则目录

- 用户自定义验证码规则仍由本地 DB、备份、导入导出链路承载。
- 官方规则来自 `smscode-rules` 内容型子模块、远程 raw GitHub 与应用私有缓存；它们在运行时映射为 `SmsCodeRuleSpec`，不写入用户规则表。
- 运行时合并顺序固定为：用户自定义规则优先，官方规则按 `priority` 与索引顺序，其次才走共享内置通用解析。

### 仍处于兼容期的直接访问

以下位置仍可直接访问底层配置，但不应继续扩散：

- `SmsCodeApplication`
  - Koin 启动与 initializer 调度。
- `RuntimeGraph`
  - 应用进程内运行时单例装配中心。
- `PrefsReader`
  - Xposed/runtime 跨进程读取。
- `RuntimeSettingsCache`
  - 应用进程 runtime 热路径只读缓存。
  - 仅缓存 `PreferenceDataSource` / `SettingsRepository` 的运行时快照，不提供新的业务语义入口。
- `SmsBlacklistUtils`
  - runtime-only 工具箱。
  - 仅限 runtime/Xposed 使用，不作为 Native UI / repository API。
- `PrefsSourceChain`
  - 仅负责运行时 source-chain 解析逻辑。

### 配置治理后续约束

1. Native 剩余直接依赖 `AppPreferencesDataStore` 的页面继续迁到 repository。
2. Native 设置入口继续统一通过 repository 返回配置快照。
3. 内部 `MessageTypeGateSnapshot` 仅供 runtime / diagnostics 使用，不作为用户设置模型。
4. 新配置项默认先在本文件登记，再落地实现。

## Xposed / Runtime 接入

### 入口模型

- 主入口：`io.github.magisk317.relay.xp.LibXposedEntry`。
- `META-INF/xposed/java_init.list` 指向主入口，由 libxposed 框架实例化。
- 业务 hook 调度统一通过 `LibXposedEntry`。

### Hook 兼容层

- 统一兼容层包：`io.github.magisk317.relay.xp.compat`。
- 主要组件：
  - `XposedBridge`
  - `XposedHelpers`
  - `XC_MethodHook` / `XC_MethodReplacement`
  - `XC_LoadPackage.LoadPackageParam`

### 配置读取链路

`PrefsReader` 是 Xposed/runtime 场景的唯一首选入口，读取优先级固定为：

1. `remote_libxposed`
2. `default`

说明：

- UI 不应依赖这条链路作为主配置 API。
- 应用内设置页优先走 repository。
- `PrefsReader` 的职责是 runtime source-chain resolver，不是全项目通用配置 API。

### 运行时事件入口

- 短信、应用通知、来电最终都应收敛为 `RelayEvent`。
- 跨进程转发入口：`io.github.magisk317.relay.platform.ipc.ForwardReceiver`。
- 自定义消息入口：`io.github.magisk317.relay.platform.ipc.CustomMessageReceiver`。
- 短信 hook 派发协调：`io.github.magisk317.relay.platform.ipc.SmsHookDispatchCoordinator`。
- 主管线：`EventPipeline`。
- 运行时依赖来源：`RuntimeGraph`。

约束：

- Xposed/runtime 负责采集与标准化。
- 不在 hook 层直接实现 sender 选择、路由、结果落库。
- 不在 `runtime` 的运行时入口直接依赖 Koin API。
- 应用进程内 runtime 热路径可通过 `RuntimeSettingsCache` 做短 TTL 只读缓存，但底层事实来源仍是 `PreferenceDataSource` / repository。

### 发布策略与构建变体 (Flavors)

- 仅维护 libxposed 新 API 单轨发布（Play / GitHub）。
- **变体隔离策略**：构建变体细分为 `githubWithE2ee`、`githubNoE2ee` 以及 `play` 渠道等。
  - 对于 Play 渠道：依赖 Google Play Feature Delivery 处理如 Matrix E2EE 等动态模块的按需下发。
  - 对于 GitHub (非 Play) 渠道：通过源码集与 Flavor 变体直接进行全量/精简版本的静态隔离（例如 `app/src/githubNoE2ee` 不包含 Matrix SDK，以缩减包体积）。

## 设置结构

### 设置

- 通用。
- 验证码功能。
- 转发功能入口。

### 高级

- 转发配置。
- 特殊提醒。
- 拦截与过滤。
- Remote Agent / Backend。
- 实验性与诊断。

说明：

- “消息类型进入主处理管线”是内部运行时概念。
- 不作为面向用户的设置术语。
- 远程控制台也暴露用户能力开关，不直接暴露内部 message-type gate。

## 后续开发约束

1. 新运行时规则优先落在 `runtime/domain` 或 `relay/engine` 的纯领域实现，不落在 `app` / `mobile/ui`。
2. 新跨进程入口优先落在 `runtime/platform`。
3. 新设置页优先走 `SettingsRepository` 与 contract snapshot/update 模型。
4. 不新增 `legacy` / `forwarder` 目录或向旧兼容 facade 增加主编排逻辑。
5. 新 sender 能力落在 `relay/sender`，通过 `relay/engine/api` 的 dispatcher/service 接口暴露给 runtime；不要在 `runtime` 新增 sender-specific 分支。
6. `xpbridge/core` 新增内容优先是 DTO/typealias/facade 签名，实现逻辑放 `runtime` / `relay/android`。
7. `runtime` 不直接依赖 Koin；若 UI 需要 DI，优先复用 `RuntimeGraph` 构造的实例。
8. 新远程 API 必须同步 `shared/contracts/openapi.json`、Backend test、Android DTO test 和 TS contract test。
9. sender 字段 schema 变更必须重新生成 `shared/contracts/senderSchemas.json`，并保持 Android / Web / Desktop 漂移测试通过。
