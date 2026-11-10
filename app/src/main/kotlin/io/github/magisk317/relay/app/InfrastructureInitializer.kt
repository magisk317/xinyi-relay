package io.github.magisk317.relay.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.android.otel.MagiskOtelBootstrap
import io.github.magisk317.relay.feature.mode.NonRootRuntimeState
import io.github.magisk317.relay.feature.mode.RuntimeSignalRefresher
import io.github.magisk317.relay.feature.mode.XposedRuntimeState
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class InfrastructureInitializer(
    private val runtimeSignals: RuntimeSignalRefresher,
) : AppInitializer {
    override fun init(application: Application) {
        MagiskOtelBootstrap.install(application, serviceVersion = BuildConfig.VERSION_NAME)
        MagiskOtel.event(name = "app.boot", attributes = mapOf("result" to "ok", "process" to "main"))
        FlavorXposedRuntimeInitializer.installPlatformBridges()
        NonRootRuntimeState.configure(BuildConfig.ENABLE_NONROOT_MODE)
        AppInfrastructureCoordinator.initialize(
            application = application,
            shouldSuppressSystemHooks = FlavorXposedRuntimeInitializer::shouldSuppressSystemHooks,
        )
        runtimeScope.launch {
            runtimeSignals.refresh()
            settleAndReconcile(application, "app_init_settled")
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                runtimeScope.launch {
                    // Reading signals must not wait for cold-start reconciliation.
                    runtimeSignals.refresh()
                    reconcileAfterEnvironmentKnown("process_resume")
                }
            }
        })
    }

    companion object {
        private const val SETTLE_TIMEOUT_MS = 800L
        private const val SETTLE_POLL_MS = 50L
        private val environmentSettled = AtomicBoolean(false)
        private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        suspend fun settleAndReconcile(application: Application, reason: String) {
            val signals = GlobalContext.get().get<RuntimeSignalRefresher>()
            val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                signals.refresh()
                if (XposedRuntimeState.xposedActive.value) break
                delay(SETTLE_POLL_MS)
            }
            signals.refresh()
            environmentSettled.set(true)
            XLog.i(
                message = "Xposed runtime settle complete: active=%s reason=%s package=%s",
                XposedRuntimeState.xposedActive.value,
                reason,
                application.packageName,
            )
        }

        fun markEnvironmentSettled() {
            environmentSettled.set(true)
        }

        private fun reconcileAfterEnvironmentKnown(reason: String) {
            val active = XposedRuntimeState.xposedActive.value
            if (!environmentSettled.get() && !active) {
                XLog.i(message = "Xposed runtime reconcile deferred until settle: reason=%s active=%s", reason, active)
            }
        }
    }
}
