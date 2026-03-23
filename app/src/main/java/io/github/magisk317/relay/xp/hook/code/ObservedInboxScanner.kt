package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.provider.Telephony
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

internal class ObservedInboxScanner(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsIdTracker: SmsInboxSeenTracker,
    private val smsCodeParser: suspend (Context, String) -> String = { context, body ->
        XpSmsCodeParser.parseSmsCodeIfExists(context, body)
    },
    private val inboxRowLoader: (Long) -> List<InboxRow> = { cutoff ->
        loadRecentInboxRows(phoneContext, cutoff)
    },
) {
    data class InboxRow(
        val smsId: Long,
        val sender: String,
        val body: String,
        val date: Long,
        val read: Boolean,
    )

    fun scan(triggerUri: String, recentSmsWindowMs: Long): List<ObservedInboxScanRecord> {
        val cutoff = System.currentTimeMillis() - recentSmsWindowMs
        val resolvedTriggerUri = triggerUri.ifBlank { DEFAULT_SMS_TRIGGER_URI }
        return runCatching {
            inboxRowLoader(cutoff).mapNotNull { row ->
                if (!smsIdTracker.markSeen(row.smsId)) {
                    return@mapNotNull null
                }
                val code = runBlocking { smsCodeParser(pluginContext, row.body) }.orEmpty()
                if (code.isBlank()) {
                    return@mapNotNull null
                }
                ObservedInboxScanRecord(
                    smsId = row.smsId,
                    triggerUri = resolvedTriggerUri,
                    sender = row.sender,
                    body = row.body,
                    date = row.date,
                    read = row.read,
                    code = code,
                )
            }
        }.getOrElse {
            XLog.w("SmsInboxObserver scan failed: %s", it.message ?: it.javaClass.simpleName)
            emptyList()
        }
    }

    companion object {
        private const val DEFAULT_SMS_TRIGGER_URI = "content://sms"
        private const val MAX_RECENT_SMS_COUNT = 32

        private fun loadRecentInboxRows(
            phoneContext: Context,
            cutoff: Long,
        ): List<InboxRow> {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
            )
            val selection = "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}>?"
            val selectionArgs = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), cutoff.toString())
            val sortOrder = "${Telephony.Sms.DATE} DESC limit $MAX_RECENT_SMS_COUNT"
            val rows = mutableListOf<InboxRow>()
            phoneContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    rows += InboxRow(
                        smsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty(),
                        body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty(),
                        date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                        read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) != 0,
                    )
                }
            }
            return rows
        }
    }
}
