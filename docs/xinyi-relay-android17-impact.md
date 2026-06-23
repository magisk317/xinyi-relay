# xinyi-relay Android 17 影响分析

> 项目：xinyi-relay (io.github.magisk317.xinyi.relay)
> targetSdk：37
> 分析日期：2026-06-17

---

## 总览

| 影响等级 | 数量 |
|---------|------|
| 🔴 严重 | 1 |
| ⚠️ 高 | 2 |
| ⚠️ 中 | 2 |
| ⚠️ 低 | 2 |

---

## 🔴 严重问题

### 1. SMS_RECEIVED_ACTION 3 小时延迟

**文件**: 
- `smscode/core/verification/src/main/java/io/github/magisk317/smscode/verification/SmsIntentHookSupport.kt:16`
- `modules/hook/entry/src/main/java/io/github/magisk317/relay/xp/hook/forward/SmsForwardHook.kt:169`

```kotlin
// SmsIntentHookSupport.kt
action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION

// SmsForwardHook.kt
action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
```

**影响**: Android 17 对 OTP 短信施加 3 小时延迟。如果 xinyi-relay 依赖 `SMS_RECEIVED_ACTION` 广播来转发短信，可能会受到影响。

**修复方案**:
- 确认是否使用此广播读取/转发 OTP
- 如是，迁移到 SMS Retriever 或 SMS User Consent API
- 或使用 LSPosed hook 绕过延迟（Xposed 模块可能不受限制）

---

## ⚠️ 高风险问题

### 2. 应用内存限制

**影响**: Android 17 基于设备总 RAM 限制应用内存。

**修复方案**:
- 建立内存基准
- 在受限环境中测试
- 使用 `adb shell am memory-limiter` 调试

### 3. 后台音频安全加固

**影响**: 后台音频 API 调用会静默失败。

**当前状态**: 项目未发现后台音频播放。

**修复方案**: ✅ 无需修改。

---

## ⚠️ 中风险问题

### 4. PendingIntent 使用

**文件**: 
- `smscode/core/verification/src/main/java/io/github/magisk317/smscode/verification/CodeNotificationDeliveryHelper.kt`
- `smscode/core/verification/src/main/java/io/github/magisk317/smscode/verification/CodeNotificationPayload.kt`
- `smscode/core/verification/src/main/java/io/github/magisk317/smscode/verification/CodeNotificationActionPayload.kt`

**当前状态**: 使用 `PendingIntent.FLAG_IMMUTABLE`，符合最佳实践。

**修复方案**: ✅ 无需修改。

### 5. 内部文件写入权限

**文件**: `modules/relay/android/src/main/java/io/github/magisk317/relay/android/prefs/AppPreferencesDataStore.kt`

**影响**: Android 17 可能对内部文件写入有更严格的限制。

**修复方案**: 
- 验证 `setBoolean()`/`setInt()` 在 Android 17 上的行为
- 确保文件操作符合 Scoped Storage 要求

---

## ⚠️ 低风险问题

### 6. 隐式 URI 授权限制（Android 18）

**当前状态**: 项目未发现使用 `ACTION_SEND`/`ACTION_SEND_MULTIPLE`/`ACTION_IMAGE_CAPTURE`。

**修复方案**: ✅ 无需修改。

### 7. 每个应用的密钥库限制

**当前状态**: 项目未发现大量密钥创建。

**修复方案**: ✅ 无需修改。

---

## 建议优先级

1. **立即处理**: 验证 SMS_RECEIVED_ACTION 在 Xposed 环境下的行为
2. **短期处理**: 内存限制测试
3. **中期处理**: 验证内部文件写入权限
