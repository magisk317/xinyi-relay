package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.security.IpcTokenGate
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object AutoInputResultHandler {
    val action: String
        get() = SystemInputInjectorHook.resolveActionAutoInputResult()

    fun handle(context: Context, intent: Intent, onComplete: () -> Unit = {}) {
        runCatching {
            AUTO_INPUT_RESULT_EXECUTOR.execute {
                try {
                    handleOnWorker(context, intent)
                } finally {
                    onComplete()
                }
            }
        }.onFailure { error ->
            XLog.w(
                "AutoInput result worker rejected: %s",
                error.message ?: error.javaClass.simpleName,
            )
            onComplete()
        }
    }

    private fun handleOnWorker(context: Context, intent: Intent) {
        val attemptId = intent.getLongExtra("attemptId", -1L)
        if (attemptId <= 0L) return
        val runtimeGraph = RuntimeGraph.from(context)
        val expectedToken = runBlocking {
            RuntimeSettingsCache.getString(
                key = PrefConst.KEY_IPC_TOKEN,
                defaultValue = "",
            ) { key, defaultValue ->
                runtimeGraph.preferenceDataSource.getString(key, defaultValue)
            }
        }
        val receivedToken = intent.getStringExtra(SystemInputInjectorHook.EXTRA_IPC_TOKEN)
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
        val success = intent.getBooleanExtra("success", false)
        val reason = intent.getStringExtra("reason")
        XLog.w(
            "Diag AutoInputResultReceiver onReceive: attemptId=%d success=%s reason=%s",
            attemptId,
            success,
            reason ?: "<none>",
        )
        val analyticsEnabled = runBlocking {
            RuntimeSettingsCache.getBoolean(
                key = PrefConst.KEY_ENABLE_ANALYTICS,
                defaultValue = true,
            ) { key, defaultValue ->
                runtimeGraph.preferenceDataSource.getBoolean(key, defaultValue)
            }
        }
        if (!analyticsEnabled) {
            XLog.w("Diag AutoInputResultReceiver analytics disabled: attemptId=%d", attemptId)
            return
        }

        runCatching {
            val updatedRows = runBlocking {
                RuntimeGraph.from(context).runtimeRecordFacade
                    .updateAutoInputResult(attemptId, success, reason)
            }
            if (updatedRows <= 0) {
                XLog.w(
                    "Diag AutoInputResultReceiver skipped stale result: attemptId=%d success=%s reason=%s",
                    attemptId,
                    success,
                    reason ?: "<none>",
                )
                return@runCatching
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

    private val workerIndex = AtomicInteger(1)
    private val AUTO_INPUT_RESULT_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoInputResult-${workerIndex.getAndIncrement()}")
    }
}
