package io.github.magisk317.relay.android.otel

import android.content.Context
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Gates MagiskOtel (and aligns with local analytics pref):
 * - debug/CI: always enabled, no user toggle
 * - release: default on via pref, user can disable
 */
object MagiskOtelBootstrap {
    private const val SERVICE_NAME = "xinyi-relay"
    private const val PROJECT_ID = "84113188"
    private const val PROJECT_NAME = "xinyi-relay"

    fun shouldShowToggle(): Boolean = !BuildConfig.DEBUG

    fun isEffectivelyEnabled(userPrefEnabled: Boolean): Boolean =
        BuildConfig.DEBUG || userPrefEnabled

    fun isEnabled(context: Context): Boolean =
        isEffectivelyEnabled(PrefsReader.analyticsEnabled(context))

    fun install(context: Context, serviceVersion: String? = null) {
        configure(
            enabled = isEnabled(context),
            serviceVersion = serviceVersion ?: resolveVersion(context),
        )
    }

    fun refresh(userPrefEnabled: Boolean, serviceVersion: String) {
        configure(
            enabled = isEffectivelyEnabled(userPrefEnabled),
            serviceVersion = serviceVersion,
        )
    }

    fun configure(enabled: Boolean, serviceVersion: String) {
        MagiskOtel.configure(
            MagiskOtel.Config(
                enabled = enabled,
                serviceName = SERVICE_NAME,
                serviceVersion = serviceVersion,
                projectId = PROJECT_ID,
                projectName = PROJECT_NAME,
                environment = if (BuildConfig.DEBUG) "debug" else "release",
            ),
        )
    }

    private fun resolveVersion(context: Context): String {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    }
}
