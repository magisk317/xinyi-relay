package io.github.magisk317.relay.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.feature.mode.XposedRuntimeState
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
        XposedRuntimeState.refresh(application)
        AppInfrastructureCoordinator.initialize(
            application = application,
            shouldSuppressSystemHooks = FlavorXposedRuntimeInitializer::shouldSuppressSystemHooks,
        )

        // Activation can lag ~tens-hundreds of ms after process start (LSPosed bind),
        // so wait for the runtime to report active, or a short timeout, before settling.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            settleAndReconcile(application, reason = "app_init_settled")
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                // Defer reconcile until the cold-start settle has decided.
                // A runtime stop is always safe and may arrive via Xposed bind earlier.
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
                XposedRuntimeState.refresh(application)
                if (XposedRuntimeState.xposedActive.value ||
                    ActivationDiagnosticsStore.isModuleActivated(application)
                ) {
                    break
                }
                delay(SETTLE_POLL_MS)
            }
            val xposedActive = XposedRuntimeState.refresh(application)
            environmentSettled.set(true)
            XLog.i(
                "Xposed runtime settle complete: active=%s reason=%s activated=%s",
                xposedActive,
                reason,
                ActivationDiagnosticsStore.isModuleActivated(application),
            )
        }

        fun markEnvironmentSettled() {
            environmentSettled.set(true)
        }

        fun reconcileAfterEnvironmentKnown(application: Application, reason: String) {
            XposedRuntimeState.refresh(application)
            val xposedActive = XposedRuntimeState.xposedActive.value
            if (!environmentSettled.get() && !xposedActive) {
                XLog.i("Xposed runtime reconcile deferred until settle: reason=%s active=%s", reason, xposedActive)
                return
            }
        }
    }
}
