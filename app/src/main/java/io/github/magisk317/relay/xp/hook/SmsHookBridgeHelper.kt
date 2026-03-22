package io.github.magisk317.relay.xp.hook

import android.content.Context
import io.github.magisk317.relay.common.utils.ActivationDiagnosticsStore
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.RuntimeLogStore

internal object SmsHookBridgeHelper {
    fun resolvePluginContext(
        phoneContext: Context?,
        currentPluginContext: Context?,
        applicationId: String,
    ): Context? {
        if (currentPluginContext != null) return currentPluginContext
        return runCatching {
            phoneContext?.createPackageContext(
                applicationId,
                Context.CONTEXT_IGNORE_SECURITY,
            )
        }.getOrNull()
    }

    fun recordSmsHookHeartbeat(
        pluginContext: Context,
        phoneContext: Context,
        packageName: String,
        source: String,
    ) {
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = pluginContext,
            packageName = packageName,
            processName = phoneContext.applicationInfo?.processName ?: packageName,
            source = source,
            verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
            route = RuntimeLogStore.ROUTE_SMS_HOOK,
        )
    }
}
