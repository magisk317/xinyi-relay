# Privacy Policy / 隐私政策

**Effective Date / 生效日期:** 2026-10-18

[English Version](#privacy-policy-for-信驿-relay) | [中文版本](#信驿-relay-隐私政策)

---

## Privacy Policy for 信驿 Relay

### 1. Introduction
Welcome to 信驿 Relay ("we," "our," or "us"). This policy explains what data is processed by the app, how it is used, and how it is protected.

### 2. What Data Is Processed

#### 2.1 SMS, Notification, and Call Data
信驿 Relay can process:
- SMS content and sender metadata
- App notification title/content/package metadata
- Incoming call metadata (when enabled)

This processing is used only for features you enable, such as relay, filtering, parsing, and autofill.

#### 2.2 Relay Targets You Configure
You can configure relay targets (Webhook, Telegram, Email, etc.).
- Destination addresses and tokens are configured by you.
- Data is sent directly from your device to your configured target endpoint.
- We do not proxy this data through our own server.

#### 2.3 Cross-App Migration Import
To support migration, 信驿 Relay may read export data from compatible local source apps via `ContentProvider` (signature-protected).
- Supported source packages may include:
  - `io.github.magisk317.xinyi.relay`
  - `io.github.magisk317.relay`
- Import happens locally on device.
- Import is only used to migrate your own local settings/rules/records.

### 3. Local Storage and Retention
- App data is stored locally on your device, e.g.:
  - `/data/data/<applicationId>/`
- You can remove data by uninstalling the app or clearing app data in system settings.

### 4. Data Sharing
- We do not sell your personal data.
- We do not operate a mandatory central backend to collect your SMS/notification/call content.
- Data leaves your device only when you explicitly configure and enable relay targets.

#### 4.1 Optional Remote Backend and Console
信驿 Relay now supports an optional architecture based on Android Agent + Backend + Web/Desktop console.
- This architecture is optional and not required for local-only usage.
- If you deploy or bind to a backend, that backend may store:
  - device metadata
  - configuration snapshots
  - relay / record data that you choose to sync
- The project provides self-hosted backend software and container images, but you decide whether to deploy it and where it runs.
- In the current implementation, synced records and configuration may be stored in plaintext on the backend for management and synchronization.

### 5. Technical Diagnostics

信驿 Relay collects limited technical diagnostic data to understand feature usage, keep the app reliable, and diagnose failures. This is enabled by default and can be turned off in the app settings. When turned off, the app stops sending new diagnostic data, and features that rely on it (such as the overview statistics cards) stop displaying it.

When enabled, two kinds of technical diagnostic data may be sent:

- Product-level usage diagnostics (Firebase services provided by Google): a randomly generated per-installation identifier; app version and build environment; device model; operating system version; language and region; coarse activity markers; feature and screen usage; and technical result fields of core operations (for example, relay outcome fields such as sender type, message type, and success/failure).
- Runtime diagnostics (OpenTelemetry spans sent to the project self-hosted observability service): module and hook runtime states, operation stages, and success/failure or reason fields, used for stability and failure diagnosis.

The random installation identifier is not the Android advertising ID and is not intentionally linked to an account, name, phone number, or email address, but its transmission is still data collection.

Technical diagnostic data must not contain SMS or notification content, incoming call numbers, relay rules, relay target addresses or credentials, or any token or secret. The complete installed-app list and usage-history records are not included.

Diagnostic data already transmitted is retained according to the respective service settings and cannot currently be deleted through an in-app control. The local installation identifier remains in private app storage until app data is cleared or the app is uninstalled.

### 6. Security
- Sensitive permissions are used only for declared features.
- Network communication uses standard encrypted transport when supported by your configured endpoints.
- Migration import uses local app-to-app interfaces with restricted permissions.

### 7. Children’s Privacy
This app is not directed to children under 13. We do not knowingly collect personal data from children.

### 8. Policy Updates
This policy may be updated over time. Updated versions will be published in the repository/app resources.

### 9. Contact
If you have questions about this policy, contact:

**Email:** play@usdt.edu.kg

---

## 信驿 Relay 隐私政策

### 1. 简介
欢迎使用 信驿 Relay（以下简称“我们”）。本政策说明应用会处理哪些数据、用途是什么，以及如何保护这些数据。

### 2. 数据处理范围

#### 2.1 短信、通知与来电数据
信驿 Relay 可处理以下信息：
- 短信内容与发送方元数据
- 应用通知标题/内容/包名等元数据
- 来电相关元数据（在你启用时）

以上处理仅用于你主动启用的功能，如转发、过滤、解析与自动填写。

#### 2.2 你自行配置的转发目标
你可以配置转发目标（Webhook、Telegram、邮件等）：
- 目标地址和凭据由你自行配置；
- 数据由你的设备直接发送到你配置的目标端点；
- 我们不会通过自有服务器中转这些数据。

#### 2.3 跨应用迁移导入
为支持迁移，信驿 Relay 可能通过本地 `ContentProvider` 读取兼容来源应用导出的数据；该 Provider 由应用内 caller 自检与系统/特权 caller 判断限制访问。
- 可能支持的来源包名包括：
  - `io.github.magisk317.xinyi.relay`
  - `io.github.magisk317.relay`
- 导入过程在本地设备内完成；
- 仅用于迁移你自己的本地配置/规则/记录。

### 3. 本地存储与保留
- 数据默认保存在设备本地，例如：
  - `/data/data/<applicationId>/`
- 你可通过卸载应用或系统“清除应用数据”删除数据。

### 4. 数据共享
- 我们不会出售你的个人数据；
- 我们不运营一个强制使用的中心化后端来收集短信/通知/来电内容；
- 仅当你明确配置并启用转发目标时，数据才会离开你的设备。

#### 4.1 可选远程 Backend 与控制台
信驿 Relay 当前已支持一个可选架构：Android Agent + Backend + Web/Desktop 控制台。
- 该架构为可选能力，不影响本地单机使用；
- 若你主动启用自建或云端 Backend，后端可能保存：
  - 设备元数据
  - 每台设备的配置 mirror 与配置审计
  - 设备动作指令（铃声/勿扰模式等一次性控制指令）与执行回执、操作方审计
  - 你选择同步的转发/记录数据
- 项目提供自建 Backend 程序与容器镜像，但是否部署、部署到哪里由你自行决定。
- 当前实现下，后端会以明文形式保存你选择同步的记录与配置，用于管理与同步。

### 5. 技术诊断数据

信驿 Relay 会收集有限的技术诊断数据，用于了解功能使用情况、保持应用可靠性和诊断失败原因。该功能默认开启，你可以在应用设置中关闭；关闭后，应用不再发送新的诊断数据，依赖该数据的功能（如总览页统计卡片）也将停止展示。

开启时，可能发送两类技术诊断数据：

- 产品级使用诊断（Google 提供的 Firebase 服务）：随机生成的、按安装区分的标识符；应用版本与构建环境；设备型号；操作系统版本；语言与地区；粗粒度活跃标记；功能与页面使用情况；核心操作的技术结果字段（例如转发结果的发送方类型、消息类型、成功/失败等）。
- 运行时诊断（OpenTelemetry 链路数据，发送至项目自建的可观测性服务）：模块与 hook 的运行时状态、操作阶段、成功/失败与原因字段，用于稳定性与失败诊断。

随机安装标识符不是 Android 广告 ID，也不会被有意关联到账号、姓名、手机号或邮箱，但发送该标识符仍属于数据收集。

技术诊断数据不得包含短信或通知内容、来电号码、转发规则、转发目标地址或凭据，也不包含任何 token 或密钥；不包含完整的已安装应用列表和使用记录。

已经发送的诊断数据按照相应服务的设置保留，目前无法通过应用内入口删除。本地安装标识符会保留在应用私有存储中，直到清除应用数据或卸载应用。

### 6. 安全
- 敏感权限仅用于声明功能；
- 网络通信在目标端支持时使用标准加密传输；
- 迁移导入使用受权限保护的本地应用间接口。

### 7. 儿童隐私
本应用不面向 13 岁以下儿童。我们不会有意收集儿童个人信息。

### 8. 政策更新
本政策可能更新，更新后将发布在仓库和应用资源中。

### 9. 联系方式
如对本政策有疑问，请联系：

**邮箱：** play@usdt.edu.kg
