package io.github.magisk317.relay.android.platform.ipc

import android.content.Context
import android.os.Binder
import io.github.magisk317.xposed.logging.PackageCallerGuard

/** Caller policy shared by exported providers serving processes in the system Xposed scope. */
object ProviderCallerPolicy {
    private val SYSTEM_SCOPE_PACKAGES = setOf(
        "android",
        "system",
        "com.android.phone",
        "com.xiaomi.phone",
        "com.android.providers.telephony",
        "com.android.mms",
    )
    private val systemScopeGuard = PackageCallerGuard(SYSTEM_SCOPE_PACKAGES)

    fun isSelf(context: Context): Boolean =
        Binder.getCallingUid() == context.applicationInfo?.uid

    fun isSelfOrSystemScope(context: Context): Boolean =
        systemScopeGuard.isCallerAllowed(context)

    internal fun isSystemScopePackage(packageName: String, applicationFlags: Int): Boolean =
        systemScopeGuard.isPackageAllowed(packageName, applicationFlags)
}
