package io.github.magisk317.relay.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.service.StandardModeService
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.android.otel.MagiskOtelBootstrap
import io.github.magisk317.xposed.logging.MagiskOtel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InfrastructureInitializer : AppInitializer {
    override fun init(application: Application) {
        MagiskOtelBootstrap.install(
            application,
            serviceVersion = BuildConfig.VERSION_NAME,
        )
        MagiskOtel.event(
            name = "app.boot",
            attributes = mapOf(
                "result" to "ok",
                "process" to "main",
            ),
        )

        FlavorXposedRuntimeInitializer.installPlatformBridges()
        WorkModeResolver.resolve(application)
        CallStateMonitor.init(application)
        AppInfrastructureCoordinator.initialize(
            application = application,
            shouldSuppressSystemHooks = FlavorXposedRuntimeInitializer::shouldSuppressSystemHooks,
        )

        // Design: detect environment first, then run only that mode.
        // Enhanced (Xposed) must never start StandardModeService.
        // Activation can lag ~tens-hundreds of ms after process start (LSPosed bind),
        // so wait for Enhanced or a short timeout before the first reconcile.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            settleAndReconcile(application, reason = "app_init_settled")
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                // Do not start Standard while cold-start settle is still deciding.
                // Enhanced stop is always safe and may arrive via Xposed bind earlier.
                reconcileAfterEnvironmentKnown(application, "process_resume")
            }
        })
    }

    companion object {
        private const val SETTLE_TIMEOUT_MS = 800L
        private const val SETTLE_POLL_MS = 50L
        private val environmentSettled = AtomicBoolean(false)

        suspend fun settleAndReconcile(application: Application, reason: String) {
            val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                WorkModeResolver.resolve(application)
                if (WorkModeResolver.mode.value == WorkMode.Enhanced ||
                    ActivationDiagnosticsStore.isModuleActivated(application)
                ) {
                    break
                }
                delay(SETTLE_POLL_MS)
            }
            val mode = WorkModeResolver.resolve(application)
            environmentSettled.set(true)
            XLog.i(
                "WorkMode settle complete: mode=%s reason=%s activated=%s",
                mode,
                reason,
                ActivationDiagnosticsStore.isModuleActivated(application),
            )
            StandardModeService.reconcile(application, mode, reason)
        }

        fun markEnvironmentSettled() {
            environmentSettled.set(true)
        }

        fun reconcileAfterEnvironmentKnown(application: Application, reason: String) {
            WorkModeResolver.resolve(application)
            val mode = WorkModeResolver.mode.value
            if (!environmentSettled.get() && mode != WorkMode.Enhanced) {
                XLog.i("WorkMode reconcile deferred until settle: reason=%s mode=%s", reason, mode)
                return
            }
            StandardModeService.reconcile(application, mode, reason)
        }
    }
}
