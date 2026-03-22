package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import io.github.magisk317.relay.xp.XpStringEscaper
import io.github.magisk317.smscode.core.utils.XLog
import java.util.concurrent.Executors

internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val smsRoleStateResolver = SmsRoleStateResolver()
    private val smsInboxScanner = ObservedInboxScanner(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        smsIdTracker = SmsInboxSeenTracker(MAX_TRACKED_SMS_IDS),
    )
    private val observedSmsHandler = ObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        roleStateLogger = ::logSmsRoleState,
    )
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            queryExecutor.execute { scanRecentInbox(uri?.toString().orEmpty()) }
        }
    }

    fun register() {
        runCatching {
            phoneContext.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            XLog.i("SmsInboxObserver registered")
        }.onFailure {
            XLog.w("SmsInboxObserver register failed: %s", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun scanRecentInbox(triggerUri: String) {
        smsInboxScanner.scan(
            triggerUri = triggerUri,
            recentSmsWindowMs = RECENT_SMS_WINDOW_MS,
        ).forEach { record ->
            XLog.w(
                "Diag SMS provider observed: sms_id=%d trigger_uri=%s sender_hash=%s date=%d read=%s code=%s body=%s",
                record.smsId,
                record.triggerUri,
                senderHash(record.sender),
                record.date,
                record.read,
                XpStringEscaper.escape(record.code),
                XpStringEscaper.escape(record.body),
            )
            logSmsRoleStateForSms(record.smsId, record.triggerUri)
            observedSmsHandler.handle(record)
        }
    }

    private fun logSmsRoleState(eventId: String) {
        val roleState = smsRoleStateResolver.resolve(phoneContext)
        XLog.w(
            "Diag observer sms role: event_id=%s defaultSms=%s roleHolders=%s",
            eventId,
            roleState.defaultSms ?: "<none>",
            if (roleState.roleHolders.isEmpty()) "<none>" else roleState.roleHolders.joinToString(","),
        )
    }

    private fun logSmsRoleStateForSms(smsId: Long, triggerUri: String) {
        val roleState = smsRoleStateResolver.resolve(phoneContext)
        XLog.w(
            "Diag observer sms role: sms_id=%d trigger_uri=%s defaultSms=%s roleHolders=%s",
            smsId,
            triggerUri.ifBlank { Telephony.Sms.CONTENT_URI.toString() },
            roleState.defaultSms ?: "<none>",
            if (roleState.roleHolders.isEmpty()) "<none>" else roleState.roleHolders.joinToString(","),
        )
    }

    private fun senderHash(sender: String): String {
        if (sender.isBlank()) return "none"
        return Integer.toHexString(sender.hashCode())
    }

    companion object {
        private const val RECENT_SMS_WINDOW_MS = 10 * 60 * 1000L
        private const val MAX_TRACKED_SMS_IDS = 128
        private val queryExecutor = Executors.newSingleThreadExecutor()
    }
}
