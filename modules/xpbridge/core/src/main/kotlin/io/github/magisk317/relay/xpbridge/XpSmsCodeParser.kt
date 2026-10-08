package io.github.magisk317.relay.xpbridge

import android.content.Context

object XpSmsCodeParser {
    suspend fun parseSmsCodeIfExists(context: Context, content: String): String {
        return XpSmsRuntimeBridge.parseSmsCodeIfExists(context, content)
    }
}
