# 桌面端双轨发布计划（KMP 新轨 + Tauri 现役轨）

最后更新：2026-10-02

## 1. 两轨各自怎么发布

| 轨 | 代码位置 | 打包入口 | 触发方式 | 产物 | 发到哪里 |
|---|---|---|---|---|---|
| 现役 Tauri 轨 | `frontend/desktop/`（Bun + Vite + Rust + Tauri 2） | `.github/workflows/desktop-release.yml` | `workflow_dispatch` + tag `v*.*.*` | 六平台：Linux x64/arm64 → AppImage/deb/rpm；Windows x64/arm64 → NSIS exe；macOS intel/arm64 → app/dmg | 与同 tag 的 APK 挂在同一个 GitHub Release；GitLab 侧另有一条 `desktop:release:gitlab` 通道 |
| 现役 Tauri 轨（演练） | 同上 | `.github/workflows/desktop-ci.yml` | 仅 `workflow_dispatch` | 同上，但只作 artifact 上传，不发布 | 不发布 |
| KMP 新轨 | `desktop/`、`modules/desktop/{core,data}` | 无 workflow；本地可用 `compose.desktop` 的 `:desktop:packageDistributionForCurrentOS` / `:desktop:run` | 无 | 无 | 无 |

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
| P0-3 | KMP 轨没有任何打包/发布流水线 | **未做**，方案见 §3 |
| P0-4 | `:desktop:core`（Store + 同步引擎）与 `:desktop:data`（Room schema + 旧库导入）没有接进 `:desktop` UI，新轨仍是纯远端客户端 | **未做**，缺口清单见 `docs/DESKTOP_PARITY.md` §5 |
| P0-5 | KMP 轨没有版本/包名来源，打出来的包与 `versionName` 无关 | **未做**：要么让打包从 `versionName` 派生，要么在 workflow 里注入，否则会出现"两个桌面版本" |

P0-3 与 P0-5 是"能出包"的最小集合；P0-4 里的本地存储、本地服务器、托盘、通知、单实例是"能替代 Tauri 轨"的最小集合，见 §5 的门槛表。

## 3. KMP 轨打包流水线（建议）

新增 `.github/workflows/desktop-kmp-package.yml`，结构照抄 `desktop-release.yml` 的六平台矩阵，只换构建步骤：

1. `actions/checkout@v7`（不需要 submodules，桌面模块不依赖 gitlab 子模块）；
2. `actions/setup-java` 提供 JDK（桌面模块不依赖 toolkit 的 Android setup action）；Android SDK 不需要；
3. `./gradlew :desktop:packageDistributionForCurrentOS`（compose desktop plugin 已配 `mainClass`，产物为当前 OS 的 msi/exe/deb/rpm/dmg）；跑通后再考虑 `packageUberJarForCurrentOS` 作为跨平台兜底产物；
4. 版本处理：从 `gradle/libs.versions.toml` 读 `versionName`，以 `-PdesktopVersion=...` 或 `ORG_GRADLE_PROJECT_desktopVersion` 注入，让 `:desktop` 的 `compose.desktop.application` 带上版本与包名（P0-5）；
5. `upload-artifact` 保留每个平台的产物；
6. tag 推送时上传到同一个 GitHub Release，资产名带 `-kmp` 后缀（如 `xinyi-relay_0.2.8_amd64-kmp.deb`），与 Tauri 产物区分；GitLab 侧暂不加 job，避免两套 CI 同时维护两轨桌面产物。

接入顺序建议：先只开 `workflow_dispatch` 做六平台打包演练（对齐 `desktop-ci.yml` 的角色），连续跑通两三轮后再挂到 tag 触发上。

## 4. 灰度门槛

| 阶段 | tag 形态 | Android 渠道 | 桌面两轨产物 |
|---|---|---|---|
| alpha | `v0.2.9-alpha.1` | internal | Tauri 轨照常出；KMP 轨只作为 workflow artifact，不宣传 |
| beta | `v0.2.9-beta.1` | beta | KMP 轨可作为"预览版"附在 release 上，note 里必须写清功能缺口（引用 parity 清单 §5） |
| stable | `v0.2.9` | production | KMP 轨必须已覆盖 §5 表的必备项，才允许作为默认桌面端宣传 |

进 stable 的桌面侧硬门槛（与 parity 清单 §5 的"仍缺能力"一一对应）：

- 托盘图标与菜单；
- RunMode（Local / Remote / Hybrid）与局域网本地服务器；
- `:desktop:core` / `:desktop:data` 接入 `:desktop` UI，旧库导入有 UI 入口；
- 系统通知、数据库导出/导入、诊断导出、打开外部链接；
- 系统钥匙串保存 token、单实例锁；
- 设备配置审计日志页。

## 5. 风险与回滚

- **默认轨未切换前**：回滚等于继续发 Tauri 轨，两个 workflow 都保留，用户手里的旧产物继续可用；没有自动更新，回滚不推送任何东西给已安装用户。
- **资产覆盖**：现有发布步骤用 `gh release upload --clobber` 和 `gh release create --verify-tag`，同名资产会被覆盖。KMP 轨首次接入时建议去掉 `--clobber`，让重打改用不同资产名，避免同一 tag 下产物被静默替换。
- **数据兼容**：`:desktop:data` 的 Room schema 与 `LegacyDatabaseImporter` 在接入 `:desktop` UI 之前，不要随 stable 发布，否则用户升级后本地库没有读取方。
- **版本分叉**：两轨必须共用 `versionName`。任何让 KMP 轨独立版本号的提议都会让用户无法判断手上是不是最新版，也都会让 `check_release_guard.sh` 的 tag/版本一致性校验失效。
- **CI 负载**：KMP 打包矩阵与 Tauri 矩阵同时挂在 tag 上会让 release 流水线翻倍；可等 KMP 轨成为默认后再下掉 Tauri 矩阵（见 `docs/TAURI_RETIREMENT.md`）。

## 6. 衔接退役

发布计划的终点不是"两轨长期并存"，而是把 Tauri 轨从默认降为兼容期备选再删除。判定标准、时间线与删除清单见 `docs/TAURI_RETIREMENT.md`。
