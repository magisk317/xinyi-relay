# 更新日志 (CHANGELOG)

本日志记录了项目近期的主要变更。

---

## [v0.1.2] - 2026-06-01
- 版本：`versionCode 33` / `versionName 0.1.2`。
- `[core]` 修复通过容灾或文件重新导入短信时，会错误覆盖并清除已有“已转发”状态的问题。
- `[backup]` 完善 WebDAV 与 Google Drive 云备份。支持自定义备份路径、密码显示，补充网络探测，并支持配置变更后真实触发自动备份。
- `[sms-code]` 接入官方验证码规则快照。支持运行时远程刷新，解析遵循“用户优先、官方次之、内置兜底”策略。
- `[scheduled]` 补齐定时短信核心执行链路，新增相关 UI 与 WebUI 配置页，完善启动期权限提示。
- `[sender]` 新增云湖 Bot 通道。Telegram 支持自定义 API Base，Bark 支持 AES 加密。所有发送器表单收敛至统一视图。
- `[desktop]` 桌面端支持本地、远程和混合模式，本地模式接入 SQLite。WebUI 与桌面端共用 API 客户端。
- `[backend]` 增强服务端安全：支持版本化迁移、登录限流、Origin 校验和请求防爆，并增加记录保留期清理逻辑。
- `[diagnostics]` 运行日志按天轮转并统一 JSONL 格式，导出时自动脱敏敏感 Token。App 全局提示 (Snackbar) 改为即时替换。
- `[architecture]` 重构多处核心模块，移除无障碍保活废弃路径，DI 收敛到 Koin。升级 Java 26，补齐发布冲突恢复 CI 工作流。

> Full Changelog: https://github.com/magisk317/xinyi-relay/compare/v0.1.1...v0.1.2

---

## [v0.1.1] - 2026-05-01
- 版本：`versionCode 21` / `versionName 0.1.1`。
- `[desktop]` 新增信驿 Desktop 桌面端初始实现：基于 Tauri + React + Vite 构建，支持 Windows/macOS/Linux，提供配置管理、实时状态刷新、记录查看与发送器编辑能力。
- `[sender]` 发送器能力继续完善：新增发送器生效时段，支持按时间窗口启停；邮件发送器拆分认证身份与对外展示身份，减少多账户或代发场景下的配置耦合；发送器与远程配置入口也同步收口，常用设置路径更直观。
- `[backup]` 备份与导出链路增加发送器配置完整性检查，并兼容旧 JSON 中遗留的 sender 字段与参数格式，恢复时能更稳地保留历史通道配置。
- `[performance]` 优化偏好项读取性能：为 `PrefsReader` 增加 10 秒 TTL 缓存，显著降低高频 Hook 场景下的跨进程或磁盘 IO 开销。
- `[sms-hook]` 增强短信转发稳定性：SmsHandlerHook 引入内存二级去重缓存与非阻塞文件同步，并为短信解析增加严格超时判定；新增可配置短信转发去重窗口、验证码长窗口去重、相同验证码重复自动输入拦截，同时合并 `DELIVER/RECEIVED` 双广播去重并将窗口扩展到 60 秒，压制重复转发、重复填码与通知回环。
- `[runtime]` 新增安全自定义消息广播入口，并将通知门控查询、提醒电量接收等数据库与广播处理移出主线程，降低界面线程阻塞风险。
- `[ui]` 记录详情弹层改为限高滚动布局，长内容场景下浏览与复制更稳定。
- `[maintenance]` 引入自动化 Gradle 维护插件：自动清理主工程、build-logic 及所有子模块的旧版 Gradle 缓存，保持开发环境整洁。
- `[diagnostics]` 增加入站短信 Hook 诊断输出，结合更新后的 `REFACTORING` 文档，排查兼容性与拦截异常时更容易定位问题。
- `[test]` 补充 DataStore 自动修复逻辑、依赖强制规则合并行为等回归测试，提升配置系统与依赖治理脚本的稳健性。
- `[submodule]` 同步更新 `smscode-core` (含 Android 16 兼容性文档) 与 `magisk-ui-kit` 子模块指针。
- `[release/tag]` 发布入口从单一整包 tag 扩展为组件化 tag：`v0.1.1` 继续代表完整发布，另新增 `mobile-v0.1.1`、`desktop-v0.1.1`、`backend-v0.1.1`，分别驱动移动端、桌面端和后端的独立发布链路；`scripts/release_ref.sh` 统一负责 tag 生成、解析与 release kind 判断。
- `[release/script]` `scripts/release_tag.sh` 现支持 `all|mobile|desktop|backend` 目标参数；仅 `all/mobile` 会同步 Android `distribution/whatsnew`、Fastlane changelog 与截图，`desktop/backend` 则跳过移动端元数据同步，避免组件发布被无关校验阻塞。
- `[release/guard]` `scripts/check_release_guard.sh` 已按 release kind 分流：完整版与移动端 tag 继续校验 `CHANGELOG`、`distribution/whatsnew`、Fastlane metadata、截图与 tag/version 一致性；桌面端和后端 tag 则只保留与自身相关的版本和提交卫生检查。
- `[ci/mobile]` Android 发布工作流现同时支持完整 tag 与 `mobile-v*`；GitHub Draft Release、Google Play 轨道选择、Xposed-Modules-Repo 发布、符号包上传与 release title 解析都统一走 `release_ref.sh`，减少脚本和 workflow 各自解析 tag 带来的分叉。
- `[ci/desktop]` Desktop Release 工作流支持 `desktop-v*` 独立发布：若使用完整 tag，会等待移动端 workflow 创建同名 Release 后再补传桌面资产；若使用桌面组件 tag，则直接创建或更新桌面专用 Draft Release，方便桌面端单独迭代。
- `[ci/backend]` Backend GHCR 发布工作流支持 `backend-v*` 独立镜像发布：`beta` 分支继续维护 `beta` 通道，多架构 manifest 仍会在完整 tag 下同步推送 `latest` 与版本标签，而后端组件 tag 会单独生成镜像说明并创建对应 GitHub Release 草稿。
- `[notification]` Telegram 发布通知已按 release kind 切换内容来源：完整版/移动端继续引用 `distribution/whatsnew`，桌面端与后端则改为输出对应组件发布说明，避免沿用移动端文案误导用户。
- `[xposed/build]` 移动端构建继续收敛到 libxposed 单轨：应用依赖与发布资产命名不再围绕 `legacy/api101` 双 flavor 展开，GitHub/Play 产物、AAB 路径、更新检查与 CI 任务名进一步统一，为后续只维护当前主线构建打基础。
- `[ci/fix]` 发布工作流中的 APK artifact 下载名已与上传名重新对齐，避免 Release/Xposed-Modules-Repo 阶段因匹配不到 `apks-github` 而出现“构建成功但发布阶段无资产”的假失败。

## [v0.1.0] - 2026-04-08
- 版本：`versionCode 19` / `versionName 0.1.0`。
- 发布说明：本版本开始正式收敛 `Android Agent + Docker Backend + Web / Desktop` 远程形态，手机端、后端与控制台的职责边界更清晰，本地接入 Docker + Caddy 的使用路径也更稳定。
- `[docker/backend]` Docker 后端 Agent 支持继续完善：Backend 已补齐设备绑定、配置快照、记录上报、配置审计与实时事件能力，Android Agent 对接本地 Docker + Caddy 自签 HTTPS 的路径更顺畅，README、Backend 文档与远程架构文档也已统一按正式架构表述更新。
- `[remote-agent]` Android Agent 远程入口继续完善：高级页新增 Remote Agent 入口，远程控制页交互更顺手；配置快照应用改为先与本地完整配置合并再落地，避免服务端仅下发局部字段时误清空未包含的本地配置。
- `[sms-hook]` 验证码短信在部分 ROM 上即便走 `dispatchSmsDeliveryIntent` 备用拦截链路，也会在 `pref_block_sms` 成功拦截后继续补发 Relay 转发，不再出现“验证码能自动填写但不转发”的路径缺口。
- `[records]` 验证码记录写主库失败时仍会落到 `CodeRecord_*` fallback 文件；应用启动后现在会自动把这批 fallback 记录回灌数据库，修复“验证码记录未出现在界面”的旧问题。
- `[forwarding]` 转发接收侧新增对 telephony NMS 副本的抑制：短信 Hook 已成功转发后，后续系统短信应用的通知副本会被压制，减少验证码和普通短信在记录与转发链路中的重复回环。
- `[ci/build]` Android 37 SDK 兼容方案已从 CI 里的 `android-37 -> android-37.0` 目录 alias workaround 切换为 `compileSdkMinor = 0`，并同步到共享 `build-logic`、`magisk-ui-kit` 与 `smscode-core`；同时移除了已失效的 alias helper，并修复了相机权限对应的 ChromeOS lint 错误。

> Full Changelog: https://github.com/magisk317/xinyi-relay/compare/v0.0.4...v0.1.0

## [v0.0.4] - 2026-04-06
- 版本：`versionCode 17` / `versionName 0.0.4`。
- 发布说明：当前版本开始为后续 `Android Agent + 云端 Backend + Web / Desktop` 新架构做准备，嵌入式 WebUI 配置入口已隐藏，并默认停用本地 WebUI 服务与相关开关。
- `[architecture]` 高级页与嵌入式 WebUI 高级页中的 WebUI 配置入口已隐藏，运行时默认关闭本地 WebUI 开关与 LAN 访问能力，避免旧链路继续自启动。
- `[forwarding]` 验证码短信转发链路继续收敛：分发前统一补齐 SIM 路由，并让已成功的验证码直发路径抑制后续重复广播，`CARD_SLOT` / SIM 备注显示更稳定。
- `[release]` 已同步对齐最新 `beta` 远端提交与共享子模块指针，继续收敛构建基线、发布输入与 CI 拉取一致性。

> Full Changelog: https://github.com/magisk317/xinyi-relay/compare/v0.0.3...v0.0.4

## [v0.0.3]

- 版本升级到 `versionName 0.0.3` / `versionCode 16`。
- Relay 主界面、发送器流程与 WebUI 完成本地化并跟随应用语言，验证码拦截区块标题等细节继续统一，减少中英文混排和重复标题。
- 短信转发与自动输入链路继续加固：补上 SIM 槽位 / `subId` 多级回退与供应商字符串兜底，观察侧调度对齐共享去重逻辑，同时恢复短信优先提升并压制 telephony 通知回环。
- 启动期偏好与配置诊断更稳，`DataStore` 整数项支持从旧字符串值自动恢复；运行日志单文件大小已纳入备份恢复，减少升级后配置丢失或类型不匹配的问题。
- 设置页恢复备份入口重新补回，保活配置拆分更清晰；重复短信记录继续保留转发状态，`CARD_SLOT` 也会显示 SIM 备注，排查和回看更直接。
- 日志导出包统一增加 `relay` 前缀，Android 37 兼容平台与共享 `smscode-core` 同步更新，构建与发布链路的兼容性继续收敛。

## [v0.0.2]

- 版本升级到 `versionName 0.0.2` / `versionCode 15`。
- 继续对齐共享 `smscode-core`：同步最新子模块，收紧 Hook/反射异常边界并清理多处 `TooGenericExceptionCaught` / `DEPRECATION` 风险点，减少不同 flavor 间的实现漂移。
- WebUI 与 xpbridge 拆分为独立模块，工程结构更清晰，后续前端与桥接层迭代成本更低。
- 转发链路继续加固：广播改为先确认再异步派发，验证码自动输入反馈时序优化，转发去重与自动输入重试进一步增强。
- 更新检查按当前 Xposed flavor 匹配 GitHub 发布资产，减少 `legacy` / `api101` 安装包选错概率。
- 设置页数值输入与无障碍说明继续整理，概览页底栏样式也做了简化收尾。

## [v0.0.1]

- 首个稳定版发布，版本号去除 alpha 标记，发布编号升级到 14。
- GitHub Release 恢复 `legacy + api101` 双轨 APK 发布，旧框架用户可继续使用 `legacy` 兼容轨。
- Google Play 保持仅发布 `api101`，避免商店分发与旧框架兼容轨混淆。
- APK 产物、更新选择与 CI 全部补齐 `legacy/api101` 标识，避免设备拿到错误 Xposed API flavor 的安装包。
- 发布链路更新：Google Play 改走 fastlane，GitHub Release 与符号包上传补齐存在性判断，减少 CI 因空产物失败。
- 延续上一轮体验优化：概览卡片编辑模式更顺滑，验证码拦截开关移入实验性功能，远端偏好同步可靠性提升，部分图标语义更一致。

## [v0.0.1-alpha.6]

- 设置开关改为即时生效，并统一提示反馈。
- 配置保存流程重构，减少遗漏与状态不同步。
- 转发过滤相关开关即时生效，降低配置未应用的概率。
- 定时提醒与验证码记录页开关即时生效，体验更一致。
- 文档与赞助名单更新。

## [v0.0.1-alpha.5]

- 修复重启后 `system_server` 注入失败，恢复通话通知链路稳定性。
- 通知事件按消息类型统一去重，降低通话/应用通知重复记录概率。
- 详细日志改为主日志 + 链路分文件双写，并在导出摘要中列出日志文件清单。
- 设置页依赖关系全量收敛：父开关关闭时隐藏子项且保留值。
- 新增详细日志单文件大小配置。

## [v0.0.1-alpha.4]

- 修复 system_server 分发与自动输入链路，提升验证码自动输入稳定性。
- 放宽系统 Hook 匹配条件（兼容 `android/system` 包名与 `system` 进程差异）。
- 新增 ntfy 转发通道（配置页、发送实现、参数清洗与校验、单元测试）。
- 日志标签统一为 `relay`，便于线上问题排查。
- 构建侧依赖工具链小幅更新（Renovate/pnpm 维护项）。

## [v0.0.1-alpha.3]

- 修复模块激活状态判定：优先使用 libxposed service 运行时信号，并保留最近激活回退。
- 清理旧 `getModuleVersion` / `ModuleUtilsHook` 链路，统一到新 API 判定路径。
- 截图资源收敛为单图拼接（通用目录），并放宽发布校验的最少截图数量要求。

## [v0.0.1-alpha.2]

- 切换到 libxposed 新入口模型，并提供兼容 Hook Bridge，降低迁移风险。
- 重构偏好读取链路为可插拔 Source Chain，补齐 Runtime 能力探测与回退路径。
- 发布链路收敛为 libxposed 新 API 单轨工作流，简化发布校验。

## [v0.0.1-alpha]

- 初始 Alpha 版本发布。
- 完成信驿 Relay 独立化（包名与发布链路）。
- 提供短信/通知转发、记录、WebUI 等核心能力。
