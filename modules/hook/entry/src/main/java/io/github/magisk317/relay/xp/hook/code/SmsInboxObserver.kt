package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.relay.xpbridge.XpStringEscaper
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.runtime.verification.ObservedInboxScanner
import io.github.magisk317.smscode.verification.SmsInboxSeenTracker
import io.github.magisk317.smscode.runtime.verification.SmsRoleStateResolver
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val smsRoleStateResolver = SmsRoleStateResolver()
    private val smsInboxScanner = createInboxScanner()
    private val queryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val observedSmsHandler = ObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        actionExecutor = queryExecutor,
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
            emitObserver(result = "ok", reason = "registered")
            queryExecutor.execute { repairRecentRouting() }
        }.onFailure {
            XLog.w("SmsInboxObserver register failed: %s", it.message ?: it.javaClass.simpleName)
            emitObserver(result = "error", reason = "register_failed", statusOk = false)
        }
    }

    fun unregister() {
        runCatching {
            phoneContext.contentResolver.unregisterContentObserver(observer)
            XLog.i("SmsInboxObserver unregistered")
            emitObserver(result = "ok", reason = "unregistered")
        }.onFailure {
            XLog.w("SmsInboxObserver unregister failed: %s", it.message ?: it.javaClass.simpleName)
            emitObserver(result = "error", reason = "unregister_failed", statusOk = false)
        }
        queryExecutor.shutdownNow()
    }

    private fun repairRecentRouting() {
        var scanned = 0
        var updated = 0
        createInboxScanner().scanRouting(
            triggerUri = ROUTING_REPAIR_TRIGGER_URI,
            recentSmsWindowMs = ROUTING_REPAIR_WINDOW_MS,
        ).forEach { record ->
            scanned += 1
            if (observedSmsHandler.repairRouting(record)) {
                updated += 1
            }
        }
        XLog.log(
            Log.INFO,
            LogRoute.SMS_HOOK,
            false,
            false,
            "Diag SMS routing repair finished: scanned=%d updated=%d",
            scanned,
            updated,
        )
        emitObserver(
            result = "ok",
            reason = "routing_repair",
            extra = mapOf(
                "scanned_count" to scanned.toString(),
                "updated_count" to updated.toString(),
            ),
        )
    }

    private fun scanRecentInbox(triggerUri: String) {
        val startedAt = System.nanoTime()
        var handled = 0
        smsInboxScanner.scan(
            triggerUri = triggerUri,
            recentSmsWindowMs = RECENT_SMS_WINDOW_MS,
        ).forEach { record ->
            handled += 1
            val sensitiveDebugLog = XpPrefs.isSensitiveDebugLogMode(pluginContext)
            XLog.w(
                "Diag SMS provider observed: sms_id=%d trigger_uri=%s sender_hash=%s date=%d read=%s simSlot=%d subId=%d code=%s body=%s",
                record.smsId,
                record.triggerUri,
                senderHash(record.sender),
                record.date,
                record.read,
                record.simSlot,
                record.subId,
                if (sensitiveDebugLog) XpStringEscaper.escape(record.code) else XpStringEscaper.summarizeCode(record.code),
                if (sensitiveDebugLog) XpStringEscaper.escape(record.body) else XpStringEscaper.summarizeBody(record.body),
            )
            logSmsRoleStateForSms(record.smsId, record.triggerUri)
            observedSmsHandler.handle(record)
        }
        emitObserver(
            result = "ok",
            reason = if (handled == 0) "scan_empty" else "scan_handled",
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            extra = mapOf("handled_count" to handled.toString()),
        )
    }

    private fun emitObserver(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        durationMs: Long = 0L,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "hook",
            "stage" to "sms_inbox_observer",
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "sms.observe", attributes = attrs, statusOk = statusOk)
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

    private fun createInboxScanner(): ObservedInboxScanner {
        return ObservedInboxScanner(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsIdTracker = SmsInboxSeenTracker(MAX_TRACKED_SMS_IDS),
            smsCodeParser = XpSmsCodeParser::parseSmsCodeIfExists,
        )
    }

    companion object {
        private const val RECENT_SMS_WINDOW_MS = 10 * 60 * 1000L
        private const val ROUTING_REPAIR_WINDOW_MS = 24 * 60 * 60 * 1000L
        private const val ROUTING_REPAIR_TRIGGER_URI = "content://sms"
        private const val MAX_TRACKED_SMS_IDS = 128
    }
}
