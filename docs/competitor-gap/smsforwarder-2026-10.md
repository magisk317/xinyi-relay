# 竞品差距清单：SmsForwarder vs xinyi-relay（2026-10-17 快照）

> 调研触发：竞品 issue [#776](https://github.com/pppscn/SmsForwarder/issues/776)「远程指令控制手机关闭静音和勿扰状态」（2026-10-17 提交，label `help wanted`，无评论未排期）。
> 信息源：GitHub API（README main + issues 776/554/614/466/64），拉取日期 2026-10-17。xinyi 侧数据全部来自本仓库实查（HEAD = f957f681）。

## 1. 竞品功能面（SmsForwarder v3.x）

| 能力 | 细节 |
|---|---|
| 采集 | 短信 / 来电 / APP 通知（普通 receiver 通道，无 root/Xposed） |
| 转发通道 | 钉钉群机器人、钉钉企业内、企微群机器人、企微应用、飞书群机器人、飞书企业应用、邮箱、bark、webhook、Telegram、Server酱、PushPlus、手机短信（13 类） |
| 远程控制（v3.0） | 远程发短信、查短信、查通话、查话簿、查电量 |
| 自动任务・快捷指令（v3.3） | 本地自动化（issue 554 已支持震动动作；614 bark 重要警告已闭环） |
| 设备控制 | **无**（776 所请，help wanted 挂着） |

## 2. 逐项对照

| 维度 | SmsForwarder | xinyi-relay | 判定 |
|---|---|---|---|
| 短信采集 | receiver | Xposed hook（SmsHandlerHook/SmsProviderHook）+ 黑名单系统级拦截 + 收件箱 observer | xinyi 领先 |
| MMS | 通知栏解析 | Xposed hook + `MmsNotificationPduParser`（APK 内置可测）+ Shizuku ContentProvider | 领先 |
| 通知 / 来电 | 有 | 有（hook 增强 + ended-call enrichment） | 持平 |
| 发送通道 | 13 类 | dingtalk / wecom / feishu / bark / webhook / smtp / telegram / matrix（e2ee） | 数量少；e2ee 通道独占 |
| 远程指令框架 | 有（发短信/查短信/查通话/查话簿/查电量） | **无** | **P0 差距** |
| 设备控制（静音/勿扰） | 无（776 待做） | **无**，但执行底座最强（root shell + Xposed + Shizuku） | 机会面 |
| 自动任务 / 快捷指令 | v3.3 | `mobile/feature/scheduled` 定时发送（SenderActiveSchedule/ScheduledTask） | 部分覆盖 |
| 云端 | 自有服务端 | Go backend + WebUI console + 设备绑定（bindCode/deviceToken）+ 配置同步（revision+mutation+ACK）+ 记录上传 | 领先 |
| 桌面端 | 无 | Compose Desktop（KMP 单轨） | 领先 |
| root / Xposed | 无依赖 | 全家桶（FeatureGate 门控，root-only 子集需 root 叠加） | 领先 |

## 3. 结论

1. **P0 — 远程指令框架 + 设备控制（776 场景）**：竞品已验证需求（v3.0 起重投入；776 是延伸）。xinyi 已有通道底座（AgentApi `pendingCommands` / ACK 状态机）和执行底座（`RootShellExecutor` / Xposed / Shizuku / `PermissionBridge`），缺的是指令类型定义与执行封装。竞品做不动设备控制的根因（纯 app 权限墙）在 xinyi 不存在——这是**反向超车点**。
2. **P1 — 远程查询类**（查短信/通话/话簿/电量）：可复用 root 读系统 DB 的既有能力（`RootDbCatchupEngine` 已有 sqlite3 先例），封装为查询 action。
3. **P1 — 快捷指令/自动任务扩展**：xinyi 已有 scheduled 骨架，可对齐竞品 v3.3 动作集。
4. **P2 — 发送通道数量补齐**：价值最低，xinyi 通道少而精（e2ee），不建议追数量。

## 4. 后续

设备控制指令 + root-first 权限引导的落地计划见同目录 `../plans/device-control-and-permission-onboarding.md`。
