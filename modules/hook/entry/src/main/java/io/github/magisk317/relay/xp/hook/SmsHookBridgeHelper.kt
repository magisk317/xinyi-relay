package io.github.magisk317.relay.xp.hook

import android.content.Context
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpPrefs

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
        val verboseLogging = XpPrefs.isVerboseLogMode(pluginContext)
        ensureHookProcessPrefs(pluginContext)
        XpHookDiagnostics.bindRuntimeLogContext(
            context = pluginContext,
            verboseLogging = verboseLogging,
        )
        XpHookDiagnostics.recordSmsHookHeartbeat(
            context = pluginContext,
            packageName = packageName,
            processName = phoneContext.applicationInfo?.processName ?: packageName,
            source = source,
            verboseLogging = verboseLogging,
        )
    }

    /**
     * Ensure local SharedPreferences fallback is available in hook process.
     * Mirrors XposedSmsCode's ensureHookProcessLogging → PrefsReader.setHookContext().
     */
    fun ensureHookProcessPrefs(context: Context) {
        PrefsReader.setHookContext(context)
    }
}
