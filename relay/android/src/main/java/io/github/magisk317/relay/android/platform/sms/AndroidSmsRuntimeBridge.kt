package io.github.magisk317.relay.android.platform.sms

import android.content.Context
import io.github.magisk317.relay.android.sms.SmsBlacklistUtils
import io.github.magisk317.relay.android.sms.SmsCodeUtils
import io.github.magisk317.relay.contract.sms.SmsBlacklistMatchResult
import io.github.magisk317.relay.contract.sms.SmsRuntimeBridge

object AndroidSmsRuntimeBridge : SmsRuntimeBridge {
    override suspend fun parseSmsCodeIfExists(context: Context, content: String): String {
        return SmsCodeUtils.parseSmsCodeIfExists(context, content)
    }

    override fun matchSmsBlacklist(context: Context, sender: String?, body: String?): SmsBlacklistMatchResult {
        val result = SmsBlacklistUtils.match(context, sender, body)
        return SmsBlacklistMatchResult(
            matched = result.matched,
            matchType = result.matchType,
            pattern = result.pattern,
            actionDelete = result.actionDelete,
            actionBlock = result.actionBlock,
        )
    }
}
