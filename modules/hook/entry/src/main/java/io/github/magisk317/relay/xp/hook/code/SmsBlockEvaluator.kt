package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.relay.xpbridge.XpSmsBlacklist
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.verification.SmsBlockEvaluator as SharedSmsBlockEvaluator

internal object SmsBlockEvaluator {
    const val BLOCK_REASON_BLACKLIST = "blacklist_block"
    const val BLOCK_REASON_PREF_BLOCK = "pref_block_sms"

    data class Result(
        val smsMsg: SmsMsg?,
        val blockReason: String?,
        val blacklistDeleteOnly: Boolean,
    )

    private val delegate = SharedSmsBlockEvaluator(
        incomingSmsParser = SmsMsg::fromIntent,
        blacklistMatcher = { context, sender, body ->
            XpSmsBlacklist.match(context, sender, body).toVerificationResult()
        },
        blockSmsEnabledReader = XpPrefs::blockSmsEnabled,
        smsCodeParser = { context, body -> XpSmsCodeParser.parseSmsCodeIfExists(context, body) },
    )

    fun evaluate(
        pluginContext: Context,
        intent: Intent,
        eventId: String,
        source: String,
    ): Result? {
        val result = delegate.evaluate(
            pluginContext = pluginContext,
            intent = intent,
            eventId = eventId,
            source = source,
        ) ?: return null
        return Result(
            smsMsg = result.smsMsg,
            blockReason = result.blockReasonWireValue,
            blacklistDeleteOnly = result.blacklistDeleteOnly,
        )
    }

    private fun XpSmsBlacklist.MatchResult.toVerificationResult(): BlacklistMatchResult {
        return BlacklistMatchResult(
            matched = matched,
            matchType = matchType,
            pattern = pattern,
            actionDelete = actionDelete,
            actionBlock = actionBlock,
        )
    }
}
