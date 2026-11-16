package io.github.magisk317.relay.xp.hook.telephony

import android.content.Context
import io.github.magisk317.forward.contract.ForwardBroadcastContract
import io.github.magisk317.relay.contract.xpbridge.XpSmsBlacklistHitRecord
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.XpSmsBlacklist
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderBlacklistEngine
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderDraft
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderWriteDecision
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.runBlocking

/**
 * Blacklist enforcement at the sms provider choke point.
 *
 * Xiaomi network SMS is inserted straight into `content://sms` and never passes
 * the telephony dispatch chain (xinyi-relay#307), so this guard evaluates the
 * shared blacklist the dispatch chain uses and cancels the provider write when
 * the matched entry asks for delete/block. The decision core (gate ordering,
 * hit semantics, diag attribution) lives in the shared
 * [SmsProviderBlacklistEngine]; this host reads only its own entitlement gate
 * and records the hit through the app record facade.
 */
internal object ProviderSmsBlacklistGuard {
    const val SOURCE = "sms_provider"

    fun evaluate(pluginContext: Context, drafts: List<SmsProviderDraft>): SmsProviderWriteDecision {
        val matches = drafts.map { draft ->
            runCatching { XpSmsBlacklist.match(pluginContext, draft.address, draft.body) }
                .getOrDefault(BlacklistMatchResult(matched = false))
        }
        val decision = SmsProviderBlacklistEngine.decide(
            enabled = true,
            entitlementAllowed = XpPrefs.mobileAutomationAllowed(pluginContext),
            conflictSuppressed = false,
            matches = matches,
        )
        val matched = decision.matched
        val hitIndex = matches.indexOfFirst { it == matched }
        if (matched != null && hitIndex >= 0) {
            recordHit(pluginContext, drafts[hitIndex], matched)
        }
        return decision.write
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
            blockReason = SmsProviderBlacklistEngine.BLOCK_REASON,
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
