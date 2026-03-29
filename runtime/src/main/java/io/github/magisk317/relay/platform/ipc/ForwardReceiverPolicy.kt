package io.github.magisk317.relay.platform.ipc

import android.os.Build
import io.github.magisk317.relay.common.constant.MessageType

object ForwardReceiverPolicy {
    const val API_LEVEL_34 = 34
    const val SYSTEM_UID = 1000
    const val PHONE_UID = 1001
    private const val NOTIFY_DEDUP_WINDOW_MS = 10_000L
    private const val NMS_HOOK_SUPPRESS_TTL_MS = 30_000L
    private const val SMS_HOOK_SUCCESS_SUPPRESS_TTL_MS = 120_000L
    private const val NOTIFY_DEDUP_MAX_ENTRIES = 256

    fun shouldAllowSystemTokenBypass(
        msgType: String,
        forwardSource: String,
        sentFromUid: Int?,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = when {
        (
            msgType == ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY ||
                msgType == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY
            ) && forwardSource == ForwardBroadcastContract.SOURCE_NMS_HOOK -> {
            sentFromUid == SYSTEM_UID || (sdkInt < API_LEVEL_34 && sentFromUid == null)
        }
        msgType == ForwardBroadcastContract.MSG_TYPE_SMS &&
            forwardSource == ForwardBroadcastContract.SOURCE_SMS_HOOK -> {
            sentFromUid == SYSTEM_UID ||
                sentFromUid == PHONE_UID ||
                (sdkInt < API_LEVEL_34 && sentFromUid == null)
        }
        else -> false
    }

    fun shouldAllowSmsHookTokenBypass(
        sentFromUid: Int?,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean {
        return sentFromUid == SYSTEM_UID ||
            sentFromUid == PHONE_UID ||
            (sdkInt < API_LEVEL_34 && sentFromUid == null)
    }

    fun resolveSimSlot(
        rawSlot: Int?,
        subId: Int,
        slotIndexResolver: (Int) -> Int,
    ): Int {
        if (subId > 0) {
            val slotFromSubId = slotIndexResolver(subId)
            if (slotFromSubId >= 0) return slotFromSubId
        }
        val slot = rawSlot ?: return -1
        return when {
            slot in 0..1 -> slot
            slot == 2 -> 1
            else -> -1
        }
    }

    fun resolveRelayMessageType(
        msgType: String,
        smsCode: String?,
    ): MessageType = when {
        msgType == ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY -> MessageType.APP_NOTIFY
        msgType == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY -> MessageType.CALL_NOTIFY
        !smsCode.isNullOrBlank() -> MessageType.SMS_CODE
        else -> MessageType.SMS_PLAIN
    }

    fun shouldDropDuplicateNotify(
        msgType: String,
        packageName: String?,
        sender: String?,
        body: String?,
        notifyChannelId: String?,
        recentNotify: MutableMap<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val key = buildNotifyDedupKey(
            msgType = msgType,
            packageName = packageName,
            sender = sender,
            body = body,
            notifyChannelId = notifyChannelId,
        ) ?: return false

        val previous = recentNotify[key]
        if (previous != null && nowMs - previous < NOTIFY_DEDUP_WINDOW_MS) {
            return true
        }
        recentNotify[key] = nowMs

        if (recentNotify.size > NOTIFY_DEDUP_MAX_ENTRIES) {
            val cutoff = nowMs - NOTIFY_DEDUP_WINDOW_MS * 2
            recentNotify.entries.removeIf { it.value < cutoff }
        }
        return false
    }

    fun shouldDropDuplicateForward(
        msgType: String,
        forwardSource: String,
        packageName: String?,
        sender: String?,
        body: String?,
        notifyChannelId: String?,
        smsCode: String?,
        recentNotify: MutableMap<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val key = when {
            msgType == ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY ||
                msgType == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY ->
                buildNotifyDedupKey(msgType, packageName, sender, body, notifyChannelId)

            msgType == ForwardBroadcastContract.MSG_TYPE_SMS && !smsCode.isNullOrBlank() ->
                buildSmsCodeForwardDedupKey(packageName, sender, smsCode)

            msgType == ForwardBroadcastContract.MSG_TYPE_SMS &&
                forwardSource == ForwardBroadcastContract.SOURCE_NMS_HOOK ->
                buildNotifyDedupKey(msgType, packageName, sender, body, notifyChannelId)

            else -> null
        } ?: return false

        val previous = recentNotify[key]
        if (previous != null && nowMs - previous < NOTIFY_DEDUP_WINDOW_MS) {
            return true
        }
        recentNotify[key] = nowMs

        if (recentNotify.size > NOTIFY_DEDUP_MAX_ENTRIES) {
            val cutoff = nowMs - NOTIFY_DEDUP_WINDOW_MS * 2
            recentNotify.entries.removeIf { it.value < cutoff }
        }
        return false
    }

    fun shouldDropOngoingCallNotify(
        callStage: String?,
        notifyChannelId: String?,
        body: String?,
    ): Boolean {
        if (notifyChannelId?.trim() == "phone_ongoing_call") return true
        val normalizedStage = callStage?.trim().orEmpty()
        if (normalizedStage == "ongoing") return true
        val normalizedBody = body?.trim().orEmpty()
        return normalizedBody.contains("当前通话")
    }

    fun markNmsHookSeen(
        key: String,
        nmsHookSeen: MutableMap<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        nmsHookSeen[key] = nowMs
        if (nmsHookSeen.size > NOTIFY_DEDUP_MAX_ENTRIES) {
            val cutoff = nowMs - NMS_HOOK_SUPPRESS_TTL_MS * 2
            nmsHookSeen.entries.removeIf { it.value < cutoff }
        }
    }

    fun shouldDropTelephonyState(
        key: String,
        nmsHookSeen: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val seenAt = nmsHookSeen[key] ?: return false
        return nowMs - seenAt < NMS_HOOK_SUPPRESS_TTL_MS
    }

    fun markSuccessfulSmsHookDispatch(
        smsCode: String?,
        company: String?,
        sender: String?,
        recentSuccessfulSmsHook: MutableMap<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val key = buildSuccessfulSmsHookKey(smsCode = smsCode, company = company, sender = sender) ?: return
        recentSuccessfulSmsHook[key] = nowMs
        if (recentSuccessfulSmsHook.size > NOTIFY_DEDUP_MAX_ENTRIES) {
            val cutoff = nowMs - SMS_HOOK_SUCCESS_SUPPRESS_TTL_MS * 2
            recentSuccessfulSmsHook.entries.removeIf { it.value < cutoff }
        }
    }

    fun shouldSuppressReclassifiedNmsSms(
        smsCode: String?,
        company: String?,
        sender: String?,
        recentSuccessfulSmsHook: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val key = buildSuccessfulSmsHookKey(smsCode = smsCode, company = company, sender = sender) ?: return false
        val seenAt = recentSuccessfulSmsHook[key] ?: return false
        return nowMs - seenAt < SMS_HOOK_SUCCESS_SUPPRESS_TTL_MS
    }

    private fun buildNotifyDedupKey(
        msgType: String,
        packageName: String?,
        sender: String?,
        body: String?,
        notifyChannelId: String?,
    ): String? {
        val normalizedType = msgType.trim()
        val normalizedPackage = packageName.orEmpty().trim()
        val normalizedSender = sender.orEmpty().trim()
        val normalizedBody = body.orEmpty().trim()
        val normalizedChannel = notifyChannelId.orEmpty().trim()
        if (
            normalizedType.isEmpty() ||
            (
                normalizedPackage.isEmpty() &&
                    normalizedSender.isEmpty() &&
                    normalizedBody.isEmpty() &&
                    normalizedChannel.isEmpty()
                )
        ) {
            return null
        }
        return buildString {
            append(normalizedType)
            append('|')
            append(normalizedPackage)
            append('|')
            append(normalizedSender)
            append('|')
            append(normalizedBody)
            append('|')
            append(normalizedChannel)
        }
    }

    private fun buildSmsCodeForwardDedupKey(
        packageName: String?,
        sender: String?,
        smsCode: String?,
    ): String? {
        val normalizedPackage = packageName.orEmpty().trim()
        val normalizedSender = sender.orEmpty().trim()
        val normalizedCode = smsCode.orEmpty().trim()
        if (normalizedCode.isEmpty()) return null
        if (normalizedPackage.isEmpty() && normalizedSender.isEmpty()) return null
        return buildString {
            append("sms_code|")
            append(normalizedPackage)
            append('|')
            append(normalizedSender)
            append('|')
            append(normalizedCode)
        }
    }

    private fun buildSuccessfulSmsHookKey(
        smsCode: String?,
        company: String?,
        sender: String?,
    ): String? {
        val normalizedCode = smsCode.orEmpty().trim()
        if (normalizedCode.isEmpty()) return null
        val normalizedCompany = company.orEmpty().trim()
        val normalizedSender = sender.orEmpty().trim()
        val identity = normalizedCompany.ifEmpty { normalizedSender }
        if (identity.isEmpty()) return null
        return "sms_hook_success|$identity|$normalizedCode"
    }
}
