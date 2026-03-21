package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.smscode.core.utils.XLog
import io.github.magisk317.relay.data.db.DBManager
import io.github.magisk317.relay.data.db.DBProvider
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.ui.record.CodeRecordRestoreManager
import io.github.magisk317.relay.xp.hook.code.action.CallableAction

/**
 * 记录验证码短信
 */
class RecordSmsAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val eventId: String = "",
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (PrefsReader.recordCodeSmsEnabled(mPluginContext)) {
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
        if (PrefsReader.deduplicateSms(mPluginContext)) {
            if (shouldSkipByDedup(smsMsg, eventLabel)) {
                return
            }
        }
        try {
            val smsMsgUri = DBProvider.smsMsgContentUri(mPluginContext)
            val resolver = mPluginContext.contentResolver

            val values = ContentValues().apply {
                put("body", smsMsg.body)
                put("company", smsMsg.company)
                put("date", smsMsg.date)
                put("sender", smsMsg.sender)
                put("sms_code", smsMsg.smsCode)
                put("package_name", smsMsg.packageName)
                put("msg_type", smsMsg.msgType)
                put("forward_status", smsMsg.forwardStatus)
                put("forward_target", smsMsg.forwardTarget)
                put("forward_message", smsMsg.forwardMessage)
                put("forward_time", smsMsg.forwardTime)
            }

            resolver.insert(smsMsgUri, values)
            XLog.w("Diag record provider insert success: event_id=%s", eventLabel)

            val projections = arrayOf("_id")
            val order = "date ASC"
            val selection = "msg_type = ? AND sms_code IS NOT NULL AND sms_code != ''"
            val selectionArgs = arrayOf(SmsMsg.MSG_TYPE_SMS.toString())
            val cursor: Cursor? = resolver.query(smsMsgUri, projections, selection, selectionArgs, order)
            if (cursor == null) {
                XLog.w("Diag record retention query returned null: event_id=%s", eventLabel)
                return
            }

            val count = cursor.count
            val limit = PrefsReader.getHistoryLimit(
                context = mPluginContext,
                msgType = SmsMsg.MSG_TYPE_SMS,
                isCodeSms = true,
            )
            if (limit > 0 && count > limit) {
                // 删除最早的记录，直至剩余数目为 limit
                val operations = ArrayList<ContentProviderOperation>()
                val deleteSelection = "_id = ?"
                for (i in 0 until count - limit) {
                    if (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                        val operation = ContentProviderOperation.newDelete(smsMsgUri)
                            .withSelection(deleteSelection, arrayOf(id.toString()))
                            .build()
                        operations.add(operation)
                    }
                }

                resolver.applyBatch(DBProvider.authority(mPluginContext), operations)
                XLog.w(
                    "Diag record retention cleanup success: event_id=%s removed=%d limit=%d",
                    eventLabel,
                    count - limit,
                    limit,
                )
            }
            cursor.close()
        } catch (t: Throwable) {
            XLog.w(
                "Diag record provider insert failed: event_id=%s err=%s",
                eventLabel,
                t.message ?: t.javaClass.simpleName,
            )
            if (CodeRecordRestoreManager.exportToFile(mPluginContext, smsMsg)) {
                XLog.w("Diag record file fallback success: event_id=%s", eventLabel)
            } else {
                XLog.w("Diag record file fallback failed: event_id=%s", eventLabel)
            }
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
        val db = DBManager.get(mPluginContext)
        val fingerprintDup = runCatching {
            db.querySmsMsgByFingerprintInRange(sender, body, from, to) != null
        }.getOrDefault(false)
        if (fingerprintDup) {
            XLog.w("Diag record dedup skip: reason=fingerprint_window event_id=%s", eventLabel)
            return true
        }

        val code = smsMsg.smsCode
        if (code.isNullOrBlank()) return false

        val pkg = smsMsg.packageName
        val company = smsMsg.company
        val channelDup = runCatching {
            (pkg?.isNotBlank() == true && db.querySmsMsgByCodeAndPackageInRange(code, pkg, from, to) != null) ||
                (company?.isNotBlank() == true && db.querySmsMsgByCodeAndCompanyInRange(code, company, from, to) != null)
        }.getOrDefault(false)
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
