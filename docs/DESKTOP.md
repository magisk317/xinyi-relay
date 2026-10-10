# 桌面端架构与能力文档（Compose Multiplatform Desktop）

最后更新：2026-10-10

桌面端是基于 Kotlin Multiplatform (KMP) + Compose Multiplatform 的**单轨**应用，逐页复刻了 `frontend/webui/` React 控制台，并集成了完整的本地镜像与单机能力。原 Rust/Tauri 轨已于 2026-10-04 正式退役删除（Git 恢复标签见 annotated tag `tauri-retirement`）。

---

## 1. 架构与模块划分

| 模块 | 路径 | 职责与技术栈 |
|---|---|---|
| `:desktop` | `desktop/` | Compose Desktop 应用壳、页面路由、桌面平台集成（托盘/通知/钥匙串/单实例） |
| `:desktop:core` | `modules/desktop/core/` | 领域模型、状态管理、离线同步引擎（`SyncEngine`）、本地镜像读路由（`DesktopReadRouter`） |
| `:desktop:data` | `modules/desktop/data/` | Room KMP 本地数据库、SQLite 实体/DAO、快照导出/导入（`DatabaseTransfer`） |

### 核心设计原则

- **单一版本源**：桌面端包版本由 `gradle/libs.versions.toml` 的 `versionName` 单一驱动，发布构建可由 `-PdesktopVersion` 剥离预发后缀，并由 `scripts/release/check_release_guard.sh` 实施自动化防呆校验。
- **运行模式（RunMode）**：支持 `Remote`（默认，纯远端）、`Local`（本地镜像优先，内嵌服务器承接设备）、`Hybrid`（远端主读，本地热缓存）。读路由由 `DesktopReadRouter` 动态分流。
- **UI 风格与门禁豁免**：采用桌面 Material 3 组件，不引入 Android 侧的 miuix 规范；双轨合同门禁（`dual_track_check.py`）按 `desktop/` 与 `modules/desktop/` 路径前缀进行针对性豁免。

---

## 2. WebUI 页面级对照（Parity）

桌面端 9 个路由与 WebUI 完全同名同序对齐：

| 页面路由 | WebUI React 源 | Compose Desktop 实现 | 覆盖要点 |
|---|---|---|---|
| **Login** | `LoginPage.tsx` | `ui/LoginScreen.kt` | 浏览器交接登录（`/api/v1/auth/desktop/*`）、会话刷新、首次管理员配置引导 |
| **Overview** | `OverviewPage.tsx` | `ui/pages/OverviewPage.kt` | 系统指标卡、端点展示、设备总数，响应实时事件刷新 |
| **Analytics** | `AnalyticsPage.tsx` | `ui/pages/AnalyticsPage.kt` | 待执行命令清单、分类计数、审计流水展示 |
| **Apps** | `AppsPage.tsx` | `ui/pages/AppsPage.kt` | 应用包级转发/通知开关、模板失焦持久化、过滤器 chip |
| **Records** | `RecordsPage.tsx` | `ui/pages/RecordsPage.kt` | 四类记录 Tab、元数据解析（SIM/联系人/公司折叠）、验证码一键复制 |
| **Senders** | `SendersPage.tsx` | `ui/pages/SendersPage.kt` | 19 种通道结构化表单、排程规则编辑、变更同步下发 |
| **Settings** | `SettingsPage.tsx` | `ui/pages/SettingsPage.kt` | 运行模式切换（Remote/Local/Hybrid）、管理员改密表单 |
| **Advanced** | `AdvancedPage.tsx` | `ui/pages/AdvancedPage.kt` | 绑定码生成与二维码展示、设备重命名/停用/吊销、本地同步与数据库维护卡 |
| **ScheduledTasks** | `ScheduledTasksPage.tsx` | `ui/pages/ScheduledTasksPage.kt` | 定时任务列表与移动端添加引导卡 |

### 保留的桌面平台差异

1. **对话框**：替代浏览器的 `window.confirm` / `prompt`，统一为 Compose 桌面模态框（`ConfirmDialog` / `RenameDeviceDialog`）。
2. **文件对话框**：文件保存与选取采用 AWT 原生模态框（`DesktopFileDialog`），在 headless 测试环境中安全降级。
3. **外部链接**：顶栏与外部文档跳转经由系统默认浏览器打开（`DesktopLinkOpener`），仅放行安全协议。
4. **绑定二维码**：在 `Local` 模式下自动指向内嵌本地服务器地址（`localServerUrl`），其余模式取远端控制台地址。

---

## 3. 本地与单机能力清单

| 能力 | 实现落点 | 行为说明 |
|---|---|---|
| **系统托盘** | `desktop.platform.AwtTray` | 承接最小化到托盘、右键菜单（页面跳转/监控启停/退出）、左键单击还原前置窗口 |
| **局域网服务器** | `desktop.local.DesktopLocalServer` | 基于 JDK 内置 `HttpServer`（零第三方网络依赖），提供心跳、上报、命令拉取等 8 条核心路由 |
| **本地存储** | `modules/desktop/data` | Room KMP 本地数据库，映射设备、配置命令、审计流水及转发记录 |
| **双向同步** | `modules/desktop/core/sync/SyncEngine` | 登录后自动 initialPull，后台 5 分钟周期同步，支持 Advanced 页手动 Pull/Push 与状态卡 |
| **系统通知** | `desktop.platform.AwtNotifier` | 接入系统原生通知托盘，在接收到重要事件（如转发失败）时进行桌面提示 |
| **凭据安全** | `desktop.session.SystemCredentialStore` | Linux 下接入 `secret-tool`，macOS 接入钥匙串，拒绝在磁盘明文保存 Token |
| **单实例防重** | `desktop.platform.FileLockInstanceGuard` | 基于文件锁机制，第二实例启动时提示并自动退出 |
| **数据库快照** | `desktop.data.DatabaseTransfer` | 导出/导入标准化 JSON 快照（携带格式签名 `xinyi-relay-desktop-database` 与版本号） |
| **诊断导出** | `desktop.session.DesktopDiagnostics` | 导出脱敏的运行状态、网络连接、会话概况与排障信息 JSON 文件 |

---

## 4. 打包与 CI 发布流水线

### 4.1 流水线现状（GitLab CI，Linux 单轨）

桌面端打包任务定义于 `.gitlab-ci.yml`，由以下两个架构 Job 负责：
- `desktop-kmp:linux:x64`
- `desktop-kmp:linux:arm64`

打包脚本为 `scripts/ci/gitlab_desktop_kmp_package.sh`，直接调用：
```bash
./gradlew :desktop:packageDistributionForCurrentOS :desktop:packageUberJarForCurrentOS -PdesktopVersion=<version>
```
产物汇总至 `desktop-artifacts/`，汇入统一的 `desktop:release:gitlab` 发布任务，资产命名遵循 `kmp-<os>-<arch>-<pkg>.<ext>`。

### 4.2 平台支持与设计取舍

- **仅接 Linux 的原因**：目前自建 Runner 仅覆盖 Linux x64 与 Linux arm64，且环境已调通 JDK 27（Hotspot，满足 `jpackage`/`jlink` 工具链需求）。未经验证的 Windows/macOS 构建机暂不接入。
- **单格式原则**：由于 Compose Desktop 插件在多格式共用 app-image 目录时在 Gradle 9 上存在 implicit-dependency 校验限制，当前默认 Linux 仅输出 `.deb` 与 uber `.jar`。
- **未来扩展 Windows / macOS 的前提**：
  1. 具备在线的专用 Windows/macOS Runner；
  2. Windows 需补齐 Credential Manager 凭据存储分支与 PowerShell 打包引导脚本；
  3. macOS 需配置 Apple 开发者代码签名与公证。

---

## 5. 测试覆盖与本地验证

本地无缓存全量校验命令：
```bash
./gradlew :desktop:compileKotlinJvm :desktop:jvmTest \
  :desktop:core:jvmTest :desktop:data:jvmTest verifyStructureBoundaries \
  --no-build-cache --rerun-tasks --no-configuration-cache
```

当前测试覆盖 27 个测试类、150+ 个用例，涵盖认证、同步状态机、本地服务器路由、数据库迁移快照、钥匙串脱敏与诊断导出。

---

## 6. 附录：Tauri 历史退役归档（2026-10-04）

- **退役执行**：Rust/Tauri 轨于 2026-10-04 完全清退（提交 `68d2622e`），移除了 `frontend/desktop/` 下 65 个文件（约 13,000 行代码），Git 历史恢复点永久封存于 tag `tauri-retirement`。
- **旧库迁移判据（原 §2.2）**：由于 Tauri 桌面端在开发期从未对外正式发布，外部用户无任何存量数据库持有人，因此不建立自动迁移 UI 入口；`LegacyDatabaseImporter.kt` 与对应测试保留在工程中，用于证明 Room schema 与历史 SQLite 结构的一致性。
- **快照格式隔离**：`DatabaseTransfer` 采用强类型格式标记 `xinyi-relay-desktop-database`，严密防范并拒绝加载格式不符的旧版裸 SQLite 文件。
