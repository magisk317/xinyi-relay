package io.github.magisk317.relay.receiver

import io.github.magisk317.relay.android.otel.MagiskOtelBootstrap
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.smscode.runtime.common.autoinput.AutoInputResultProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.magisk317.xposed.logging.MagiskOtel

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
        val deps = RuntimeGraph.from(context)
        val result = when (
            val validation = AutoInputResultProcessor.validate(
                intent = intent,
                expectedAction = action,
            ) {
                RuntimeSettingsCache.getString(
                    key = PrefConst.KEY_IPC_TOKEN,
                    defaultValue = "",
                ) { key, defaultValue ->
                    deps.preferenceDataSource.getString(key, defaultValue)
                }
            }
        ) {
            AutoInputResultProcessor.ValidationResult.Ignored -> {
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "ignored",
                    ),
                    statusOk = true,
                )
                return
            }
            AutoInputResultProcessor.ValidationResult.MissingAttemptId -> {
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "missing_attempt_id",
                    ),
                    statusOk = true,
                )
                return
            }
            is AutoInputResultProcessor.ValidationResult.RejectedToken -> {
                XLog.w(
                    "Diag AutoInputResultReceiver rejected token: expectedEmpty=%s receivedEmpty=%s",
                    validation.expectedTokenEmpty,
                    validation.receivedTokenEmpty,
                )
                MagiskOtel.event(
                    name = "auto.input",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "result_handler",
                        "reason" to "token_rejected",
                    ),
                    statusOk = false,
                )
                return
            }
            is AutoInputResultProcessor.ValidationResult.Accepted -> validation.result
        }
        val attemptId = result.attemptId
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
        if (!MagiskOtelBootstrap.isEffectivelyEnabled(analyticsEnabled)) {
            XLog.w("Diag AutoInputResultReceiver analytics disabled: attemptId=%d", attemptId)
            MagiskOtel.event(
                name = "auto.input",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "result_handler",
                    "reason" to "analytics_disabled",
                ),
                statusOk = true,
            )
            return
        }

        runCatching {
            val runtimeRecordFacade = deps.runtimeRecordFacade
            when (
                AutoInputResultProcessor.persist(
                    result = result,
                    update = {
                        runtimeRecordFacade.updateAutoInputResult(
                            it.attemptId,
                            it.success,
                            it.reason,
                        ).toLong()
                    },
                    upsert = {
                        runtimeRecordFacade.upsertAutoInputResult(
                            it.attemptId,
                            it.success,
                            it.reason,
                        )
                    },
                )
            ) {
                AutoInputResultProcessor.PersistenceOutcome.UPDATED -> {
                    MagiskOtel.event(
                        name = "auto.input",
                        attributes = mapOf(
                            "result" to if (success) "ok" else "error",
                            "duration_ms" to "0",
                            "process" to "app",
                            "stage" to "result_handler",
                            "reason" to "updated",
                        ),
                        statusOk = success,
                    )
                }
                AutoInputResultProcessor.PersistenceOutcome.UPSERTED -> {
                    XLog.i(
                        "Diag AutoInputResultReceiver recovered stale result via upsert: attemptId=%d success=%s",
                        attemptId,
                        success,
                    )
                    MagiskOtel.event(
                        name = "auto.input",
                        attributes = mapOf(
                            "result" to if (success) "ok" else "error",
                            "duration_ms" to "0",
                            "process" to "app",
                            "stage" to "result_handler",
                            "reason" to "upserted",
                        ),
                        statusOk = success,
                    )
                }
                AutoInputResultProcessor.PersistenceOutcome.STALE -> {
                    XLog.w(
                        "Diag AutoInputResultReceiver skipped stale result: attemptId=%d success=%s reason=%s",
                        attemptId,
                        success,
                        reason ?: "<none>",
                    )
                    MagiskOtel.event(
                        name = "auto.input",
                        attributes = mapOf(
                            "result" to "skip",
                            "duration_ms" to "0",
                            "process" to "app",
                            "stage" to "result_handler",
                            "reason" to "stale",
                        ),
                        statusOk = true,
                    )
                    return@runCatching
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
        }.onFailure { error ->
            XLog.w(
                "AutoInput result persist failed: %s",
                error.message ?: error.javaClass.simpleName,
            )
            MagiskOtel.event(
                name = "auto.input",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "result_handler",
                    "reason" to "persist_failed",
                    "error_class" to error.javaClass.simpleName,
                ),
                statusOk = false,
            )
        }
    }
}
