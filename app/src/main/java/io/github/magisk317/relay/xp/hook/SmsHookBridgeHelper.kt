package io.github.magisk317.relay.xp.hook

import android.content.Context
import io.github.magisk317.relay.xp.XpHookDiagnostics
import io.github.magisk317.relay.xp.XpPrefs

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
        XpHookDiagnostics.recordSmsHookHeartbeat(
            context = pluginContext,
            packageName = packageName,
            processName = phoneContext.applicationInfo?.processName ?: packageName,
            source = source,
            verboseLogging = XpPrefs.isVerboseLogMode(pluginContext),
        )
    }
}
