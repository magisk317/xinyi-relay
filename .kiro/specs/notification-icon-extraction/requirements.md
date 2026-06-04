# Requirements Document

## Introduction

为 xinyi-relay Xposed 模块增加通知来源应用图标提取能力。在通过 Webhook/HTTP API 转发通知时，将来源 App 的图标以 Base64 PNG 字符串形式嵌入，通过 `{{APP_ICON}}` 模板变量供用户在自定义消息模板中使用。此功能面向 Quote/0 墨水屏等设备，其 Text API 支持 `icon` 字段接收 Base64 PNG 数据。

## Glossary

- **Icon_Extractor**: 运行在 system_server 进程中的图标提取组件，负责通过 PackageManager 获取应用图标、绘制到 Bitmap 并编码为 Base64 PNG 字符串
- **Icon_Cache**: 基于 LruCache 的应用图标编码缓存，以 packageName 为键存储 Base64 编码结果，避免重复编码
- **Forward_Payload**: 通知转发过程中通过 Intent Extra 和事件模型传递的数据载体，包含通知正文、发送者、包名等信息
- **Message_Formatter**: 消息模板引擎，负责将模板变量（如 `{{APP_ICON}}`）替换为实际值后输出最终转发内容
- **App_Icon**: 通过 PackageManager.getApplicationIcon() 获取的应用启动器图标 Drawable，经缩放和 PNG 编码后的 Base64 字符串表示
- **Template_Variable**: 消息模板中以 `{{NAME}}` 格式定义的占位符，在转发时由 Message_Formatter 替换为实际值

## Requirements

### Requirement 1: 图标提取

**User Story:** 作为 Xposed 模块开发者，我希望从通知来源应用的 PackageManager 中提取应用图标，以便将图标数据传递给下游转发流程。

#### Acceptance Criteria

1. WHEN a notification is intercepted by the hook, THE Icon_Extractor SHALL retrieve the application icon via PackageManager.getApplicationIcon() using the package name from the intercepted StatusBarNotification (sbn.packageName)
2. WHEN the application icon Drawable is retrieved, THE Icon_Extractor SHALL render the Drawable onto a 48×48 pixel ARGB_8888 Bitmap
3. WHEN the Bitmap is rendered, THE Icon_Extractor SHALL compress the Bitmap to PNG format and encode the result as a Base64 string with NO_WRAP flag
4. IF PackageManager.getApplicationIcon() throws an exception, THEN THE Icon_Extractor SHALL return an empty string without interrupting the notification forwarding flow
5. IF the Drawable rendering or PNG compression fails, THEN THE Icon_Extractor SHALL return an empty string without interrupting the notification forwarding flow

### Requirement 2: 图标编码缓存

**User Story:** 作为 Xposed 模块开发者，我希望缓存已编码的应用图标 Base64 字符串，以避免在 system_server 进程中高频通知场景下重复进行图标编码操作导致 GC 压力和性能下降。

#### Acceptance Criteria

1. THE Icon_Cache SHALL store encoded Base64 icon strings keyed by package name with a maximum capacity of 64 entries
2. WHEN the Icon_Extractor receives a request for a package icon, THE Icon_Cache SHALL return the cached Base64 string if the package name exists in the cache
3. WHEN the Icon_Extractor successfully encodes an icon to a non-empty Base64 string, THE Icon_Cache SHALL store the result for the corresponding package name
4. WHEN the Icon_Cache reaches its maximum capacity of 64 entries, THE Icon_Cache SHALL evict the least recently used entry to make room for new entries
5. IF the icon extraction returns an empty string, THEN THE Icon_Cache SHALL NOT store the empty result

### Requirement 3: IPC 数据传递

**User Story:** 作为模块运行时组件，我希望图标数据能通过 Intent Extra 从 system_server 进程传递到模块应用进程，以便后续转发流程能访问图标数据。

#### Acceptance Criteria

1. WHEN the hook constructs the forward Intent, THE Forward_Payload SHALL include the Base64 icon string as an Intent Extra with key "app_icon"
2. WHEN ForwardBroadcastPayload parses the received Intent, THE Forward_Payload SHALL extract the "app_icon" extra into an appIcon field, defaulting to empty string if absent
3. WHEN ForwardBroadcastPayload converts to RelayEvent, THE Forward_Payload SHALL pass the appIcon value to the RelayEvent instance

> **Note:** ForwardBroadcastPayload.toIntent() 在重传场景下也会将 appIcon 写入 "app_icon" extra（反向路径），此行为由 toIntent() 框架方法隐式支持，无需额外处理。

### Requirement 4: 事件模型传递

**User Story:** 作为消息引擎，我希望 appIcon 字段能在 RelayEvent、MsgInfo 等事件模型中透传，以便模板引擎在格式化消息时可以访问图标数据。

#### Acceptance Criteria

1. THE RelayEvent data class SHALL include an appIcon field of type String with default value of empty string
2. THE MsgInfo data class SHALL include an appIcon field of type String with default value of empty string
3. WHEN DispatchPayloadContext converts a RelayEvent to MsgInfo, THE DispatchPayloadContext SHALL copy the appIcon field from RelayEvent to MsgInfo

### Requirement 5: 模板变量暴露

**User Story:** 作为用户，我希望在自定义消息模板中使用 `{{APP_ICON}}` 变量获取通知来源应用的图标 Base64 数据，以便在 Quote/0 等设备上显示应用图标。

#### Acceptance Criteria

1. THE Message_Formatter SHALL register "APP_ICON" as a template variable mapped to the appIcon field value from the RelayEvent
2. WHEN a message template contains `{{APP_ICON}}` placeholder, THE Message_Formatter SHALL replace the placeholder with the corresponding Base64 PNG string
3. WHEN the appIcon field is empty, THE Message_Formatter SHALL replace `{{APP_ICON}}` with an empty string, resulting in no visible content at that position
4. WHEN the Message_Formatter removes empty-value lines, THE Message_Formatter SHALL treat lines containing only the APP_ICON key with an empty value the same as other empty-value lines

### Requirement 6: AdaptiveIconDrawable 兼容

**User Story:** 作为 Xposed 模块开发者，我希望图标提取能正确处理 Android 8.0+ 的 AdaptiveIconDrawable，以确保自适应图标也能被正确渲染为 PNG。

#### Acceptance Criteria

1. WHEN the retrieved Drawable is an AdaptiveIconDrawable (Android 8.0+), THE Icon_Extractor SHALL correctly render the adaptive icon layers onto the target Bitmap via setBounds and draw
2. WHEN the retrieved Drawable is a standard BitmapDrawable or VectorDrawable, THE Icon_Extractor SHALL correctly render it onto the target Bitmap via setBounds and draw

### Requirement 7: 内存占用控制

**User Story:** 作为 Xposed 模块开发者，我希望图标缓存的内存占用可控，以避免对 system_server 进程的稳定性造成影响。

#### Acceptance Criteria

1. THE Icon_Cache SHALL limit total memory usage to 不超过 320KB（64 entries × ~5KB per entry 上限）under maximum capacity
2. THE Icon_Extractor SHALL use a fixed target size of 48×48 pixels for Bitmap rendering, producing approximately 3-5KB of Base64 encoded data per icon
3. WHEN a Bitmap is no longer needed after encoding, THE Icon_Extractor SHALL call recycle() on the Bitmap to promptly release native memory
