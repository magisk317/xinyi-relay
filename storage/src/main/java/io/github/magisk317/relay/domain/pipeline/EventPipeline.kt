package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.ForwardFlowLog
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.service.DispatchPayloadContext
import io.github.magisk317.relay.domain.service.MessageFormatter
import io.github.magisk317.relay.domain.service.SystemInfoProvider
import io.github.magisk317.relay.domain.sender.SenderSettingSanitizer
import io.github.magisk317.relay.model.ForwardCommonConfig
import io.github.magisk317.relay.model.MsgInfo
import io.github.magisk317.relay.model.Sender

data class EventPipelineResult(
    val dispatched: Boolean,
    val blockedReason: String? = null,
    val dispatchError: Throwable? = null,
)

private data class RecordContext(
    val smsMsgType: Int,
    val recordId: Long?,
)

private data class SenderResolution(
    val allSenders: List<Sender>,
    val enabledSenders: List<Sender>,
    val selectedSenders: List<Sender>,
    val blockedReason: String? = null,
    val forcedStatus: Int? = null,
)

class EventPipeline(
    private val db: AppDatabase,
    private val eventGatekeeper: EventGatekeeper,
    private val routingResolver: RoutingResolver,
    private val senderSelector: SenderSelector,
    private val dispatchExecutor: DispatchExecutor,
    private val dispatchResultWriter: DispatchResultWriter,
    private val messageFormatter: MessageFormatter,
    private val systemInfoProvider: SystemInfoProvider,
    private val settingsRepository: SettingsRepository,
    private val preferenceDataSource: PreferenceDataSource,
) {
    suspend fun process(
        event: RelayEvent,
        preferredRecordId: Long? = null,
        traceId: String? = null,
    ): EventPipelineResult {
        val gateDecision = eventGatekeeper.check(event, traceId.orEmpty())
        if (!gateDecision.allowed) {
            ForwardFlowLog.w(traceId, "Event gate blocked type=${event.messageType} reason=${gateDecision.reason}")
            return EventPipelineResult(dispatched = false, blockedReason = gateDecision.reason)
        }

        val preRouteDecision = routingResolver.evaluatePreRoute(event)
        if (preRouteDecision.blocked) {
            ForwardFlowLog.w(
                traceId,
                "Forward filter pre-route blocked type=${event.messageType} pkg=${event.packageName} reason=${preRouteDecision.reason}",
            )
            return EventPipelineResult(dispatched = false, blockedReason = preRouteDecision.reason)
        }

        val recordContext = resolveRecordContext(event, preferredRecordId)

        if (!preferenceDataSource.getBoolean(PrefConst.KEY_RELAY_FEATURES_ENABLED, true)) {
            val reason = "转发功能已关闭"
            dispatchResultWriter.persistForwardResult(
                recordId = recordContext.recordId,
                results = emptyList(),
                defaultMessage = reason,
                msgTypeForAnalytics = recordContext.smsMsgType,
            )
            ForwardFlowLog.i(traceId, "Relay feature gate blocked sender dispatch type=${event.messageType}")
            return EventPipelineResult(dispatched = false, blockedReason = reason)
        }

        return runCatching {
            val senderResolution = resolveSenders(event, traceId)
            if (senderResolution.selectedSenders.isEmpty()) {
                val reason = senderResolution.blockedReason ?: "未启用任何转发通道"
                dispatchResultWriter.persistForwardResult(
                    recordId = recordContext.recordId,
                    results = emptyList(),
                    defaultMessage = reason,
                    forcedStatus = senderResolution.forcedStatus,
                    msgTypeForAnalytics = recordContext.smsMsgType,
                )
                ForwardFlowLog.w(traceId, "No eligible senders: $reason")
                return EventPipelineResult(dispatched = false, blockedReason = reason)
            }

            val msgForSend = buildDispatchPayload(event)
            val dispatchResults = dispatchExecutor.dispatchToSenders(
                senderResolution.selectedSenders,
                msgForSend,
                traceId,
            )
            dispatchResultWriter.persistForwardResult(
                recordId = recordContext.recordId,
                results = dispatchResults,
                defaultMessage = "未启用任何转发通道",
                msgTypeForAnalytics = recordContext.smsMsgType,
            )
            EventPipelineResult(dispatched = true)
        }.getOrElse { error ->
            XLog.e("Event pipeline failed", error)
            dispatchResultWriter.persistForwardResult(
                recordId = recordContext.recordId,
                results = emptyList(),
                defaultMessage = "转发异常: ${error.message ?: error.javaClass.simpleName}",
                forceFailed = true,
                msgTypeForAnalytics = recordContext.smsMsgType,
            )
            EventPipelineResult(dispatched = false, dispatchError = error)
        }
    }

    private suspend fun resolveRecordContext(
        event: RelayEvent,
        preferredRecordId: Long?,
    ): RecordContext {
        val smsMsgType = resolveSmsMsgType(event.messageType)
        val canRecord = isMessageTypeRecordEnabled(event.messageType)
        var recordId = preferredRecordId ?: dispatchResultWriter.findRecordIdByFingerprint(
            sender = event.sender,
            body = event.body,
            date = event.timestamp,
            msgType = smsMsgType,
        )
        if (
            canRecord &&
            recordId == null &&
            !(event.messageType == MessageType.CALL_NOTIFY && event.isCallAlertStart())
        ) {
            recordId = dispatchResultWriter.insertRecord(
                sender = event.sender,
                body = event.body,
                date = event.timestamp,
                company = event.companyOrAppName,
                smsCode = event.smsCode,
                packageName = event.packageName,
                notifyChannelId = event.notifyChannelId,
                msgType = smsMsgType,
                isCodeSms = event.messageType == MessageType.SMS_CODE,
                callType = event.callType,
            )
        }
        return RecordContext(
            smsMsgType = smsMsgType,
            recordId = recordId,
        )
    }

    private suspend fun resolveSenders(
        event: RelayEvent,
        traceId: String?,
    ): SenderResolution {
        val allSenders = db.senderDao().getAll()
            .map { it.toDomain() }
            .map(SenderSettingSanitizer::sanitizeSenderLenient)
        val enabledSenders = allSenders.filter { it.status == 1 }
        val baseSenders = senderSelector.selectBaseSenders(enabledSenders, event)
        val routing = routingResolver.resolve(baseSenders, event, traceId)
        if (routing.senders.isNotEmpty()) {
            return SenderResolution(
                allSenders = allSenders,
                enabledSenders = enabledSenders,
                selectedSenders = routing.senders,
            )
        }
        val filteredByRule = routing.filteredReasonParts.isNotEmpty()
        val blockedReason = if (filteredByRule) {
            "可用通道命中过滤规则，已拦截（${routing.filteredReasonParts.joinToString(" | ")}）"
        } else {
            routing.routingResult?.noEligibleReason
                ?: senderSelector.buildNoEligibleReason(allSenders, enabledSenders, event)
        }
        return SenderResolution(
            allSenders = allSenders,
            enabledSenders = enabledSenders,
            selectedSenders = emptyList(),
            blockedReason = blockedReason,
            forcedStatus = if (filteredByRule) SmsMsg.FORWARD_STATUS_BLOCKED else null,
        )
    }

    private suspend fun buildDispatchPayload(event: RelayEvent): MsgInfo {
        val effectiveConfig = resolveEffectiveConfig(event)
        val envSnapshot = systemInfoProvider.getSnapshot(effectiveConfig.deviceName)
        val dispatchContext = DispatchPayloadContext.from(event)
        val renderedContent = messageFormatter.format(event, dispatchContext, effectiveConfig, envSnapshot)
        return dispatchContext.toMsgInfo(event, renderedContent)
    }

    private suspend fun resolveEffectiveConfig(event: RelayEvent): ForwardCommonConfig {
        val commonConfig = settingsRepository.loadForwardCommonConfig()
        return when (event.messageType) {
            MessageType.APP_NOTIFY -> {
                val appConfig = db.appInfoDao().getByPackageName(event.packageName)
                if (appConfig?.notifyTemplate?.isNotBlank() == true) {
                    commonConfig.copy(messageTemplate = appConfig.notifyTemplate)
                } else {
                    val appNotifyTemplate = settingsRepository.loadAppNotifyTemplate()
                    if (appNotifyTemplate.isNotBlank()) {
                        commonConfig.copy(messageTemplate = appNotifyTemplate)
                    } else {
                        commonConfig
                    }
                }
            }

            MessageType.CALL_NOTIFY -> {
                val callNotifyTemplate = settingsRepository.loadCallNotifyTemplate()
                if (callNotifyTemplate.isNotBlank()) {
                    commonConfig.copy(messageTemplate = callNotifyTemplate)
                } else {
                    commonConfig
                }
            }

            else -> commonConfig
        }
    }

    private fun resolveSmsMsgType(messageType: MessageType): Int {
        return when (messageType) {
            MessageType.APP_NOTIFY -> SmsMsg.MSG_TYPE_APP_NOTIFY
            MessageType.CALL_NOTIFY -> SmsMsg.MSG_TYPE_CALL_NOTIFY
            MessageType.SMS_CODE, MessageType.SMS_PLAIN -> SmsMsg.MSG_TYPE_SMS
        }
    }

    private suspend fun isMessageTypeRecordEnabled(messageType: MessageType): Boolean {
        val key = when (messageType) {
            MessageType.SMS_CODE -> PrefConst.KEY_ENABLE_CODE_RECORDS_CODE
            MessageType.SMS_PLAIN -> PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS
            MessageType.APP_NOTIFY -> PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY
            MessageType.CALL_NOTIFY -> PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY
        }
        return preferenceDataSource.getBoolean(key, true)
    }
}
