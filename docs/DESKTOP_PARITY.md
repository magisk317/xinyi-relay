# 桌面端 Parity 清单（React webUI → KMP Compose Desktop）

最后更新：2026-10-03

## 1. 为什么有这份清单

桌面端有两套实现并存：

| 轨 | 位置 | 技术栈 | 状态 |
|---|---|---|---|
| 现役轨 | `frontend/desktop/` | Tauri 2（Rust + React + SQLite） | 0.2.x，随 tag `v*.*.*` 出六平台产物 |
| 新轨 | `desktop/`、`modules/desktop/core`、`modules/desktop/data` | Kotlin Multiplatform + Compose Multiplatform | 控制台页面移植完成，尚未打包发布 |

新轨的目标是**逐页复刻 `frontend/webui/` 这个 React 控制台**，最终替换 Tauri 端。本清单记录：

1. webUI 每个页面在新轨上的落点与完成状态；
2. 移植时有意保留的差异和原因；
3. 现役 Tauri 端独有、新轨还不具备的能力（这是退役判定的依据）。

## 2. 页面级 parity

webUI 共 9 个页面，新轨 9 个路由全部有对应实现（`DesktopRoute` 与 webUI 路由同名同序）。

| webUI 页面 | React 源 | 新轨落点 | 状态 |
|---|---|---|---|
| `LoginPage.tsx` | 144 行 | `ui/LoginScreen.kt` | 完成 |
| `OverviewPage.tsx` | 106 行 | `ui/pages/OverviewPage.kt` | 完成 |
| `AnalyticsPage.tsx` | 185 行 | `ui/pages/AnalyticsPage.kt` | 完成 |
| `AppsPage.tsx` | 284 行 | `ui/pages/AppsPage.kt` | 完成 |
| `RecordsPage.tsx` | 407 行 | `ui/pages/RecordsPage.kt` + `ui/pages/records/RecordMetadata.kt` | 完成 |
| `SendersPage.tsx` | 317 行 | `ui/pages/SendersPage.kt` + `config/SenderDefaults.kt` | 完成 |
| `SettingsPage.tsx` | 105 行 | `ui/pages/SettingsPage.kt` | 完成 |
| `AdvancedPage.tsx` | 175 行 | `ui/pages/AdvancedPage.kt` + `ui/QrCode.kt` | 完成 |
| `ScheduledTasksPage.tsx` | 21 行 | `ui/pages/ScheduledTasksPage.kt` | 完成 |

### 各页覆盖要点

- **Login**：浏览器交接登录（与 Rust 端同一套 `/api/v1/auth/desktop/*` 流程）、会话刷新、管理员初始化引导。
- **Overview**：`systemInfo` 四个指标卡、本地/公网端点、设备数来自 console 的设备列表；与 React 一致只在 `device.*` 与 `records.ingested` 这 7 类事件上重载（`remote.shouldRefreshSummaryOn`）。
- **Analytics**：revision + 四类记录计数五个指标卡、待执行 config 命令清单（revision/status/actor/summary/时间）、最近活动的 8 台设备；与 Overview 共用同一组 7 类事件过滤。
- **Apps**：包级启用/转发/通知开关、模板编辑（失焦提交）、过滤器 chip 行；只依赖 `connected` 显示 LiveBadge，与 React 一致不做实时重载。
- **Records**：四个 tab、应用/包名/号码线三个过滤器、分页、元数据解析（sim 标签、联系人名、通话公司名折叠）、验证码一键复制（`ui/DesktopClipboard.kt`）。
- **Senders**：19 种通道类型、按 `:relay:sender:api` 的 `SenderSettingSchemas` 生成结构化表单（布尔/枚举/文本/数字/JSON）、启用中排程规则编辑（黑名单/白名单 + 星期 + 时段）、增删改与 `replace_senders` 变更下发。
- **Settings**：所选设备的 revision / 待执行命令数 / `updatedAt` 状态卡、管理员改密表单（两个密码框 + 提示卡）。
- **Advanced**：定时任务入口跳转、一次性绑定码 + 二维码、设备列表（显示名/平台/型号/版本/启停/最近在线）、重命名、停用启用、吊销。
- **ScheduledTasks**：信息页，“添加任务”按钮给出“请在手机端添加”的提示卡。

## 3. 能力层 parity

| 能力 | React 位置 | 新轨落点 | 说明 |
|---|---|---|---|
| i18n 三语言 | `src/i18n.tsx` | `i18n/Messages.kt`、`i18n/LocalePreference.kt` | 从 i18n.tsx 机械移植的消息表，显式设置 > 后端语言标签 > 系统Locale |
| 实时事件流 | `src/realtime.tsx` | `remote/RealtimeFeed.kt` | SSE；页面按需订阅重载，Overview/Analytics 用 `shouldRefreshSummaryOn` 过滤 7 类事件，Records 用 3 类，Advanced 用 4 类，Apps/Senders 不重载 |
| 设备配置上下文 | `src/deviceConfig.tsx` | `session/DesktopConsoleState.kt` | 设备列表、选中设备持久化（同一 key `relay-webui-selected-device-id`）、待执行命令折进 effective root |
| 后端 profile 管理 | `frontend/desktop` 的 profile 存储 | `session/ProfileStore.kt`、`session/SessionManager.kt` | 多后端 profile + token 持久化 |
| 控制台 HTTP 客户端 | `src/api/client.ts` | `remote/ConsoleClient.kt` | 合同 DTO 统一来自 `:relay:contract` |
| 错误提示 | `template.tsx` 的 ErrorBanner | `ui/ConsoleComponents.kt` | 同一套视觉 |
| 本地存储同步 | — | `modules/desktop/{core,data}` 已接入 `:desktop` UI（组合根创建 + 登录后 initialPull / 5 分钟周期同步 + 页脚状态展示；页面读取仍走 `ConsoleClient`，即 Remote 模式）| 见 §5 |

## 4. 有意保留的差异

| 差异 | React 做法 | 新轨做法 | 原因 |
|---|---|---|---|
| 事件上报 | 每页调用 `trackEvent` | 不移植 | 上报链路不进入桌面端 |
| `window.confirm` | 原生确认框 | `ui/ConsoleComponents.kt` 的 `ConfirmDialog` | 桌面端统一对话框外观 |
| `window.prompt`（重命名设备） | 原生输入框 | `RenameDeviceDialog` | 同上 |
| `window.alert`（定时任务提示） | 原生提示 | `NoticeCard` 提示卡 | 同上，沿用 webUI 的内联提示样式 |
| 跳转定时任务页 | `window.location.href='/scheduled-tasks'` | `RouteContent` 的 `onNavigate` 回调 | 桌面端无 URL 路由 |
| 绑定二维码里的 `base_url` | `window.location.origin` | 当前 profile 的 `baseUrl` | 桌面端没有 location |
| 手机端 tab bar | `layout.tsx` 底部导航 | 只有侧边栏 | 桌面窗口宽度恒定 |
| 响应式网格 | `md:grid-cols-*` 断点 | `MetricRow` 按最小列宽折行 | 桌面端只做窄窗口折行 |
| 登录页 | webUI 账号密码登录入口 | 只保留浏览器交接登录 | 桌面端沿用 Rust 端的登录方式 |
| UI 组件语言 | webUI 自建 React 组件 | 直接调用 Compose Multiplatform 的 Material 3 叶子组件，不经 `:magisk-ui-kit` | 见下方「UI 组件语言的取舍」 |

### UI 组件语言的取舍

`desktop/` 此前声明了对 `:magisk-ui-kit` 的依赖，但 41 个源文件中没有任何 `io.github.magisk317.uikit.*` 导入：147 处界面调用全部直接使用 Compose Multiplatform 的 Material 3 叶子组件。2026-10-02 删除该依赖，理由：

- ui-kit 的职责是 Android 双轨合同（M3 与 miuix 两套实现按文件名分流，由 `scripts/checks/dual_track_check.py` 把关），对外 API 大量暴露 miuix 语义；
- 桌面端是独立的单轨 Compose Desktop 应用，不并入 Android 的 M/X 两轨，双轨门禁已按 `desktop/`、`modules/desktop/` 路径前缀豁免；
- 让桌面端改用 ui-kit 等于把 miuix 审美搬进桌面端，属于产品决策而非工程欠账；确有需要时把依赖加回来即可，门禁豁免与源集布局都不阻碍这件事。

## 5. 新轨相对 Tauri 现役端仍缺的能力

这些是 Tauri 端（`frontend/desktop/`）有、KMP 新轨还没有的能力；退役计划以逐项补齐为前提。

| 能力 | Tauri 落点 | KMP 新轨现状 |
|---|---|---|
| 托盘图标与菜单（显示主窗口、跳转页面、触发动作） | `src-tauri/src/tray.rs` | 已补齐：`desktop.platform` 的 `DesktopTray`/`AwtTray`（`java.awt.SystemTray`，无托盘时 `install` 返回 false，其余操作全部惰性无抛），`TrayMenu` 按 `tray.rs` 原顺序六行（quit 前分隔符）；菜单跳转与左键唤醒经 `WindowState.isMinimized` 还原并前置窗口，「打开设备」落在 Advanced（绑定码 + 设备列表所在页），「重新连接监控」走 feed stop/start；与 `AwtNotifier` 共享同一托盘图标（全进程只有一个托盘条目），4 个单元用例 |
| RunMode：Local / Remote / Hybrid | `src-tauri/src/main.rs`（`enum RunMode`，默认 Remote） | 部分完成：模型、持久化与切换 UI 已落地——`desktop.session.DesktopRunMode` 枚举 + `profiles.json` 的 `runMode` 字段（旧文件缺失该字段时默认 Remote），设置页「运行模式」`RelaySelect` 三选一（切换即时持久化），`DesktopApp` 按模式装配本地镜像（Remote 关闭，Local/Hybrid 开启并周期同步）；Local/Hybrid 数据读路由与本地服务器待后续切片 |
| 局域网本地服务器（agent 心跳/上报入口） | `src-tauri/src/local_server.rs`（axum） | 无 |
| 本地 SQLite 存储 | `src-tauri/src/sqlite_store.rs`、`src-tauri/src/store.rs` | `:desktop:data` 已有等价 Room schema（设备、config mirror/命令/审计、记录、本地设备绑定），`:desktop:core` 已有 Store + 同步引擎，**已接入 `:desktop` UI**：`DesktopApp` 组合根创建 `DesktopLocalRuntime`（`OkHttpRemoteStore` 对活 profile），登录后 initialPull + 5 分钟周期同步，页脚展示设备/记录数与失败态 |
| 旧库导入（Tauri 期本地数据迁移） | — | 按 `docs/TAURI_RETIREMENT.md` §2.2 改判为不做：桌面轨零发布，没有用户持有旧库。`LegacyDatabaseImporter.kt` 已实现并有测试，保留作为 schema 兼容的证据，不接 UI、不自动触发 |
| 离线/本地记录同步 | `src-tauri/src/sync.rs` | `:desktop:core/sync/SyncEngine.kt` 已实现并有测试，单飞同步用 `Mutex.tryLock` |
| 系统通知 | `tauri-plugin-notification`（桌面横幅 + 测试通知） | 已补齐：`desktop.platform` 的 `DesktopNotifier`/`AwtNotifier`（`java.awt.SystemTray`，无托盘时 `notify` 返回 false），`DesktopApp` 在 realtime 事件上触发（`device.heartbeat` 静默），11 个单元用例 |
| 数据库导出 / 导入 | `desktop_export_database` / `desktop_import_database` | 已补齐：`:desktop:data` 的 `DatabaseTransfer`（format id + version 的 JSON 快照，7 表全量、记录表翻页读全、导入单事务 replace-all、信封先校验、坏格式 / 版本过新 / 缺表一律具名拒绝且整体回滚）+ `desktop.platform.DesktopFileDialog` / `AwtFileDialog`（AWT 模态框，OS 调用经可注入 lambda、headless 退化为取消、`.json` 过滤放行目录）+ `desktop.local.DatabaseTransferController`（骑本地镜像同一条连接、tmp + rename 原子写、UTC 戳默认名 `xinyi-relay-desktop-<yyyyMMdd-HHmmss>.json`、Exported / Imported / Cancelled / Failed 四态）+ Advanced 页「本地数据库」卡（导出 PRIMARY / 导入 WARNING，导入前 ConfirmDialog，镜像关闭时显示不可用说明，成功提示 6 秒自清，失败进 ErrorBanner）；16 个单元用例（data 11 + desktop 5）；旧 Tauri `local-data.db` 是裸 SQLite 文件，按 §2.2 不兼容、不导入 |
| 诊断信息导出 | `desktop_export_diagnostics` | 无 |
| 打开外部链接 | `desktop_open_external_url` | 已补齐：`DesktopLinkOpener`/`AwtLinkOpener`（仅放行 http/https），顶栏「打开控制台」按钮消费，失败闪现 `platform.openLinkFailed` 提示 |
| 系统凭据保存（keyring） | `storage.rs`、`keyring` crate | `ProfileStore` 用 `java.util.prefs`，未用系统钥匙串 |
| 单实例锁 | `tauri-plugin-single-instance` | 已补齐：`main()` 入口 `FileLockInstanceGuard`（advisory `FileLock`，进程死亡由 OS 释放），第二实例弹本地化对话框后 `exitProcess(1)` |
| 设备绑定的本地服务器地址展示 | Tauri 前端 `pages/DevicesPage.tsx` | 无（依赖本地服务器能力） |
| 设备配置审计日志页 | Tauri 前端 `pages/ConfigPage.tsx` | 已补齐：`AnalyticsPage` 在待执行命令面板之后渲染审计流水（`deviceConfigAuditLogs(id, 30, 0)`：事件类型/操作方/版本/时间，端点失败降级为空列表），`ConsoleClientTest` 覆盖端点解码与查询参数 |

## 6. 测试覆盖

| 模块 | 测试类 | 用例数 |
|---|---|---|
| `:desktop` | 17（auth/config/i18n/local/platform/remote/session/ui + QrCode + RecordMetadata + RealtimeEventFilter + session state + run mode + DatabaseTransferController） | 99 |
| `:desktop:core` | 3（SyncEngine / RecordSync / DeviceConfigQueue） | 15 |
| `:desktop:data` | 3（DesktopSchema / LegacyDatabaseImporter / DatabaseTransfer） | 15 |

合计 23 个测试类 / 129 个用例，2026-10-03 以 `--no-build-cache --rerun-tasks --no-configuration-cache` 在 lzc 上全量通过。

本地验证命令（lzc，与 CI 一致）：

```bash
export ANDROID_HOME=/home/lzc/.gitlab-runner-magisk/android-sdk
./gradlew :desktop:compileKotlinJvm :desktop:jvmTest \
  :desktop:core:jvmTest :desktop:data:jvmTest verifyStructureBoundaries \
  --no-build-cache --rerun-tasks --no-configuration-cache
```

打新轨的包（与 CI 同一个脚本，2026-10-03 在 lzc 上验证：产出 deb + uber jar 各一个）：

```bash
scripts/ci/gitlab_desktop_kmp_package.sh kmp-linux-x64
ls -la desktop-artifacts/kmp-linux-x64/
```

脚本自己会从 Adoptium stage 一份 JDK 27 到 `<repo>/.jdk-27/`（首次约 300 MB，之后复用），产物落在 `<repo>/desktop-artifacts/`，两个目录都已在 `.gitignore` 里。`desktop-artifacts/` 是连字符根目录名，`verifyStructureBoundaries` 的白名单里有它，本地打包后再跑结构检查不会因此失败。

## 7. 双轨并存的 CI 现状

两轨代码同时压在 `beta` 分支上，CI 门禁对两轨都生效。两处此前会长期红着的问题已于 2026-10-02 修正：

| 问题 | 影响 | 处理 |
|---|---|---|
| 双轨合同门禁把 `desktop/` 的裸 M3 叶子组件全部记为 shared 轨违约（274 处） | `.github/workflows/dual-track.yml` 在每次 push 上失败 | `desktop/`、`modules/desktop/` 是单轨 Compose Desktop 应用，不会并入 Android 的 M/X 两轨，按路径前缀豁免（`scripts/checks/dual_track_check.py`） |
| CI 路径过滤把 `modules/desktop/**` 归入 app/mobile 桶 | `:desktop:core:jvmTest`、`:desktop:data:jvmTest` 从不执行 | 与 `desktop/*` 共用同一组任务，并带上 `verifyStructureBoundaries`（`scripts/ci/select_android_test_tasks.sh`） |

新轨**已有 GitLab 侧的打包/发布流水线，但只覆盖 Linux**：`.gitlab-ci.yml` 里的 `desktop-kmp:linux:x64`、`desktop-kmp:linux:arm64` 调 `scripts/ci/gitlab_desktop_kmp_package.sh`，产物汇入两轨共用的 `desktop:release:gitlab`。Windows/macOS 的 job 未接，GitHub 线也未接。两轨都没有自动更新器。发布方式细节见 `docs/DESKTOP_RELEASE_PLAN.md`，退役判据见 `docs/TAURI_RETIREMENT.md`。
