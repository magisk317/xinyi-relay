package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.security.IpcTokenGate
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputResultBroadcastContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AutoInputResultHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val action: String
        get() = AutoInputActions.resultAction

    fun handle(context: Context, intent: Intent, onComplete: () -> Unit = {}) {
        scope.launch {
            try {
                handleOnWorker(context, intent)
            } finally {
                onComplete()
            }
        }
    }

    private suspend fun handleOnWorker(context: Context, intent: Intent) {
        val result = when (
            val receiverResult = AutoInputResultBroadcastContract.readResult(
                intent = intent,
                expectedAction = action,
            )
        ) {
            AutoInputResultBroadcastContract.ReceiverResult.Ignored -> return
            AutoInputResultBroadcastContract.ReceiverResult.MissingAttemptId -> return
            is AutoInputResultBroadcastContract.ReceiverResult.Accepted -> receiverResult.result
        }
        val attemptId = result.attemptId
        val deps = RuntimeGraph.from(context)
        val expectedToken = RuntimeSettingsCache.getString(
            key = PrefConst.KEY_IPC_TOKEN,
            defaultValue = "",
        ) { key, defaultValue ->
            deps.preferenceDataSource.getString(key, defaultValue)
        }
        val receivedToken = intent.getStringExtra(AutoInputBroadcastContract.EXTRA_IPC_TOKEN)
        val tokenDecision = IpcTokenGate.evaluate(
            expectedToken = expectedToken,
            receivedToken = receivedToken,
        )
        if (!tokenDecision.accepted) {
            XLog.w(
                "Diag AutoInputResultReceiver rejected token: attemptId=%d expectedEmpty=%s receivedEmpty=%s",
                attemptId,
                expectedToken.isBlank(),
                receivedToken.isNullOrBlank(),
            )
            return
        }
        val success = result.success
        val reason = result.reason
        XLog.w(
            "Diag AutoInputResultReceiver onReceive: attemptId=%d success=%s reason=%s",
            attemptId,
            success,
            reason ?: "<none>",
        )
        val analyticsEnabled = RuntimeSettingsCache.getBoolean(
            key = PrefConst.KEY_ENABLE_ANALYTICS,
            defaultValue = true,
        ) { key, defaultValue ->
            deps.preferenceDataSource.getBoolean(key, defaultValue)
        }
        if (!analyticsEnabled) {
            XLog.w("Diag AutoInputResultReceiver analytics disabled: attemptId=%d", attemptId)
            return
        }

        runCatching {
            val runtimeRecordFacade = RuntimeGraph.from(context).runtimeRecordFacade
            val updatedRows = runtimeRecordFacade.updateAutoInputResult(attemptId, success, reason)
            if (updatedRows <= 0) {
                val upserted = runCatching {
                    runtimeRecordFacade.upsertAutoInputResult(attemptId, success, reason)
                }.onFailure { error ->
                    XLog.w(
                        "AutoInput result upsert failed: %s",
                        error.message ?: error.javaClass.simpleName,
                    )
                }.getOrDefault(0L)
                if (upserted <= 0L) {
                    XLog.w(
                        "Diag AutoInputResultReceiver skipped stale result: attemptId=%d success=%s reason=%s",
                        attemptId,
                        success,
                        reason ?: "<none>",
                    )
                    return@runCatching
                }
                XLog.i(
                    "Diag AutoInputResultReceiver recovered stale result via upsert: attemptId=%d success=%s",
                    attemptId,
                    success,
                )
            }
            if (success) {
                AnalyticsTracker.logEvent("auto_input_success")
            } else {
                AnalyticsTracker.logEvent(
                    "auto_input_fail",
                    mapOf("reason" to (reason ?: "unknown")),
                )
            }
        }.onFailure { error ->
            XLog.w(
                "AutoInput result persist failed: %s",
                error.message ?: error.javaClass.simpleName,
            )
        }
    }
}
