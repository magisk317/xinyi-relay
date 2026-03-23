package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.xp.XpRecordFacade
import io.github.magisk317.relay.xp.XpCodeRecordExporter
import io.github.magisk317.relay.xp.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

/**
 * 记录验证码短信
 */
class RecordSmsAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val eventId: String = "",
    private val enabled: Boolean,
    private val deduplicateEnabled: Boolean,
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {
    private val runtimeRecordFacade = XpRecordFacade(pluginContext)

    override fun action(): Bundle? {
        if (enabled) {
            recordSmsMsg(mSmsMsg)
        }
        return null
    }

    private fun recordSmsMsg(smsMsg: SmsMsg) {
        val eventLabel = eventId.ifBlank { "<none>" }
        XLog.w(
            "Diag record start: event_id=%s sender_hash=%s body_len=%d code_present=%s",
            eventLabel,
            senderHash(smsMsg.sender),
            smsMsg.body?.length ?: 0,
            !smsMsg.smsCode.isNullOrBlank(),
        )
        if (deduplicateEnabled) {
            if (shouldSkipByDedup(smsMsg, eventLabel)) {
                return
            }
        }
        try {
            val recordId = runBlocking {
                runtimeRecordFacade.insertSmsRecord(
                    smsMsg = smsMsg,
                    isCodeSms = true,
                )
            }
            if (recordId != null) {
                XLog.w("Diag record provider insert success: event_id=%s record_id=%d", eventLabel, recordId)
                return
            }
            XLog.w("Diag record provider insert failed: event_id=%s err=insert_returned_null", eventLabel)
        } catch (t: Throwable) {
            XLog.w(
                "Diag record provider insert failed: event_id=%s err=%s",
                eventLabel,
                t.message ?: t.javaClass.simpleName,
            )
        }
        if (XpCodeRecordExporter.exportToFile(mPluginContext, smsMsg)) {
            XLog.w("Diag record file fallback success: event_id=%s", eventLabel)
        } else {
            XLog.w("Diag record file fallback failed: event_id=%s", eventLabel)
        }
    }

    private fun shouldSkipByDedup(smsMsg: SmsMsg, eventLabel: String): Boolean {
        val sender = smsMsg.sender
        val body = smsMsg.body
        if (sender.isNullOrBlank() || body.isNullOrBlank()) {
            return false
        }
        val timestamp = if (smsMsg.date > 0) smsMsg.date else System.currentTimeMillis()
        val from = (timestamp - DEDUP_WINDOW_MS).coerceAtLeast(0L)
        val to = timestamp + DEDUP_WINDOW_MS
        val fingerprintDup = runBlocking {
            runCatching {
                runtimeRecordFacade.hasSmsDuplicateInRange(
                    sender = sender,
                    body = body,
                    dateFrom = from,
                    dateTo = to,
                    msgType = SmsMsg.MSG_TYPE_SMS,
                )
            }.getOrDefault(false)
        }
        if (fingerprintDup) {
            XLog.w("Diag record dedup skip: reason=fingerprint_window event_id=%s", eventLabel)
            return true
        }

        val code = smsMsg.smsCode
        if (code.isNullOrBlank()) return false

        val pkg = smsMsg.packageName
        val company = smsMsg.company
        val channelDup = runBlocking {
            runCatching {
                (pkg?.isNotBlank() == true && runtimeRecordFacade.hasSmsCodeDuplicateByPackageInRange(code, pkg, from, to)) ||
                    (company?.isNotBlank() == true && runtimeRecordFacade.hasSmsCodeDuplicateByCompanyInRange(code, company, from, to))
            }.getOrDefault(false)
        }
        if (channelDup) {
            XLog.w("Diag record dedup skip: reason=code_channel event_id=%s", eventLabel)
            return true
        }
        return false
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 5_000L
    }
}
