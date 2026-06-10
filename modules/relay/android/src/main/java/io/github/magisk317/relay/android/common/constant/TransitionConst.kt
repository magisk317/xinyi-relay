package io.github.magisk317.relay.android.common.constant

import android.content.Context
import android.content.pm.PackageManager

object TransitionConst {
    const val TARGET_RELAY_PACKAGE = "com.github.tianma8023.xposed.smscode"

    fun isRelayInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TARGET_RELAY_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
