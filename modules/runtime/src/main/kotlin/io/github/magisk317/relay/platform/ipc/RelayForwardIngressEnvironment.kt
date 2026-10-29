package io.github.magisk317.relay.platform.ipc

import android.content.Context
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.forward.engine.event.RelayEvent
import io.github.magisk317.forward.runtime.ingress.AppForwardingState
import io.github.magisk317.forward.runtime.ingress.BlacklistHitRecord
import io.github.magisk317.forward.runtime.ingress.ForwardIngressEnvironment
import io.github.magisk317.forward.runtime.ingress.ForwardIngressTrace
import io.github.magisk317.forward.runtime.ingress.ForwardPipelineOutcome
import io.github.magisk317.forward.runtime.ingress.TelephonyCodeMatch
import io.github.magisk317.relay.android.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.android.platform.metadata.SourceMetadataResolver
import io.github.magisk317.relay.android.sms.SmsCodeUtils
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.contract.xpbridge.XpSmsBlacklistHitRecord
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache

/**
 * Bridges the shared ingest receivers to the relay product: settings, app
 * gate, blacklist sink, contact metadata, rule catalog and the event pipeline.
 */
class RelayForwardIngressEnvironment(
    private val appContext: Context,
) : ForwardIngressEnvironment {

    override val trace: ForwardIngressTrace = object : ForwardIngressTrace {
        override fun d(traceId: String?, message: String) = ForwardFlowLog.d(traceId, message)
        override fun i(traceId: String?, message: String) = ForwardFlowLog.i(traceId, message)
        override fun w(traceId: String?, message: String) = ForwardFlowLog.w(traceId, message)
        override fun e(traceId: String?, message: String, throwable: Throwable?) =
            ForwardFlowLog.e(traceId, message, throwable)
    }

    override val forwardAction: String = PrefConst.ACTION_FORWARD_SMS

    override val customMessageAction: String = PrefConst.ACTION_INGEST_CUSTOM_MESSAGE

    override suspend fun expectedIpcToken(): String {
        val deps = RuntimeDependencies.get()
        return RuntimeSettingsCache.getString(
            key = PrefConst.KEY_IPC_TOKEN,
            defaultValue = "",
        ) { key, defaultValue ->
            deps.preferenceDataSource.getString(key, defaultValue)
        }
    }

    override suspend fun smsCodeDedupWindowSeconds(): Int = runCatching {
        val deps = RuntimeDependencies.get()
        deps.preferenceDataSource.getString(
            PrefConst.KEY_SMS_FORWARD_DEDUP_WINDOW_SEC,
            PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT.toString(),
        ).toIntOrNull()?.coerceIn(
            PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MIN,
            PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MAX,
        ) ?: PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT
    }.getOrDefault(PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT)

    override suspend fun isAppNotifyMessageTypeEnabled(): Boolean = runCatching {
        RuntimeDependencies.get().preferenceDataSource.getBoolean(
            PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
            true,
        )
    }.getOrDefault(true)

    override suspend fun isForwardAppNotifyEnabled(): Boolean = runCatching {
        RuntimeDependencies.get().preferenceDataSource.getBoolean(
            PrefConst.KEY_FORWARD_APP_NOTIFY_ENABLED,
            true,
        )
    }.getOrDefault(true)

    override suspend fun appForwardingState(packageName: String): AppForwardingState? {
        val appInfo = runCatching {
            RuntimeDependencies.get().database.appInfoDao().getByPackageName(packageName)
        }.getOrNull() ?: return null
        val enabled = appInfo.forwarding == true
        return AppForwardingState(
            forwarding = enabled,
            forwardingConfigured = appInfo.forwardingConfigured == true || enabled,
        )
    }

    override suspend fun recordBlacklistHit(record: BlacklistHitRecord): Long? {
        return RuntimeRecordFacade(appContext).insertSmsBlacklistHit(record.toXpRecord())
    }

    override fun resolveContactName(sender: String): String =
        SourceMetadataResolver.resolveContactName(appContext, sender)

    override fun resolvePhoneArea(sender: String): String =
        SourceMetadataResolver.resolvePhoneArea(sender)

    override suspend fun parseTelephonyCode(content: String): TelephonyCodeMatch? {
        val parsed = runCatching {
            SmsCodeUtils.parseSmsCodeResultIfExists(appContext, content)
        }.getOrNull() ?: return null
        val code = parsed.code.trim()
        if (code.isBlank()) return null
        return TelephonyCodeMatch(
            code = code,
            company = SmsCodeUtils.parseCompany(content),
        )
    }

    override suspend fun processEvent(event: RelayEvent, traceId: String): ForwardPipelineOutcome {
        val result = RuntimeDependencies.get().eventPipeline.process(
            event = event,
            traceId = traceId,
        )
        return ForwardPipelineOutcome(
            dispatched = result.dispatched,
            blockedReason = result.blockedReason,
            dispatchError = result.dispatchError?.let { error ->
                error.message ?: error.javaClass.simpleName
            },
        )
    }

    private fun BlacklistHitRecord.toXpRecord(): XpSmsBlacklistHitRecord = XpSmsBlacklistHitRecord(
        eventId = eventId,
        source = source,
        sender = sender,
        body = body,
        smsDate = smsDate,
        matchType = matchType,
        pattern = pattern,
        actionDelete = actionDelete,
        actionBlock = actionBlock,
        blockReason = blockReason,
        createdAt = createdAt,
    )
}
