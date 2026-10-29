package io.github.magisk317.relay.android.platform.ipc

import android.content.Context
import android.os.Binder
import io.github.magisk317.xposed.logging.PackageCallerGuard
import java.util.concurrent.atomic.AtomicReference

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

    /**
     * Live LSPosed scope, published by the app process after the service binder arrives.
     * With a dynamic module scope the user may select packages this list has never heard
     * of, so the provider has to trust the framework's answer instead of a static set.
     */
    private val liveScopePackages = AtomicReference<Set<String>>(emptySet())

    fun updateLiveScope(packages: Collection<String>) {
        liveScopePackages.set(packages.filter { it.isNotBlank() }.toSet())
    }

    fun isSelf(context: Context): Boolean =
        Binder.getCallingUid() == context.applicationInfo?.uid

    fun isSelfOrSystemScope(context: Context): Boolean =
        systemScopeGuard.isCallerAllowed(context) || isCallerInLiveScope(context)

    private fun isCallerInLiveScope(context: Context): Boolean {
        val scope = liveScopePackages.get()
        if (scope.isEmpty()) return false
        val callingPackages = runCatching {
            context.packageManager.getPackagesForUid(Binder.getCallingUid())
        }.getOrNull() ?: return false
        return callingPackages.any { it in scope }
    }

    internal fun isSystemScopePackage(packageName: String, applicationFlags: Int): Boolean =
        systemScopeGuard.isPackageAllowed(packageName, applicationFlags)
}
