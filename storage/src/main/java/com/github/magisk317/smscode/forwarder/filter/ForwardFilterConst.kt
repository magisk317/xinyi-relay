package com.github.magisk317.smscode.forwarder.filter

object ForwardFilterConst {
    const val MSG_TYPE_SMS = "sms"
    const val MSG_TYPE_APP_NOTIFY = "app_notify"

    const val SCOPE_GLOBAL = "global"
    const val SCOPE_PACKAGE = "package"
    const val SCOPE_SENDER = "sender"
    const val SCOPE_ANDROID_CHANNEL = "android_channel"

    const val POLICY_ALLOW = "allow"
    const val POLICY_DENY = "deny"

    const val MATCH_CONTAINS = "contains"
    const val MATCH_REGEX = "regex"

    private const val ANDROID_CHANNEL_SCOPE_DELIMITER = "::"

    fun buildAndroidChannelScopeKey(packageName: String, notifyChannelId: String): String {
        val pkg = packageName.trim()
        val channelId = notifyChannelId.trim()
        if (pkg.isEmpty() || channelId.isEmpty()) return ""
        return "$pkg$ANDROID_CHANNEL_SCOPE_DELIMITER$channelId"
    }

    fun extractNotifyChannelId(scopeKey: String, packageName: String): String {
        val key = scopeKey.trim()
        val pkg = packageName.trim()
        if (key.isEmpty()) return ""
        val prefix = "$pkg$ANDROID_CHANNEL_SCOPE_DELIMITER"
        return if (pkg.isNotEmpty() && key.startsWith(prefix)) {
            key.removePrefix(prefix)
        } else {
            key
        }
    }
}
