# 桌面端双轨发布计划（KMP 新轨 + Tauri 现役轨）

最后更新：2026-10-02

## 1. 两轨各自怎么发布

| 轨 | 代码位置 | 打包入口 | 触发方式 | 产物 | 发到哪里 |
|---|---|---|---|---|---|
| 现役 Tauri 轨 | `frontend/desktop/`（Bun + Vite + Rust + Tauri 2） | `.github/workflows/desktop-release.yml` | `workflow_dispatch` + tag `v*.*.*` | 六平台：Linux x64/arm64 → AppImage/deb/rpm；Windows x64/arm64 → NSIS exe；macOS intel/arm64 → app/dmg | 与同 tag 的 APK 挂在同一个 GitHub Release；GitLab 侧另有一条 `desktop:release:gitlab` 通道 |
| 现役 Tauri 轨（演练） | 同上 | `.github/workflows/desktop-ci.yml` | 仅 `workflow_dispatch` | 同上，但只作 artifact 上传，不发布 | 不发布 |
| KMP 新轨 | `desktop/`、`modules/desktop/{core,data}` | `scripts/ci/gitlab_desktop_kmp_package.sh`，调 `:desktop:packageDistributionForCurrentOS` + `:desktop:packageUberJarForCurrentOS` | tag `v*.*.*` 上的 `desktop-kmp:linux:x64` / `desktop-kmp:linux:arm64`（允许失败）；也接受 `web` 手动触发 | Linux deb + 一个跨平台兜底的 uber jar | 与同 tag 的 Tauri 桌面产物挂在同一个 GitLab Release，资产名前缀 `kmp-`；GitHub 线未接 |

两轨共用的细节：

- **单一版本源**是 `gradle/libs.versions.toml` 的 `versionName`（当前 `0.2.8`）。Tauri 侧不自己维护版本，打包前由 `scripts/release/sync_desktop_version.sh` 把 `versionName` 同步进 `frontend/desktop/package.json`、`src-tauri/Cargo.toml`、`src-tauri/tauri.conf.json`；GitHub 与 GitLab 的桌面打包 job 都在构建前调用它。
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
| P0-4 | `:desktop:core`（Store + 同步引擎）与 `:desktop:data`（Room schema + 旧库导入）没有接进 `:desktop` UI，新轨仍是纯远端客户端 | 部分完成：本地存储已接入 UI（登录后 initialPull + 5 分钟周期同步 + 页脚状态）；通知、打开外链、单实例、审计流水页面已补齐；托盘菜单、RunMode/本地服务器、数据库导出/导入、诊断导出、钥匙串仍缺，见 `docs/DESKTOP_PARITY.md` §5 |
| P0-5 | KMP 轨没有版本/包名来源，打出来的包与 `versionName` 无关 | 已做：`desktop/build.gradle.kts` 的 `packageVersion` 直接读 `libs.versions.versionName`，发布时用 `-PdesktopVersion` 覆盖以剥掉预发后缀 |

P0-3 与 P0-5 是"能出包"的最小集合；P0-4 里本地存储、通知、单实例、审计页面已补齐，仍缺本地服务器、托盘菜单、数据库导出/导入、诊断导出与钥匙串，见 §5 的门槛表。

## 3. KMP 轨打包流水线（已落地，GitLab 线，仅 Linux）

两个锚点加两个 job，全在 `.gitlab-ci.yml`：`.xinyi_desktop_kmp_rules`（规则）、`.xinyi_desktop_kmp_base`（产物与 Gradle 环境）、`.xinyi_desktop_kmp_linux`（镜像与预备步骤）。两个 job 是 `desktop-kmp:linux:x64` 和 `desktop-kmp:linux:arm64`，产物目录分别是 `desktop-artifacts/kmp-linux-x64` 和 `desktop-artifacts/kmp-linux-arm64`。

设计取舍：

- **只接 Linux**：目前只有 Linux 两个 runner 上的打包被真实跑通过，Windows 和 macOS 的 job 一律不接——没有验证过的产线宁可没有，也不要一个看着通、跑起来才发现的。
- **JDK 自己 stage**：桌面模块是纯 JVM，只需要 JDK，不需要 Android SDK、Node、Rust。runner 上唯一会 stage JDK 的锚点是 toolkit 的 `.magisk_android_base`，它会把 SDK、`ANDROID_HOME` 和 aapt2 的绕法一起拖进来，所以这里用 `scripts/ci/gitlab_stage_jdk27.sh` 从 Adoptium 单独取一份 JDK 27（要 Hotspot 而非 JRE：jpackage 需要 jlink）。stage 到检出目录里，多 job 共用一把锁，避免同一个 runner 上的并发 job 重复下载。
- **每个 OS 只出一种格式**：`compose.desktop` 插件在 Linux 上把 AppImage、Deb、Rpm 都指向同一个 app image 目录且不声明相互依赖，同时声明两种 Linux 格式会在 Gradle 9 上撞 implicit-dependency 校验。所以默认 Linux→Deb、macOS→Dmg、Windows→Exe，需要别的格式时用 `-PdesktopTargetFormats=Rpm,AppImage` 覆盖。
- **KMP 打包失败不许拖垮 Tauri 发布**：`.xinyi_desktop_kmp_rules` 的 tag 规则默认带 `allow_failure: true`（由 `XINYI_DESKTOP_KMP_ALLOW_FAILURE` 控制），新轨出包失败时 Tauri 桌面发布照常走完。两轨并存期间这条不能摘。
- **资产名不撞车**：`scripts/release/gitlab_desktop_release.sh` 用「产物所在目录名 + 文件名」拼资产名并查重，所以 KMP 侧一律用 `kmp-` 前缀（如 `kmp-linux-x64-xinyi-relay-desktop-kmp_0.2.8_amd64.deb`），和 Tauri 的 `linux-x64-…` 不会同名。

**未接的部分**：Windows 与 macOS 的 KMP job；GitHub 线（`.github/workflows/desktop-kmp-package.yml`）。前者要等对应 runner 上真实跑过一轮再补，后者要等 GitLab 线跑顺、且 KMP 轨要作为默认桌面端之前再补。

**当前验证边界**：GitLab 线的两个 job 用的打包脚本，已在 lzc 上以 CI 形态端到端跑通（`--no-build-cache --rerun-tasks --no-configuration-cache`，产出 deb 与 uber jar 各一个）。lzc 上 `JAVA_HOME` 本就是 JDK 27，所以 CI staged 的 JDK 与本地构建用的是同一主版本。

## 4. 灰度门槛

| 阶段 | tag 形态 | Android 渠道 | 桌面两轨产物 |
|---|---|---|---|
| alpha | `v0.2.9-alpha.1` | internal | Tauri 轨照常出；KMP 轨只作为 workflow artifact，不宣传 |
| beta | `v0.2.9-beta.1` | beta | KMP 轨可作为"预览版"附在 release 上，note 里必须写清功能缺口（引用 parity 清单 §5） |
| stable | `v0.2.9` | production | KMP 轨必须已覆盖 §5 表的必备项，才允许作为默认桌面端宣传 |

进 stable 的桌面侧硬门槛（与 parity 清单 §5 的"仍缺能力"一一对应）：

- 托盘图标与菜单；
- RunMode（Local / Remote / Hybrid）与局域网本地服务器；
- `:desktop:core` / `:desktop:data` 接入 `:desktop` UI（旧库导入不做 UI 入口，理由见 `docs/TAURI_RETIREMENT.md` 判据 3）；
- 系统通知、数据库导出/导入、诊断导出、打开外部链接；
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
