package io.github.magisk317.relay.contract.xpbridge

import android.content.Context
import android.content.Intent
import kotlin.math.abs

interface XpSmsDispatchRuntimeBridge {
    fun ensureIncomingEventId(intent: Intent): String

    fun parseIncomingSms(intent: Intent): XpSmsRecord?

    fun prepareParsedSms(
        smsMsg: XpSmsRecord,
        sourceIntent: Intent? = null,
        eventId: String? = null,
    ): XpPreparedSmsHookDispatch

    suspend fun prepareIngressSms(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: XpSmsRecord,
        sourceIntent: Intent? = null,
        eventId: String? = null,
    ): XpPreparedSmsHookDispatch?

    fun enrichObservedSms(
        phoneContext: Context,
        sender: String,
        body: String,
        date: Long,
        smsCode: String,
    ): XpSmsRecord

    fun dispatchPreparedSms(
        context: Context,
        prepared: XpPreparedSmsHookDispatch,
        sentFromUid: Int?,
    ): XpSmsHookDispatchResult
}

object NoopXpSmsDispatchRuntimeBridge : XpSmsDispatchRuntimeBridge {
    override fun ensureIncomingEventId(intent: Intent): String {
        val existing = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty().trim()
        if (existing.isNotEmpty()) return existing
        val generated = buildEventId("sms", abs(intent.hashCode()).toString(36))
        intent.putExtra(EXTRA_EVENT_ID, generated)
        return generated
    }

    override fun parseIncomingSms(intent: Intent): XpSmsRecord? = null

    override fun prepareParsedSms(
        smsMsg: XpSmsRecord,
        sourceIntent: Intent?,
        eventId: String?,
    ): XpPreparedSmsHookDispatch {
        return XpPreparedSmsHookDispatch(
            smsMsg = smsMsg,
            payload = XpForwardPayload(
                sender = smsMsg.sender,
                body = smsMsg.body,
                date = smsMsg.date,
                company = smsMsg.company,
                smsCode = smsMsg.smsCode,
                packageName = smsMsg.packageName,
                notifyChannelId = smsMsg.notifyChannelId,
                msgType = XpForwardPayload.MSG_TYPE_SMS,
                forwardSource = SOURCE_SMS_HOOK,
                eventId = eventId?.takeIf { it.isNotBlank() } ?: buildEventId(
                    prefix = "sms",
                    seed = smsMsg.sender.orEmpty() + smsMsg.body.orEmpty(),
                ),
                simSlot = sourceIntent?.readIntExtraCompat(
                    "slot",
                    "simId",
                    "sim_id",
                    "simSlot",
                    EXTRA_SIM_SLOT,
                    "android.telephony.extra.SLOT_INDEX",
                ),
                subId = sourceIntent?.readIntExtraCompat(
                    "subscription",
                    "subscription_id",
                    EXTRA_SUB_ID,
                    "android.telephony.extra.SUBSCRIPTION_INDEX",
                    "android.telephony.extra.SUBSCRIPTION_ID",
                ),
            ),
            messageType = null,
        )
    }

    override suspend fun prepareIngressSms(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: XpSmsRecord,
        sourceIntent: Intent?,
        eventId: String?,
    ): XpPreparedSmsHookDispatch? = null

    override fun enrichObservedSms(
        phoneContext: Context,
        sender: String,
        body: String,
        date: Long,
        smsCode: String,
    ): XpSmsRecord {
        return XpSmsRecord(
            sender = sender,
            body = body,
            date = date,
            smsCode = smsCode,
            msgType = XpSmsRecord.MSG_TYPE_SMS,
        )
    }

    override fun dispatchPreparedSms(
        context: Context,
        prepared: XpPreparedSmsHookDispatch,
        sentFromUid: Int?,
    ): XpSmsHookDispatchResult {
        return XpSmsHookDispatchResult(
            dispatched = false,
            bypassUsed = false,
            tokenPresent = false,
        )
    }

    private fun Intent.readIntExtraCompat(vararg keys: String): Int? {
        for (key in keys) {
            if (!hasExtra(key)) continue
            val intValue = getIntExtra(key, Int.MIN_VALUE)
            if (intValue != Int.MIN_VALUE) return intValue
            val longValue = getLongExtra(key, Long.MIN_VALUE)
            if (longValue != Long.MIN_VALUE) return longValue.toInt()
            getStringExtra(key)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun buildEventId(prefix: String, seed: String): String {
        val now = System.currentTimeMillis().toString(36)
        val suffix = abs((seed + now).hashCode()).toString(36)
        return "${prefix}_${now}_$suffix"
    }

    private const val EXTRA_EVENT_ID = "event_id"
    private const val EXTRA_SIM_SLOT = "sim_slot"
    private const val EXTRA_SUB_ID = "sub_id"
    private const val SOURCE_SMS_HOOK = "sms_hook"
}
