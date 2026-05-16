package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.ObservedInboxScanner as SharedObservedInboxScanner
import io.github.magisk317.smscode.verification.SmsInboxSeenTracker

internal class ObservedInboxScanner(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsIdTracker: SmsInboxSeenTracker,
    private val smsCodeParser: suspend (Context, String) -> String = { context, body ->
        XpSmsCodeParser.parseSmsCodeIfExists(context, body)
    },
    private val inboxRowLoader: (Long, Long?) -> List<InboxRow> = { cutoff, triggeredSmsId ->
        loadRecentInboxRows(phoneContext, cutoff, triggeredSmsId)
    },
) {
    data class InboxRow(
        val smsId: Long,
        val sender: String,
        val body: String,
        val date: Long,
        val read: Boolean,
    )

    private val delegate = SharedObservedInboxScanner(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        smsIdTracker = smsIdTracker,
        smsCodeParser = smsCodeParser,
        inboxRowLoader = { cutoff, triggeredSmsId ->
            inboxRowLoader(cutoff, triggeredSmsId).map { row ->
                SharedObservedInboxScanner.InboxRow(
                    smsId = row.smsId,
                    sender = row.sender,
                    body = row.body,
                    date = row.date,
                    read = row.read,
                )
            }
        },
    )

    fun scan(triggerUri: String, recentSmsWindowMs: Long): List<ObservedInboxScanRecord> {
        return delegate.scan(triggerUri, recentSmsWindowMs)
    }

    companion object {
        private const val MAX_RECENT_SMS_COUNT = 32

        private fun loadRecentInboxRows(
            phoneContext: Context,
            cutoff: Long,
            triggeredSmsId: Long?,
        ): List<InboxRow> {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
            )
            val selection: String
            val selectionArgs: Array<String>
            val sortOrder: String?
            if (triggeredSmsId != null) {
                selection = "${Telephony.Sms._ID}=? AND ${Telephony.Sms.TYPE}=?"
                selectionArgs = arrayOf(triggeredSmsId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
                sortOrder = null
            } else {
                selection = "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}>?"
                selectionArgs = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), cutoff.toString())
                sortOrder = "${Telephony.Sms.DATE} DESC limit $MAX_RECENT_SMS_COUNT"
            }
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

        fun parseTriggeredSmsId(triggerUri: String): Long? {
            if (triggerUri.isBlank()) return null
            val parsedId = runCatching { Uri.parse(triggerUri) }.getOrNull()
                ?.takeIf { it.scheme == "content" && it.authority == "sms" }
                ?.lastPathSegment
                ?.toLongOrNull()
            return parsedId
                ?: Regex("""^content://sms(?:/[^/?#]+)*/(\d+)(?:[?#].*)?$""")
                    .find(triggerUri)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
        }
    }
}
