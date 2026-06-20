package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.contract.xpbridge.XpSmsBlacklistHitRecord
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

internal object SmsBlacklistHitRecorder {
    fun record(
        pluginContext: Context,
        smsMsg: SmsMsg?,
        blacklistResult: BlacklistMatchResult,
        decision: SmsHandlerDispatchDecision.Decision,
        eventId: String,
        source: String,
    ) {
        if (!blacklistResult.matched) return
        val hit = XpSmsBlacklistHitRecord(
            eventId = eventId,
            source = source,
            sender = smsMsg?.sender,
            body = smsMsg?.body,
            smsDate = smsMsg?.date ?: 0L,
            matchType = blacklistResult.matchType,
            pattern = blacklistResult.pattern,
            actionDelete = blacklistResult.actionDelete,
            actionBlock = blacklistResult.actionBlock,
            blockReason = decision.blockReason?.wireValue,
        )
        runCatching {
            runBlocking {
                XpRecordFacade(pluginContext).insertSmsBlacklistHit(hit)
            }
        }.onFailure {
            XLog.w("Diag sms blacklist hit record failed: event_id=%s error=%s", eventId, it.message ?: "unknown")
        }
    }
}
