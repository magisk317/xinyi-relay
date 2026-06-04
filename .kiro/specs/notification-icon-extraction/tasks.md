# Implementation Plan: 通知图标提取（Notification Icon Extraction）

## Overview

基于现有 xinyi-relay 架构，沿数据流方向逐层实现 App 图标提取功能：Hook 层提取编码 → IPC 层传递 → 事件模型层透传 → 模板层暴露变量。每层实现后紧跟测试任务，确保增量验证。

## Tasks

- [x] 1. IPC 层常量与数据载体扩展
  - [x] 1.1 在 ForwardBroadcastContract.kt 中添加 EXTRA_APP_ICON 常量
    - 在 `ForwardBroadcastContract` 对象中新增 `const val EXTRA_APP_ICON = "app_icon"`
    - _Requirements: 3.1_

  - [x] 1.2 在 ForwardBroadcastPayload.kt 中扩展 appIcon 字段及解析逻辑
    - 在 `ForwardBroadcastPayload` data class 中新增 `val appIcon: String = ""` 字段
    - 在 `fromIntent()` 中添加 `appIcon = intent.getStringExtra(ForwardBroadcastContract.EXTRA_APP_ICON).orEmpty()` 解析
    - 在 `toRelayEvent()` 中传递 `appIcon = appIcon`
    - 在 `toIntent()` 的 `populatePayload` 调用中添加 `appIcon = appIcon` 参数
    - _Requirements: 3.2, 3.3_

- [x] 2. 事件模型层扩展
  - [x] 2.1 在 RelayEvent.kt 中添加 appIcon 字段
    - 在 `RelayEvent` data class 中新增 `val appIcon: String = ""` 字段（放置于 `phoneArea` 之后）
    - _Requirements: 4.1_

  - [x] 2.2 在 MsgInfo.kt 中添加 appIcon 字段
    - 在 `MsgInfo` data class 中新增 `val appIcon: String = ""` 字段（放置于 `phoneArea` 之后）
    - _Requirements: 4.2_

  - [x] 2.3 在 DispatchPayloadContext.kt 中传递 appIcon 字段
    - 在 `toMsgInfo()` 方法中添加 `appIcon = event.appIcon`
    - _Requirements: 4.3_

  - [x] 2.4 编写 appIcon 数据流透传属性测试
    - **Property 7: appIcon 数据流透传保持**
    - 验证从 Intent Extra 解析经 ForwardBroadcastPayload → RelayEvent → MsgInfo 的完整路径中 appIcon 值保持不变
    - 验证 ForwardBroadcastPayload 的 toIntent() → fromIntent() 往返一致性，appIcon 字段在序列化/反序列化后保持不变
    - 使用 Kotest property testing，生成随机 Base64 字符串验证透传一致性
    - **Validates: Requirements 3.2, 3.3, 4.3**

- [x] 3. 模板层变量注册
  - [x] 3.1 在 MessageFormatter.kt 中注册 APP_ICON 模板变量
    - 在 `variables` map 中添加 `"APP_ICON" to event.appIcon`（放置于 `APP_VERSION` 之后）
    - _Requirements: 5.1, 5.2_

  - [x] 3.2 编写 MessageFormatter 模板变量替换属性测试
    - **Property 8: 模板变量替换正确性**
    - 验证非空 appIcon 时 `{{APP_ICON}}` 被正确替换为实际值，且结果不再包含占位符
    - 验证空 appIcon 时 `{{APP_ICON}}` 被替换为空字符串
    - 使用 Kotest property testing，生成随机字符串验证替换正确性
    - **Validates: Requirements 5.2, 5.3**

  - [x] 3.3 编写 MessageFormatter 单元测试
    - 测试包含 `{{APP_ICON}}` 的模板在有效 appIcon 时正确替换
    - 测试空 appIcon 时 `{{APP_ICON}}` 行被 `removeEmptyValueLines` 移除
    - _Requirements: 5.3, 5.4_

  - [x] 3.4 编写空 appIcon 无副作用属性测试
    - **Property 10: 空 appIcon 对消息格式无副作用**
    - 验证空 appIcon 时模板输出不含 `{{APP_ICON}}` 占位符，仅含 APP_ICON 的行被移除
    - 使用 Kotest property testing，生成随机模板验证空值行为
    - **Validates: Requirements 5.3, 5.4**

- [x] 4. Checkpoint - 确保模型层和模板层测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Hook 层图标提取与编码实现
  - [x] 5.1 在 NotificationManagerHook.kt 中添加 LruCache 缓存字段
    - 添加类级字段 `private val appIconCache = android.util.LruCache<String, String>(64)`
    - _Requirements: 2.1, 7.1_

  - [x] 5.2 实现 encodeDrawableToBase64Png 方法
    - 创建 48×48 ARGB_8888 Bitmap
    - 通过 `setBounds(0, 0, targetSize, targetSize)` 和 `draw(canvas)` 渲染 Drawable
    - 对所有 Drawable 子类型（包括 BitmapDrawable、VectorDrawable、AdaptiveIconDrawable）统一使用 setBounds + draw 处理，无需类型判断
    - 压缩为 PNG 格式（quality=100）并 Base64.NO_WRAP 编码
    - 编码完成后调用 `bitmap.recycle()` 释放内存
    - 使用 try-finally 确保 Bitmap 异常时也能回收
    - _Requirements: 1.2, 1.3, 6.1, 6.2, 7.2, 7.3_

  - [x] 5.3 实现 resolveAppIconBase64 方法
    - 先查询 `appIconCache.get(packageName)`，命中则直接返回
    - 未命中时通过 `pm.getApplicationInfo(packageName, 0)` + `pm.getApplicationIcon(info)` 获取 Drawable
    - 调用 `encodeDrawableToBase64Png(drawable, 48)` 编码
    - 使用 `runNonFatalOrNull` 包裹全部提取逻辑，失败返回空字符串
    - 非空结果写入缓存，空字符串不缓存
    - _Requirements: 1.1, 1.4, 1.5, 2.2, 2.3, 2.5_

  - [x] 5.4 在 handleEnqueueNotificationInternal 中集成图标提取并传递 Intent Extra
    - 在 `pm.getApplicationInfo` 之后调用 `resolveAppIconBase64(pm, pkg)`
    - 将返回值通过 `forwardIntent.putExtra(ForwardBroadcastContract.EXTRA_APP_ICON, appIconBase64)` 写入 Intent
    - _Requirements: 3.1_

  - [x] 5.5 编写 encodeDrawableToBase64Png 属性测试
    - **Property 1: Bitmap 渲染尺寸不变量**
    - 验证生成的 Bitmap 始终为 48×48 像素、ARGB_8888 配置
    - **Property 2: Base64 PNG 编码往返正确性**
    - 验证 Base64 解码再 PNG 解码能得到有效 48×48 Bitmap
    - **Validates: Requirements 1.2, 1.3, 7.2**

  - [x] 5.6 编写 LruCache 行为属性测试
    - **Property 3: 缓存读写一致性**
    - 验证存入后立即读取返回相同字符串
    - **Property 4: 缓存容量不变量**
    - 验证无论插入多少条目，缓存大小不超过 64
    - **Property 5: LRU 淘汰正确性**
    - 验证满容量插入新条目后，最近最少使用的条目被淘汰
    - **Property 6: 空结果不缓存**
    - 验证空字符串不被缓存
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

  - [x] 5.7 编写 Hook 层单元测试
    - 测试 `encodeDrawableToBase64Png` 使用 ColorDrawable 输出合法 Base64 PNG
    - 测试 `resolveAppIconBase64` 在 PackageManager 抛出异常时返回空字符串
    - 测试 AdaptiveIconDrawable 能正确渲染为非空 Base64
    - 测试编码完成后 Bitmap 已 recycle
    - _Requirements: 1.4, 1.5, 6.1, 7.3_

  - [x] 5.8 编写 resolveAppIconBase64 异常不传播属性测试
    - **Property 9: 异常不传播不变量**
    - 使用 Kotest property testing 验证 resolveAppIconBase64 在各种异常情况下（OOM、NPE、SecurityException、NameNotFoundException）都返回空字符串且不抛出异常
    - **Validates: Requirements 1.4, 1.5**

- [x] 6. Final checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- 标记 `*` 的任务为可选测试任务，可跳过以加速 MVP 交付
- 每个任务引用了对应的需求编号以确保可追溯性
- Checkpoint 确保增量验证，避免累积错误
- 属性测试使用 Kotest property testing 模块（`kotest-property`），验证系统通用正确性属性
- 单元测试验证具体的示例和边界情况
- 实现顺序为先扩展下游模型/接口，再实现上游 Hook 层，确保代码编写时类型和接口已就绪

## Task Dependency Graph

> **注：** Wave 4 中 5.1→5.2→5.3→5.4 存在顺序依赖（5.2 使用 5.1 的 LruCache，5.3 调用 5.2 的 encodeDrawableToBase64Png，5.4 调用 5.3 的 resolveAppIconBase64），应按编号顺序执行。

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "2.2"] },
    { "id": 1, "tasks": ["1.2", "2.3"] },
    { "id": 2, "tasks": ["2.4", "3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4"] },
    { "id": 4, "tasks": ["5.1", "5.2", "5.3", "5.4"] },
    { "id": 5, "tasks": ["5.5", "5.6", "5.7", "5.8"] }
  ]
}
```
