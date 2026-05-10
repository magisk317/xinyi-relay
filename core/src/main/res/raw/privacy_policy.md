# Privacy Policy / 隐私政策

**Effective Date / 生效日期:** 2026-03-06

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
  - `/data/data/io.github.magisk317.relay/`
- You can remove data by uninstalling the app or clearing app data in system settings.

### 4. Data Sharing
- We do not sell your personal data.
- We do not run a backend service to collect your SMS/notification/call content.
- Data leaves your device only when you explicitly configure and enable relay targets.

### 5. Security
- Sensitive permissions are used only for declared features.
- Network communication uses standard encrypted transport when supported by your configured endpoints.
- Migration import uses local app-to-app interfaces with restricted permissions.

### 6. Children’s Privacy
This app is not directed to children under 13. We do not knowingly collect personal data from children.

### 7. Policy Updates
This policy may be updated over time. Updated versions will be published in the repository/app resources.

### 8. Contact
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
  - `/data/data/io.github.magisk317.relay/`
- 你可通过卸载应用或系统“清除应用数据”删除数据。

### 4. 数据共享
- 我们不会出售你的个人数据；
- 我们没有用于收集短信/通知/来电内容的后端服务；
- 仅当你明确配置并启用转发目标时，数据才会离开你的设备。

### 5. 安全
- 敏感权限仅用于声明功能；
- 网络通信在目标端支持时使用标准加密传输；
- 迁移导入使用受权限保护的本地应用间接口。

### 6. 儿童隐私
本应用不面向 13 岁以下儿童。我们不会有意收集儿童个人信息。

### 7. 政策更新
本政策可能更新，更新后将发布在仓库和应用资源中。

### 8. 联系方式
如对本政策有疑问，请联系：

**邮箱：** play@usdt.edu.kg
