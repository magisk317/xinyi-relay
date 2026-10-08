# 桌面端双轨发布计划（KMP 新轨 + Tauri 现役轨）

最后更新：2026-10-03

## 1. 两轨各自怎么发布

桌面端为单轨（Rust/Tauri 轨已于 2026-10-04 退役删除，见 `docs/TAURI_RETIREMENT.md`）：

| 代码位置 | 打包入口 | 触发方式 | 产物 | 发到哪里 |
|---|---|---|---|---|
| `desktop/`、`modules/desktop/{core,data}` | `scripts/ci/gitlab_desktop_kmp_package.sh`，调 `:desktop:packageDistributionForCurrentOS` + `:desktop:packageUberJarForCurrentOS` | tag `v*.*.*` 上的 `desktop-kmp:linux:x64` / `desktop-kmp:linux:arm64`；也接受 `web` 手动触发 | Linux deb + 同架构的 uber jar（免安装形态） | 与同 tag 的 APK 挂在同一个 GitLab Release，资产名前缀 `kmp-`；GitHub 线未接 |

两轨共用的细节：

- **单一版本源**是 `gradle/libs.versions.toml` 的 `versionName`（当前 `0.2.8`）。桌面打包从它派生包版本（`-PdesktopVersion`，打包脚本自己会剥掉预发后缀），不再有第二个需要同步的文件。
- **tag 规则**：`v{VERSION_NAME}`（`scripts/release/release_ref.sh` 只认 `v[0-9]*.[0-9]*.[0-9]*`）。Android 侧按后缀决定 Play 渠道（`v…-alpha.N` → internal、`v…-beta.N` → beta、无后缀 → production，见 `release.yml` 的 determine_track）；同一个 tag 同时触发 `release.yml` 和 `desktop-release.yml`，所以桌面产物和 APK 天然同 tag 同 release。
- **预发版本号会被剥掉后缀**：打包步骤用正则把 `tauri.conf.json` 里的 `0.2.9-alpha.1` 还原成 `0.2.9`，避免安装包版本带预发标记。
- **两轨都没有自动更新**：Tauri 未接 `tauri-plugin-updater`，KMP 轨也没有更新器。用户只能手动下载下一个 tag 的产物——这也意味着"回滚"天然只是"下一个 tag 用什么轨"的问题。
- **签名**：Tauri 轨的 Windows 用 `secrets.WINDOWS_CERTIFICATE` / `WINDOWS_CERTIFICATE_PASSWORD`；macOS 当前不签名不公证。

## 2. 发布阻塞项

| 编号 | 项 | 状态 |
|---|---|---|
| P0-1 | 双轨合同门禁把 `desktop/` 的裸 M3 用法记为违约，`dual-track.yml` 每次 push 都红 | 已修复（`db93a237`，按路径前缀豁免 `desktop/`、`modules/desktop/`） |
| P0-2 | CI 路径过滤把 `modules/desktop/**` 归进 app/mobile 桶，`:desktop:core:jvmTest`、`:desktop:data:jvmTest` 从不执行 | 已修复（`5da2b9a1`，与 `desktop/*` 共用任务集） |
| P0-3 | KMP 轨没有任何打包/发布流水线 | 部分完成（GitLab 线）：`desktop-kmp:linux:x64`、`desktop-kmp:linux:arm64` 两个 job 已接进 `.gitlab-ci.yml`，产物汇入两轨共用的 `desktop:release:gitlab`。Windows/macOS 未接 |
| P0-4 | `:desktop:core`（Store + 同步引擎）与 `:desktop:data`（Room schema + 旧库导入）没有接进 `:desktop` UI，新轨仍是纯远端客户端 | **已完成**：本地存储接入 UI（登录后 initialPull + 5 分钟周期同步 + 页脚状态 + Advanced 页手动 pull/push 与结果卡），RunMode 模型/持久化/切换 UI 与 Local 数据读路由（`ConsoleDataClient` 双实现 + `DesktopReadRouter`）、本地服务器（8 路由）、系统钥匙串、托盘、通知、打开外链、单实例、审计流水页面、数据库导出/导入、诊断信息导出均已落地，见 `docs/DESKTOP_PARITY.md` §5 |
| P0-5 | KMP 轨没有版本/包名来源，打出来的包与 `versionName` 无关 | 已做：`desktop/build.gradle.kts` 的 `packageVersion` 直接读 `libs.versions.versionName`，发布时用 `-PdesktopVersion` 覆盖以剥掉预发后缀 |

P0-3 与 P0-5 是"能出包"的最小集合；P0-4 的清单已全部落地（托盘、RunMode + 本地服务器、本地存储接入 UI、通知、数据库导出/导入、诊断导出、打开外链、系统钥匙串、单实例、审计日志页），详见 parity §5 与 §6 的测试证据。

## 3. 桌面打包流水线（已落地，GitLab 线，仅 Linux）

两个锚点加两个 job，全在 `.gitlab-ci.yml`：`.xinyi_desktop_kmp_rules`（规则）、`.xinyi_desktop_kmp_base`（产物与 Gradle 环境）、`.xinyi_desktop_kmp_linux`（镜像与预备步骤）。两个 job 是 `desktop-kmp:linux:x64` 和 `desktop-kmp:linux:arm64`，产物目录分别是 `desktop-artifacts/kmp-linux-x64` 和 `desktop-artifacts/kmp-linux-arm64`。

设计取舍：

- **只接 Linux**：目前只有 Linux 两个 runner 上的打包被真实跑通过，Windows 和 macOS 的 job 一律不接——没有验证过的产线宁可没有，也不要一个看着通、跑起来才发现的。
- **JDK 自己 stage**：桌面模块是纯 JVM，只需要 JDK，不需要 Android SDK、Node、Rust。runner 上唯一会 stage JDK 的锚点是 toolkit 的 `.magisk_android_base`，它会把 SDK、`ANDROID_HOME` 和 aapt2 的绕法一起拖进来，所以这里用 `scripts/ci/gitlab_stage_jdk27.sh` 从 Adoptium 单独取一份 JDK 27（要 Hotspot 而非 JRE：jpackage 需要 jlink）。stage 到检出目录里，多 job 共用一把锁，避免同一个 runner 上的并发 job 重复下载。
- **每个 OS 只出一种格式**：`compose.desktop` 插件在 Linux 上把 AppImage、Deb、Rpm 都指向同一个 app image 目录且不声明相互依赖，同时声明两种 Linux 格式会在 Gradle 9 上撞 implicit-dependency 校验。所以默认 Linux→Deb、macOS→Dmg、Windows→Exe，需要别的格式时用 `-PdesktopTargetFormats=Rpm,AppImage` 覆盖。
- **桌面产物是可选产物，失败不阻塞 release**：打包 job 的 tag 规则带 `allow_failure: true`。发布节奏以 Android 应用为准，桌面出包失败只应留下一个红色的可选 job，而不是拖住整个 release。`desktop:release:gitlab` 本身也是 `allow_failure: true`，所以"两个 job 都没产物"时也不会挡发布。
- **资产名带 `kmp-` 前缀**：`scripts/release/gitlab_desktop_release.sh` 用「产物所在目录名 + 文件名」拼资产名并查重，所以资产形如 `kmp-linux-x64-xinyi-relay-desktop-kmp_0.2.8_amd64.deb`。这个前缀是双轨时期为避撞名留下的，现在只用于保持历史资产名稳定。

**未接的部分**：Windows 与 macOS 的 job；GitHub 线（`.github/workflows/desktop-kmp-package.yml`）。前者要等对应 runner 上真实跑过一轮再补，后者同理。

**当前验证边界**：GitLab 线的两个 job 用的打包脚本，已在 lzc 上以 CI 形态端到端跑通（`--no-build-cache --rerun-tasks --no-configuration-cache`，产出 deb 与 uber jar 各一个）。lzc 上 `JAVA_HOME` 本就是 JDK 27，所以 CI staged 的 JDK 与本地构建用的是同一主版本。

### 3.1 若要支持 Windows / macOS

桌面端是纯 JVM，不需要 Rust/Node/Android SDK，但**必须有对应操作系统的构建机**：`compose.desktop` 的 `packageDistributionForCurrentOS` 走 jpackage，而 jpackage 只为本机产出安装包（官方原话是 "suitable for the host system"），没有交叉打包选项。文件系统层面的证据是：当前 uber jar 里只嵌了 `libskiko-linux-x64.so` 一个 Skiko 原生库，Windows 的 `skiko-windows-x64.dll` / `-arm64.dll` 与 macOS 的 `libskiko-macos-{x64,arm64}.dylib` 都在 `skiko-awt-runtime-all` 里、但没被打进这个 jar。

按平台拆开需要的东西：

| 目标 | 构建机 | 额外要做的事 |
|---|---|---|
| Linux x64 | 已有（`magisk-amd64-linux`） | — |
| Linux arm64 | 已有（`magisk-arm64-linux`） | — |
| Windows x64 | 需要一台 Windows runner | `scripts/ci/gitlab_stage_jdk27.ps1` 与打包驱动（PowerShell 版，见下）；产物为 exe/msi |
| macOS arm64 | 需要一台 macOS runner | 与 Linux 同一个 bash 脚本即可（`uname` 分支已处理 `Darwin`）；产物为 dmg/pkg；未签名未公证，首次运行要手动放行 |
| Windows arm64 | 需要 Windows on ARM 构建机 | 同 Windows x64 |
| macOS x64 | 需要 Intel Mac 构建机 | 同 macOS arm64 |

**GitLab 线现在没有这些 runner**：项目/组可见的 runner 列表里，Windows 与 macOS 的共享 runner 全部是 `paused`/`stale`，自定义的两个 Linux runner 才是实际在用的；`.gitlab-ci.yml` 里 `XINYI_GITLAB_WINDOWS_AMD64_RUNNER_TAG`、`XINYI_GITLAB_MACOS_ARM64_RUNNER_TAG` 指向的标签目前没有任何在线 runner 承接。所以"支持 Windows/macOS"的第一步不是写代码，是先有机器。

**GitHub 线是另一条路，它有跑通过的历史，但近半年一直是红的**：Tauri 时代 `desktop-release.yml` 跑六平台。`gh run list` 里最近 8 次全部 failed（最后几次倒在 `pnpm install`：工作目录不存在，应该是 workflow 引用的 `working-directory: desktop` 与实际路径 `frontend/desktop` 脱节）；而更早的 `v0.1.1`–`v0.1.3` 三个 release 每个都带 12 个桌面资产（deb/rpm/AppImage、`x64`/`arm64`/`x86` 的 exe 与 msi），说明那条流水线当年是能出包的——只是**从来没出过 macOS 资产**（12 个里没有 dmg）。`v0.2.0` 之后桌面资产归零。

GitHub 托管 runner 自带 Windows 与 macOS 机器（含 arm64 变体），所以它是**唯一不需要自备机器**的路子；但原 workflow 是 Tauri 工具链写的，要重写，且要等 GitLab 线跑顺之后再接。

代码侧还差两处（与有没有机器无关）：

1. **Windows 的凭据存储是缺的**：`SystemCredentialStore` 对 `Platform.WINDOWS` 直接 `unsupported()`（设计如此：宁可明确拒绝，也不静默把 bearer token 写进明文文件）。这会把 `saveSession` 抛成异常，而 `SessionManager.onAuthenticated` 没有捕获，所以 Windows 上**登录会失败**。要支持 Windows，得先实现 DPAPI / Windows Credential Manager 分支。
2. **打包脚本的 Windows 版**：`scripts/ci/gitlab_stage_jdk27.ps1` 与 `scripts/ci/gitlab_desktop_kmp_windows.ps1` 已经写好并逐行核对过，但当前**不在仓库里**——它们写出来时 Windows job 没接，且没有任何 PowerShell 环境可以语法校验，所以当时没有提交。要接 Windows 线时，这两个文件需要重新加回并至少在一台 Windows 机器上真跑一遍。

macOS 不需要第 1 条（`security` 分支已实现），只差机器和签名（不签名也能发，用户手动放行）。

## 4. 灰度门槛

| 阶段 | tag 形态 | Android 渠道 | 桌面两轨产物 |
|---|---|---|---|
| alpha | `v0.2.9-alpha.1` | internal | Tauri 轨照常出；KMP 轨只作为 workflow artifact，不宣传 |
| beta | `v0.2.9-beta.1` | beta | KMP 轨可作为"预览版"附在 release 上，note 里必须写清功能缺口（引用 parity 清单 §5） |
| stable | `v0.2.9` | production | KMP 轨必须已覆盖 §5 表的必备项，才允许作为默认桌面端宣传 |

进 stable 的桌面侧硬门槛（与 parity 清单 §5 的"仍缺能力"一一对应）：

- 托盘图标与菜单（已补齐，见 parity §5）；
- 局域网本地服务器（RunMode 的 Local 数据读路由已补齐，见 parity §5）；
- `:desktop:core` / `:desktop:data` 接入 `:desktop` UI（旧库导入不做 UI 入口，理由见 `docs/TAURI_RETIREMENT.md` 判据 3）；
- 系统通知（已补齐，见 parity §5）、打开外部链接；
- 系统钥匙串保存 token、单实例锁；
- 设备配置审计日志页。

## 5. 风险与回滚

- **默认轨未切换前**：回滚等于继续发 Tauri 轨，两个 workflow 都保留，用户手里的旧产物继续可用；没有自动更新，回滚不推送任何东西给已安装用户。
- **资产覆盖**：GitHub 侧现有发布步骤用 `gh release upload --clobber` 和 `gh release create --verify-tag`，同名资产会被覆盖；GitLab 侧的 `scripts/release/gitlab_desktop_release.sh` 按「目录名 + 文件名」拼资产名并查重，重打同名 tag 会静默替换同名资产。KMP 轨的资产名带 `kmp-` 前缀，与 Tauri 轨天然区分，但同一 tag 下重跑 KMP job 仍会覆盖自己上一次的产物。
- **数据兼容**：`:desktop:data` 的 Room schema 已经和 Tauri 端的 SQLite 对齐，`LegacyDatabaseImporter` 及其测试证明了这一点。按"旧版本没人用"的前提，本项目不为旧库补自动导入与 UI 入口（详见 `docs/TAURI_RETIREMENT.md` 判据 3），因此这里剩下的一句是：在 `:desktop:data` 接入 `:desktop` UI 之前，Room schema 不要随 stable 发布，否则新轨写出来的本地库没有读取方。
- **版本分叉**：两轨必须共用 `versionName`。任何让 KMP 轨独立版本号的提议都会让用户无法判断手上是不是最新版，也都会让 `check_release_guard.sh` 的 tag/版本一致性校验失效。
- **CI 负载**：KMP 打包矩阵与 Tauri 矩阵同时挂在 tag 上会让 release 流水线翻倍。目前 KMP 侧只跑 Linux 两个 job，是 Tauri 侧 Linux 两个 job 的一比一复刻；等 KMP 轨成为默认后再下掉 Tauri 矩阵（见 `docs/TAURI_RETIREMENT.md`）。

## 6. 衔接退役

发布计划的终点不是"两轨长期并存"，而是把 Tauri 轨从默认降为兼容期备选再删除。判定标准、时间线与删除清单见 `docs/TAURI_RETIREMENT.md`。
