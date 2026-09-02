package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.android.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.platform.ipc.EventDeduplicator
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastDispatcher
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastPayload
import io.github.magisk317.relay.platform.ipc.ForwardPayloadFactory
import io.github.magisk317.relay.platform.ipc.SmsIngressAdapter
import io.github.magisk317.xposed.logging.MagiskOtel

object StandardMessageIngressHandler {
    private const val NANOS_PER_MILLI = 1_000_000L

    fun isSmsReceived(intent: Intent): Boolean {
        return intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
    }

    fun isMmsWapPush(intent: Intent): Boolean {
        val isWapPush = intent.action == Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION ||
            intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION
        if (!isWapPush) return false
        return intent.type.isNullOrBlank() || intent.type == MMS_MIME_TYPE
    }

    suspend fun shouldHandleStandardMode(context: Context, source: String): Boolean {
        if (!isMobileAutomationAllowed(context)) {
            XLog.i("%s: mobile entitlement unavailable, skipping standard ingress", source)
            emitIngest(
                result = "skip",
                stage = "mobile_entitlement_gate",
                reason = "mobile_entitlement",
                msgType = "unknown",
                durationMs = 0L,
                source = source,
            )
            return false
        }
        val appContext = context.applicationContext
        WorkModeResolver.resolve(appContext)
        val mode = WorkModeResolver.mode.value
        if (mode != WorkMode.Standard) {
            XLog.i("%s: mode=%s, skipping standard ingress", source, mode)
            MagiskOtel.event(
                name = "sms.ingest",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "standard_mode_gate",
                    "reason" to "mode_${mode.name.lowercase()}",
                    "source" to source,
                ),
                statusOk = true,
            )
            return false
        }
        return true
    }

    suspend fun dispatchSms(context: Context, intent: Intent) {
        if (!isMobileAutomationAllowed(context)) {
            emitIngest(
                result = "skip",
                stage = "standard_sms",
                reason = "mobile_entitlement",
                msgType = "sms",
                durationMs = 0L,
            )
            return
        }
        val startedAt = System.nanoTime()
        val smsMsg = SmsMsg.fromIntent(intent)
        val payload = buildSmsPayload(
            context = context,
            smsMsg = smsMsg,
            intent = intent,
        ) ?: return
        if (EventDeduplicator.isDuplicate(payload.eventId)) {
            XLog.i("StandardSmsReceiver: Duplicate event skipped (eventId=%s)", payload.eventId)
            emitIngest(
                result = "skip",
                stage = "standard_sms",
                reason = "duplicate",
                msgType = "sms",
                durationMs = elapsedMs(startedAt),
                eventIdPresent = payload.eventId.isNotBlank(),
            )
            return
        }
        ForwardBroadcastDispatcher.dispatchFromHost(
            context = context,
            payload = payload,
        )
        emitIngest(
            result = "ok",
            stage = "standard_sms",
            msgType = "sms",
            durationMs = elapsedMs(startedAt),
            eventIdPresent = payload.eventId.isNotBlank(),
        )
    }

    internal suspend fun buildSmsPayload(
        context: Context,
        smsMsg: SmsMsg,
        intent: Intent,
        smsCodeParser: (suspend (Context, String) -> String)? = null,
    ): ForwardBroadcastPayload? {
        if (smsMsg.sender.isNullOrBlank() || smsMsg.body.isNullOrBlank()) {
            XLog.e("StandardSmsReceiver: Failed to parse SMS")
            emitIngest(
                result = "error",
                stage = "standard_sms",
                reason = "parse_missing_fields",
                msgType = "sms",
                durationMs = 0L,
                statusOk = false,
            )
            return null
        }

        val eventId = ForwardPayloadFactory.ensureSmsEventId(intent, smsMsg)
        XLog.i("StandardSmsReceiver: Intercepted SMS from %s (eventId=%s)", smsMsg.sender ?: "<null>", eventId)
        val result = SmsIngressAdapter.toPayload(
            pluginContext = context.applicationContext,
            phoneContext = context,
            smsMsg = smsMsg,
            sourceIntent = intent,
            eventId = eventId,
            smsCodeParser = smsCodeParser,
        )
        if (result == null) {
            emitIngest(
                result = "error",
                stage = "standard_sms",
                reason = "adapter_null",
                msgType = "sms",
                durationMs = 0L,
                eventIdPresent = eventId.isNotBlank(),
                statusOk = false,
            )
            return null
        }
        return result.payload
    }

    suspend fun dispatchMms(context: Context, intent: Intent) {
        if (!isMobileAutomationAllowed(context)) {
            emitIngest(
                result = "skip",
                stage = "standard_mms",
                reason = "mobile_entitlement",
                msgType = "mms",
                durationMs = 0L,
            )
            return
        }
        val startedAt = System.nanoTime()
        val payload = ForwardPayloadFactory.mmsPayload(intent)
        if (EventDeduplicator.isDuplicate(payload.eventId)) {
            XLog.i("StandardMmsReceiver: Duplicate event skipped (eventId=%s)", payload.eventId)
            emitIngest(
                result = "skip",
                stage = "standard_mms",
                reason = "duplicate",
                msgType = "mms",
                durationMs = elapsedMs(startedAt),
                eventIdPresent = payload.eventId.isNotBlank(),
            )
            return
        }
        XLog.i("StandardMmsReceiver: Intercepted MMS eventId=%s", payload.eventId)
        ForwardBroadcastDispatcher.dispatchFromHost(
            context = context,
            payload = payload,
        )
        emitIngest(
            result = "ok",
            stage = "standard_mms",
            msgType = "mms",
            durationMs = elapsedMs(startedAt),
            eventIdPresent = payload.eventId.isNotBlank(),
        )
    }

    private fun emitIngest(
        result: String,
        stage: String,
        msgType: String,
        durationMs: Long,
        reason: String? = null,
        eventIdPresent: Boolean? = null,
        source: String? = null,
        statusOk: Boolean = true,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "app",
            "stage" to stage,
            "msg_type" to msgType,
        )
        if (reason != null) {
            attrs["reason"] = reason
        }
        if (eventIdPresent != null) {
            attrs["event_id_present"] = eventIdPresent.toString()
        }
        if (source != null) {
            attrs["source"] = source
        }
        MagiskOtel.event(name = "sms.ingest", attributes = attrs, statusOk = statusOk)
    }

    private fun elapsedMs(startedAt: Long): Long {
        return ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)
    }

    private suspend fun isMobileAutomationAllowed(context: Context): Boolean =
        AppPreferencesDataStore.getBoolean(
            context = context,
            key = PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
            defaultValue = PrefConst.DEFAULT_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
        )

    private const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
}
