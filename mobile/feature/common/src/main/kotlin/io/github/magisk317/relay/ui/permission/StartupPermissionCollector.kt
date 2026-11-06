package io.github.magisk317.relay.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.xposed.permission.PermissionBridge
import io.github.magisk317.xposed.permission.SetupProgress
import io.github.magisk317.xposed.permission.SetupProgressPolicy
import io.github.magisk317.xposed.permission.SetupProgressStore
import io.github.magisk317.xposed.permission.StartupPermissionCollectorCore
import io.github.magisk317.xposed.permission.StartupPermissionConfig
import io.github.magisk317.xposed.permission.StartupPermissionProbes
import io.github.magisk317.xposed.permission.StartupRequirement
import io.github.magisk317.xposed.permission.StartupRootGrants

/**
 * xinyi host adapter over the shared startup-permission core (kit permission module).
 *
 * Everything platform-shaped and the collection flow live in the kit; this file keeps
 * only the product surface: labels (host copy), component class names, and the
 * PermissionBridge-backed root channel.
 */
data class PermissionRequirement(
    val key: String,
    val label: String,
    val rootGrantable: Boolean,
) {
    /** True for settings-page (non runtime) permissions. */
    val isSpecial: Boolean get() = key.startsWith(SPECIAL_KEY_PREFIX)

    companion object {
        const val SPECIAL_KEY_PREFIX = StartupRequirement.SPECIAL_KEY_PREFIX

        /** The battery-optimization exemption requirement; excluded from the early prompt. */
        const val BATTERY_OPTIMIZATION_KEY = "$SPECIAL_KEY_PREFIX" + "battery_optimization"
    }
}

data class PermissionStatus(
    val requirement: PermissionRequirement,
    val granted: Boolean,
)

object StartupPermissionCollector {

    /** Missing runtime + special permissions, runtime ones first, deduplicated by key. */
    fun collectMissing(context: Context, config: StartupPermissionConfig): List<PermissionRequirement> {
        return StartupPermissionCollectorCore.collectMissing(context, config, { key -> startupLabelFor(context, key) }, relayRootGrantChannel)
            .map { requirement ->
                PermissionRequirement(
                    key = requirement.key,
                    label = startupLabelFor(context, requirement.key),
                    rootGrantable = requirement.rootGrantable,
                )
            }
    }

    /** Every startup permission with its live grant state, runtime ones first. */
    fun collectStatuses(context: Context, config: StartupPermissionConfig): List<PermissionStatus> {
        return StartupPermissionCollectorCore.collectStatuses(context, config, { key -> startupLabelFor(context, key) }, relayRootGrantChannel)
            .map { status ->
                PermissionStatus(
                    requirement = PermissionRequirement(
                        key = status.requirement.key,
                        label = startupLabelFor(context, status.requirement.key),
                        rootGrantable = status.requirement.rootGrantable,
                    ),
                    granted = status.granted,
                )
            }
    }

    /** System settings intent for [requirement], or null for runtime permissions. */
    fun buildSystemIntent(context: Context, requirement: PermissionRequirement): Intent? {
        return io.github.magisk317.xposed.permission.StartupSetupPermissionIntents.buildSystemIntent(
            context = context,
            requirementKey = requirement.key,
            config = startupPermissionConfig(context, enableAutoInputAccessibility = true),
        )
    }

    /** True when the notification-listener component is enabled in secure settings. */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        return StartupPermissionCollectorCore.isNotificationListenerEnabled(context, startupPermissionConfig(context, enableAutoInputAccessibility = true))
    }

    /** Host copy: curated labels first, PackageManager label fallback for the rest. */
    private fun startupLabelFor(context: Context, key: String): String {
        if (key.startsWith(StartupRequirement.SPECIAL_KEY_PREFIX)) {
            val specialKey = key.removePrefix(StartupRequirement.SPECIAL_KEY_PREFIX)
            return when (specialKey) {
                "exact_alarm" -> context.getString(R.string.startup_permission_exact_alarm)
                "install_unknown_apps" -> context.getString(R.string.startup_permission_install_unknown_apps)
                "notification_listener" -> context.getString(R.string.startup_permission_notification_listener)
                "accessibility_service" -> context.getString(R.string.startup_permission_accessibility_service)
                "battery_optimization" -> context.getString(R.string.startup_permission_battery_optimization)
                else -> specialKey
            }
        }
        return when (key) {
            Manifest.permission.SEND_SMS -> context.getString(R.string.startup_permission_send_sms)
            Manifest.permission.READ_PHONE_STATE -> context.getString(R.string.startup_permission_read_phone_state)
            Manifest.permission.POST_NOTIFICATIONS -> context.getString(R.string.startup_permission_post_notifications)
            Manifest.permission.READ_CONTACTS -> context.getString(R.string.startup_permission_read_contacts)
            else -> StartupPermissionProbes.defaultPermissionLabel(context, key)
        }
    }
}

/** Builds the app-specific collector config for this package and distribution. */
fun startupPermissionConfig(context: Context, enableAutoInputAccessibility: Boolean): StartupPermissionConfig {
    return StartupPermissionConfig(
        notificationListenerComponent = "${context.packageName}/$NOTIFICATION_LISTENER_SERVICE_CLASS_NAME",
        autoInputAccessibilityComponent = if (enableAutoInputAccessibility) {
            "${context.packageName}/$AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME"
        } else {
            null
        },
    )
}

/**
 * Grants everything the root bridge can cover for this app in one pass. Every command
 * is idempotent, so this is safe to re-run (retry button).
 */
fun runRootGrants(
    context: Context,
    config: StartupPermissionConfig,
    log: ((String) -> Unit)? = null,
): Boolean = StartupRootGrants.grantAll(context, config, log)

private val relayRootGrantChannel = object : io.github.magisk317.xposed.permission.StartupRootGrantChannel {
    override fun coverage(
        context: Context,
        requirementKey: String,
    ): io.github.magisk317.xposed.permission.StartupRootCoverage {
        // The bridge command table is deterministic: coverage never comes back UNKNOWN.
        if (requirementKey.startsWith(StartupRequirement.SPECIAL_KEY_PREFIX)) {
            return io.github.magisk317.xposed.permission.StartupRootCoverage.COVERED
        }
        val covered = PermissionBridge.commandsFor(context, requirementKey) != null
        return if (covered) {
            io.github.magisk317.xposed.permission.StartupRootCoverage.COVERED
        } else {
            io.github.magisk317.xposed.permission.StartupRootCoverage.NOT_COVERED
        }
    }

    override fun grantAll(context: Context, config: StartupPermissionConfig, log: ((String) -> Unit)?): Boolean =
        StartupRootGrants.grantAll(context, config, log)
}

const val STARTUP_PERMISSION_PREFS = io.github.magisk317.xposed.permission.STARTUP_PERMISSION_PREFS
private const val NOTIFICATION_LISTENER_SERVICE_CLASS_NAME =
    "io.github.magisk317.relay.service.AppNotificationListenerService"
private const val AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME =
    "io.github.magisk317.relay.service.AutoInputAccessibilityService"
