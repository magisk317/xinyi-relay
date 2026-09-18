package io.github.magisk317.relay.platform.permission

import android.Manifest
import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Grants the permissions the app can obtain through the root channel, so the
 * startup prompt no longer has to bounce the user through system dialogs and
 * settings pages.
 *
 * Every grant here is idempotent and additive: shared Secure settings are read
 * back and merged, never overwritten, so services the user already enabled stay
 * untouched.
 */
object PermissionBridge {

    private const val NOTIFICATION_LISTENER_COMPONENT =
        "io.github.magisk317.xinyi.relay/io.github.magisk317.relay.service.AppNotificationListenerService"

    /** Shell commands granting [permission] through root, or null when it cannot be bridged. */
    fun commandsFor(context: Context, permission: String): List<String>? {
        val pkg = context.packageName
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            -> listOf("pm grant $pkg $permission")

            Manifest.permission.PACKAGE_USAGE_STATS ->
                listOf("appops set $pkg GET_USAGE_STATS allow")

            Manifest.permission.SCHEDULE_EXACT_ALARM ->
                listOf("appops set $pkg SCHEDULE_EXACT_ALARM allow")

            Manifest.permission.REQUEST_INSTALL_PACKAGES ->
                listOf("appops set $pkg REQUEST_INSTALL_PACKAGES allow")

            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ->
                listOf("dumpsys deviceidle whitelist +$pkg")

            else -> null
        }
    }

    /**
     * Adds [component] to the enabled accessibility services. The Secure setting is
     * shared with every other accessibility service, so the current value is merged
     * instead of replaced — writing it outright would silently disable them all.
     */
    fun accessibilityCommands(
        currentServices: String?,
        component: String,
    ): List<String> {
        val existing = currentServices
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.split(":")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (existing.contains(component)) return emptyList()
        val merged = (existing + component).joinToString(":")
        return listOf(
            "settings put secure enabled_accessibility_services $merged",
            "settings put secure accessibility_enabled 1",
        )
    }

    /**
     * Every command worth attempting at startup. Each one is idempotent, so this
     * is safe to run on every launch; without root every command fails fast.
     */
    fun bridgeCommands(
        context: Context,
        notificationListenerEnabled: Boolean,
        accessibilityServices: String?,
        accessibilityComponent: String?,
    ): List<String> = buildList {
        for (permission in BRIDGEABLE_PERMISSIONS) {
            commandsFor(context, permission)?.let(::addAll)
        }
        if (!notificationListenerEnabled) {
            add("cmd notification allow_listener $NOTIFICATION_LISTENER_COMPONENT")
        }
        if (accessibilityComponent != null) {
            addAll(accessibilityCommands(accessibilityServices, accessibilityComponent))
        }
    }

    /** Runs [commands] through the root shell; a failure on one does not skip the rest. */
    suspend fun runRoot(commands: List<String>): Boolean = withContext(Dispatchers.IO) {
        var allSucceeded = true
        for (command in commands) {
            val ok = runCatching {
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exit = process.waitFor()
                if (exit != 0) {
                    XLog.w("PermissionBridge failed (%d) for '%s': %s", exit, command, output)
                }
                exit == 0
            }.onFailure { error ->
                XLog.w("PermissionBridge unavailable for '%s': %s", command, error.message ?: "unknown")
            }.getOrDefault(false)
            if (!ok) allSucceeded = false
        }
        allSucceeded
    }

    private val BRIDGEABLE_PERMISSIONS = arrayOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.PACKAGE_USAGE_STATS,
        Manifest.permission.SCHEDULE_EXACT_ALARM,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    )
}
