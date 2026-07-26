# Xinyi Relay 单 APK 自适应工作模式计划

> 最近更新：2026-07-17  
> 版本：v3.1  
> 状态：P0/P1 代码与自动化验收已完成；仅保留真实设备/运营商回归  
> 依据：当前 `xinyi-relay` 代码与提交 `5bc2fcc3`、`24a756b4`、`298f1160`、`9ba7c801`

---

## 〇、结论：不需要独立 `lite` 变体

Enhanced 与 Standard 是**同一个 APK 的运行时能力状态**，不是两套产品：

```text
应用/进程启动
    └─ WorkModeResolver.resolve()
         ├─ libxposed runtime 已连接或本次开机仍有 Hook 心跳 → Enhanced
         └─ 未安装、未激活且没有有效 runtime/Hook 心跳 → Standard
```

- 不新增 `full` / `lite` flavor，不增加 `.lite` applicationId，也不维护第二套安装、升级、数据迁移和 CI 路径。
- `WorkMode.Inactive` 只保留给显式的全局停用语义；“缺少 Xposed”不再等于 Inactive。
- Xposed 服务 `bind` / `died` 回调必须立即重新解析模式，并同步协调 `StandardModeService` 与 `CallStateMonitor`。
- `PhoneProcessRestartCoordinator` 只允许在**成功的 Xposed 服务绑定回调**之后请求。普通启动、开机广播和 Standard 模式不得探测 root 或重启电话/MMS 进程。
- Standard 模式本身不以“一次性授予全部权限”为前提。短信、彩信、通话、通知、Root 和 Xposed 能力分别检查自己的权限或能力；缺少一种能力只降级对应功能。

这同时解决了独立 lite 方案的主要问题：用户不需要在 Xposed 安装/失效时切换 APK，同一安装实例可以在 Enhanced 与 Standard 之间恢复，配置和数据库也无需迁移。

---

## 一、发行 flavor 与工作模式正交

现有 flavor 继续表达**发行与依赖策略**，不表达运行模式。所有发行包都使用同一套自适应模式解析。

| flavor | 发行策略 | 无 Xposed 时的 Standard 能力 |
|---|---|---|
| `play` | Google Play 合规策略 | **通知监听为主的 Standard 子集**；manifest 明确移除短信、彩信、电话/通话记录权限及 SMS/MMS receiver，也移除自动输入 AccessibilityService |
| `githubNoE2ee` | GitHub 发行、不打包 Matrix E2EE | 权限允许时可用短信、彩信、通话、通知等完整 Standard 能力 |
| `githubWithE2ee` | GitHub 发行、打包 Matrix E2EE | 同上，另启用 E2EE 发行依赖 |
| `fdroid` | F-Droid 发行策略 | 按合并后的 manifest 与用户实际授权启用对应 Standard 能力 |

Play 包即使没有电话权限仍是有效的 Standard 模式，不能因为 `StandardModePermissions` 为空或未满足电话权限而进入 Inactive。`StandardModePermissions.requiredPermissions()` 只返回当前发行 manifest 实际声明的权限。

---

## 二、运行时能力模型

### 2.1 模式切换与生命周期

| 触发点 | 当前实现 | 期望结果 | 状态 |
|---|---|---|---|
| App 基础设施初始化 | `InfrastructureInitializer` 解析模式并 `StandardModeService.reconcile(..., "app_init")` | 无 Xposed 时立即进入 Standard 并启动前台服务 | ✅ |
| 进程回到前台 | `ProcessLifecycleOwner` 重新解析并 reconcile | 修复激活状态变化和被杀后的服务状态 | ✅ |
| 开机完成 | `BootCompletedReceiver` 解析模式、检查能力并 reconcile | Standard 按渠道能力恢复 | ✅ |
| Xposed 服务绑定 | `XposedServiceRuntimeCoordinator.handleServiceBound()` 后立即 reconcile | 切到 Enhanced、停止 Standard 服务、刷新通话监听 | ✅ |
| Xposed 服务死亡 | `handleServiceDied()` 后立即重新解析并 reconcile | 无其他有效 runtime/Hook 心跳时回落 Standard；否则保持 Enhanced | ✅ |
| 电话进程重启 | 仅 `onServiceBind` 在 reconcile 之后调用 | 只做真正的 Xposed 安装/更新 bootstrap | ✅ |

对应契约由 `XposedServiceBridgeContractTest` 约束：启动路径不得调用电话进程重启，bind 顺序必须是“记录绑定 → reconcile → 请求重启”，died 路径必须 reconcile 且不得请求重启。

### 2.2 每项能力独立判定

| 能力 | Standard 条件 | Enhanced/Root 条件 | Play Standard |
|---|---|---|---|
| 通知转发 | 用户启用 NotificationListener | 不依赖 Xposed | ✅ 主能力 |
| 短信广播接收 | manifest 声明并授予 `RECEIVE_SMS` | Hook 拦截另由 Enhanced gate 控制 | ❌ manifest 移除 |
| 彩信广播接收 | manifest 声明并授予 `RECEIVE_MMS` | Hook/Shizuku 路径分别判定 | ❌ manifest 移除 |
| 通话状态 | `READ_PHONE_STATE` 且相关开关启用 | Hook 通话拦截仅 Enhanced | ❌ manifest 移除 |
| 通话结束记录补全 | `READ_CALL_LOG` | 不依赖 Xposed | ❌ manifest 移除 |
| 标准模式前台服务 | WorkMode 为 Standard | Enhanced 时停止 | ✅ |
| 电池优化引导 | Standard 且发行包声明相应能力 | Play 不请求该权限 | 仅提示策略允许的能力 |
| Xposed 短信拦截/拦截删除/Hook 保活 | 不可用 | `WorkMode.Enhanced` | 取决于运行环境 |
| Root DB catchup / force-stop recovery | 独立检查 root | 不把 Root 等同于 Xposed | 取决于 root |

`StandardModeFeatureGate` 只负责 Xposed-only、Root-only 和 Inactive 边界；它不应把整个 Standard 模式绑定到一组全有或全无的权限。

---

## 三、P0/P1 完成度（按当前代码校准）

### P0：标准模式可持续运行

| # | 原问题 | 当前证据 | 状态 |
|---|---|---|---|
| P0-1 | `StandardModeService` 未接入启动入口 | app init、process resume、boot、service bind/died 均走 `reconcile()`；Standard 启动、Enhanced/Inactive 停止 | ✅ 已实现 |
| P0-2 | 电池优化 helper 未集成 | Service 记录豁免状态；Overview 与启动权限提示可检查/跳转；Play manifest 不请求该权限 | ✅ 已实现 |
| P0-3 | 缺 4 个 Hook 保活 gate | OOM adj、anti-kill、standby bypass、doze bypass 均在 Xposed-only 集合 | ✅ 已实现 |
| P0-4 | 保活 UI 在 Standard 下仍可操作 | `ForwardKeepAliveScreen` 统一灰化四个 Hook 开关并显示 Xposed 依赖提示 | ✅ 已实现 |
| P0-5 | 无 Xposed 被误判为不可用 | `WorkModeResolver` 只在 Enhanced/Standard 间按激活状态解析；Inactive 不再是缺 Xposed 的结果 | ✅ 已实现 |
| P0-6 | 模式变化协调不及时 | libxposed bind/died 回调同步更新激活诊断并立即 reconcile | ✅ 已实现 |
| P0-7 | 普通启动可能误重启电话进程 | 重启请求收敛到成功 bind 路径，并有源码契约测试 | ✅ 已实现 |
| P0-8 | `dataSync` FGS 在 Android 15+ 有累计时限且不能由开机广播启动 | `StandardModeService` 改为语义匹配的 `remoteMessaging`，三发行 merged manifest 均保留对应权限/类型并移除 `dataSync` | ✅ 已实现 |

### P1：标准能力与 UI 降级补全

| # | 原问题 | 当前证据 | 状态 |
|---|---|---|---|
| P1-1 | 通话结束后缺少号码/方向补全 | `CallLogQueryHelper` 按会话窗口/方向选最佳记录；仅在直接号码和 recent ingress 都缺失且有权限时，以 0/250/500/1000/2000ms 有界、可取消退避等待 provider 落库，最终只发送一次 | ✅ 已实现 |
| P1-2 | 缺少 Standard MMS 接收 | `StandardMmsReceiver` 注册 WAP push；APK 自带有界 `Notification.ind` metadata parser，不依赖隐藏系统类；稳定 eventId、SIM 路由、诊断降级与去重后进入统一管线 | ✅ 已实现 |
| P1-3 | Overview 限制说明不完整 | Standard 卡片展示电池提示及 Xposed-only 功能限制 | ✅ 已实现 |
| P1-4 | `blockSmsEnabled` 未标注 Xposed 依赖 | `VerificationSettingsScreen` 通过 feature gate 灰化并提示 | ✅ 已实现 |
| P1-5 | Root DB / force-stop recovery 未独立判 Root | `ForwardKeepAliveScreen` 对 catchup、writeback、recovery、relaunch 分别检查 root 并禁用无效操作 | ✅ 已实现 |
| P1-6 | 发行权限被当成全局模式开关 | 权限按当前 manifest 求交；Play 无电话权限时仍保留通知 Standard 子集 | ✅ 已实现 |
| P1-7 | 免打扰时间段缺失 | `ForwardSilentPeriodEvaluator`、设置 UI、持久化和发送管线检查已经落地 | ✅ 已实现 |

主要落地提交：

- `5ae23221 feat(standard): complete fallback ingress coverage`：服务、电池、UI/Root gates、CallLog、MMS、manifest 和测试。
- `5bc2fcc3 fix(runtime): make standard mode adaptive without xposed`：单 APK 模式解析、按发行 manifest 请求权限、bind/died 即时 reconcile 与电话进程重启边界。
- `e552015c feat(sender): add forward silent period`：免打扰设置与发送管线。
- `24a756b4 fix(runtime): use remote messaging foreground service`：修复 Android 15+ 开机启动与累计时限冲突，补发行 manifest 契约。
- `298f1160 fix(call): retry delayed call log resolution`：补 CallLog provider 延迟落库的有限退避、取消与单次派发契约。
- `9ba7c801 fix(mms): parse notification PDUs in standard mode`：内置安全的 MMS Notification.ind metadata parser，移除对未打包隐藏类的反射。

---

## 四、验收结果与剩余设备回归

代码、单元/契约测试、结构闸门、GitHub APK 和 Play merged manifest 均已验证。剩余工作只是真实设备/运营商运行时回归，而不是新增 lite flavor。

### 4.1 定向测试与结构检查

```bash
cd /path/to/xinyi-relay

./gradlew \
  :policy:testGithubWithE2eeDebugUnitTest \
  :core:testGithubWithE2eeDebugUnitTest \
  :runtime:testGithubWithE2eeDebugUnitTest \
  :app:testGithubWithE2eeDebugUnitTest \
  -PskipGoogleServices=true \
  -PallowIncompatibleDebugSigning=true

./gradlew \
  verifyModuleBoundaries \
  verifyStructureBoundaries \
  verifyDependencyGovernance
```

2026-07-17 最终自动化证据：

- core/runtime 强制重跑测试与 detekt：`255 actionable tasks`，runtime `215 passing`。
- P0/P1 汇总命令：`1361 actionable tasks`，policy `8 passing`、core Play `42 passing`、app 契约 `13 passing`，三项架构闸门通过。
- `smscode-core:verification` detekt/test 通过；GitHub WithE2EE Debug APK 构建成功。
- 清理旧 lite 构建产物后重新生成 `universal_githubWithE2ee_xinyi-relay_v0.2.1-beta-20260717_150857_debug.apk`，SHA-256 为 `da97ae6869e56735d1416ac617c785cf08a569596312676b6d3df614fddacaac`。
- `apkanalyzer` 确认 APK 定义 `MmsNotificationPduParser`，未打包/依赖 `com.google.android.mms`；manifest 使用 `FOREGROUND_SERVICE_REMOTE_MESSAGING`。
- Play fresh merged manifest 保留 `StandardModeService` + `remoteMessaging`，且不含 SMS/MMS/电话权限、receiver 或自动输入 AccessibilityService。

重点测试证据：

- `WorkModeResolverPropertyTest`
- `StandardModePermissionsTest`
- `StandardModeFeatureGateTest`
- `XposedServiceBridgeContractTest`
- `RelayManifestContractTest`
- `CallLogQueryHelperTest`
- `CallEndResolutionPolicyTest`
- `StandardMessageIngressHandlerTest`
- `ForwardPayloadFactoryTest`
- `MmsNotificationPduParserTest`
- `ForwardSilentPeriodEvaluatorTest`

### 4.2 构建与 APK 合并清单

```bash
cd /path/to/xinyi-relay

./gradlew :app:assembleGithubWithE2eeDebug \
  -PskipGoogleServices=true \
  -PallowIncompatibleDebugSigning=true \
  -PallowConflictBypass=true

# Play 合规校验应使用 merged manifest / 最终 APK：
# - 不含 RECEIVE_SMS / RECEIVE_MMS / READ_SMS / READ_PHONE_STATE / READ_CALL_LOG
# - 不含 StandardSmsReceiver / StandardMmsReceiver / AutoInputAccessibilityService
# - 仍含 NotificationListenerService 和 StandardModeService
# - StandardModeService 类型为 remoteMessaging；不含 dataSync FGS 权限/类型
```

### 4.3 设备回归矩阵

| 场景 | 验收点 |
|---|---|
| 无 Xposed / 未激活 | 同一 APK 自动为 Standard；服务常驻通知出现；拒绝某一权限只禁用对应能力 |
| Standard + GitHub flavor | 分别点验通知、SMS、真实运营商 MMS Notification.ind、来电/通话结束、去重、转发和电池提示 |
| Android 15+ 无 Xposed 开机 | `BOOT_COMPLETED` 可启动 `remoteMessaging` FGS，不出现 dataSync 启动异常或 6h timeout |
| CallLog 延迟写入 | 通话结束后 3.75s 总预算内出现的记录可补全号码/方向；新来电/模式切换会取消旧等待且不重复派发 |
| Xposed 服务成功绑定 | 不重装 APK 即切 Enhanced；Standard 服务停止；Hook 能力可用；电话进程重启请求只在该时点发生 |
| Xposed 服务死亡/框架停用 | 立即重新解析；无有效 runtime/Hook 心跳时不重装 APK 即回落 Standard，服务和通话监听同步 reconcile |
| Play flavor | 不出现电话权限申请；Standard 仍可通过通知监听转发；合规移除项不出现在最终 manifest |
| Root 有/无 | Root DB/recovery 开关分别可用或灰化，不受 Standard/Enhanced 名称误导 |

---

## 五、明确排除

- 不实现 `full` / `lite` mode flavor、第二 applicationId 或第二套安装包。
- 不把现有 `play` / `githubNoE2ee` / `githubWithE2ee` / `fdroid` 发行 flavor 改造成工作模式。
- 本轮**排除全部 Android 17 专项**，包括本地网络新权限适配和 API 37/Android 17 设备矩阵；不得把这些项目混入 P0/P1 完成度。
- WorkManager 全量发送管线、SIM 热插拔、网络/蓝牙/定位等扩展仍是独立 P2 候选，不影响本轮单 APK 自适应模式验收。

---

## 六、成功标准

1. 只有一个应用身份；同一 APK 可随 Xposed 服务状态在 Enhanced/Standard 间切换。
2. 无 Xposed 时默认 Standard，而不是 Inactive；不要求先授予全部电话权限。
3. 每项 Standard 功能只由自己的 manifest、用户授权和运行时能力决定。
4. bind/died 后立即协调模式、前台服务和通话监听；电话进程重启仅发生在成功 bind 路径。
5. GitHub 发行具备完整 Standard 电话能力；Play 发行保持通知为主的合规 Standard 子集。
6. P0/P1 定向测试、结构检查和构建/manifest 检查已有可复现证据；设备回归按上表补充真实系统/运营商证据。
