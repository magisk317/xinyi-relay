# 信驿 Relay

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=io.github.magisk317.relay">
        <img src="https://play.google.com/intl/zh-CN/badges/static/images/badges/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/magisk317/xinyi-relay/releases">
        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
</div>

<div align="center">

[![Commits](https://img.shields.io/github/commit-activity/y/magisk317/xinyi-relay?style=flat-square)](https://github.com/magisk317/xinyi-relay/graphs/commit-activity) [![Last Commit](https://img.shields.io/github/last-commit/magisk317/xinyi-relay?style=flat-square)](https://github.com/magisk317/xinyi-relay/commits) [![Contributors](https://img.shields.io/github/contributors/magisk317/xinyi-relay?style=flat-square)](https://github.com/magisk317/xinyi-relay/graphs/contributors) [![CI](https://img.shields.io/github/actions/workflow/status/magisk317/xinyi-relay/ci.yml?branch=beta&style=flat-square&label=Build&logo=github-actions&logoColor=white)](https://github.com/magisk317/xinyi-relay/actions/workflows/ci.yml) [![Latest Release](https://img.shields.io/github/v/release/magisk317/xinyi-relay?include_prereleases&style=flat-square&logo=github)](https://github.com/magisk317/xinyi-relay/releases) [![Release Date](https://img.shields.io/github/release-date/magisk317/xinyi-relay?style=flat-square)](https://github.com/magisk317/xinyi-relay/releases) [![Downloads](https://img.shields.io/github/downloads/magisk317/xinyi-relay/total?style=flat-square&color=blue)](https://github.com/magisk317/xinyi-relay/releases) [![License](https://img.shields.io/github/license/magisk317/xinyi-relay?style=flat-square)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.04.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.5.0--nightly-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.2.0-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-26-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-101-orange?style=flat-square)](https://github.com/rovo89/XposedBridge) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)

</div>

信驿 Relay 是一个面向 Xposed/LSPosed 的消息转发与验证码自动填写工具，支持短信、应用通知、来电等来源的统一处理。

项目当前由两部分组成：

- 手机端：负责短信、通知、来电、自动输入与 Xposed Hook
- Backend：负责设备绑定、配置快照、记录上报，以及 Web / Desktop 远程控制台

旧内嵌 WebUI 已退出 Android 主运行链，当前正式架构为 `Android Agent + Backend + Web / Desktop`。

[English Version](./README-EN.md)

# 应用截图
<img src="./art/common/01.png" width="720"/>

# 交流与反馈
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)


## 手机端

手机端是面向 Xposed/LSPosed 的 Android 模块，负责本地事件采集、验证码解析与自动填写。

### 安装与使用
1. Root 设备并安装 LSPosed/Xposed 框架；
2. 安装信驿 Relay，按框架版本选择合适 APK：
   - GitHub Release：提供 `legacy` 与 `api101`
   - Google Play：仅提供 `api101`
3. 激活模块并重启；
4. 在应用内配置转发通道、路由规则、拦截策略与验证码自动填写。

### 兼容性
- **最低 Android 8.0（API 26），Target SDK 37。**
- **适用于偏原生系统，第三方深度定制 Rom 可能存在兼容性差异。**
- **代码库：100% Kotlin + Jetpack Compose + Room + Coroutines。**

### 主要能力
- 短信转发：按规则转发验证码短信与普通短信
- 应用通知转发：按应用维度绑定转发通道
- 来电信息转发：提取并转发来电相关信息
- 全局/应用级转发过滤：关键词、来源、优先级等策略控制
- 验证码自动解析、复制与自动填写
- 记录与备份：支持导出/导入配置与历史记录

### 自定义消息广播接口
- 首版开放安全广播入口：`io.github.magisk317.relay.ACTION_INGEST_CUSTOM_MESSAGE`
- 必填参数：
  - `ipc_token`
  - `message`
- 选填参数：
  - `title`
  - `app_name`
  - `package_name`
  - `notify_channel_id`
  - `event_id`
  - `target_sender_ids`（`long[]`，仅向指定 senderId 列表分发）
- 示例：

```bash
adb shell am broadcast \
  -a io.github.magisk317.relay.ACTION_INGEST_CUSTOM_MESSAGE \
  -n io.github.magisk317.xinyi.relay/io.github.magisk317.relay.platform.ipc.CustomMessageReceiver \
  --es ipc_token YOUR_IPC_TOKEN \
  --es title "自定义消息" \
  --es message "Hello from adb" \
  --es app_name "ADB" \
  --es package_name "com.example.custom"
```

## Backend

Backend 是信驿 Relay 的自建远程控制面，默认部署模式为“本地优先、保留公网能力”。

### 组成
- Go API
- PostgreSQL
- Caddy
- Web 控制台
- Tauri Desktop 桌面壳

### 默认部署方式
- Docker Compose 默认直接拉取 GHCR 镜像 `ghcr.io/magisk317/xinyi-relay-backend:beta`
- Android Agent、Web 与 Desktop 共享同一套 Backend API
- 本地 HTTPS 使用 Caddy `tls internal`，可通过用户证书接入 Android Agent

### 入口文档
- [Backend 使用说明](backend/README.md)
- [Backend API 概览](backend/API_OVERVIEW.md)
- [远程架构](docs/REMOTE_ARCHITECTURE.md)

欢迎反馈，欢迎提出意见或建议。

# 发布元数据维护
- Fastlane 元数据目录：`fastlane/metadata/android`
- 发版前同步 Fastlane 更新日志与截图：`scripts/sync_fastlane_metadata.sh`
- 发版前校验版本与发布元数据：`scripts/check_release_guard.sh`
- Fastlane 的 `changelogs/{versionCode}.txt` 由 `distribution/whatsnew` 自动同步生成。

# 代码库说明
- 主工程入口始终以仓库根目录为准。
- `smscode-core` 作为内嵌共享库子模块参与构建，不作为日常开发的主构建根工程。
- 运行时分层与模块边界说明见 [架构与运行时重构说明](docs/REFACTORING.md)。

# 文档
- [更新日志 (Changelog)](docs/CHANGELOG.md)
- [架构与运行时重构说明](docs/REFACTORING.md)
- [远程架构 (Remote Architecture)](docs/REMOTE_ARCHITECTURE.md)
- [Backend 使用说明](backend/README.md)
- [Backend API 概览](backend/API_OVERVIEW.md)
- [隐私政策 (Privacy Policy)](docs/PRIVACY.md)
- [赞助与捐赠 (Donations)](docs/DONATIONS.md)

# 感谢
- [原始项目 (tianma8023/XposedSmsCode)](https://github.com/tianma8023/XposedSmsCode)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Material Dialogs](https://github.com/afollestad/material-dialogs)
- [EventBus](https://github.com/greenrobot/EventBus)
- [Room](https://developer.android.com/training/data-storage/room)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)


# 协议
所有源码遵循 [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt) 协议。

# 赞助与捐赠
如果本项目对你有帮助，欢迎支持开发者。你的支持会直接用于项目维护与持续迭代。

赞助名单与说明请见：[赞助与捐赠文档](docs/DONATIONS.md)。

| 支付宝收款码 | 微信赞赏码 | 微信收款码 |
| :---: | :---: | :---: |
| ![Alipay](./art/sponsorship/alipay.png) | ![WeChat Appreciation](./art/sponsorship/wx.png) | ![WeChat Collect](./art/sponsorship/wx_collect.png) |

# Star History
![Star History Chart](https://api.star-history.com/svg?repos=magisk317/xinyi-relay&type=Date)
