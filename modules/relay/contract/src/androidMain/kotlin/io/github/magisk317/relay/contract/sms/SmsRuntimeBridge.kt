package io.github.magisk317.relay.contract.sms

import android.content.Context
import io.github.magisk317.smscode.verification.BlacklistMatchResult

/**
 * Blacklist match outcome crossing the bridge.
 *
 * The verification contract in core already defines this shape and this module
 * depends on core, so alias it instead of keeping a fifth copy of the same fields.
 */
typealias SmsBlacklistMatchResult = BlacklistMatchResult

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
