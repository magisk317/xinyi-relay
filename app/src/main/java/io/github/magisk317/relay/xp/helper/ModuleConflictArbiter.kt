package io.github.magisk317.relay.xp.helper

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.magisk317.relay.common.constant.TransitionConst
import io.github.magisk317.smscode.core.utils.XLog

object ModuleConflictArbiter {
    const val SUPPRESSION_REASON = "suppressed_by_relay_package"
    const val TARGET_RELAY_PACKAGE = TransitionConst.TARGET_RELAY_PACKAGE

    @Volatile
    private var checked = false

    @Volatile
    private var suppressedByRelay = false

    @Volatile
    private var decisionLogged = false

    fun shouldSuppressByRelay(context: Context?, source: String): Boolean {
        if (context == null) return false
        if (!checked) {
            synchronized(this) {
                if (!checked) {
                    suppressedByRelay = isPackageInstalled(context, TARGET_RELAY_PACKAGE)
                    checked = true
                }
            }
        }
        logDecisionOnce(source)
        return suppressedByRelay
    }

    private fun logDecisionOnce(source: String) {
        if (decisionLogged) return
        synchronized(this) {
            if (decisionLogged) return
            if (suppressedByRelay) {
                XLog.w(
                    "Conflict arbiter active: reason=%s source=%s package=%s",
                    SUPPRESSION_REASON,
                    source,
                    TARGET_RELAY_PACKAGE,
                )
            } else {
                XLog.i(
                    "Conflict arbiter bypassed: source=%s package=%s",
                    source,
                    TARGET_RELAY_PACKAGE,
                )
            }
            decisionLogged = true
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
