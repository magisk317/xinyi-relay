# 信驿 Relay

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=io.github.magisk317.xinyi.relay">
        <img src="https://play.google.com/intl/zh-CN/badges/static/images/badges/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://gitlab.com/magisk3171/xinyi-relay/-/releases">
        <img src="https://img.shields.io/badge/Get%20it%20on-GitLab-FC6D26?style=for-the-badge&logo=gitlab&logoColor=white" alt="Get it on GitLab" height="40"/>
    </a>
</div>

<div align="center">

<!-- badges:platform:start -->
[![GitLab](https://img.shields.io/badge/GitLab-magisk3171/xinyi--relay-FC6D26?style=flat-square&logo=gitlab&logoColor=white)](https://gitlab.com/magisk3171/xinyi-relay) [![CI](https://img.shields.io/gitlab/pipeline-status/magisk3171%2Fxinyi-relay?branch=beta&style=flat-square&logo=gitlab&label=CI)](https://gitlab.com/magisk3171/xinyi-relay/-/pipelines?ref=beta) [![Latest Release](https://img.shields.io/gitlab/v/release/magisk3171%2Fxinyi-relay?include_prereleases&style=flat-square&logo=gitlab)](https://gitlab.com/magisk3171/xinyi-relay/-/releases) [![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)
<!-- badges:platform:end -->

<!-- badges:tech:start -->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Java](https://img.shields.io/badge/Java-26%2B-E76F00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.06.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.6.1-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.3.0--rc02-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-26-brightgreen?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-102-orange?style=flat-square)](https://github.com/libxposed/api) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)
<!-- badges:tech:end -->

</div>

信驿 Relay 是一个面向 Xposed/LSPosed 的消息转发与验证码自动填写工具，支持短信、应用通知、来电等来源的统一处理。

项目当前由三大部分组成：

- 手机端 (Android)：负责本地事件采集、验证码解析、自动输入与 Xposed Hook
- 服务端 (Backend)：负责多设备绑定、配置快照、记录上报与云端 Web 控制台
- 桌面端 (Desktop)：跨平台桌面管理工具，内建本地 SQLite 数据库，支持独立离线运行与云端同步

旧内嵌 WebUI 已退出 Android 主运行链，当前正式架构为 `Android Agent + Backend / Desktop` 协同工作。

[English Version](./README-EN.md)

# 应用截图
<img src="./docs/assets/common/01.png" width="720"/>

# 交流与反馈
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)


## 手机端

手机端是面向 Xposed/LSPosed 的 Android 模块，负责本地事件采集、验证码解析与自动填写。

### 安装与使用
1. Root 设备并安装 LSPosed/Xposed 框架；
2. 安装信驿 Relay：
   - GitLab Release：提供 APK 下载
   - Google Play：提供商店分发
3. 激活模块并重启；
4. 在应用内配置转发通道、路由规则、拦截策略与验证码自动填写。

### 兼容性
- 最低 Android 8.0（API 26）。
- 适用于偏原生系统，第三方深度定制 Rom 可能存在兼容性差异。

### 主要能力
- 短信转发：按规则转发验证码短信与普通短信
- 应用通知转发：按应用维度绑定转发通道
- 来电信息转发：提取并转发来电相关信息
- 全局/应用级转发过滤：关键词、来源、优先级等策略控制
- 验证码自动解析、复制与自动填写
- 验证码规则：内置官方只读规则、远程刷新缓存与用户自定义规则分层合并
- 记录与备份：支持导出/导入配置与历史记录



## Backend

Backend 是信驿 Relay 的自建远程控制面，默认部署模式为“本地优先、保留公网能力”。

### 组成
- Go API
- PostgreSQL
- Caddy
- Web 控制台

### 默认部署方式
- Docker Compose 默认直接拉取 Docker Hub 镜像 `docker.io/alpha317/xinyi-relay-backend:beta`
- Android Agent、Web 与 Desktop 共享同一套 Backend API
- 本地 HTTPS 使用 Caddy `tls internal`，可通过用户证书接入 Android Agent

### 入口文档
- [Backend 使用说明](backend/README.md)
- [Backend API 概览](backend/API_OVERVIEW.md)
- [远程架构](docs/ARCHITECTURE.md)
- [Desktop 使用说明](frontend/desktop/README.md)

### 日志位置

Backend 和 Desktop 都支持日志文件输出，便于问题排查：

- **Backend Docker**：`backend/logs/backend.log`（需在 `.env` 中配置 `RELAY_LOG_FILE`）
- **Desktop**：
  - macOS：`~/Library/Logs/io.github.magisk317.relay.desktop/`
  - Windows：`%APPDATA%\io.github.magisk317.relay.desktop\logs\`
  - Linux：`~/.local/share/io.github.magisk317.relay.desktop/logs/`

详见各组件的 README 文档。

## 桌面端 (Desktop)

桌面端是基于 Tauri + Rust 构建的跨平台管理应用（支持 macOS / Windows / Linux）。它不再仅是 Backend 的外壳，而是升级为**全功能客户端**，支持以下三种运行模式：

- **Local（本地模式）**：完全离线运行，使用自带的内置 SQLite 数据库管理设备、配置与历史记录，最大程度保护隐私。
- **Remote（远程模式）**：作为传统的控制台端，直接连接并管理你的独立 Backend 云端实例。
- **Hybrid（混合模式）**：以本地极速响应为主，需要时通过 Sync 协议与 Backend 实例进行双向数据同步。

## Desktop Release 说明

- Desktop Release 默认提供 Linux、macOS 与 Windows 包。
- macOS 当前为 unsigned 发布，首次运行时可能需要用户在系统设置里手动允许。
- Windows 当前使用仓库自管的自签名证书签名；若系统拦截，可先导入公开证书 [frontend/desktop/certs/windows-codesign.cer](frontend/desktop/certs/windows-codesign.cer) 再运行安装包。
- 该 Windows 证书仅用于当前项目的小众分发，不是公有 CA 商业签名证书；请仅在你信任本项目 Release 的前提下导入。

欢迎反馈，欢迎提出意见或建议。



# 文档
- [更新日志 (Changelog)](docs/CHANGELOG.md)
- [自定义消息广播接口 (Custom Broadcast)](modules/runtime/README.md)
- [系统与代码库架构](docs/ARCHITECTURE.md)
- [Backend 使用说明](backend/README.md)
- [Backend API 概览](backend/API_OVERVIEW.md)
- [隐私政策 (Privacy Policy)](docs/PRIVACY.md)
- [赞助与捐赠 (Donations)](docs/DONATIONS.md)

# 感谢
- [XposedSmsCode](https://github.com/tianma8023/XposedSmsCode)
- [LSPosed API](https://github.com/libxposed/api)
- [SmsForwarder](https://github.com/pppscn/SmsForwarder)
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
| ![Alipay](./docs/assets/sponsorship/alipay.png) | ![WeChat Appreciation](./docs/assets/sponsorship/wx.png) | ![WeChat Collect](./docs/assets/sponsorship/wx_collect.png) |

# Star History
![Star History Chart](https://api.star-history.com/svg?repos=magisk317/xinyi-relay&type=Date)
