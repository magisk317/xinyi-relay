# 设备控制与 Root-First 权限引导落地计划

> 触发：SmsForwarder issue #776（远程关静音/勿扰，竞品 help wanted 挂着）+ 启动权限弹窗体验整改。
> 差距依据：`../competitor-gap/smsforwarder-2026-10.md`。
> 工作基线：分支 `feat/device-control-onboarding`（track origin/beta，基点 f957f681，从远端新代码拉出，不触碰本地其他在途改动）。
> 子模块分支：`magisk-xposed-kit` → `feat/permission-bridge-onboarding`（基点 409cf36，落后 origin/main 1 个提交，需要时再 FF）；`magisk-ui-kit` → `feat/onboarding-components`（基点 7e0d1cb，已含 origin/main）。
> 所有代码行为描述均基于基点实查（2026-10-17）。

## 0. 任务总览与顺序

| 任务 | 内容 | 风险 |
|---|---|---|
| A | PACKAGE_USAGE_STATS 定位修正 + 启动引导项清理 | 低 |
| B | Root-First 分步权限引导（DataBackup 模式） | 中 |
| C | 设备控制指令（issue #776 主体，含 app UI） | 高 |
| D | mipush 引导页同构统一（后置） | 中 |

顺序 A → B → C → D：A 是纯清理；B 依赖 A 的清理结果，直接解决"启动弹一堆权限"；C 是功能主体，复用 B 的授权体验；D 等 B 稳定后横向推广。

## 1. 已确认的决策（含对早期假设的修正）

1. **PACKAGE_USAGE_STATS 不能删**（修正早期"业务零使用可删"的判断）。实查：`mobile/feature/appconfig` 的「按使用时长排序」在用——
   - `AppConfigViewModel.loadUsageStats()` 调 `UsageStatsManager.queryAndAggregateUsageStats`（近 30 天前台时长）供 `SortOption.USAGE` 排序；
   - `UsageStatsPermissionGranter` 已有完整按需授权链：root 场景 `appops set ... GET_USAGE_STATS allow`（主）+ `cmd appops set ...`（兜底），无 root 走系统设置页；
   - `PermissionBridge.commandsFor` 已含 PACKAGE_USAGE_STATS 的 appops 分支，root 用户启动即授。
   - **做法**：manifest 保留声明；仅从启动引导移除该项（见任务 A）。
2. **Root-First 顺序现状已实现**：`StartupPermissionPrompt` 的 LaunchedEffect 先 `PermissionBridge.runRoot(bridgeCommands(...))` 再 `startPromptIfNeeded()`。"弹一堆权限"的痛点在无 root 场景：串行系统页面跳转、无前置说明、无进度感知。整改 = 分步引导替代连续弹窗（任务 B），不是改顺序。
3. **引导模式**：采用 Android-DataBackup 的 `feature/setup` 分步路由模式（独立模块 + ViewModel + 步骤状态机），不采用其 root service AIDL 常驻方案。
4. **降级路径不加 lite 身份**：无 root = 完整功能、仅授权方式变为手动逐项授（铁律）。
5. **app 要加 UI**（主人明确要求）：设备控制除 WebUI 外，app 内新增设备控制页，本地手动执行与远端指令同一执行器。

## 2. 任务 A：启动引导项清理（PACKAGE_USAGE_STATS）

- `app/src/main/AndroidManifest.xml`：**保留** PACKAGE_USAGE_STATS 声明（appconfig 排序在用）。
- `mobile/feature/settings/src/main/kotlin/io/github/magisk317/relay/ui/home/settings/StartupPermissionPrompt.kt`：
  - 删 `StartupSpecialPermission.USAGE_ACCESS` 枚举项（L144）；
  - 删 `isApplicable` 中 USAGE_ACCESS 分支（L182-183）；
  - 删 `isGranted` 中 USAGE_ACCESS 分支（L204）；
  - 删 `buildIntent` 中 USAGE_ACCESS 分支（L224-225）；
  - 删私有函数 `Context.hasUsageAccess()`。
- 字符串资源删 `startup_permission_usage_access`（`values/` + `values-zh-rCN/`）。
- `PermissionBridge` 的 PACKAGE_USAGE_STATS 分支**不动**（共享子模块，root 一键授权仍走它）。
- appconfig 按需授权链路**不动**。
- 效果：启动少一项弹窗；按使用时长排序首次使用时才请求，root 用户无感。

## 3. 任务 B：Root-First 分步权限引导

新增 `mobile/feature/setup` 模块（参考 DataBackup feature/setup：独立模块 + `SetupViewModel` + 步骤状态机，Compose 实现）：

- **S0 欢迎**：功能一句话 + 隐私说明（链 `docs/PRIVACY.md`）。
- **S1 Root 探测**：`RootShellExecutor.canUseRoot()`（`modules/runtime` domain/recovery）。有 root → S2；无 root → 展示降级说明卡（Xposed 模式功能完整，权限需手动逐项授），仍进 S3，不阻断。
- **S2 一键授权**：`PermissionBridge.bridgeCommands(...)` + `runRoot`，逐项回显结果（复用现有 XLog 回调通道展示）。此步即现状 LaunchedEffect 里的逻辑，平移进引导流。
- **S3 残留项引导**：runtime 权限一组请求（POST_NOTIFICATIONS/READ_CONTACTS/SEND_SMS/READ_PHONE_STATE）；特殊权限逐项卡片引导（EXACT_ALARM / INSTALL_UNKNOWN_APPS / NOTIFICATION_LISTENER / ACCESSIBILITY_SERVICE / BATTERY_OPTIMIZATION；USAGE_ACCESS 已按任务 A 移除）。`collectMissingStartup*` / `buildIntent` 逻辑从 settings 模块搬入或抽公共。
- **S4 完成**：写 prompted 标记（现有 prefs `startup_permission_prompt` / `prompted_version_code`），进入主页。

入口改造：`mobile/ui` `MainActivity`（现 `StartupPermissionPrompt` 挂载点 L440）改为「setup 未完成 → 显示 SetupScreen；已完成 → 原逻辑」。注意：MainActivity 有他人未提交在途改动，本分支基点为其推送前的版本，对方推送后 rebase 解冲突。

共享组件（步骤指示器、权限行卡片）：如任务 D 需要 mipush 复用，抽到 `magisk-ui-kit`（feat/onboarding-components 分支），遵守共享子模块铁律（先改子模块并验证两个父仓，再分别 bump gitlink，父指针默认留本地）。

## 4. 任务 C：设备控制指令（issue #776 主体）

### 4.1 指令模型：复用传输层，分流状态机

现有下行通道是 config mutation 流：`AgentConfigCommandsPullResponse.pendingCommands` → `RemoteAgentRepository.pullPendingCommands()`（L275）→ `applyPulledCommand`（revision 守卫 + mutation 应用 + ACK）。设备动作是一次性指令、无 revision 语义，**不塞进 mutation 流**：

- `modules/relay/contract/.../remote/AgentApiContracts.kt` 新增：
  - `DeviceActionCommandResponse{ id, action, params: JsonObject, status, failureReason, actorType, actorId, createdAt }`；
  - `AgentConfigCommandsPullResponse` 增加 `pendingActions: List<DeviceActionCommandResponse> = emptyList()`（随周期 pull 下发，零新增轮询，kotlinx 默认值向后兼容）；
  - `DeviceActionAckRequest{ actionId, status, failureReason }`。
- `OpenApiRouteContracts.kt` 注册新路由（参照现有 `queueDeviceConfigCommand` L157-160 模式）；`AgentApiContractsOpenApiTest` / `OpenApiRouteContractsTest` 补用例。
- **action 白名单（首版两项，正面对应 776）**：
  - `SET_RINGER_MODE`：params `{ "mode": "normal|silent|vibrate" }`；
  - `SET_INTERRUPTION_FILTER`：params `{ "filter": "all|priority|none|alarms" }`。
  - 白名单 client + server 双侧校验；未知 action / 非法参数 → ACK failed + `failureReason=unsupported_action|invalid_params`。

### 4.2 服务端（backend/api）

- `internal/store/device_config.go` 同层新增 `device_actions` 表（id, device_id, action, params, status, failure_reason, actor_type, actor_id, created_at, updated_at, applied_at）+ `internal/database/migrate.go` 迁移链追加。
- `internal/http/handlers_device_config.go` 同层新增：console 侧排队路由（deviceToken / console auth 复用 `middleware.go`）、pull 响应合并 pendingActions、ACK 路由；`ratelimit.go` 覆盖新路由。
- 测试：`handlers_device_config_test.go` + store 集成测试补用例。

### 4.3 客户端执行（modules/runtime）

- 新增 `DeviceActionExecutor`（domain/action/）：白名单校验 → 执行 → ACK，OTel 埋点参照 `emitAgent` 模式。
- 降级链：
  - `SET_RINGER_MODE`：`AudioManager.setRingerMode`（无需特殊权限，API 优先）；失败 → root 命令（候选 `cmd audio` / `service call audio`，**命令串实现期真机实测**，ROM 差异大，集中一处便于维护）。
  - `SET_INTERRUPTION_FILTER`：`NotificationManager.setInterruptionFilter`（需 `ACCESS_NOTIFICATION_POLICY`）；manifest 补声明 `ACCESS_NOTIFICATION_POLICY` + `MODIFY_AUDIO_SETTINGS`；先试 `PermissionBridge` root 授权（命令串真机实测，无可靠命令则不假装成功）；授权墙破不了 → 引导系统页 `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`，ACK failed + 明确 reason。
- 挂点：`RemoteAgentRepository.scheduleBackgroundSync`（pullPendingCommands 之后，L649 附近）拉取执行，复用 `backgroundSyncInFlight` 合并逻辑。
- 安全面：白名单收敛 + 参数校验 + 必须已绑定设备（`snapshot.bound`）+ 服务端 ratelimit + actor 审计字段。首版只放这两个低影响面动作，不开放任意 shell。

### 4.4 App UI（mobile）

- `mobile/ui` `NavGraph.kt` 新增 `DeviceControlRoute` + 设备控制页（放 `mobile/feature/settings` 或独立 feature 模块）：静音/响铃/振动/勿扰开关；当前状态展示（`AudioManager.ringerMode` / `NotificationManager.currentInterruptionFilter`）；最近一次执行结果；root 授权状态。
- 本地手动执行与远端指令走同一 `DeviceActionExecutor`（本地不过服务端）。

### 4.5 WebUI / Desktop

- WebUI：`frontend/webui/src` 新增设备控制卡片（参照 `deviceConfig.tsx` 模式，挂 `pages/OverviewPage.tsx` 或新页），i18n 走 `i18n.tsx`，测试进 `src/__tests__`。
- Desktop 后置可选：`SyncEngine.queueDeviceConfigCommand` 同层加 `queueDeviceAction`。

### 4.6 测试与真机矩阵

- 单测：executor 白名单/参数/降级链（fake shell runner 参照 `UsageStatsPermissionGranterTest`）；contract 与后端路由测试；WebUI vitest。
- 真机矩阵（命令串实测表，实现期回填本文档）：有 root × 无 root，至少各覆盖一个主流 ROM；逐项记录 appops / cmd notification / deviceidle / ringer 命令的实际行为。

## 5. 任务 D：mipush 引导页统一（后置）

- 前置：任务 B 在 xinyi 迭代稳定。
- `MiPushFramework` 侧现状：权限逻辑散在 `manager/ui/.../RequestPermissionViewModel.kt`、`xmsf/shell/.../app/di/ManagerPermissionAdapter.kt`、`platform/support/PermissionUtils.kt`，无独立引导 UI；该仓已内置 `magisk-xposed-kit` 子模块，`PermissionBridge` 直接复用。
- 改造为与 xinyi 同构的分步引导；公共 UI 组件走 `magisk-ui-kit`（feat/onboarding-components），按铁律验证两父仓后分别 bump gitlink。

## 6. 风险与对策

1. **root 命令 ROM 差异**（appops / cmd notification / deviceidle / ringer 各 ROM 行为不一）：命令串集中单点维护 + 真机矩阵实测回填。
2. **DND 授权墙**：`ACCESS_NOTIFICATION_POLICY` 可能无可靠 root 授权命令 → 降级系统设置页引导，功能不假装成功（ACK failed + 明确 reason）。
3. **远程控制安全面**：白名单 + ratelimit + 审计 + 仅限已绑定设备；首版仅两个低影响动作。
4. **MainActivity 他人在途改动**：本分支从 origin/beta 拉出，对方推送后 rebase，冲突手工解。
5. **共享子模块改动**：遵守铁律——子模块先改并验证 xinyi + mipush 两个父仓，再 bump 父仓 gitlink，父指针默认留本地、明确要求才推。

## 7. 合并前验证清单

- [ ] Gradle 全量编译通过（新增模块 + 子模块 bump 后，`--no-build-cache` 至少一轮）
- [ ] 单测：contract / runtime executor / backend handlers+store / WebUI 全绿
- [ ] detekt 无新增告警
- [ ] 真机走查：引导 S0-S4；两个 action 的 root / 无 root 路径；WebUI 下发 → 执行 → ACK 闭环
- [ ] 文档同步：`docs/ARCHITECTURE.md` 指令通道章节补 device_actions；`docs/PRIVACY.md` 如涉及
- [ ] 提交信息英文 conventional、纯 ASCII、无 Co-Authored-By
