package io.github.magisk317.relay.ui.app

import android.app.Application
import io.github.magisk317.relay.feature.mode.FrameworkIdentity
import io.github.magisk317.relay.feature.mode.NonRootRuntimeState
import io.github.magisk317.relay.feature.mode.RuntimeSignalRefresher
import io.github.magisk317.relay.feature.mode.XposedRuntimeState
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.runtime.state.FrameworkIdentityResolver
import io.github.magisk317.xposed.runtime.state.FrameworkRead
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.xposed.service.FrameworkHookTargets
import io.github.magisk317.xposed.service.XposedServiceBridge as KitServiceBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Collects fresh framework evidence; persisted targets and installed packages do not prove activity. */
internal class XposedRuntimeSignals(private val application: Application) : RuntimeSignalRefresher {
    private val refreshMutex = Mutex()

    fun currentFrameworkRead(): FrameworkRead {
        val service = KitServiceBridge.currentService ?: return FrameworkRead.Unavailable
        return runCatching {
            val targets = FrameworkHookTargets.read(application, service)
            if (targets == null) FrameworkRead.Unavailable else FrameworkRead.Answered(targets.size)
        }.getOrElse { error ->
            XLog.w(message = "Failed to read current framework targets: %s", error)
            FrameworkRead.Unavailable
        }
    }

    override fun frameworkIdentity(): FrameworkIdentity {
        val service = KitServiceBridge.currentService
        val snapshot = RuntimeStateStore.snapshot(application)
        val name = runCatching { service?.frameworkName }.getOrElse { error ->
            XLog.w(message = "Failed to read framework name: %s", error)
            null
        }
        val version = runCatching { service?.frameworkVersion }.getOrElse { error ->
            XLog.w(message = "Failed to read framework version: %s", error)
            null
        }
        val resolved = FrameworkIdentityResolver.resolve(
            serviceName = name,
            serviceVersion = version,
            lastName = snapshot.lastServiceFrameworkName,
            lastVersion = snapshot.lastServiceFrameworkVersion,
        )
        return FrameworkIdentity(resolved.name, resolved.version)
    }

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            XposedRuntimeState.refresh(application, currentFrameworkRead())
            NonRootRuntimeState.refresh()
            Unit
        }
    }
}
