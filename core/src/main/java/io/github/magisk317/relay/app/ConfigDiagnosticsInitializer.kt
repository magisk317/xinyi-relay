package io.github.magisk317.relay.app

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.repository.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ConfigDiagnosticsInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        AppInitExecution.runWhenUserUnlocked(application, scope, "ConfigDiagnosticsInitializer") {
            val runtimeGraph = RuntimeGraph.from(application)
            logInstallSnapshot(application)
            logConfigSnapshot(runtimeGraph.configRepository, runtimeGraph)
        }
    }

    private suspend fun logConfigSnapshot(
        configRepository: ConfigRepository,
        runtimeGraph: RuntimeGraph,
    ) {
        val senders = configRepository.getAllSenders()
        val rules = configRepository.getAllRules()
        val smsRules = configRepository.getAllSmsCodeRules()
        val forwardingApps = configRepository.getAllAppInfo().count { it.forwarding }
        val notifyRoutes = runtimeGraph.database.notifyRouteRuleDao().getAll().size
        val forwardFilters = runtimeGraph.database.forwardFilterRuleDao().getAll().size
        val enabledSenders = senders.count { it.status == 1 }
        val appNotifyEnabledSenders = senders.count { it.status == 1 && it.receiveAppNotify == 1 }

        XLog.i(
            "Config snapshot: senders=%d enabledSenders=%d " +
                "appNotifyEnabledSenders=%d legacyRules=%d smsRules=%d " +
                "notifyRoutes=%d forwardFilters=%d forwardingApps=%d",
            senders.size,
            enabledSenders,
            appNotifyEnabledSenders,
            rules.size,
            smsRules.size,
            notifyRoutes,
            forwardFilters,
            forwardingApps,
        )
    }

    private fun logInstallSnapshot(application: Application) {
        val packageInfo = runCatching { getSelfPackageInfo(application) }.getOrNull() ?: return
        XLog.i(
            "Install snapshot: package=%s firstInstall=%d lastUpdate=%d sourceDir=%s",
            application.packageName,
            packageInfo.firstInstallTime,
            packageInfo.lastUpdateTime,
            application.applicationInfo.sourceDir,
        )
    }

    private fun getSelfPackageInfo(application: Application): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.packageManager.getPackageInfo(
                application.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            application.packageManager.getPackageInfo(application.packageName, 0)
        }
    }
}
