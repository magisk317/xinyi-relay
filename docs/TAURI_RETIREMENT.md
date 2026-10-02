# Tauri 桌面端退役方案

最后更新：2026-10-02

## 1. 先看清两轨的关系

| | 现役轨 | 新轨 |
|---|---|---|
| 位置 | `frontend/desktop/` | `desktop/`、`modules/desktop/core`、`modules/desktop/data` |
| 技术栈 | Tauri 2（Rust + React + Vite + SQLite） | Kotlin Multiplatform + Compose Multiplatform（jvm） |
| 规模 | 65 个受管文件：Rust 6,734 行（`src-tauri/src/`）+ TS/TSX 6,646 行（`src/`，9 个页面） | 控制台 9 个页面已对齐 `frontend/webui`（见 `docs/DESKTOP_PARITY.md`） |
| 独有本地能力 | 托盘、本地 axum 服务器、SQLite、同步、通知、单实例、系统钥匙串、导出/导入 | 部分已实现在 `:desktop:core` / `:desktop:data`，但**未接进 `:desktop` UI** |
| 发布方式 | 六平台矩阵，随 tag `v*.*.*` 出包 | 无打包流水线 |

两轨**共用** `frontend/shared/`（契约与 console 客户端工具），但 `frontend/shared/` 同时被 `frontend/webui` 引用，所以退役 Tauri 端**不删** `frontend/shared/`。

## 2. 退役判据

六条全部满足，才允许进入删除阶段。每条都要有可验证的证据，不能凭"差不多齐了"判断。

| # | 判据 | 验证方式 | 现状 |
|---|---|---|---|
| 1 | parity 清单 §5 的能力缺口全部补齐：托盘、RunMode + 本地服务器、本地存储接入 `:desktop` UI、旧库导入入口、通知、数据库导出/导入、诊断导出、打开外链、系统钥匙串、单实例、审计日志页 | 逐项在 `:desktop` 里找到落点并有测试或人工走查记录 | 未满足 |
| 2 | KMP 轨有可用的六平台打包流水线 | 连续三个 tag 由 KMP workflow 产出全部平台产物 | 未满足（`desktop-kmp-package.yml` 尚未建立） |
| 3 | 旧数据能被新轨读取，不是让用户手工搬文件 | `LegacyDatabaseImporter` 在真实旧库上跑通一次，且新轨启动时自动导入 | 部分满足（导入器已有实现和测试，缺 UI 入口与自动触发） |
| 4 | 用户迁移路径写明：两轨都没有自动更新，已装 Tauri 版的用户必须手动下载新轨；token 保存位置不同（keyring → prefs），需要重新登录 | release note 模板里固定两段说明 | 未满足 |
| 5 | 双轨并存至少跨越一个 stable 版周期无回归 | release 记录 + issue | 未满足 |
| 6 | 文档、README、CI 引用全部改口 | `git grep -il tauri` 只剩 §4.2 允许保留的历史条目 | 未满足 |

## 3. 时间线（按 tag 推进，不绑具体日期）

- **阶段 0（现在）**：KMP 轨只完成控制台移植，没有打包流水线；桌面端唯一可用形态仍是 Tauri 轨。这一阶段不发布任何 KMP 产物。
- **阶段 1（建流水线）**：新增 `.github/workflows/desktop-kmp-package.yml`，先只开 `workflow_dispatch`；alpha tag 阶段产物只作 artifact。Tauri 轨完全不变。
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

- **本地数据库**：Tauri 端的 SQLite（`sqlite_store.rs`、`store.rs`）就是 `:desktop:data` 的 schema 来源，`LegacyDatabaseImporter` 已经能读它。退役前必须确认新轨首次启动会自动导入旧库，而不是要求用户手工搬文件。
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
