package io.github.magisk317.relay.android.otel

import io.github.magisk317.relay.android.platform.compat.PlatformCompat
import android.content.Context
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.xposed.logging.AnonymousInstallationId
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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

    fun isEnabled(context: Context): Boolean = runBlocking(Dispatchers.IO) {
        isEffectivelyEnabled(
            AppPreferencesDataStore.getBoolean(
                context,
                RelayPrefConst.KEY_ENABLE_ANALYTICS,
                true,
            ),
        )
    }

    fun install(context: Context, serviceVersion: String? = null, serviceCommit: String = BuildConfig.COMMIT_HASH) {
        configure(
            context = context,
            enabled = isEnabled(context),
            serviceVersion = serviceVersion ?: resolveVersion(context),
            serviceCommit = serviceCommit,
        )
    }

    fun refresh(
        context: Context,
        userPrefEnabled: Boolean,
        serviceVersion: String,
        serviceCommit: String = BuildConfig.COMMIT_HASH,
    ) {
        configure(
            context = context,
            enabled = isEffectivelyEnabled(userPrefEnabled),
            serviceVersion = serviceVersion,
            serviceCommit = serviceCommit,
        )
    }

    fun configure(
        context: Context,
        enabled: Boolean,
        serviceVersion: String,
        serviceCommit: String = BuildConfig.COMMIT_HASH,
    ) {
        val installationId = AnonymousInstallationId.getOrCreate(context, TELEMETRY_PREFS_NAME)
        runBlocking {
            AppPreferencesDataStore.setString(
                context,
                AnonymousInstallationId.PREFERENCE_KEY,
                installationId,
            )
        }
        MagiskOtel.configureForInstallation(
            context,
            MagiskOtel.Config(
                enabled = enabled,
                serviceName = SERVICE_NAME,
                serviceVersion = serviceVersion,
                serviceCommit = serviceCommit,
                projectId = PROJECT_ID,
                projectName = PROJECT_NAME,
                environment = if (BuildConfig.DEBUG) "debug" else "release",
                suppressedResultValues = SUPPRESSED_RESULT_VALUES,
            ),
            TELEMETRY_PREFS_NAME,
        )
    }

    private fun resolveVersion(context: Context): String {
        return runCatching {
            PlatformCompat.getPackageInfo(context.packageManager, context.packageName).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    }

    /**
     * Skip dominates this service (~82% of every span): an ingest path that had nothing to
     * relay. Dropping it keeps ok / error readable.
     */
    val SUPPRESSED_RESULT_VALUES: Set<String> = setOf("skip")

    private const val TELEMETRY_PREFS_NAME = "relay_telemetry_prefs"
}
