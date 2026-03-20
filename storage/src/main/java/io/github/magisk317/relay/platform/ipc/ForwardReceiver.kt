package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.os.Build
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.CallSessionTracker
import io.github.magisk317.relay.common.utils.ForwardFlowLog
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.pipeline.StorageRuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.platform.metadata.SourceMetadataResolver
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ForwardReceiver : BroadcastReceiver() {
    @Suppress("CyclomaticComplexMethod")
    override fun onReceive(context: Context, intent: Intent) {
        val runtimeGraph = StorageRuntimeGraph.from(context)
        val eventPipeline = runtimeGraph.eventPipeline
        val ordered = isOrderedBroadcast
        val pendingResult = goAsync()
        val eventId = intent.getStringExtra("event_id").orEmpty()
        val traceId = buildTraceId(intent, eventId)
        val task = Runnable {
            var resultMarked = false
            fun markResult(code: Int, reason: String) {
                resultMarked = true
                setOrderedResult(pendingResult, ordered, code, reason, eventId)
            }
            runCatching {
                val receiveStartMessage = buildString {
                    append("ForwardReceiver onReceive action=")
                    append(intent.action)
                    append(" event=")
                    append(eventId.ifBlank { "<none>" })
                    append(" ordered=")
                    append(ordered)
                }
                ForwardFlowLog.i(
                    traceId,
                    receiveStartMessage,
                )
                // 1. Action validation
                if (intent.action != PrefConst.ACTION_FORWARD_SMS) {
                    XLog.e("Rejecting broadcast with invalid action: %s", intent.action)
                    ForwardFlowLog.w(traceId, "Reject invalid action=${intent.action}")
                    markResult(RESULT_REJECT_ACTION, "invalid_action")
                    return@runCatching
                }

                val sender = intent.getStringExtra("sender")
                val body = intent.getStringExtra("body")
                val date = intent.getLongExtra("date", 0L)
                val company = intent.getStringExtra("company")
                val smsCode = intent.getStringExtra("smsCode")
                val packageName = intent.getStringExtra("packageName")
                val notifyChannelId = intent.getStringExtra("notify_channel_id").orEmpty()
                val receivedToken = intent.getStringExtra("ipc_token")
                val msgTypeStr = intent.getStringExtra("msgType") ?: "sms"
                val forwardSource = intent.getStringExtra("forward_source") ?: "unknown"
                val callStage = intent.getStringExtra("call_stage").orEmpty()
                val sentFromUid = resolveSentFromUidCompat()
                val sentFromPkg = resolveSentFromPackageCompat()
                val subId = readIntExtra(
                    intent,
                    "sub_id",
                    "subscription",
                    "subscription_id",
                    "android.telephony.extra.SUBSCRIPTION_INDEX",
                    "android.telephony.extra.SUBSCRIPTION_ID",
                )
                val rawSlot = readIntExtra(
                    intent,
                    "sim_slot",
                    "slot",
                    "simId",
                    "sim_id",
                    "simSlot",
                    "android.telephony.extra.SLOT_INDEX",
                )
                val callType = readIntExtra(intent, "call_type") ?: 0

                // 2. Verifying IPC Token: prevent third-party apps from spoofing broadcasts.
                // We retrieve local token from DataStore (which is synced to xposed_prefs).
                val expectedToken = runBlocking {
                    RuntimeSettingsCache.getString(
                        key = io.github.magisk317.relay.common.constant.PrefConst.KEY_IPC_TOKEN,
                        defaultValue = "",
                    ) { key, defaultValue ->
                        runtimeGraph.preferenceDataSource.getString(key, defaultValue)
                    }
                }
                val tokenMatched = expectedToken.isNotEmpty() && receivedToken == expectedToken
                val allowSystemBypass = shouldAllowSystemTokenBypass(
                    msgType = msgTypeStr,
                    forwardSource = forwardSource,
                    sentFromUid = sentFromUid,
                )

                if (!tokenMatched && !allowSystemBypass) {
                    XLog.e("IPC Token mismatch! Security breach attempt or uninitialized token. Rejecting broadcast.")
                    val rejectTokenMessage = buildString {
                        append("Reject token mismatch event=")
                        append(eventId.ifBlank { "<none>" })
                        append(" pkg=")
                        append(packageName.orEmpty())
                        append(" expectedEmpty=")
                        append(expectedToken.isEmpty())
                        append(" receivedEmpty=")
                        append(receivedToken.isNullOrBlank())
                        append(" source=")
                        append(forwardSource)
                        append(" sentFromUid=")
                        append(sentFromUid ?: -1)
                        append(" sentFromPkg=")
                        append(sentFromPkg ?: "<none>")
                    }
                    ForwardFlowLog.w(
                        traceId,
                        rejectTokenMessage,
                    )
                    markResult(RESULT_REJECT_TOKEN, "token_mismatch")
                    return@runCatching
                }
                if (!tokenMatched && allowSystemBypass) {
                    val bypassMessage = buildString {
                        append("Token bypass accepted source=")
                        append(forwardSource)
                        append(" msgType=")
                        append(msgTypeStr)
                        append(" sentFromUid=")
                        append(sentFromUid ?: -1)
                        append(" sentFromPkg=")
                        append(sentFromPkg ?: "<none>")
                    }
                    XLog.w("IPC token bypass accepted. %s", bypassMessage)
                    ForwardFlowLog.w(traceId, bypassMessage)
                    if (receivedToken.isNullOrBlank()) {
                        runCatching {
                            io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
                                .triggerImmediate(context, reason = "token_blank_bypass")
                        }.onFailure { error ->
                            XLog.w(
                                "Trigger root DB catchup failed: %s",
                                error.message ?: error.javaClass.simpleName,
                            )
                        }
                    }
                }
                if (
                    msgTypeStr == "app_notify" &&
                    !shouldForwardAppNotify(runtimeGraph, packageName, traceId, forwardSource)
                ) {
                    markResult(RESULT_REJECT_APP_GATE, "app_gate_drop")
                    return@runCatching
                }
                if (msgTypeStr == "call_notify" && shouldDropOngoingCallNotify(callStage, notifyChannelId, body)) {
                    ForwardFlowLog.i(
                        traceId,
                        buildString {
                            append("Drop ongoing call notify stage=")
                            append(callStage.ifBlank { "<empty>" })
                            append(" channel=")
                            append(notifyChannelId)
                            append(" source=")
                            append(forwardSource)
                        },
                    )
                    markResult(RESULT_DROP_DUPLICATE, "ongoing_drop")
                    return@runCatching
                }
                if (msgTypeStr == "call_notify") {
                    val sourceKey = CallSessionTracker.buildSourceKey(
                        sender = sender,
                        body = body,
                        callType = callType,
                        packageName = packageName,
                    )
                    if (forwardSource == ROUTE_NMS_HOOK) {
                        markNmsHookSeen(sourceKey)
                    } else if (forwardSource == ROUTE_TELEPHONY_STATE && shouldDropTelephonyState(sourceKey)) {
                        ForwardFlowLog.i(
                            traceId,
                            buildString {
                                append("Drop telephony_state call notify because nms_hook seen key=")
                                append(sourceKey)
                            },
                        )
                        markResult(RESULT_DROP_DUPLICATE, "telephony_state_suppressed")
                        return@runCatching
                    }
                }
                if (
                    (msgTypeStr == "app_notify" || msgTypeStr == "call_notify") &&
                    shouldDropDuplicateNotify(
                        msgType = msgTypeStr,
                        packageName = packageName,
                        sender = sender,
                        body = body,
                        notifyChannelId = notifyChannelId,
                    )
                ) {
                    XLog.i(
                        "Drop duplicate notify: type=%s pkg=%s sender=%s channel=%s callType=%d source=%s",
                        msgTypeStr,
                        packageName.orEmpty(),
                        sender.orEmpty(),
                        notifyChannelId,
                        callType,
                        forwardSource,
                    )
                    ForwardFlowLog.i(
                        traceId,
                        buildString {
                            append("Drop duplicate notify type=")
                            append(msgTypeStr)
                            append(" pkg=")
                            append(packageName.orEmpty())
                            append(" sender=")
                            append(sender.orEmpty())
                            append(" channel=")
                            append(notifyChannelId)
                            append(" callType=")
                            append(callType)
                            append(" source=")
                            append(forwardSource)
                        },
                    )
                    markResult(RESULT_DROP_DUPLICATE, "duplicate_drop")
                    return@runCatching
                }
                val callSessionDecision = if (msgTypeStr == "call_notify") {
                    CallSessionTracker.evaluate(
                        stageRaw = callStage,
                        sender = sender,
                        body = body,
                        callType = callType,
                        packageName = packageName,
                    )
                } else {
                    null
                }
                val resolvedCallStage = callSessionDecision?.stage.orEmpty()
                if (msgTypeStr == "call_notify" && callSessionDecision != null && !callSessionDecision.allow) {
                    ForwardFlowLog.i(
                        traceId,
                        buildString {
                            append("Drop call session stage=")
                            append(resolvedCallStage.ifBlank { "<empty>" })
                            append(" key=")
                            append(callSessionDecision.key)
                            append(" source=")
                            append(forwardSource)
                        },
                    )
                    markResult(RESULT_DROP_DUPLICATE, "call_session_drop")
                    return@runCatching
                }

                XLog.i("IPC verified and received message from: %s", sender ?: "")
                val normalizedSubId = subId ?: 0
                val resolvedSimSlot = resolveSimSlot(rawSlot, normalizedSubId)
                val contactName = SourceMetadataResolver.resolveContactName(context, sender ?: "")
                val phoneArea = SourceMetadataResolver.resolvePhoneArea(sender ?: "")
                XLog.i(
                    "Resolved metadata: sim_slot=%d sub_id=%d contact=%s area=%s",
                    resolvedSimSlot,
                    normalizedSubId,
                    contactName.ifBlank { "<empty>" },
                    phoneArea.ifBlank { "<empty>" },
                )
                ForwardFlowLog.d(
                    traceId,
                    buildString {
                        append("Resolved metadata simSlot=")
                        append(resolvedSimSlot)
                        append(" subId=")
                        append(normalizedSubId)
                        append(" contact=")
                        append(contactName.ifBlank { "<empty>" })
                        append(" area=")
                        append(phoneArea.ifBlank { "<empty>" })
                    },
                )

                val relayMessageType = when {
                    msgTypeStr == "app_notify" -> MessageType.APP_NOTIFY
                    msgTypeStr == "call_notify" -> MessageType.CALL_NOTIFY
                    !smsCode.isNullOrBlank() -> MessageType.SMS_CODE
                    else -> MessageType.SMS_PLAIN
                }
                val relayEvent = RelayEvent(
                    messageType = relayMessageType,
                    sourceType = forwardSource,
                    sender = sender.orEmpty(),
                    body = body.orEmpty(),
                    timestamp = date,
                    packageName = packageName.orEmpty(),
                    notifyChannelId = notifyChannelId,
                    companyOrAppName = company.orEmpty(),
                    smsCode = smsCode,
                    callType = callType,
                    callStage = resolvedCallStage,
                    simSlot = resolvedSimSlot,
                    subId = normalizedSubId,
                    contactName = contactName,
                    phoneArea = phoneArea,
                )
                ForwardFlowLog.i(
                    traceId,
                    "RelayEvent normalized " +
                        "type=${relayEvent.messageType.name.lowercase()} " +
                        "pkg=${relayEvent.packageName.ifBlank { "<none>" }} " +
                        "source=$forwardSource",
                )
                val pipelineResult = runBlocking {
                    eventPipeline.process(
                        event = relayEvent,
                        traceId = traceId,
                    )
                }
                if (pipelineResult.dispatchError != null) {
                    markResult(RESULT_DISPATCH_FAILED, "dispatch_failed")
                } else {
                    markResult(RESULT_OK, pipelineResult.blockedReason ?: "processed")
                }
            }.onFailure { error ->
                XLog.e("ForwardReceiver unexpected error", error)
                ForwardFlowLog.e(
                    traceId,
                    buildString {
                        append("ForwardReceiver unexpected error action=")
                        append(intent.action)
                        append(" event=")
                        append(eventId.ifBlank { "<none>" })
                    },
                    error,
                )
                if (!resultMarked) {
                    markResult(RESULT_DISPATCH_FAILED, "receiver_exception")
                }
            }
            if (!resultMarked) {
                markResult(RESULT_DISPATCH_FAILED, "receiver_no_result")
            }
            ForwardFlowLog.d(traceId, "ForwardReceiver finished")
            pendingResult.finish()
        }
        runCatching {
            FORWARD_EXECUTOR.execute(task)
        }.onFailure { error ->
            XLog.e("ForwardReceiver failed to schedule task", error)
            ForwardFlowLog.e(
                traceId,
                buildString {
                    append("ForwardReceiver schedule failed action=")
                    append(intent.action)
                    append(" event=")
                    append(eventId.ifBlank { "<none>" })
                },
                error,
            )
            setOrderedResult(pendingResult, ordered, RESULT_DISPATCH_FAILED, "executor_rejected", eventId)
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "ForwardReceiver"
        private const val NOTIFY_DEDUP_WINDOW_MS = 10_000L
        private const val NMS_HOOK_SUPPRESS_TTL_MS = 30_000L
        private const val NOTIFY_DEDUP_MAX_ENTRIES = 256
        private const val API_LEVEL_34 = 34
        private const val RESULT_OK = 0
        private const val RESULT_REJECT_ACTION = -101
        private const val RESULT_REJECT_TOKEN = -102
        private const val RESULT_REJECT_APP_GATE = -103
        private const val RESULT_DROP_DUPLICATE = -104
        private const val RESULT_DISPATCH_FAILED = -105
        private const val FORWARD_WORKER_COUNT = 2
        private const val SYSTEM_UID = 1000
        private const val PHONE_UID = 1001
        private val workerIndex = AtomicInteger(1)
        private val FORWARD_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(FORWARD_WORKER_COUNT) { runnable ->
            Thread(runnable, "ForwardReceiverWorker-${workerIndex.getAndIncrement()}")
        }
        private val recentNotify = ConcurrentHashMap<String, Long>()
        private val nmsHookSeen = ConcurrentHashMap<String, Long>()
        private const val ROUTE_NMS_HOOK = "nms_hook"
        private const val ROUTE_TELEPHONY_STATE = "telephony_state"
    }

    private fun shouldAllowSystemTokenBypass(
        msgType: String,
        forwardSource: String,
        sentFromUid: Int?,
    ): Boolean {
        // Keep strict token verification by default.
        // Controlled bypass is only for system-origin paths when token lookup is temporarily unavailable.
        return when {
            (msgType == "app_notify" || msgType == "call_notify") && forwardSource == "nms_hook" -> {
                if (sentFromUid == SYSTEM_UID) {
                    true
                } else if (Build.VERSION.SDK_INT < API_LEVEL_34 && sentFromUid == null) {
                    XLog.w("IPC token bypass accepted for nms_hook without sender uid (API<34)")
                    true
                } else {
                    false
                }
            }
            msgType == "sms" && forwardSource == "sms_hook" -> {
                if (sentFromUid == SYSTEM_UID || sentFromUid == PHONE_UID) {
                    true
                } else if (Build.VERSION.SDK_INT < API_LEVEL_34 && sentFromUid == null) {
                    XLog.w("IPC token bypass accepted for sms_hook without sender uid (API<34)")
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun resolveSentFromUidCompat(): Int? {
        if (Build.VERSION.SDK_INT < API_LEVEL_34) return null
        return runCatching { getSentFromUid() }.getOrNull()
    }

    private fun resolveSentFromPackageCompat(): String? {
        if (Build.VERSION.SDK_INT < API_LEVEL_34) return null
        return runCatching { getSentFromPackage() }.getOrNull()
    }

    private fun resolveSimSlot(rawSlot: Int?, subId: Int): Int {
        if (subId > 0) {
            val slotFromSubId = runCatching { SubscriptionManager.getSlotIndex(subId) }.getOrDefault(-1)
            if (slotFromSubId >= 0) return slotFromSubId
        }
        val slot = rawSlot ?: return -1
        return when {
            slot in 0..1 -> slot
            slot == 2 -> 1
            else -> -1
        }
    }

    private fun readIntExtra(intent: Intent, vararg keys: String): Int? {
        for (key in keys) {
            if (!intent.hasExtra(key)) continue
            val intValue = intent.getIntExtra(key, Int.MIN_VALUE)
            if (intValue != Int.MIN_VALUE) return intValue
            val longValue = intent.getLongExtra(key, Long.MIN_VALUE)
            if (longValue != Long.MIN_VALUE) return longValue.toInt()
            intent.getStringExtra(key)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun shouldDropDuplicateNotify(
        msgType: String,
        packageName: String?,
        sender: String?,
        body: String?,
        notifyChannelId: String?,
    ): Boolean {
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
            return false
        }
        val key = buildString {
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

        val now = System.currentTimeMillis()
        val previous = recentNotify[key]
        if (previous != null && now - previous < NOTIFY_DEDUP_WINDOW_MS) {
            return true
        }
        recentNotify[key] = now

        if (recentNotify.size > NOTIFY_DEDUP_MAX_ENTRIES) {
            val cutoff = now - NOTIFY_DEDUP_WINDOW_MS * 2
            recentNotify.entries.removeIf { it.value < cutoff }
        }
        return false
    }

    private fun shouldDropOngoingCallNotify(
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

    private fun markNmsHookSeen(key: String) {
        val now = System.currentTimeMillis()
        nmsHookSeen[key] = now
        if (nmsHookSeen.size > NOTIFY_DEDUP_MAX_ENTRIES) {
            val cutoff = now - NMS_HOOK_SUPPRESS_TTL_MS * 2
            nmsHookSeen.entries.removeIf { it.value < cutoff }
        }
    }

    private fun shouldDropTelephonyState(key: String): Boolean {
        val now = System.currentTimeMillis()
        val seenAt = nmsHookSeen[key] ?: return false
        return now - seenAt < NMS_HOOK_SUPPRESS_TTL_MS
    }

    private fun shouldForwardAppNotify(
        runtimeGraph: StorageRuntimeGraph,
        packageName: String?,
        traceId: String,
        forwardSource: String,
    ): Boolean {
        val pkg = packageName.orEmpty().trim()
        if (pkg.isEmpty()) {
            ForwardFlowLog.w(
                traceId,
                "App notify gate pkg=<empty> source=$forwardSource final_decision=drop reason=empty_package",
            )
            XLog.w("App notify gate: empty package source=%s final_decision=drop", forwardSource)
            return false
        }
        val appInfo = runCatching { runtimeGraph.database.appInfoDao().getByPackageName(pkg) }.getOrElse { error ->
            ForwardFlowLog.e(
                traceId,
                "App notify gate query failed pkg=$pkg source=$forwardSource final_decision=drop",
                error,
            )
            XLog.e("App notify gate query failed: pkg=$pkg source=$forwardSource", error)
            return false
        }
        val enabled = appInfo?.forwarding == true
        val allowWhenMissing = appInfo == null
        val state = when {
            appInfo == null -> "missing"
            enabled -> "enabled"
            else -> "disabled"
        }
        val finalDecision = when {
            enabled -> "forward"
            allowWhenMissing -> "allow"
            else -> "drop"
        }
        ForwardFlowLog.i(
            traceId,
            "App notify gate pkg=$pkg source=$forwardSource state=$state final_decision=$finalDecision",
        )
        XLog.d(
            "App notify gate: pkg=%s source=%s state=%s final_decision=%s",
            pkg,
            forwardSource,
            state,
            finalDecision,
        )
        return enabled || allowWhenMissing
    }

    private fun setOrderedResult(
        pendingResult: PendingResult,
        ordered: Boolean,
        code: Int,
        reason: String,
        eventId: String,
    ) {
        if (!ordered) return
        runCatching {
            pendingResult.setResultCode(code)
            pendingResult.setResultData("reason=$reason;event_id=${eventId.ifBlank { "<none>" }}")
        }
    }

    private fun buildTraceId(intent: Intent, eventId: String): String {
        if (eventId.isNotBlank()) return eventId
        val pkg = intent.getStringExtra("packageName") ?: "unknown"
        val now = System.currentTimeMillis().toString(36)
        val suffix = kotlin.math.abs((pkg + now).hashCode()).toString(36)
        return "${now}_$suffix"
    }

}
