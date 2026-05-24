package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.sms.NoopSmsRuntimeBridge
import io.github.magisk317.relay.contract.sms.SmsBlacklistMatchResult
import io.github.magisk317.relay.contract.sms.SmsRuntimeBridge

object XpSmsRuntimeBridge {
    @Volatile
    private var runtimeBridge: SmsRuntimeBridge = NoopSmsRuntimeBridge

    fun installPlatformBridge(bridge: SmsRuntimeBridge?) {
        runtimeBridge = bridge ?: NoopSmsRuntimeBridge
    }

    suspend fun parseSmsCodeIfExists(context: Context, content: String): String {
        return runtimeBridge.parseSmsCodeIfExists(context, content)
    }

    fun matchSmsBlacklist(context: Context, sender: String?, body: String?): SmsBlacklistMatchResult {
        return runtimeBridge.matchSmsBlacklist(context, sender, body)
    }
}
