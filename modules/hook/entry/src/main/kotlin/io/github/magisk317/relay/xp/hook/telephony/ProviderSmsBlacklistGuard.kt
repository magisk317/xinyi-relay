package io.github.magisk317.relay.xp.hook.telephony

import android.content.Context
import io.github.magisk317.forward.contract.ForwardBroadcastContract
import io.github.magisk317.relay.contract.xpbridge.XpSmsBlacklistHitRecord
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.XpSmsBlacklist
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderDraft
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderWriteDecision
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.runBlocking

/**
 * Blacklist enforcement at the sms provider choke point.
 *
 * Xiaomi network SMS is inserted straight into `content://sms` and never passes
 * the telephony dispatch chain (xinyi-relay#307), so this guard evaluates the
 * same shared blacklist the dispatch chain uses and cancels the provider write
 * when the matched entry asks for delete/block. Verification-code extraction
 * stays on the dispatch path; a blocked network SMS is simply not written.
 */
internal object ProviderSmsBlacklistGuard {
    const val BLOCK_REASON = "blacklist_block"
    const val SOURCE = "sms_provider"

    fun evaluate(pluginContext: Context, drafts: List<SmsProviderDraft>): SmsProviderWriteDecision {
        // Mirror the shared dispatch evaluator: without mobile entitlement the
        // provider path must never act on a match.
        if (!XpPrefs.mobileAutomationAllowed(pluginContext)) return SmsProviderWriteDecision.Allow
        for (draft in drafts) {
            val result = runCatching {
                XpSmsBlacklist.match(pluginContext, draft.address, draft.body)
            }.getOrNull() ?: continue
            if (result.matched && (result.actionBlock || result.actionDelete)) {
                XLog.w(
                    message = "Diag dispatch_chain blacklist matched: source=%s pattern=%s delete=%s block=%s",
                    SOURCE,
                    result.pattern,
                    result.actionDelete,
                    result.actionBlock,
                )
                recordHit(pluginContext, draft, result)
                return SmsProviderWriteDecision.Block(BLOCK_REASON)
            }
        }
        return SmsProviderWriteDecision.Allow
    }

    private fun recordHit(pluginContext: Context, draft: SmsProviderDraft, result: BlacklistMatchResult) {
        val hit = XpSmsBlacklistHitRecord(
            eventId = ForwardBroadcastContract.buildEventId("sms", draft.address.orEmpty() + draft.body.orEmpty()),
            source = SOURCE,
            sender = draft.address,
            body = draft.body,
            smsDate = draft.date ?: 0L,
            matchType = result.matchType,
            pattern = result.pattern,
            actionDelete = result.actionDelete,
            actionBlock = result.actionBlock,
            blockReason = BLOCK_REASON,
        )
        runCatching {
            runBlocking {
                XpRecordFacade(pluginContext).insertSmsBlacklistHit(hit)
            }
        }.onFailure {
            XLog.w(message = "Diag sms blacklist hit record failed: error=%s", it.message ?: "unknown")
        }
    }
}
