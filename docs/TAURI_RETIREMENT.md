# Tauri 桌面端退役方案

最后更新：2026-10-03

## 1. 先看清两轨的关系

| | 现役轨 | 新轨 |
|---|---|---|
| 位置 | `frontend/desktop/` | `desktop/`、`modules/desktop/core`、`modules/desktop/data` |
| 技术栈 | Tauri 2（Rust + React + Vite + SQLite） | Kotlin Multiplatform + Compose Multiplatform（jvm） |
| 规模 | 65 个受管文件：Rust 6,734 行（`src-tauri/src/`）+ TS/TSX 6,646 行（`src/`，9 个页面） | 控制台 9 个页面已对齐 `frontend/webui`（见 `docs/DESKTOP_PARITY.md`） |
| 独有本地能力 | 托盘、本地 axum 服务器、SQLite、同步、通知、单实例、系统钥匙串、导出/导入 | 托盘、SQLite + 同步、通知、单实例已接入 `:desktop`（parity §5）；本地 axum 服务器、系统钥匙串仍缺，导出/导入已接入 `:desktop`（Advanced 页卡片 + 文件对话框 + 控制器） |
| 发布方式 | 六平台矩阵，随 tag `v*.*.*` 出包 | GitLab 线只接 Linux x64/arm64 两个 job；Windows/macOS 未接，GitHub 线未接 |

两轨**共用** `frontend/shared/`（契约与 console 客户端工具），但 `frontend/shared/` 同时被 `frontend/webui` 引用，所以退役 Tauri 端**不删** `frontend/shared/`。

## 2. 退役判据

六条全部满足，才允许进入删除阶段。每条都要有可验证的证据，不能凭"差不多齐了"判断。

| # | 判据 | 验证方式 | 现状 |
|---|---|---|---|
| 1 | parity 清单 §5 的能力缺口全部补齐：托盘、RunMode + 本地服务器、本地存储接入 `:desktop` UI、通知、数据库导出/导入、诊断导出、打开外链、系统钥匙串、单实例、审计日志页（旧库导入入口已按 §2.2 从清单中划掉） | 逐项在 `:desktop` 里找到落点并有测试或人工走查记录 | 未满足：托盘、本地存储接入、系统通知、打开外链、单实例、审计日志页、数据库导出/导入已补齐（旧库导入入口按 §2.2 不做）；RunMode（切换 UI 已落地，数据读路由待做）+ 本地服务器、诊断导出、系统钥匙串仍缺 |
| 2 | KMP 轨有可用的六平台打包流水线 | 连续三个 tag 由 KMP workflow 产出全部平台产物 | 部分满足。GitLab 线只接了 Linux x64/arm64 两个 job，其余四平台既没有 job 也没有 runner 标签。GitHub 线未接。

「连续三个 tag」更无从谈起：流水线刚建，还没有任何 tag 走过它。见 §2.1 |
| 3 | 旧数据能被新轨读取，不是让用户手工搬文件 | `LegacyDatabaseImporter` 在真实旧库上跑通一次，且新轨启动时自动导入 | 改判为"迁移义务不存在"。依据见 §2.2：Tauri 桌面轨从未发布过，没有用户持有它的产物，因此不再补自动导入与 UI 入口。`LegacyDatabaseImporter` 与 `LegacyDatabaseImporterTest` 保留，作为 Room schema 与 Tauri SQLite 兼容的证据 |
| 4 | 用户迁移路径写明：两轨都没有自动更新，换轨的人必须手动下载新轨；token 保存位置不同（keyring → prefs），需要重新登录。按 §2.2，已装 Tauri 桌面版的用户实际为空，但这两段说明仍然要写——它同时是"默认轨从哪个版本起换人"的行为记录 | release note 模板里固定两段说明 | 未满足 |
| 5 | 双轨并存至少跨越一个 stable 版周期无回归 | release 记录 + issue | 未满足 |
| 6 | 文档、README、CI 引用全部改口 | `git grep -il tauri` 只剩 §4.2 允许保留的历史条目 | 未满足 |

### 2.1 判据 2 的边界：为什么"六平台"在 GitLab 线上凑不齐

`.gitlab-ci.yml` 里的 runner 标签一共四个：`magisk-amd64-linux`、`magisk-arm64-linux`、`saas-windows-medium-amd64`、`saas-macos-medium-m1`。Tauri 轨宣传的六平台里，windows-arm64 和 macos-x64 在 GitLab 侧没有任何标签可挂，GitHub 线（本次明确不接）才是凑齐六平台的地方。

而本项目目前**主动只接了 Linux 两个架构**：只有 Linux 上的打包被真实跑通过，Windows/macOS 的 job 一个都没接。所以判据 2 现在的差距有两层：

- **哪几平台**：Linux x64/arm64 有；其余四平台既没有 job，其中两个连 runner 标签都没有；
- **证据积累**：「连续三个 tag 由 KMP workflow 产出全部平台产物」目前零积累，流水线刚建，还没有任何 tag 走过它。

要把判据 2 走完，只有两条路：要么把 Windows/macOS 的 job 陆续接上并各自跑通，要么把验收标准改成本项目 runner 实际能覆盖的集合。二者都要拍板。

### 2.2 判据 3 改判为"迁移义务不存在"的理由

Tauri 桌面轨的产物从未到达过用户：

- `git tag` 一共 9 个 tag（`backup-20260910-pre-reset`、`pre-thematic-rewrite`、`v0.2.1` 到 `v0.2.7`），`desktop-v*` 为 0 个。桌面轨的发布脚本从来只被 Android tag 顺带触发，没有形成过桌面轨自己的发布记录；
- 项目方的结论同样是"旧版本也没人用"。

因此"旧数据能被新轨读取，不是让用户手工搬文件"这条判据保护的对象（已装 Tauri 桌面版的用户）并不存在，继续为它投入自动导入与 UI 入口是在解决一个没有受益者的问题。改判后：

- **不再做**：启动时自动导入旧库、旧库导入的 UI 入口；
- **保留**：`LegacyDatabaseImporter` 与 `LegacyDatabaseImporterTest`。它们证明 `:desktop:data` 的 Room schema 与 Tauri 端 SQLite 是同一套结构，将来若发现有用户持有旧库，重新接上导入入口的成本很低；
- **仍然要做**：§5 里关于凭据和 profile 的兼容说明，那是已装用户重填服务器地址的问题，与旧库无关。

## 3. 时间线（按 tag 推进，不绑具体日期）

- **阶段 0（现在）**：KMP 轨已完成控制台移植，GitLab 线上也有了打包流水线，但只跑 Linux x64/arm64，且能力缺口（判据 1）还很大。桌面端默认形态仍是 Tauri 轨，KMP 产物不宣传。
- **阶段 1（建流水线）**：流水线已建于 GitLab 线，只覆盖 Linux。Windows/macOS 要等对应 runner 上真实跑过一轮才补。GitHub 线（`.github/workflows/desktop-kmp-package.yml`）尚未建，要等 GitLab 线跑顺、且 KMP 轨要作为默认桌面端之前再补。Tauri 轨完全不变。
- **阶段 2（beta 预览）**：从某个 beta tag 起，KMP 轨产物作为预览版附在 release 上，note 里写清缺口；同时集中补 §2 判据 1 的能力。
- **阶段 3（切换默认）**：某个 stable tag 起，README 与 release note 把 KMP 轨作为默认桌面端；Tauri 轨两个 workflow 保留，但不再随新 tag 触发。
- **阶段 4（冻结）**：Tauri 轨只接受安全修复，覆盖一个完整 stable 版周期。
- **阶段 5（删除）**：按 §4 清单一次性删除，单独提交，并在 CHANGELOG 记一笔。

## 4. 删除清单

### 4.1 删除代码与流水线

| 路径 | 说明 |
|---|---|
| `frontend/desktop/` | 整个 Tauri 应用，含 `certs/windows-codesign.cer`、`bun.lock`、`src-tauri/icons/` |
| `.github/workflows/desktop-release.yml` | Tauri 六平台发布 |
| `.github/workflows/desktop-ci.yml` | Tauri 打包演练 |
| `.gitlab-ci.yml` 的桌面段 | 锚点 `.xinyi_desktop_linux` / `.xinyi_desktop_macos` / `.xinyi_desktop_windows`，job `desktop:linux:x64`、`desktop:linux:arm64`、`desktop:windows:x64`、`desktop:macos:arm64`、`desktop:release:gitlab` |
| `scripts/ci/gitlab_desktop_package.sh` | GitLab 侧 Linux/macOS 打包 |
| `scripts/ci/gitlab_desktop_windows.ps1` | GitLab 侧 Windows 打包 |
| `scripts/release/gitlab_desktop_release.sh` | GitLab 侧桌面发布 |
| `scripts/release/sync_desktop_version.sh` | 只把 `versionName` 同步进 Tauri 的三个文件；KMP 轨改为从 `versionName` 派生版本后可一并删除 |
| `.gitlab-ci.yml` 的 `desktop:release:gitlab` | 注意：这个发布 job 现在是**两轨共用**的（`needs` 里同时有 Tauri 的 `desktop:linux:*` 和 KMP 的 `desktop-kmp:linux:*`）。删 Tauri 轨时必须先把 KMP 的 needs 摘掉或给 KMP 单独建发布 job，否则会连着新轨的发布一起删掉 |

### 4.2 改口（不是删除）

| 文件 | 现状 |
|---|---|
| `README.md` | 第 122 行起整段"桌面端基于 Tauri + Rust……三种运行模式" |
| `README-EN.md` | 第 120 行起对应英文段 |
| `docs/ARCHITECTURE.md` | 第 7 行"四端架构：`Android Agent + Backend + Web Frontend + Tauri Desktop`" |
| `AGENTS.md` | 第 18 行 `` `frontend/desktop/`: Tauri desktop app `` |
| `backend/API_OVERVIEW.md` | 第 5 行"Web / Tauri 管理端"、第 29 行"## Web / Tauri Auth" |
| `docs/CHANGELOG.md` | 历史条目**保留不改**，它记录的是当时的事实 |

### 4.3 明确不删

| 路径 | 原因 |
|---|---|
| `frontend/shared/` | `frontend/webui` 仍在使用 |
| `frontend/webui/` | 浏览器控制台，是 KMP 移植的来源，也是手机端入口 |
| `gradle/libs.versions.toml` 的 `versionName` / `versionCode` | Android 与桌面共用的版本源 |
| `scripts/release/release_ref.sh`、`release_tag.sh`、`check_release_guard.sh`、`bump_version_code.sh` | 通用发布脚本，与桌面端技术栈无关 |
| `:desktop:data` 里引用 Tauri schema 的注释与测试 | `LegacyDatabaseImporter`、`DesktopDatabase`、`DeviceEntity`、`LegacyDatabaseImporterTest` 用它们说明旧库结构；这些注释解释的是数据兼容，不是技术栈宣传 |
| `.magisk-ci-toolkit/security/janitor_security_fixes.sh` 里的 cargo audit 段 | 属于外部子模块；本仓库删掉 Tauri 代码后该段会空转，需要在 toolkit 侧单独处理，不在本仓库改 |

## 5. 数据与兼容

- **本地数据库**：Tauri 端的 SQLite（`sqlite_store.rs`、`store.rs`）就是 `:desktop:data` 的 schema 来源，`LegacyDatabaseImporter` 已经能读它。原判据要求"退役前必须确认新轨首次启动会自动导入旧库"，已按 §2.2 改判：桌面轨零发布，没有用户持有旧库，自动导入与 UI 入口不再补。导入器本身保留，随时可以接回来。
- **凭据**：Tauri 端用系统钥匙串（`storage.rs`），新轨 `ProfileStore` 用 `java.util.prefs`。切换默认轨意味着已装用户要重新登录一次；如果阶段 2/3 补齐了钥匙串支持，这段说明也要跟着改。
- **没有自动更新**：两轨都没有更新器，已装 Tauri 版的用户不会自动迁移到新轨。删除发布物只影响"新用户能下到什么"，已装用户继续用旧版，直到自己下载新轨。
- **profile 兼容**：两端存储 key 不同（Tauri 走 keyring / Rust store，新轨走 prefs），服务器地址要么导入，要么让用户重填。

## 6. 回滚

退役的每一步都可回退，真正的删除只发生在阶段 5：

- **阶段 3 / 4 回退**：代码和 workflow 都还在，重新让 Tauri workflow 随 tag 触发即可，只改 release note。
- **阶段 5 之后回退**：只能从 git 历史恢复 `frontend/desktop/` 与两个 workflow。因此建议在进入阶段 5 之前先打一个标记 tag（例如 `tauri-retirement`），删除提交的 message 里写上这个 tag，方便事后定位与恢复。
- 删除必须单独一个提交，不与任何其他改动混在一起，保证可以整体 revert。

## 7. 删除前检查表

- [ ] §2 六条判据全部满足，且每条都有证据
- [ ] 至少一个 stable tag 由 KMP 轨产出桌面产物
- [ ] `git grep -il tauri` 的剩余结果只是 §4.2 / §4.3 允许保留的条目
- [ ] `grep -rn 'frontend/desktop' .github .gitlab-ci.yml scripts` 无结果
- [ ] `bash scripts/ci/select_android_test_tasks.sh` 的 desktop 分支仍指向 KMP 任务集（`:desktop:compileKotlinJvm`、`:desktop:jvmTest`、`:desktop:core:jvmTest`、`:desktop:data:jvmTest`）
- [ ] `./gradlew :desktop:compileKotlinJvm :desktop:jvmTest :desktop:core:jvmTest :desktop:data:jvmTest verifyStructureBoundaries` 无缓存通过
- [ ] README / README-EN / ARCHITECTURE / AGENTS / API_OVERVIEW 已改口，CHANGELOG 增加退役条目
- [ ] 已打 `tauri-retirement` 标记 tag
