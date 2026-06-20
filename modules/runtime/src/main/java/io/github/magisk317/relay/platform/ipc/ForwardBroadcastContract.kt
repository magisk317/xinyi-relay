package io.github.magisk317.relay.platform.ipc

import android.content.Intent
import io.github.magisk317.smscode.runtime.common.sim.SmsRoutingIntentExtras
import kotlin.math.abs

object ForwardBroadcastContract {
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_SENDER = "sender"
    const val EXTRA_BODY = "body"
    const val EXTRA_DATE = "date"
    const val EXTRA_COMPANY = "company"
    const val EXTRA_SMS_CODE = "smsCode"
    const val EXTRA_PACKAGE_NAME = "packageName"
    const val EXTRA_NOTIFY_CHANNEL_ID = "notify_channel_id"
    const val EXTRA_MSG_TYPE = "msgType"
    const val EXTRA_FORWARD_SOURCE = "forward_source"
    const val EXTRA_CALL_STAGE = "call_stage"
    const val EXTRA_CALL_TYPE = "call_type"
    const val EXTRA_IPC_TOKEN = "ipc_token"
    const val EXTRA_SIM_SLOT = SmsRoutingIntentExtras.EXTRA_SIM_SLOT
    const val EXTRA_SUB_ID = SmsRoutingIntentExtras.EXTRA_SUB_ID
    const val EXTRA_APP_ICON = "app_icon"
    const val EXTRA_MATCH_TYPE = "blacklist_match_type"
    const val EXTRA_PATTERN = "blacklist_pattern"
    const val EXTRA_ACTION_DELETE = "blacklist_action_delete"
    const val EXTRA_ACTION_BLOCK = "blacklist_action_block"
    const val EXTRA_BLOCK_REASON = "blacklist_block_reason"
    const val EXTRA_CREATED_AT = "blacklist_created_at"

    const val MSG_TYPE_SMS = "sms"
    const val MSG_TYPE_APP_NOTIFY = "app_notify"
    const val MSG_TYPE_CALL_NOTIFY = "call_notify"
    const val MSG_TYPE_BLACKLIST_HIT = "blacklist_hit"

    const val SOURCE_SMS_HOOK = "sms_hook"
    const val SOURCE_NOTIFICATION_LISTENER = "nls"
    const val SOURCE_NMS_HOOK = "nms_hook"
    const val SOURCE_TELEPHONY_STATE = "telephony_state"

    fun populatePayload(
        intent: Intent,
        sender: String?,
        body: String?,
        date: Long,
        company: String?,
        smsCode: String?,
        packageName: String?,
        notifyChannelId: String,
        msgType: String,
        forwardSource: String,
        eventId: String,
        callType: Int? = null,
        callStage: String? = null,
        simSlot: Int? = null,
        subId: Int? = null,
        appIcon: String = "",
    ) {
        intent.putExtra(EXTRA_EVENT_ID, eventId)
        intent.putExtra(EXTRA_SENDER, sender)
        intent.putExtra(EXTRA_BODY, body)
        intent.putExtra(EXTRA_DATE, date)
        intent.putExtra(EXTRA_COMPANY, company)
        intent.putExtra(EXTRA_SMS_CODE, smsCode)
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName)
        intent.putExtra(EXTRA_NOTIFY_CHANNEL_ID, notifyChannelId)
        intent.putExtra(EXTRA_MSG_TYPE, msgType)
        intent.putExtra(EXTRA_FORWARD_SOURCE, forwardSource)
        callType?.let { intent.putExtra(EXTRA_CALL_TYPE, it) }
        callStage?.let { intent.putExtra(EXTRA_CALL_STAGE, it) }
        simSlot?.let { intent.putExtra(EXTRA_SIM_SLOT, it) }
        subId?.let { intent.putExtra(EXTRA_SUB_ID, it) }
        if (appIcon.isNotEmpty()) intent.putExtra(EXTRA_APP_ICON, appIcon)
    }

    fun putIpcToken(intent: Intent, token: String?) {
        if (!token.isNullOrBlank()) {
            intent.putExtra(EXTRA_IPC_TOKEN, token)
        }
    }

    fun copySimRoutingExtras(source: Intent, target: Intent) {
        SmsRoutingIntentExtras.copyFrom(source, target)
    }

    fun readIntExtra(intent: Intent, vararg keys: String): Int? {
        return SmsRoutingIntentExtras.readIntExtra(intent, *keys)
    }

    fun buildEventId(prefix: String, seed: String): String {
        val now = System.currentTimeMillis().toString(36)
        val suffix = abs((seed + now).hashCode()).toString(36)
        return "${prefix}_${now}_$suffix"
    }
}
