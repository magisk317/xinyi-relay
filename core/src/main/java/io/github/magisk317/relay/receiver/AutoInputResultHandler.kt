package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

object AutoInputResultHandler {
    val action: String
        get() = io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook.resolveActionAutoInputResult()

    fun handle(context: Context, intent: Intent) {
        val attemptId = intent.getLongExtra("attemptId", -1L)
        if (attemptId <= 0L) return
        val success = intent.getBooleanExtra("success", false)
        val reason = intent.getStringExtra("reason")
        XLog.w(
            "Diag AutoInputResultReceiver onReceive: attemptId=%d success=%s reason=%s",
            attemptId,
            success,
            reason ?: "<none>",
        )
        val analyticsEnabled = runBlocking {
            val runtimeGraph = RuntimeGraph.from(context)
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

        thread(name = "auto-input-result") {
            runCatching {
                runBlocking {
                    RuntimeGraph.from(context).runtimeRecordFacade
                        .updateAutoInputResult(attemptId, success, reason)
                }
            }.onFailure { error ->
                XLog.w(
                    "AutoInput result persist failed: %s",
                    error.message ?: error.javaClass.simpleName,
                )
            }
        }

        if (success) {
            AnalyticsTracker.logEvent("auto_input_success")
        } else {
            AnalyticsTracker.logEvent(
                "auto_input_fail",
                mapOf("reason" to (reason ?: "unknown")),
            )
        }
    }
}
