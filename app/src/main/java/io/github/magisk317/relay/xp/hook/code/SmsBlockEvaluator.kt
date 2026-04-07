package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.sms.SmsCodeUtils
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSmsBlacklist
import io.github.magisk317.smscode.verification.SmsIntentHookSupport
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

internal object SmsBlockEvaluator {
    const val BLOCK_REASON_BLACKLIST = "blacklist_block"
    const val BLOCK_REASON_PREF_BLOCK = "pref_block_sms"

    data class Result(
        val smsMsg: SmsMsg?,
        val blockReason: String?,
        val blacklistDeleteOnly: Boolean,
    )

    fun evaluate(
        pluginContext: Context,
        intent: Intent,
        eventId: String,
        source: String,
    ): Result? {
        val action = intent.action
        if (!SmsIntentHookSupport.isSmsAction(action)) {
            return null
        }

        val smsMsg = runCatching { SmsMsg.fromIntent(intent) }.getOrNull()
        val blacklistResult = XpSmsBlacklist.match(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                "Diag %s blacklist matched: event_id=%s type=%s pattern=%s delete=%s block=%s",
                source,
                eventId,
                blacklistResult.matchType,
                blacklistResult.pattern,
                blacklistResult.actionDelete,
                blacklistResult.actionBlock,
            )
        }
        if (blacklistResult.actionBlock) {
            return Result(
                smsMsg = smsMsg,
                blockReason = BLOCK_REASON_BLACKLIST,
                blacklistDeleteOnly = false,
            )
        }

        val deleteOnlyBlacklist = blacklistResult.matched && blacklistResult.actionDelete
        if (!XpPrefs.blockSmsEnabled(pluginContext)) {
            return Result(
                smsMsg = smsMsg,
                blockReason = null,
                blacklistDeleteOnly = deleteOnlyBlacklist,
            )
        }

        val body = smsMsg?.body.orEmpty()
        if (body.isBlank()) {
            return Result(
                smsMsg = smsMsg,
                blockReason = null,
                blacklistDeleteOnly = deleteOnlyBlacklist,
            )
        }

        val smsCode = runBlocking { SmsCodeUtils.parseSmsCodeIfExists(pluginContext, body) }.orEmpty()
        if (smsCode.isBlank()) {
            return Result(
                smsMsg = smsMsg,
                blockReason = null,
                blacklistDeleteOnly = deleteOnlyBlacklist,
            )
        }

        XLog.w(
            "Diag %s early block candidate: event_id=%s codeLength=%d",
            source,
            eventId,
            smsCode.length,
        )
        return Result(
            smsMsg = smsMsg,
            blockReason = BLOCK_REASON_PREF_BLOCK,
            blacklistDeleteOnly = deleteOnlyBlacklist,
        )
    }
}
