package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.CallSessionTracker
import io.github.magisk317.relay.android.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.android.platform.metadata.SourceMetadataResolver
import io.github.magisk317.relay.android.sms.SmsCodeUtils
import io.github.magisk317.smscode.domain.utils.RecentEventDeduplicator
import io.github.magisk317.smscode.domain.utils.SmsForwardDedupKeyFactory
import io.github.magisk317.smscode.domain.utils.SmsForwardDedupSpec
import io.github.magisk317.smscode.runtime.contract.ipc.IpcTokenMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import io.github.magisk317.xposed.logging.MagiskOtel

class ForwardReceiver : BroadcastReceiver() {
    @Suppress("CyclomaticComplexMethod")
    override fun onReceive(context: Context, intent: Intent) {
        val ordered = isOrderedBroadcast
        val pendingResult = goAsync()
        var eventId = ""
        var traceId = newReceiverTraceId()

        RECEIVER_SCOPE.launch {
            var resultMarked = false
            var broadcastFinished = false
            val startedAt = System.nanoTime()
            fun markResult(code: Int, reason: String) {
                resultMarked = true
                setOrderedResult(pendingResult, ordered, code, reason, eventId)
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                val ok = code == RESULT_OK
                MagiskOtel.event(
                    name = "sms.ingest",
                    attributes = mapOf(
                        "result" to if (ok) "ok" else if (code == RESULT_DISPATCH_FAILED) "error" else "skip",
                        "duration_ms" to durationMs.toString(),
                        "process" to "main",
                        "reason" to reason,
                        "event_id_present" to eventId.isNotBlank().toString(),
                    ),
                    statusOk = ok || code != RESULT_DISPATCH_FAILED,
                )
            }
            fun finishBroadcast() {
                if (broadcastFinished) return
                broadcastFinished = true
                pendingResult.finish()
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
                if (intent.action != PrefConst.ACTION_FORWARD_SMS) {
                    XLog.e("Rejecting broadcast with invalid action: %s", intent.action)
                    ForwardFlowLog.w(traceId, "Reject invalid action=${intent.action}")
                    markResult(RESULT_REJECT_ACTION, "invalid_action")
                    return@runCatching
                }

                val deps = RuntimeDependencies.get()
                val receivedToken = intent.getStringExtra(ForwardBroadcastContract.EXTRA_IPC_TOKEN)
                val expectedToken = RuntimeSettingsCache.getString(
                    key = PrefConst.KEY_IPC_TOKEN,
                    defaultValue = "",
                ) { key, defaultValue ->
                    deps.preferenceDataSource.getString(key, defaultValue)
                }
                if (!IpcTokenMatcher.matches(expectedToken, receivedToken)) {
                    XLog.e("IPC Token mismatch! Security breach attempt or uninitialized token. Rejecting broadcast.")
                    val rejectTokenMessage = buildString {
                        append("Reject token mismatch")
                        append(" expectedEmpty=")
                        append(expectedToken.isBlank())
                        append(" receivedEmpty=")
                        append(receivedToken.isNullOrBlank())
                    }
                    ForwardFlowLog.w(
                        traceId,
                        rejectTokenMessage,
                    )
                    markResult(RESULT_REJECT_TOKEN, "token_mismatch")
                    return@runCatching
                }

                val rawPayload = ForwardBroadcastPayload.fromIntent(intent)
                val payloadRejection = IpcPayloadLimits.validateForward(rawPayload)
                if (payloadRejection != null) {
                    ForwardFlowLog.w(traceId, "Reject oversized payload reason=$payloadRejection")
                    markResult(RESULT_REJECT_PAYLOAD, payloadRejection)
                    return@runCatching
                }
                eventId = rawPayload.eventId
                traceId = buildTraceId(eventId, rawPayload.packageName)
                val eventPipeline = deps.eventPipeline
                val originalMsgTypeStr = rawPayload.msgType
                val forwardSource = rawPayload.forwardSource
                if (originalMsgTypeStr == ForwardBroadcastContract.MSG_TYPE_BLACKLIST_HIT) {
                    val hit = BlacklistHitBroadcast.fromIntent(intent)
                    if (hit == null) {
                        ForwardFlowLog.w(traceId, "Blacklist hit payload missing event_id, drop")
                        markResult(RESULT_REJECT_ACTION, "blacklist_hit_invalid")
                        return@runCatching
                    }
                    val insertedId = RuntimeRecordFacade(context).insertSmsBlacklistHit(hit)
                    ForwardFlowLog.i(
                        traceId,
                        "Blacklist hit recorded event=${hit.eventId} source=${hit.source} " +
                            "pattern=${hit.pattern ?: "<none>"} id=${insertedId ?: -1}",
                    )
                    markResult(RESULT_OK, "blacklist_hit_recorded")
                    return@runCatching
                }
                val payload = normalizeNmsSmsPayload(
                    context = context,
                    payload = rawPayload,
                    traceId = traceId,
                )
                val normalizedSender = payload.sender
                val normalizedBody = payload.body
                val normalizedDate = payload.date
                val normalizedCompany = payload.company
                val normalizedPackageName = payload.packageName
                val normalizedNotifyChannelId = payload.notifyChannelId
                val msgTypeStr = payload.msgType
                val normalizedCallStage = payload.callStage
                val normalizedSubId = payload.subId
                val normalizedRawSlot = payload.simSlot
                val normalizedCallType = payload.callType
                if (
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_SMS &&
                    forwardSource == ForwardBroadcastContract.SOURCE_SMS_HOOK
                ) {
                    val dedupKey = SmsForwardDedupKeyFactory.build(
                        SmsForwardDedupSpec(
                            eventId = eventId,
                            sender = normalizedSender,
                            body = normalizedBody,
                            timestamp = normalizedDate,
                            msgType = msgTypeStr,
                            source = forwardSource,
                            simSlot = normalizedRawSlot,
                            subId = normalizedSubId,
                        ),
                    )
                    if (recentSmsForward.shouldDrop(dedupKey)) {
                        ForwardFlowLog.i(
                            traceId,
                            buildString {
                                append("Drop duplicate sms forward event=")
                                append(eventId.ifBlank { "<none>" })
                                append(" key=")
                                append(dedupKey)
                                append(" source=")
                                append(forwardSource)
                            },
                        )
                        markResult(RESULT_DROP_DUPLICATE, "duplicate_sms_drop")
                        return@runCatching
                    }
                    // Code-based long window dedup: same code within configurable window
                    val smsCode = payload.smsCode
                    if (!smsCode.isNullOrBlank()) {
                        val codeDedupWindowSec = runCatching {
                            deps.preferenceDataSource.getString(
                                PrefConst.KEY_SMS_FORWARD_DEDUP_WINDOW_SEC,
                                PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT.toString(),
                            ).toIntOrNull()?.coerceIn(
                                PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MIN,
                                PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MAX,
                            ) ?: PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT
                        }.getOrDefault(PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT)
                        val codeDedupKey = "code:${smsCode.trim()}"
                        val codeDedup = getCodeDedup(codeDedupWindowSec * 1000L)
                        if (codeDedup.shouldDrop(codeDedupKey)) {
                            ForwardFlowLog.i(
                                traceId,
                                buildString {
                                    append("Drop duplicate sms code forward code=")
                                    append(smsCode)
                                    append(" key=")
                                    append(codeDedupKey)
                                    append(" source=")
                                    append(forwardSource)
                                },
                            )
                            markResult(RESULT_DROP_DUPLICATE, "duplicate_code_drop")
                            return@runCatching
                        }
                    }
                }
                if (
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY &&
                    forwardSource == ForwardBroadcastContract.SOURCE_NMS_HOOK &&
                    ForwardReceiverPolicy.shouldSuppressTelephonyNmsCopyAfterSmsHook(
                        smsCode = payload.smsCode,
                        company = normalizedCompany,
                        sender = normalizedSender,
                        body = normalizedBody,
                        packageName = normalizedPackageName,
                        recentForwardedSmsHook = recentForwardedSmsHook,
                    )
                ) {
                    ForwardFlowLog.i(
                        traceId,
                        buildString {
                            append("Drop telephony nms app notify after sms_hook success pkg=")
                            append(normalizedPackageName.orEmpty().ifBlank { "<empty>" })
                            append(" sender=")
                            append(normalizedSender.orEmpty().ifBlank { "<empty>" })
                        },
                    )
                    markResult(RESULT_DROP_DUPLICATE, "telephony_nms_suppressed_after_sms_hook")
                    return@runCatching
                }
                if (
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY &&
                    !shouldForwardAppNotify(deps, normalizedPackageName, traceId, forwardSource)
                ) {
                    markResult(RESULT_REJECT_APP_GATE, "app_gate_drop")
                    return@runCatching
                }
                if (
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_SMS &&
                    forwardSource == ForwardBroadcastContract.SOURCE_NMS_HOOK &&
                    ForwardReceiverPolicy.shouldSuppressReclassifiedNmsSms(
                        smsCode = payload.smsCode,
                        company = normalizedCompany,
                        sender = normalizedSender,
                        body = normalizedBody,
                        packageName = normalizedPackageName,
                        recentSuccessfulSmsHook = recentSuccessfulSmsHook,
                    )
                ) {
                    ForwardFlowLog.i(
                        traceId,
                        buildString {
                            append("Drop reclassified nms sms after successful sms_hook code=")
                            append(payload.smsCode.orEmpty())
                            append(" company=")
                            append(normalizedCompany.orEmpty().ifBlank { "<empty>" })
                            append(" sender=")
                            append(normalizedSender.orEmpty().ifBlank { "<empty>" })
                        },
                    )
                    markResult(RESULT_DROP_DUPLICATE, "nms_sms_suppressed_after_sms_hook")
                    return@runCatching
                }
                if (
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY &&
                    ForwardReceiverPolicy.shouldDropOngoingCallNotify(
                        normalizedCallStage,
                        normalizedNotifyChannelId,
                        normalizedBody,
                    )
                ) {
                    ForwardFlowLog.i(
                        traceId,
                        buildString {
                            append("Drop ongoing call notify stage=")
                            append(normalizedCallStage.ifBlank { "<empty>" })
                            append(" channel=")
                            append(normalizedNotifyChannelId)
                            append(" source=")
                            append(forwardSource)
                        },
                    )
                    markResult(RESULT_DROP_DUPLICATE, "ongoing_drop")
                    return@runCatching
                }
                if (msgTypeStr == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY) {
                    val sourceKey = CallSessionTracker.buildSourceKey(
                        sender = normalizedSender,
                        body = normalizedBody,
                        callType = normalizedCallType,
                        packageName = normalizedPackageName,
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
                    ForwardReceiverPolicy.shouldDropDuplicateForward(
                        msgType = msgTypeStr,
                        forwardSource = forwardSource,
                        packageName = normalizedPackageName,
                        sender = normalizedSender,
                        body = normalizedBody,
                        notifyChannelId = normalizedNotifyChannelId,
                        smsCode = payload.smsCode,
                        recentNotify = recentNotify,
                    )
                ) {
                    XLog.i(
                        "Drop duplicate notify: type=%s pkg=%s sender=%s channel=%s callType=%d source=%s",
                        msgTypeStr,
                        normalizedPackageName.orEmpty(),
                        normalizedSender.orEmpty(),
                        normalizedNotifyChannelId,
                        normalizedCallType,
                        forwardSource,
                    )
                    ForwardFlowLog.i(
                        traceId,
                        buildString {
                            append("Drop duplicate notify type=")
                            append(msgTypeStr)
                            append(" pkg=")
                            append(normalizedPackageName.orEmpty())
                            append(" sender=")
                            append(normalizedSender.orEmpty())
                            append(" channel=")
                            append(normalizedNotifyChannelId)
                            append(" callType=")
                            append(normalizedCallType)
                            append(" source=")
                            append(forwardSource)
                        },
                    )
                    markResult(RESULT_DROP_DUPLICATE, "duplicate_drop")
                    return@runCatching
                }
                val callSessionDecision = if (msgTypeStr == ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY) {
                    CallSessionTracker.evaluate(
                        stageRaw = normalizedCallStage,
                        sender = normalizedSender,
                        body = normalizedBody,
                        callType = normalizedCallType,
                        packageName = normalizedPackageName,
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

                XLog.i("IPC verified and received message from: %s", normalizedSender ?: "")
                val resolvedSubId = normalizedSubId ?: 0
                val resolvedSimSlot = ForwardReceiverPolicy.resolveSimSlot(normalizedRawSlot, resolvedSubId) { id ->
                    resolvePlatformSlotIndex(id)
                }
                val contactName = SourceMetadataResolver.resolveContactName(context, normalizedSender ?: "")
                val phoneArea = SourceMetadataResolver.resolvePhoneArea(normalizedSender ?: "")
                XLog.i(
                    "Resolved metadata: sim_slot=%d sub_id=%d contact=%s area=%s",
                    resolvedSimSlot,
                    resolvedSubId,
                    contactName.ifBlank { "<empty>" },
                    phoneArea.ifBlank { "<empty>" },
                )
                ForwardFlowLog.d(
                    traceId,
                    buildString {
                        append("Resolved metadata simSlot=")
                        append(resolvedSimSlot)
                        append(" subId=")
                        append(resolvedSubId)
                        append(" contact=")
                        append(contactName.ifBlank { "<empty>" })
                        append(" area=")
                        append(phoneArea.ifBlank { "<empty>" })
                    },
                )

                val relayEvent = payload.copy(
                    callStage = resolvedCallStage,
                    simSlot = resolvedSimSlot,
                    subId = resolvedSubId,
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
                if (
                    msgTypeStr == ForwardBroadcastContract.MSG_TYPE_SMS &&
                    forwardSource == ForwardBroadcastContract.SOURCE_SMS_HOOK &&
                    relayEvent.messageType == io.github.magisk317.relay.contract.constant.MessageType.SMS_CODE
                ) {
                    ForwardReceiverPolicy.markSuccessfulSmsHookDispatch(
                        smsCode = payload.smsCode,
                        company = normalizedCompany,
                        sender = normalizedSender,
                        body = normalizedBody,
                        recentSuccessfulSmsHook = recentSuccessfulSmsHook,
                    )
                }
                // Sync trigger is handled by EventPipeline.finally via messageSyncTrigger callback.
                // No duplicate scheduleMessageTriggeredSync call needed here.

                if (!resultMarked) {
                    markResult(RESULT_OK, "accepted_async")
                    ForwardFlowLog.i(traceId, "ForwardReceiver broadcast acknowledged result=accepted_async")
                    finishBroadcast()
                }

                val pipelineResult = eventPipeline.process(
                    event = relayEvent,
                    traceId = traceId,
                )
                if (pipelineResult.dispatchError != null) {
                    ForwardFlowLog.w(
                        traceId,
                        "Async dispatch finished with error after broadcast ack: " +
                            (pipelineResult.dispatchError.message ?: pipelineResult.dispatchError.javaClass.simpleName),
                    )
                } else {
                    ForwardFlowLog.i(
                        traceId,
                        "Async dispatch completed dispatched=${pipelineResult.dispatched} " +
                            "blockedReason=${pipelineResult.blockedReason ?: "<none>"}",
                    )
                    if (
                        pipelineResult.dispatched &&
                        msgTypeStr == ForwardBroadcastContract.MSG_TYPE_SMS &&
                        forwardSource == ForwardBroadcastContract.SOURCE_SMS_HOOK
                    ) {
                        ForwardReceiverPolicy.markForwardedSmsHookDispatch(
                            smsCode = payload.smsCode,
                            company = normalizedCompany,
                            sender = normalizedSender,
                            body = normalizedBody,
                            recentForwardedSmsHook = recentForwardedSmsHook,
                        )
                        ForwardReceiverPolicy.markSuccessfulSmsHookDispatch(
                            smsCode = payload.smsCode,
                            company = normalizedCompany,
                            sender = normalizedSender,
                            body = normalizedBody,
                            recentSuccessfulSmsHook = recentSuccessfulSmsHook,
                        )
                    }
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
            finishBroadcast()
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
        private const val RESULT_REJECT_PAYLOAD = -106
        private val RECEIVER_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val recentNotify = ConcurrentHashMap<String, Long>()
        private val recentForwardedSmsHook = ConcurrentHashMap<String, Long>()
        private val recentSmsForward = RecentEventDeduplicator(windowMs = SMS_FORWARD_DEDUP_WINDOW_MS)
        private val recentSuccessfulSmsHook = ConcurrentHashMap<String, Long>()
        private val nmsHookSeen = ConcurrentHashMap<String, Long>()
        private val recentCodeDedupRef = AtomicReference<RecentEventDeduplicator?>(null)
        private val recentCodeDedupWindowMs = AtomicLong(0L)

        internal fun getCodeDedup(windowMs: Long): RecentEventDeduplicator {
            if (recentCodeDedupWindowMs.get() == windowMs) {
                recentCodeDedupRef.get()?.let { return it }
            }
            val fresh = RecentEventDeduplicator(windowMs = windowMs)
            recentCodeDedupRef.set(fresh)
            recentCodeDedupWindowMs.set(windowMs)
            return fresh
        }
        private const val ROUTE_NMS_HOOK = ForwardBroadcastContract.SOURCE_NMS_HOOK
        private const val ROUTE_TELEPHONY_STATE = ForwardBroadcastContract.SOURCE_TELEPHONY_STATE
        private const val SMS_FORWARD_DEDUP_WINDOW_MS = 10_000L
        private val TELEPHONY_NMS_PACKAGE_ALLOWLIST = setOf(
            "com.android.phone",
            "com.android.providers.telephony",
            "com.android.mms",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
        )
    }

    private suspend fun normalizeNmsSmsPayload(
        context: Context,
        payload: ForwardBroadcastPayload,
        traceId: String,
    ): ForwardBroadcastPayload {
        if (payload.msgType != ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY) return payload
        if (payload.forwardSource != ForwardBroadcastContract.SOURCE_NMS_HOOK) return payload
        val packageName = payload.packageName.orEmpty().trim()
        if (!isTelephonyNmsPackage(packageName)) return payload
        val content = buildNmsNotificationContent(payload)
        if (content.isBlank()) return payload
        val parsedResult = runCatching {
            SmsCodeUtils.parseSmsCodeResultIfExists(context, content)
        }.getOrNull() ?: return payload
        val smsCode = parsedResult.code.trim()
        if (smsCode.isBlank()) return payload
        val company = SmsCodeUtils.parseCompany(content).ifBlank {
            payload.company.orEmpty().ifBlank { payload.sender.orEmpty() }
        }
        ForwardFlowLog.i(
            traceId,
            "Reclassified nms_hook telephony notify to sms_code pkg=$packageName codeLength=${smsCode.length}",
        )
        XLog.w(
            "NMS telephony notify reclassified to sms_code: pkg=%s code_len=%d",
            packageName,
            smsCode.length,
        )
        return payload.copy(
            msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
            smsCode = smsCode,
            company = company.ifBlank { payload.company.orEmpty() }.takeIf { it.isNotBlank() },
        )
    }

    private fun resolvePlatformSlotIndex(subId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        return runCatching {
            android.telephony.SubscriptionManager.getSlotIndex(subId)
        }.getOrDefault(-1)
    }

    private fun buildNmsNotificationContent(payload: ForwardBroadcastPayload): String {
        return buildString {
            payload.sender?.trim()?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            payload.body?.trim()?.takeIf { it.isNotBlank() }?.let { append(it) }
        }.trim()
    }

    private fun isTelephonyNmsPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return packageName in TELEPHONY_NMS_PACKAGE_ALLOWLIST || packageName.contains("telephony")
    }

    private suspend fun shouldForwardAppNotify(
        deps: RuntimeDependencies,
        packageName: String?,
        traceId: String,
        forwardSource: String,
    ): Boolean {
        val messageTypeEnabled = deps.preferenceDataSource.getBoolean(
            PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
            true,
        )
        val forwardTypeEnabled = deps.preferenceDataSource.getBoolean(
            PrefConst.KEY_FORWARD_APP_NOTIFY_ENABLED,
            true,
        )
        val pkg = packageName.orEmpty().trim()
        if (pkg.isEmpty()) {
            ForwardFlowLog.w(
                traceId,
                "App notify gate pkg=<empty> source=$forwardSource " +
                    "messageTypeEnabled=$messageTypeEnabled forwardTypeEnabled=$forwardTypeEnabled " +
                    "final_decision=drop reason=empty_package",
            )
            XLog.w(
                "App notify gate: empty package source=%s messageTypeEnabled=%s forwardTypeEnabled=%s final_decision=drop",
                forwardSource,
                messageTypeEnabled,
                forwardTypeEnabled,
            )
            return false
        }
        val appInfo = runCatching {
            deps.database.appInfoDao().getByPackageName(pkg)
        }.getOrElse { error ->
            ForwardFlowLog.e(
                traceId,
                "App notify gate query failed pkg=$pkg source=$forwardSource " +
                    "messageTypeEnabled=$messageTypeEnabled forwardTypeEnabled=$forwardTypeEnabled " +
                    "final_decision=drop",
                error,
            )
            XLog.e("App notify gate query failed: pkg=$pkg source=$forwardSource", error)
            return false
        }
        val enabled = appInfo?.forwarding == true
        val configured = appInfo?.forwardingConfigured == true || enabled
        val allowWhenMissing = !configured
        val state = when {
            !configured -> "missing"
            enabled -> "enabled"
            else -> "disabled"
        }
        val finalDecision = when {
            !messageTypeEnabled -> "drop_message_type_disabled"
            !forwardTypeEnabled -> "drop_forward_type_disabled"
            enabled -> "forward"
            allowWhenMissing -> "drop_missing"
            else -> "drop"
        }
        ForwardFlowLog.i(
            traceId,
            "App notify gate pkg=$pkg source=$forwardSource state=$state " +
                "messageTypeEnabled=$messageTypeEnabled forwardTypeEnabled=$forwardTypeEnabled " +
                "final_decision=$finalDecision",
        )
        XLog.d(
            "App notify gate: pkg=%s source=%s state=%s messageTypeEnabled=%s forwardTypeEnabled=%s final_decision=%s",
            pkg,
            forwardSource,
            state,
            messageTypeEnabled,
            forwardTypeEnabled,
            finalDecision,
        )
        if (!messageTypeEnabled || !forwardTypeEnabled) return false
        return enabled
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

    private fun buildTraceId(eventId: String, packageName: String?): String {
        if (eventId.isNotBlank()) return eventId
        val now = System.currentTimeMillis().toString(36)
        val suffix = kotlin.math.abs((packageName.orEmpty() + now).hashCode()).toString(36)
        return "${now}_$suffix"
    }

    private fun newReceiverTraceId(): String =
        "ipc_${System.currentTimeMillis().toString(Character.MAX_RADIX)}"

}
