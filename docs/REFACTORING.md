# Relay 架构重整说明

最后更新：2026-05-25

本文档描述当前代码库的推荐分层、已经固化的边界，以及后续开发应遵循的落点规则。路线图和阶段计划见仓库外层的 `xinyi-relay-refactor-plan.md`。

## 当前分层

### `app`

- Android 应用壳：manifest、Application、入口 Activity、service、receiver、初始化启动。
- 负责把 `hook/entry`、`core`、`mobile/ui`、`relay/android`、`xpbridge/core` 等模块打包进 APK。
- 安装 app 进程需要的平台桥接实现，例如 `AndroidNotificationPlatformBridge`。
- 允许保留 app-process adapter：`CodeNotificationReceiver`、auto-input accessibility/result、force-stop recovery wakeup、低电量提醒 receiver。
- 不直接承载转发主编排、sender 选择、路由、结果落库。
- 不再承载嵌入式 WebUI 服务启动、TLS 或静态资源打包链路。

### `hook/entry`

- Xposed/libxposed 入口、hook 调度和 hook 进程所需最小资源。
- 主入口：`io.github.magisk317.relay.xp.LibXposedEntry`。
- 不直接依赖 `core`。
- 可安装 Xposed facade 所需的 runtime / relay/android 桥接实现；不要把业务编排逻辑直接写入 hook 入口。
- 不承载 UI 页面、应用初始化或 repository 装配。
- hook 侧只做采集、解析、拦截和桥接委托；sender 选择、路由、记录写回继续下沉到 runtime 主链。

### `xpbridge/core`

- Xposed/runtime 之间的兼容 facade、DTO、typealias 和桥接接口。
- notification facade 已收敛到 `relay/contract` 的 `NotificationPlatformBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- SMS parser / blacklist facade 已收敛到 `relay/contract` 的 `SmsRuntimeBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- clipboard facade 已收敛到 `relay/contract` 的 `ClipboardPlatformBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- app config facade 已收敛到 `relay/contract` 的 `XpAppConfigRuntimeBridge` 合同，具体 runtime 实现由 `runtime` 提供。
- record/export facade 已收敛到 `relay/contract` 的 `XpRecordRuntimeBridge` / `XpSmsRecord` 合同，具体 runtime 实现由 `runtime` 提供。
- diagnostics facade 已收敛到 `relay/contract` 的 `XpDiagnosticsRuntimeBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- prefs facade 已收敛到 `relay/contract` 的 `XpPrefsRuntimeBridge` 合同，具体 Android 实现由 `relay/android` 提供。
- SMS dispatch facade 已收敛到 `relay/contract` 的 `XpSmsDispatchRuntimeBridge` / `XpPreparedSmsHookDispatch` / `XpForwardPayload` 合同，具体 runtime 实现由 `runtime` 提供。
- 已固化的禁止项：不依赖 `runtime`、`relay/android`、`relay/engine` 实现模块、`smscode-core/smscode-domain` 或 Compose runtime。
- 新增桥接模型优先放 `relay/contract` 或 `relay/engine/api`；新增桥接实现优先放 `runtime` / `relay/android`。

### `relay/contract`

- 项目级稳定 contract：常量、设置模型、repository 接口、远程同步接口、JSON codec、Prefs bridge model、Xposed dispatch model。
- `RelayJson` 是业务 JSON 的首选入口。
- 新增跨模块 DTO 或配置模型时优先考虑这里；不要把 UI-only 或平台-only 类型塞进 contract。

### `relay/engine/api`

- 运行时、Android、UI 共享的 engine API。
- 承载事件模型、sender 模型、repository/service 接口、调度表达式工具等稳定 API。
- `mobile/ui`、`relay/android`、`relay/sender` 等上层或平台模块优先依赖 `:relay:engine:api`，不直接依赖 `:relay:engine` 实现。
- `SenderDispatcher` 和 sender runtime service 接口在这里；`runtime` 只消费这些 API，不直接 import sender 实现包。

### `relay/engine`

- 纯领域实现与可复用算法。
- 当前承载过滤、通知路由、sender 选择等实现。
- 对外通过 `:relay:engine:api` 暴露稳定类型。

### `relay/net`

- 共享 HTTP client 与轻量网络 helper。
- `relay/sender`、`runtime` 等需要发起网络请求的模块依赖它。
- 不承载 sender 配置、路由、过滤或运行时编排逻辑。
- 普通网络调用优先使用 `RelayHttpClients`；特殊场景可以显式构造 client，但需要说明原因。

### `relay/sender`

- sender 配置模型、配置 JSON codec、发送实现、发送结果模型和 sender 日志入口。
- 不依赖 `relay/engine` 实现模块，只依赖 `relay/engine/api` 与 `relay/net`。
- 提供默认 `SenderDispatcher`、sender 配置修复与定时短信发送实现，并通过 `SenderRuntimeInstaller` 装配到 `relay/engine/api` 的 service registry。

### `relay/android`

- Android 平台数据源、Room、DataStore、PrefsReader、DBProvider、诊断、日志落地、系统信息 provider。
- 提供 app/hook 可安装的平台桥接实现，例如 notification channel / delivery diagnostics adapter、SMS parser / blacklist adapter、clipboard adapter、Xposed diagnostics adapter。
- `RelayLogger` / `XLog` / `RuntimeLogStore` 的 Android 落地在这里。
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
- 当前没有 `runtime/legacy` 或 `runtime/forwarder` 目录；后续不要以兼容名义复活这些旧落点。

### `core`

- 应用侧 facade、initializer、Koin binding、系统能力协调层。
- 通话监听、特殊提醒、启动初始化、安全初始化、更新与备份外观层当前在这里。
- 短期继续承接 `RuntimeGraph` 到 UI/Koin 的桥接，避免 `mobile/ui` 直接触达 runtime 实现包。
- 不再描述为 Compose UI 主体；Compose 页面和 UI ViewModel 主体在 `mobile/ui`。

### `mobile/ui`

- Compose 页面、导航、UI ViewModel、UI-only helper。
- 不直接依赖 `runtime` / `xpbridge/core` / `relay/engine` 实现模块。
- 运行时能力经由 `core` 的 UI-facing facade、`relay/contract` 或 `relay/engine/api` 访问。
- 首页概览页已拆出 `OverviewChartCard`、`OverviewInfoCards` 与 `OverviewCardEditing`，
  `OverviewScreen` 继续只保留页面状态装配、卡片路由和顶层交互编排。
- 设置首页已拆出 `SettingsHomeSections`、`SettingsDisplayCoordinator`、`SettingsBackupCoordinator`、
  `SettingsRuntimeLogCoordinator`、`SettingsHomeDialogs` 与 `SettingsBackupUi`；`SettingsExperienceScreens`
  继续只保留页面状态装配、分组路由和顶层设置写入回调。
- sender 列表页已拆出 `SenderConfigCards`、`SenderTypeDialog`、`SenderGeneralDialogs` 与
  `SenderTemplateDialogs`，顶部配置入口、添加类型、通用/优先级配置和短信/通知/来电模板编辑
  不再挤在 `SenderListScreen` 主编排文件里。
- sender 列表项与删除撤销倒计时 Snackbar 已拆到 `SenderCard` 与 `SenderUndoSnackbar`；
  `SenderListScreen` 继续只保留列表状态、拖拽排序、删除恢复动作和顶层对话框编排。
- 现有 Compose sender 表单均已接入 `SchemaSenderConfigForm`，字段默认值和选项来自
  `SenderSettingSchemas` / `SenderSettingDraft`；表单文件只保留可见字段标签、少量兼容归一化和
  Bark 加密密钥生成等通道专属 UI。
- 新增 API 子模块优先收进所属目录，例如 `relay/engine/api`；避免在项目根目录继续增加多词模块目录。

### 根目录布局

- 自有模块根目录优先使用单词目录，复合语义使用嵌套目录表达，例如 `mobile/ui`、`relay/engine`、`xpbridge/core`。
- 允许保留多词根目录的仅限外部子模块边界，例如 `build-logic`、`magisk-ui-kit`、`smscode-core`、`smscode-rules`。
- Kotlin package 与 Android namespace 不随物理目录重排自动改名，避免目录治理扩大为 API 迁移。

## 边界守护

已落地的守护入口：

```bash
./gradlew verifyModuleBoundaries verifyStructureBoundaries verifyDependencyGovernance
```

当前守护重点：

| 边界 | 当前规则 |
| --- | --- |
| `app` | 必须依赖 `core` 和 `hook/entry`；可依赖 `relay/android` 安装平台 adapter；不得直接依赖 `runtime` |
| `hook/entry` | 不得依赖 `core` |
| `mobile/ui` | 不得依赖 `runtime`、`xpbridge/core`、`relay/engine` 实现模块 |
| `relay/android` | 依赖 `relay/engine/api`，不得依赖 `relay/engine` 实现模块 |
| `relay/sender` | 依赖 `relay/engine/api` 和 `relay/net`，不得依赖 `relay/engine` 实现模块 |
| `xpbridge/core` | 不得依赖 `runtime`、`relay/android`、`relay/engine` 实现模块、`smscode-core/smscode-domain` 或 Compose |
| `runtime` | 不直接依赖 Koin、Compose UI 或 `relay/sender` 实现包；sender 发送能力只通过 `relay/engine/api` service 接口访问 |

仍需补强的边界：

- Web / Desktop 共享前端层尚未形成独立包，当前主要共享 `shared/contracts` 类型。

## 运行时主链

运行时依赖的装配规则：

- `runtime` 内部通过 `RuntimeGraph` 按需懒加载 `AppDatabase`、repository、pipeline 与 formatter。
- `ForwardReceiver`、`CustomMessageReceiver`、调度 worker/receiver 等运行时入口直接消费 `RuntimeGraph`。
- Koin 只在 `core/app` 层复用这套实例，不作为 runtime 运行时的唯一所有者。

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

- 主流程判断只基于 `RelayEvent`。
- `MsgInfo` 仅作为 sender 下发载荷。
- 新系统事件也应优先进入 `RelayEvent -> EventPipeline`。
- sender adapter 不再回填到旧 `forwarder/*` 结构。

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
- 浏览器控制台的 API 方法表统一经由 `shared/consoleApiClient.ts` 维护；Web 侧只实现 fetch / CSRF /
  cookie / timeout transport adapter，Desktop 侧继续由 Tauri runtime 暴露本地 adapter。
- Desktop 远程 API 页面同样使用 `shared/consoleApiClient.ts` 的方法形状；Tauri runtime adapter 负责把共享
  endpoint 映射到本地 command，并需要覆盖 OpenAPI 中已声明的 endpoint。
- 认证会话响应统一经由 `shared/consoleSession.ts` 归一化，避免各端重复解释 Web login / me 响应与
  Desktop bootstrap session 状态。
- 配置快照 clone / normalize / conflict / load / save 纯逻辑统一经由 `shared/configSnapshot.ts` 维护；
  Web / Desktop 的 React hook 只保留状态 ownership 和 runtime adapter 调用。
- sender 结构化配置字段合同来自 `shared/contracts/senderSchemas.json`，由
  `scripts/generate_sender_schema_contract.py` 从 Kotlin `SenderSettingSchemas` 生成；Web / Desktop 只能在该合同上补 UI label、布局和控件类型。
- sender 字段默认值和枚举选项优先写入 `SenderSettingSchemas`，当前覆盖 Telegram / Pushplus / Bark /
  Webhook / Dingtalk / Dingtalk Inner / WeCom Robot / WeCom App / Feishu / Feishu App / Socket 的跨端选项，
  Email、Gotify、Ntfy、SMS 等通道的默认值，并同步生成到 `shared/contracts/senderSchemas.json`；
  Android Compose 表单新增 schema-driven 字段时只补展示标签和运行时 adapter。

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
- 官方规则来自 `smscode-rules` 内容型子模块、远程 raw GitHub 与应用私有缓存；它们只在运行时映射为 `SmsCodeRuleSpec`，不写入用户规则表。
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
3. 内部 `MessageTypeGateSnapshot` 仅供 runtime / diagnostics 使用，不再作为用户设置模型。
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

### 发布策略

- 仅维护 libxposed 新 API 单轨发布（Play / GitHub）。

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

- “消息类型进入主处理管线”只保留为内部运行时概念。
- 不再作为面向用户的设置术语。
- 远程控制台也只暴露用户能力开关，不直接暴露内部 message-type gate。

## 后续开发约束

1. 新运行时规则优先落在 `runtime/domain` 或 `relay/engine` 的纯领域实现，不落在 `app` / `mobile/ui`。
2. 新跨进程入口优先落在 `runtime/platform`。
3. 新设置页优先走 `SettingsRepository` 与 contract snapshot/update 模型。
4. 不再新增 `legacy` / `forwarder` 目录或向旧兼容 facade 增加主编排逻辑。
5. 新 sender 能力落在 `relay/sender`，通过 `relay/engine/api` 的 dispatcher/service 接口暴露给 runtime；不要在 `runtime` 新增 sender-specific 分支。
6. `xpbridge/core` 新增内容优先是 DTO/typealias/facade 签名，实现逻辑放 `runtime` / `relay/android`。
7. `runtime` 不直接依赖 Koin；若 UI 需要 DI，优先复用 `RuntimeGraph` 已构造的实例。
8. 新远程 API 必须同步 `shared/contracts/openapi.json`、Backend test、Android DTO test 和 TS contract test。
9. sender 字段 schema 变更必须重新生成 `shared/contracts/senderSchemas.json`，并保持 Android / Web / Desktop 漂移测试通过。
