package io.github.magisk317.relay.contract.sms

import android.content.Context

data class SmsBlacklistMatchResult(
    val matched: Boolean,
    val matchType: String? = null,
    val pattern: String? = null,
    val actionDelete: Boolean = false,
    val actionBlock: Boolean = false,
)

interface SmsRuntimeBridge {
    suspend fun parseSmsCodeIfExists(context: Context, content: String): String

    fun matchSmsBlacklist(context: Context, sender: String?, body: String?): SmsBlacklistMatchResult
}

object NoopSmsRuntimeBridge : SmsRuntimeBridge {
    override suspend fun parseSmsCodeIfExists(context: Context, content: String): String = ""

    override fun matchSmsBlacklist(context: Context, sender: String?, body: String?): SmsBlacklistMatchResult {
        return SmsBlacklistMatchResult(matched = false)
    }
}
