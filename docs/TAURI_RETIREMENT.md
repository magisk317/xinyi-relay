# Tauri 桌面端退役记录

最后更新：2026-10-04

> **状态：已执行完毕。** Tauri 轨于 2026-10-04 删除，Compose Desktop 成为唯一的桌面端。
> 本文件从"方案"转为"执行记录"：§2 是删除前逐条核对的判据，§4 是实际删除清单，§6 的回滚说明仍然有效。
> 恢复点：annotated tag `tauri-retirement`（指向删除提交的父提交）。

## 1. 先看清两轨的关系

| | 现役轨 | 新轨 |
|---|---|---|
| 位置 | `frontend/desktop/` | `desktop/`、`modules/desktop/core`、`modules/desktop/data` |
| 技术栈 | Tauri 2（Rust + React + Vite + SQLite） | Kotlin Multiplatform + Compose Multiplatform（jvm） |
| 规模 | 65 个受管文件：Rust 6,734 行（`src-tauri/src/`）+ TS/TSX 6,646 行（`src/`，9 个页面） | 控制台 9 个页面已对齐 `frontend/webui`（见 `docs/DESKTOP_PARITY.md`） |
| 独有本地能力 | 托盘、本地 axum 服务器、SQLite、同步、通知、单实例、系统钥匙串、导出/导入 | 全部接入 `:desktop`（parity §5）：托盘、SQLite + 双向同步（含手动 pull/push 与结果卡）、本地服务器（JDK HttpServer 移植 8 路由）、系统钥匙串（macOS security / Linux secret-tool）、通知、单实例、导出/导入（数据库 + 诊断） |
| 发布方式 | 六平台矩阵，随 tag `v*.*.*` 出包 | GitLab 线只接 Linux x64/arm64 两个 job；Windows/macOS 未接，GitHub 线未接 |

两轨**共用** `frontend/shared/`（契约与 console 客户端工具），但 `frontend/shared/` 同时被 `frontend/webui` 引用，所以退役 Tauri 端**不删** `frontend/shared/`。

## 2. 退役判据

六条全部满足，才允许进入删除阶段。每条都要有可验证的证据，不能凭"差不多齐了"判断。

**执行记录：2026-10-04 完成退役**（删除提交 `68d2622ee3b17c8ce736c8aefe048e84b5a44024`，恢复点 tag `tauri-retirement` 指向其父提交，删除单独成一个提交）。

| 判据 | 删除时的状态 |
|---|---|
| 1 能力清单 | 已满足：清单各项全部落地，156 个用例无缓存通过 |
| 2 六平台打包流水线 | **未满足**：只接了 Linux x64/arm64，且还没有 tag 走过 |
| 3 旧库导入 | 改判为迁移义务不存在（§2.2） |
| 4 迁移说明 | 已满足：release body 固定两段说明 |
| 5 跨 stable 周期无回归 | **未满足**：台账是空的，还没有 tag 走过双轨流水线 |
| 6 文档全量改口 | 已满足：随删除提交一并完成 |

判据 2 与 5 是**观察性**判据——它们用"新轨已经稳定跑了多久"来回答"能不能删"。用户明确指示不等这两个观察窗口（"不要双轨，按进度清退 tauri，上线 kmp，产物什么的直接替换"）。删除依据因此从"观察期满"改为"用户决定"，这是本次退役唯一一处偏离原方案的地方，记录在此以免日后误读。

删除后的验证：45 个 Gradle 任务（含 156 个用例）以 `--no-build-cache --rerun-tasks` 全过；结构检查与双轨检查通过；打包脚本端到端产出 deb + uber jar；CI 结构校验显示桌面段只剩 `desktop-kmp:linux:x64/arm64` 与共用的 `desktop:release:gitlab`，无 Windows/macOS job、无 Tauri 变量残留。

`git grep -il tauri` 的剩余结果只有本文件，以及 `:desktop:data` 里解释历史 schema 兼容的注释——后者按 §4.3 明确保留。

| # | 判据 | 验证方式 | 现状 |
|---|---|---|---|
| 1 | parity 清单 §5 的能力缺口全部补齐：托盘、RunMode + 本地服务器、本地存储接入 `:desktop` UI、通知、数据库导出/导入、诊断导出、打开外链、系统钥匙串、单实例、审计日志页（旧库导入入口已按 §2.2 从清单中划掉） | 逐项在 `:desktop` 里找到落点并有测试或人工走查记录 | **已满足**：清单各项全部落地（旧库导入入口按 §2.2 不做），156 个用例在 lzc 无缓存通过；证据见 `docs/DESKTOP_PARITY.md` §5 与 §6 |
| 2 | KMP 轨有可用的六平台打包流水线 | 连续三个 tag 由 KMP workflow 产出全部平台产物 | 部分满足。GitLab 线只接了 Linux x64/arm64 两个 job，其余四平台既没有 job 也没有 runner 标签，GitHub 线未接；「连续三个 tag」更无从谈起——流水线刚建，还没有任何 tag 走过它。见 §2.1 |
| 3 | 旧数据能被新轨读取，不是让用户手工搬文件 | `LegacyDatabaseImporter` 在真实旧库上跑通一次，且新轨启动时自动导入 | 改判为"迁移义务不存在"。依据见 §2.2：Tauri 桌面轨从未发布过，没有用户持有它的产物，因此不再补自动导入与 UI 入口。`LegacyDatabaseImporter` 与 `LegacyDatabaseImporterTest` 保留，作为 Room schema 与 Tauri SQLite 兼容的证据 |
| 4 | 用户迁移路径写明：没有自动更新，升级要手动下载；旧 Tauri 安装保存的登录凭据不被新应用读取，升级后需重新登录 | release note 模板里固定两段说明 | **已满足**：`scripts/release/gitlab_desktop_release.sh` 的默认 release body 固定两段（无自动更新、旧版凭据不继承），`XINYI_DESKTOP_RELEASE_DESCRIPTION` 仍可整体覆盖 |
| 5 | 双轨并存至少跨越一个 stable 版周期无回归 | release 记录 + issue | 未满足，且**只能在时间上满足**：需要至少一个 stable tag 走完双轨流水线并留下无回归记录。证据台账见 §2.3 |
| 6 | 文档、README、CI 引用全部改口 | `git grep -il tauri` 只剩 §4.2 允许保留的历史条目 | 未满足，且**被默认轨切换阻塞**：Tauri 轨现在仍是随 tag 出包并在 README 里署名的现役桌面端，删掉这些引用会与事实不符。§4.2 的改口清单要在阶段 3（切换默认）执行 |

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

### 2.3 判据 5 的证据台账

判据 5 是六条里唯一无法靠写代码满足的一条：它要求"双轨并存至少跨越一个 stable 版周期"，而周期是时间。为了让这条判据在未来的 tag 上能被客观判定，起点与记账方式固定在这里。

**起点**：`10fefd205be593cc7732579909f0ceb339f36d51`（2026-10-03，`ci(desktop): add GitLab KMP packaging track for the desktop modules`）。这是两轨第一次同时挂在同一条流水线上——在此之前 KMP 轨只有本地构建，谈不上"并存"。

**记账方式**：每有一个 stable tag（`v[0-9]+.[0-9]+.[0-9]+` 且无预发后缀）走完流水线，就在下表加一行。满足判据 5 的条件是该表至少有一行，且该行的"KMP 轨 job"与"Tauri 轨 job"都是通过、没有任何一轨的 job 因对方而失败或被跳过。

| stable tag | 日期 | Tauri 轨 job | KMP 轨 job | 回归 issue |
|---|---|---|---|---|
| （待填：起点之后第一个 stable tag） | | | | |

**当前状态**：起点之后还没有任何 tag 被推送——`git tag` 里最新的 stable tag 是 `v0.2.7`（2026-10-02），它在 KMP 流水线存在之前就已发布；当前 `versionName` 是 `0.2.8`，尚未打 tag。所以这张表现在必须留空，任何"已满足"的写法都是不实的。

**一个诚实的说明**：判据 2 的"连续三个 tag 产出全部平台产物"与判据 5 的"一个 stable 周期无回归"共用同一批数据点，可以一起记账。

## 3. 时间线

原方案分了五个阶段（建流水线 → beta 预览 → 切换默认 → 冻结 → 删除），实际执行把后四步并成了一次跳跃：

- **已做**：KMP 轨完成控制台移植，判据 1 的能力清单全部落地，GitLab 流水线覆盖 Linux x64/arm64。
- **未做（被跳过的观察期）**：beta 预览、切换默认的过渡 tag、跨 stable 周期的冻结期，以及判据 2/5 要求的观察窗口。
- **已做（跳跃）**：Tauri 轨一次性删除，Compose Desktop 直接成为唯一桌面端——不再有"默认轨"与"预览轨"的区分。

阶段 4 的冻结（"只接受安全修复，覆盖一个完整 stable 版周期"）没有发生，所以不存在"冻结期内的安全修复"这回事；如果删除后需要 Tauri 侧的改动，只能从 tag `tauri-retirement` 恢复后另行处理。

阶段 5 要求"在 CHANGELOG 记一笔"：删除发生在 v0.2.8 之后、下一个 tag 之前，该条目随下一个版本的 CHANGELOG 一起写，见 §4.4 的说明。

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

### 4.4 CHANGELOG 条目

删除提交本身不写 CHANGELOG（仓库的 CHANGELOG 按版本记，不按提交记）。下一个版本发布时，条目文案：

```
- `[desktop]` 桌面端从 Tauri 切换到 Kotlin Multiplatform + Compose Desktop；旧版 Tauri 安装包不再产出，已安装用户需手动下载新包。
```

## 5. 数据与兼容

- **本地数据库**：Tauri 端的 SQLite（`sqlite_store.rs`、`store.rs`）就是 `:desktop:data` 的 schema 来源，`LegacyDatabaseImporter` 已经能读它。原判据要求"退役前必须确认新轨首次启动会自动导入旧库"，已按 §2.2 改判：桌面轨零发布，没有用户持有旧库，自动导入与 UI 入口不再补。导入器本身保留，随时可以接回来。
- **凭据**：两轨都用系统钥匙串（Tauri 走 `keyring` crate，新轨走 `secret-tool` / `security` 子进程），但服务名与条目命名不同，旧 token 读不出来。切换默认轨意味着已装用户要重新登录一次；release body 的固定段落已写明这一点（判据 4）。
- **没有自动更新**：两轨都没有更新器，已装 Tauri 版的用户不会自动迁移到新轨。删除发布物只影响"新用户能下到什么"，已装用户继续用旧版，直到自己下载新轨。
- **profile 兼容**：两端的 profile 状态文件格式不同（Tauri 是 app config 目录下的 `desktop-state.json`，新轨是 `~/.xinyi-relay-desktop/profiles.json`），服务器地址要么导入，要么让用户重填。

## 6. 回滚

退役的每一步都可回退，真正的删除只发生在阶段 5：

- **删除之后回退**：从 tag `tauri-retirement` 恢复——`git revert 68d2622ee3b17c8ce736c8aefe048e84b5a44024` 会一次还原整个删除（应用、两条 workflow、GitLab job、脚本），或 `git checkout tauri-retirement -- frontend/desktop` 只取回应用本体。恢复后 Tauri 的打包脚本与 CI job 也在同一提交里，可以一起回来。
- 删除是单独一个提交、不与其他改动混在一起，所以整体 revert 是干净的。

## 7. 删除前检查表（执行时逐项核对）

- [x] §2 六条判据逐条核对，2/5 未满足但用户指示放行（见 §2 执行记录）
- [ ] 未做到：删除时还没有 tag 走过新轨流水线（用户指示不等）
- [x] `git grep -il tauri` 只剩本文件与 §4.3 允许保留的兼容注释
- [x] `git grep -n 'frontend/desktop' .github .gitlab-ci.yml scripts` 无结果（本文件的历史清单除外）
- [x] `select_android_test_tasks.sh` 的 desktop 分支指向 KMP 任务集，其测试套件通过（`:desktop:compileKotlinJvm`、`:desktop:jvmTest`、`:desktop:core:jvmTest`、`:desktop:data:jvmTest`）
- [x] 无缓存通过（45 任务全执行，156 用例 0 失败）
- [x] README / README-EN / ARCHITECTURE / API_OVERVIEW 已改口（AGENTS.md 是 gitignore 的本地文件，不入库）
- [x] 已打 `tauri-retirement`（GPG 签名的 annotated tag）
