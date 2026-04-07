# Relay 架构重整说明

本文档描述当前代码库在远程架构正式落地后的推荐分层，以及后续开发应遵循的落点规则。

## 当前分层

### `runtime`
- `bootstrap`
  - 运行时装配入口
  - 代表对象：`RuntimeGraph`
- `domain`
  - 运行时主模型与主管线
  - 事件类型：`RelayEvent`
  - 管线组件：`EventGatekeeper`、`SenderSelector`、`RoutingResolver`、`DispatchExecutor`、`DispatchResultWriter`
- `data`
  - Room / Provider / repository
  - 文件存储与备份导入导出
  - 代表对象：`RelayRecordRepository`、`SettingsRepository`
- `platform`
  - Android IPC / BroadcastReceiver 适配层
  - sender adapter 与系统交互适配
  - 定时提醒调度入口
  - 代表对象：`ForwardReceiver`
- `legacy`
  - 兼容桥接层
  - 代表对象：`SendUtils`、`LegacyRelayFacade`
- `forwarder`
  - 不再承载新的运行时代码
  - 历史 sender adapter 已迁到 `runtime/platform/sender`
  - 若后续仍需保留兼容桥，仅允许放 legacy 过渡代码

### `app`
- Android 应用壳
- Xposed hook 与系统事件采集
- 尽量只保留入口、生命周期与装配代码
- 不直接承载转发主编排
- 不再承载嵌入式 WebUI 服务启动、TLS 或静态资源打包链路

### `webui-core`
- 历史嵌入式 WebUI 代码残留
- 不再参与 Android 主运行链
- 仅作为迁移期参考与后续清理对象

### `xpbridge-core`
- Xposed/runtime 之间的桥接 DTO 与 facade
- 仅承载 `io.github.magisk317.relay.xpbridge.*`
- 不承载应用 UI 页面或应用生命周期装配

### `core`
- Compose UI、页面导航、ViewModel、系统能力外观层
- 通话监听与电量提醒等应用内协调逻辑
- 设置页优先通过 repository 读写配置
- `ComposeSettingsScreen` 仅保留为兼容壳；主路径使用新的设置体验页
- 不再内嵌 `webui/*` 与 `xpbridge/*` 包实现

## 运行时主链

运行时依赖的装配规则：

- `runtime` 内部通过 `RuntimeGraph` 按需懒加载 `AppDatabase`、repository、pipeline 与 formatter
- `ForwardReceiver`、`SendUtils` 等运行时入口直接消费 `RuntimeGraph`
- Koin 只在 `core/app` 层复用这套实例，不再作为 `runtime` 运行时的唯一所有者

统一处理顺序如下：

1. 来源组件采集原始事件
2. 标准化为 `RelayEvent`
3. `EventGatekeeper` 做模块总开关、类型级开关、来源级 gate
4. 记录判定与记录写入
5. 特殊提醒副作用
6. sender 选择与路由
7. `DispatchExecutor` 下发
8. `DispatchResultWriter` 写回结果与统计

约束：
- 主流程判断只基于 `RelayEvent`
- `MsgInfo` 仅作为 sender 下发载荷
- 新系统事件也应优先进入 `RelayEvent -> EventPipeline`

运行时组件落点补充：
- 过滤与路由：`runtime/domain/filter`、`runtime/domain/routing`
- Root DB 补偿：`runtime/domain/recovery`
- 设备名解析：`runtime/domain/system/DeviceIdentityUtils`
- 来源元数据解析：`runtime/platform/metadata/SourceMetadataResolver`
- 定时提醒调度：`runtime/platform/reminder/LowBatteryReminderScheduler`

## 配置访问规则

### 应用内 UI
- 优先使用 repository
- 不直接拼 pref key
- 不直接依赖 Provider URI

### Web / Desktop 控制台
- 统一通过 Backend API 访问配置、记录与设备状态
- 不直接访问 Android 端 repository、Provider 或 DataStore

### Xposed / 跨进程运行时
- 继续使用 `PrefsReader`
- 读取链路固定为：
  - `remote_libxposed -> provider -> shared_prefs -> default`
- `PrefsReader` 负责 source-chain 解析，不扩展为 UI 通用配置 facade
- 运行时工具箱（如 `SmsBlacklistUtils`）仅限 runtime/Xposed 使用
- 应用进程内 runtime 热路径优先复用 `RuntimeSettingsCache`，避免分散的 `runBlocking + PreferenceDataSource`

### 兼容期允许保留
- `AppPreferencesDataStore`
- `PrefsProvider`
- `DBProvider`

但它们不应再作为新页面或新业务逻辑的首选入口。

## 配置访问矩阵

本文档中的矩阵用于约束主要配置项的唯一写入口、Native UI 读取入口与 runtime 读取入口，避免再次出现多套事实来源。

### 访问原则

- Native UI 优先走 repository
- 运行时 / Xposed / 跨进程读取优先走 `PrefsReader`
- `AppPreferencesDataStore` 仅作为 repository 与应用启动初始化层的底层实现
- `PrefsProvider` 仅作为跨进程读取桥

### 主要配置域

| 配置域 | 唯一写入口 | UI 读取入口 | runtime 读取入口 | Provider fallback |
| --- | --- | --- | --- | --- |
| 模块总开关、显示模式 | `SettingsRepository.get/updateGeneralSettings()` | Native 设置页 | `PrefsReader.isEnabled()` | 允许 |
| 验证码功能 | `SettingsRepository.get/updateVerificationSettings()` | Native 设置页 | `PrefsReader.verificationFeaturesEnabled()` 及相关验证码 getter | 允许 |
| 转发功能 | `SettingsRepository.get/updateRelaySettings()` | Native 设置页 | `PrefsReader.relayFeaturesEnabled()`、消息类型 getter | 允许 |
| 特殊提醒 | `SettingsRepository.get/updateSpecialAlertSettings()` | Native 高级页 | `PrefsReader.lowBatteryReminderEnabled()` 等 | 允许 |
| 记录设置 | `SettingsRepository.get/updateRecordSettings()` | 记录页设置面板 | `PrefsReader.recordCodeSmsEnabled()` 等 | 允许 |
| 高级诊断 | `SettingsRepository.get/updateDiagnosticsSettings()` | Native 高级页 | `PrefsReader.analyticsEnabled()` 等 | 允许 |
| IPC token | `SecurityInitializer` | 不直接暴露 | `PrefsReader.getIpcToken()` | 必需 |

### 仍处于兼容期的直接访问

以下位置仍可直接访问底层配置，但不应继续扩散：

- `SmsCodeApplication`
  - Koin 启动与 initializer 调度
- `RuntimeGraph`
  - 应用进程内运行时单例装配中心
- `PrefsReader`
  - Xposed/runtime 跨进程读取
- `RuntimeSettingsCache`
  - 应用进程 runtime 热路径只读缓存
  - 仅缓存 `PreferenceDataSource` / `SettingsRepository` 的运行时快照，不提供新的业务语义入口
- `SmsBlacklistUtils`
  - runtime-only 工具箱
  - 仅限 runtime/Xposed 使用，不作为 Native UI / repository API
- `PrefsSourceChain`
  - 仅负责运行时 source-chain 解析逻辑
- `PrefsProvider`
  - 仅供 `PrefsReader` provider fallback 使用

### 运行时主链与配置落点

- 主管线：`runtime/domain/pipeline`（`EventPipeline`）
- 过滤 / 路由 / 恢复：`runtime/domain/filter`、`runtime/domain/routing`、`runtime/domain/recovery`
- 设备与环境：`runtime/domain/system`、`runtime/platform/metadata`
- sender 领域：`runtime/domain/sender`
- DB DAO / converter：`runtime/data/db/dao`、`runtime/data/db/ext`
- 备份与导入导出：`runtime/data/backup`
- 文件存储：`runtime/data/store`
- sender adapter：`runtime/platform/sender`
- 定时提醒调度：`runtime/platform/reminder`

### 配置治理后续约束

1. Native 剩余直接依赖 `AppPreferencesDataStore` 的页面继续迁到 repository
2. Native 设置入口继续统一通过 repository 返回配置快照
3. 内部 `MessageTypeGateSnapshot` 仅供 runtime / diagnostics 使用，不再作为用户设置模型
4. 新配置项默认先在本文件登记，再落地实现

## Xposed / Runtime 接入

### 入口模型

- 主入口：`io.github.magisk317.relay.xp.LibXposedEntry`
- `META-INF/xposed/java_init.list` 指向主入口，由 libxposed 框架实例化
- 业务 hook 调度统一通过 `LibXposedEntry`

### Hook 兼容层

- 统一兼容层包：`io.github.magisk317.relay.xp.compat`
- 主要组件：
  - `XposedBridge`
  - `XposedHelpers`
  - `XC_MethodHook` / `XC_MethodReplacement`
  - `XC_LoadPackage.LoadPackageParam`

### 配置读取链路

`PrefsReader` 是 Xposed/runtime 场景的唯一首选入口，读取优先级固定为：

1. `remote_libxposed`
2. `PrefsProvider`
3. `shared_prefs`
4. `default`

说明：
- UI 不应依赖这条链路作为主配置 API
- 应用内设置页优先走 repository
- `PrefsProvider` 仅作为跨进程 fallback，不承载业务语义
- `PrefsReader` 的职责是 runtime source-chain resolver，不是全项目通用配置 API

### 运行时事件入口

- 短信、应用通知、来电最终都应收敛为 `RelayEvent`
- 跨进程转发入口：`io.github.magisk317.relay.platform.ipc.ForwardReceiver`
- 主管线：`EventPipeline`
- 运行时依赖来源：`RuntimeGraph`
- 过滤 / 路由 / 恢复：`runtime/domain/filter`、`runtime/domain/routing`、`runtime/domain/recovery`
- 设备与环境：`runtime/domain/system`、`runtime/platform/metadata`
- sender 领域：`runtime/domain/sender`
- DB DAO / converter：`runtime/data/db/dao`、`runtime/data/db/ext`
- 备份与导入导出：`runtime/data/backup`
- 文件存储：`runtime/data/store`
- sender adapter：`runtime/platform/sender`
- 定时提醒调度：`runtime/platform/reminder`

约束：
- Xposed/runtime 负责采集与标准化
- 不在 hook 层直接实现 sender 选择、路由、结果落库
- 不在 `runtime` 的运行时入口直接依赖 Koin API
- sender adapter 不再回填到 `forwarder/*`
- 应用进程内 runtime 热路径可通过 `RuntimeSettingsCache` 做短 TTL 只读缓存，但底层事实来源仍是 `PreferenceDataSource` / repository

### 发布策略

- 仅维护 libxposed 新 API 单轨发布（Play / GitHub）

## 设置结构

### 设置
- 通用
- 验证码功能
- 转发功能入口

### 高级
- 转发配置
- 特殊提醒
- 拦截与过滤
- Remote Agent / Backend
- 实验性与诊断

说明：
- “消息类型进入主处理管线”只保留为内部运行时概念
- 不再作为面向用户的设置术语
- 远程控制台也只暴露用户能力开关，不直接暴露内部 message-type gate

## 后续开发约束

1. 新运行时规则优先落在 `runtime/domain`
2. 新跨进程入口优先落在 `runtime/platform`
3. 新设置页优先走 `SettingsRepository`
4. 不再向 `legacy/SendUtils` 增加编排逻辑
5. sender adapter 与系统发送适配统一落到 `runtime/platform/sender`
6. `runtime` 不直接依赖 Koin；若 UI 需要 DI，优先复用 `RuntimeGraph` 已构造的实例
