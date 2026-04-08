# 更新日志 (CHANGELOG)

本日志记录了项目近期的主要变更。

---

## [Unreleased]
- 待下个版本继续补充。

## [v0.1.0] - 2026-04-08
- 版本：`versionCode 18` / `versionName 0.1.0`。
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
