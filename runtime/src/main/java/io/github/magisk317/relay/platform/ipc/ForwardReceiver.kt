package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.CallSessionTracker
import io.github.magisk317.relay.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.bootstrap.RuntimeGraph
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
        val runtimeGraph = RuntimeGraph.from(context)
        val eventPipeline = runtimeGraph.eventPipeline
        val ordered = isOrderedBroadcast
        val pendingResult = goAsync()
        val initialPayload = ForwardBroadcastPayload.fromIntent(intent)
        val eventId = initialPayload.eventId
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

                val payload = ForwardBroadcastPayload.fromIntent(intent)
                val sender = payload.sender
                val body = payload.body
                val date = payload.date
                val company = payload.company
                val smsCode = payload.smsCode
                val packageName = payload.packageName
                val notifyChannelId = payload.notifyChannelId
                val receivedToken = intent.getStringExtra(ForwardBroadcastContract.EXTRA_IPC_TOKEN)
                val msgTypeStr = payload.msgType
                val forwardSource = payload.forwardSource
                val callStage = payload.callStage
                val sentFromUid = resolveSentFromUidCompat()
                val sentFromPkg = resolveSentFromPackageCompat()
                val subId = payload.subId
                val rawSlot = payload.simSlot
                val callType = payload.callType

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
                val allowSystemBypass = ForwardReceiverPolicy.shouldAllowSystemTokenBypass(
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
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY &&
                    !shouldForwardAppNotify(runtimeGraph, packageName, traceId, forwardSource)
                ) {
                    markResult(RESULT_REJECT_APP_GATE, "app_gate_drop")
                    return@runCatching
                }
                if (
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY &&
                    ForwardReceiverPolicy.shouldDropOngoingCallNotify(callStage, notifyChannelId, body)
                ) {
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
                if (msgTypeStr == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY) {
                    val sourceKey = CallSessionTracker.buildSourceKey(
                        sender = sender,
                        body = body,
                        callType = callType,
                        packageName = packageName,
                    )
                    if (forwardSource == ROUTE_NMS_HOOK) {
                        ForwardReceiverPolicy.markNmsHookSeen(sourceKey, nmsHookSeen)
                    } else if (
                        forwardSource == ROUTE_TELEPHONY_STATE &&
                        ForwardReceiverPolicy.shouldDropTelephonyState(sourceKey, nmsHookSeen)
                    ) {
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
                    (
                        msgTypeStr == ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY ||
                            msgTypeStr == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY
                        ) &&
                    ForwardReceiverPolicy.shouldDropDuplicateNotify(
                        msgType = msgTypeStr,
                        packageName = packageName,
                        sender = sender,
                        body = body,
                        notifyChannelId = notifyChannelId,
                        recentNotify = recentNotify,
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
                val callSessionDecision = if (msgTypeStr == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY) {
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
                if (
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY &&
                    callSessionDecision != null &&
                    !callSessionDecision.allow
                ) {
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
                val resolvedSimSlot = ForwardReceiverPolicy.resolveSimSlot(rawSlot, normalizedSubId) { id ->
                    runCatching { android.telephony.SubscriptionManager.getSlotIndex(id) }.getOrDefault(-1)
                }
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

                val relayEvent = payload.copy(
                    callStage = resolvedCallStage,
                    simSlot = resolvedSimSlot,
                    subId = normalizedSubId,
                ).toRelayEvent(
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
        private const val RESULT_OK = 0
        private const val RESULT_REJECT_ACTION = -101
        private const val RESULT_REJECT_TOKEN = -102
        private const val RESULT_REJECT_APP_GATE = -103
        private const val RESULT_DROP_DUPLICATE = -104
        private const val RESULT_DISPATCH_FAILED = -105
        private const val FORWARD_WORKER_COUNT = 2
        private val workerIndex = AtomicInteger(1)
        private val FORWARD_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(FORWARD_WORKER_COUNT) { runnable ->
            Thread(runnable, "ForwardReceiverWorker-${workerIndex.getAndIncrement()}")
        }
        private val recentNotify = ConcurrentHashMap<String, Long>()
        private val nmsHookSeen = ConcurrentHashMap<String, Long>()
        private const val ROUTE_NMS_HOOK = ForwardBroadcastContract.SOURCE_NMS_HOOK
        private const val ROUTE_TELEPHONY_STATE = ForwardBroadcastContract.SOURCE_TELEPHONY_STATE
    }

    private fun resolveSentFromUidCompat(): Int? {
        if (Build.VERSION.SDK_INT < ForwardReceiverPolicy.API_LEVEL_34) return null
        return runCatching { getSentFromUid() }.getOrNull()
    }

    private fun resolveSentFromPackageCompat(): String? {
        if (Build.VERSION.SDK_INT < ForwardReceiverPolicy.API_LEVEL_34) return null
        return runCatching { getSentFromPackage() }.getOrNull()
    }

    private fun shouldForwardAppNotify(
        runtimeGraph: RuntimeGraph,
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
            pendingResult.setResultData(
                "reason=$reason;${ForwardBroadcastContract.EXTRA_EVENT_ID}=${eventId.ifBlank { "<none>" }}",
            )
        }
    }

    private fun buildTraceId(intent: Intent, eventId: String): String {
        if (eventId.isNotBlank()) return eventId
        val pkg = intent.getStringExtra(ForwardBroadcastContract.EXTRA_PACKAGE_NAME) ?: "unknown"
        val now = System.currentTimeMillis().toString(36)
        val suffix = kotlin.math.abs((pkg + now).hashCode()).toString(36)
        return "${now}_$suffix"
    }

}
