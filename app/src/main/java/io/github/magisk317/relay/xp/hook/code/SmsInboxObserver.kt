package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.StringUtils
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.platform.ipc.SmsHookDispatchCoordinator
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.core.utils.XLog
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val runtimeRecordFacade = RuntimeRecordFacade(pluginContext)
    private val smsRoleStateResolver = SmsRoleStateResolver()
    private val smsInboxScanner = ObservedInboxScanner(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        smsIdTracker = SmsInboxSeenTracker(MAX_TRACKED_SMS_IDS),
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
                StringUtils.escape(record.code),
                StringUtils.escape(record.body),
            )
            logSmsRoleStateForSms(record.smsId, record.triggerUri)
            handleObservedCode(
                smsId = record.smsId,
                triggerUri = record.triggerUri,
                sender = record.sender,
                body = record.body,
                date = record.date,
                read = record.read,
                code = record.code,
            )
        }
    }

    private fun handleObservedCode(
        smsId: Long,
        triggerUri: String,
        sender: String,
        body: String,
        date: Long,
        read: Boolean,
        code: String,
    ) {
        val settings = SmsCodePostParseCoordinator.loadSettings(pluginContext)
        val plan = SmsCodePostParseCoordinator.createObservedSmsPlan(settings)
        val eventId = buildObservedEventId(smsId, date)
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = PrefsReader.isEnabled(pluginContext),
            suppressedByRelay = ModuleConflictArbiter.shouldSuppressByRelay(
                phoneContext,
                "SmsInboxObserver#handleObservedCode",
            ),
            duplicated = isObservedSmsDuplicated(
                settings = settings,
                sender = sender,
                body = body,
                date = date,
            ),
            plan = plan,
        )
        when (decision.skipReason) {
            SmsInboxObserverDecision.SkipReason.CONFLICT_SUPPRESSED -> {
                XLog.w("Diag observer conflict skip: event_id=%s sms_id=%d", eventId, smsId)
                return
            }

            SmsInboxObserverDecision.SkipReason.MODULE_DISABLED -> {
                XLog.w("Diag observer skip: module disabled event_id=%s", eventId)
                return
            }

            SmsInboxObserverDecision.SkipReason.DUPLICATED -> {
                XLog.w("Diag observer duplicate skip: event_id=%s", eventId)
                return
            }

            null -> Unit
        }

        logSmsRoleState(eventId)

        val smsMsg = SmsHookDispatchCoordinator.enrichObservedSms(
            phoneContext = phoneContext,
            sender = sender,
            body = body,
            date = date,
            smsCode = code,
        )

        if (decision.autoInputEnabled) {
            XLog.w(
                "Diag observer auto-input: event_id=%s sender_hash=%s read=%s uri=%s",
                eventId,
                senderHash(sender),
                read,
                triggerUri,
            )
        } else {
            XLog.w("Diag observer auto-input disabled: event_id=%s", eventId)
        }

        decision.recordSkipReason?.let { reason ->
            XLog.w("Diag observer record skipped: reason=%s event_id=%s", reason.wireValue, eventId)
        }

        SmsCodePostParseCoordinator.dispatchObservedSmsActions(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan,
        )
    }

    private fun isObservedSmsDuplicated(
        settings: SmsCodePostParseCoordinator.Settings,
        sender: String,
        body: String,
        date: Long,
    ): Boolean {
        if (!settings.deduplicateSmsEnabled) {
            return false
        }
        val timestamp = if (date > 0) date else System.currentTimeMillis()
        return runBlocking {
            runCatching {
                runtimeRecordFacade.isDuplicateSms(
                    sender = sender,
                    body = body,
                    date = timestamp,
                    msgType = SmsMsg.MSG_TYPE_SMS,
                )
            }.getOrDefault(false)
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

    private fun buildObservedEventId(smsId: Long, date: Long): String {
        val ts = if (date > 0) date else System.currentTimeMillis()
        return "sms_observed_${ts.toString(EVENT_ID_RADIX)}_${smsId.toString(EVENT_ID_RADIX)}"
    }

    private fun senderHash(sender: String): String {
        if (sender.isBlank()) return "none"
        return Integer.toHexString(sender.hashCode())
    }

    companion object {
        private const val RECENT_SMS_WINDOW_MS = 10 * 60 * 1000L
        private const val MAX_TRACKED_SMS_IDS = 128
        private const val EVENT_ID_RADIX = 36
        private val queryExecutor = Executors.newSingleThreadExecutor()
    }
}
