package io.github.magisk317.relay.domain.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import io.github.magisk317.smscode.runtime.common.notification.CallNotificationMatcher
import io.github.magisk317.xposed.runtime.state.CallNotificationSourceSnapshot
import io.github.magisk317.xposed.runtime.state.HookProcessStatus

/**
 * Finds the packages this ROM could use to publish call notifications. The result is
 * diagnostic only: the actual forwarding decision stays in the system-server hook, and the
 * scope entries never need to include the publisher (the hook observes every enqueue).
 */
object CallNotificationSourceDetector {
    const val IN_CALL_SERVICE_ACTION = "android.telecom.InCallService"
    const val TELECOM_PACKAGE = "com.android.server.telecom"

    data class KnownSource(
        val packageName: String,
        val matched: Boolean,
    )

    data class Report(
        val defaultDialerPackage: String = "",
        val dialIntentPackage: String = "",
        val incallServicePackages: List<String> = emptyList(),
        val knownSources: List<KnownSource> = emptyList(),
        val lastObserved: CallNotificationSourceSnapshot = CallNotificationSourceSnapshot(),
    )

    fun detect(
        context: Context,
        lastObserved: CallNotificationSourceSnapshot = CallNotificationSourceSnapshot(),
    ): Report {
        val pm = context.packageManager
        val defaultDialer = runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage.orEmpty()
        }.getOrDefault("")
        val dialIntent = runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                .orEmpty()
        }.getOrDefault("")
        val incallServices = runCatching {
            pm.queryIntentServices(Intent(IN_CALL_SERVICE_ACTION), PackageManager.MATCH_ALL)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
                .sorted()
        }.getOrDefault(emptyList())
        val candidates = buildList {
            add(defaultDialer)
            add(dialIntent)
            addAll(incallServices)
            add(TELECOM_PACKAGE)
        }.filter { it.isNotBlank() }.distinct()
        val knownSources = candidates.map { packageName ->
            KnownSource(
                packageName = packageName,
                matched = CallNotificationMatcher.isCallSourcePackage(packageName),
            )
        }
        return Report(
            defaultDialerPackage = defaultDialer,
            dialIntentPackage = dialIntent,
            incallServicePackages = incallServices,
            knownSources = knownSources,
            lastObserved = lastObserved,
        )
    }
}

/** Turns raw per-process heartbeats into rows the page can render. */
object HookProcessMatrix {
    data class Row(
        val packageName: String,
        val processName: String,
        val lastSeenAtMs: Long,
        val moduleVersion: Int,
        val thisBoot: Boolean,
    )

    fun build(processes: List<HookProcessStatus>, bootStartAtMs: Long): List<Row> =
        processes
            .filter { it.packageName.isNotBlank() && it.processName.isNotBlank() }
            .sortedByDescending { it.lastSeenAtMs }
            .map { status ->
                Row(
                    packageName = status.packageName,
                    processName = status.processName,
                    lastSeenAtMs = status.lastSeenAtMs,
                    moduleVersion = status.moduleVersion,
                    thisBoot = status.lastSeenAtMs >= bootStartAtMs,
                )
            }

    fun systemRow(rows: List<Row>): Row? =
        rows.firstOrNull { it.packageName == "android" || it.processName == "system" }

    fun hasSystemHeartbeatThisBoot(rows: List<Row>): Boolean =
        systemRow(rows)?.thisBoot == true
}

/** Explains why notification hooks may be missing without hiding an actual scope gap. */
object RebootVerdict {
    enum class Reason {
        OK,
        SYSTEM_SCOPE_MISSING,
        INSTALLED_AFTER_BOOT,
        SYSTEM_NOT_LOADED,
    }

    data class Result(
        val needsReboot: Boolean,
        val reason: Reason,
    )

    fun evaluate(
        bootStartAtMs: Long,
        firstInstallTimeMs: Long,
        lastUpdateTimeMs: Long,
        systemScopeSelected: Boolean,
        systemProcessSeenThisBoot: Boolean,
    ): Result = when {
        !systemScopeSelected -> Result(
            needsReboot = false,
            reason = Reason.SYSTEM_SCOPE_MISSING,
        )

        systemProcessSeenThisBoot -> Result(
            needsReboot = false,
            reason = Reason.OK,
        )

        maxOf(firstInstallTimeMs, lastUpdateTimeMs) > bootStartAtMs -> Result(
            needsReboot = true,
            reason = Reason.INSTALLED_AFTER_BOOT,
        )

        else -> Result(
            needsReboot = true,
            reason = Reason.SYSTEM_NOT_LOADED,
        )
    }
}

/** Recommends the LSPosed scope entries for this device. */
object ScopeRecommender {
    val REQUIRED_PACKAGES: List<String> = listOf(
        "com.android.phone",
        "com.android.providers.telephony",
        "com.android.mms",
    )
    val OPTIONAL_PACKAGES: List<String> = listOf(
        "com.xiaomi.phone",
    )

    fun recommend(installedPackages: Set<String>): List<String> = buildList {
        add("android")
        add("system")
        addAll(REQUIRED_PACKAGES.filter { it in installedPackages })
        addAll(OPTIONAL_PACKAGES.filter { it in installedPackages })
    }.distinct()

    fun missing(
        granted: Collection<String>,
        wanted: Collection<String>,
    ): List<String> = wanted.filterNot { it in granted }.distinct()
}
