package io.github.magisk317.relay.ui.permission

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.relay.feature.mode.BatteryOptimizationHelper
import io.github.magisk317.relay.mobilefeature.common.BuildConfig
import io.github.magisk317.xposed.permission.PermissionBridge

/**
 * One permission the startup flow asks for, described independently of any screen.
 *
 * [key] is either a manifest permission name (runtime permission) or
 * `special:<name>` (settings-page permission). [rootGrantable] tells whether the
 * root bridge step is expected to cover it, so a screen can label the grant
 * channel without re-deriving bridge coverage.
 */
data class PermissionRequirement(
    val key: String,
    val label: String,
    val rootGrantable: Boolean,
) {
    /** True for settings-page (non runtime) permissions. */
    val isSpecial: Boolean get() = key.startsWith(SPECIAL_KEY_PREFIX)

    companion object {
        const val SPECIAL_KEY_PREFIX = "special:"

        /** The battery-optimization exemption requirement; excluded from the early prompt. */
        const val BATTERY_OPTIMIZATION_KEY = "$SPECIAL_KEY_PREFIX" + "battery_optimization"
    }
}

data class PermissionStatus(
    val requirement: PermissionRequirement,
    val granted: Boolean,
)

/**
 * App-specific wiring the collector needs to stay package-agnostic: which
 * notification listener / accessibility component to probe, and whether the
 * accessibility auto-input service exists in this distribution at all.
 */
data class StartupPermissionConfig(
    val notificationListenerComponent: String,
    val autoInputAccessibilityComponent: String?,
)

/**
 * Collects the startup permissions the app still misses and knows how to send
 * the user to the matching system page.
 *
 * The logic used to live inside `StartupPermissionPrompt`; it is shared here so
 * the setup wizard and the legacy startup prompt collect the exact same set.
 * Everything app-specific arrives through [StartupPermissionConfig], so another
 * project can reuse the collector by supplying its own components.
 */
object StartupPermissionCollector {

    /** Missing runtime + special permissions, runtime ones first, deduplicated by key. */
    fun collectMissing(context: Context, config: StartupPermissionConfig): List<PermissionRequirement> {
        val requestedPermissions = context.requestedPermissionSet()
        val missing = mutableListOf<PermissionRequirement>()
        requestedPermissions
            .filter { permission ->
                permission.isRuntimePermissionSupported() &&
                    permission.isDangerousPermission(context) &&
                    !context.hasPermission(permission)
            }
            .distinct()
            .mapTo(missing) { permission ->
                PermissionRequirement(
                    key = permission,
                    label = permission.startupPermissionLabel(context),
                    rootGrantable = PermissionBridge.commandsFor(context, permission) != null,
                )
            }
        StartupSpecialPermission.values()
            .filter { it.isApplicable(context, requestedPermissions, config) && !it.isGranted(context) }
            .mapTo(missing) { special ->
                PermissionRequirement(
                    key = PermissionRequirement.SPECIAL_KEY_PREFIX + special.key,
                    label = context.getString(special.labelRes),
                    rootGrantable = true,
                )
            }
        return missing.distinctBy(PermissionRequirement::key)
    }

    /**
     * Every startup permission with its live grant state, runtime ones first,
     * deduplicated by key.
     *
     * Unlike [collectMissing] this keeps the granted entries too, so the done
     * screen can list the full set with per-item status instead of a bare count.
     */
    fun collectStatuses(context: Context, config: StartupPermissionConfig): List<PermissionStatus> {
        val requestedPermissions = context.requestedPermissionSet()
        val statuses = mutableListOf<PermissionStatus>()
        requestedPermissions
            .filter { permission ->
                permission.isRuntimePermissionSupported() && permission.isDangerousPermission(context)
            }
            .distinct()
            .mapTo(statuses) { permission ->
                PermissionStatus(
                    requirement = PermissionRequirement(
                        key = permission,
                        label = permission.startupPermissionLabel(context),
                        rootGrantable = PermissionBridge.commandsFor(context, permission) != null,
                    ),
                    granted = context.hasPermission(permission),
                )
            }
        StartupSpecialPermission.values()
            .filter { it.isApplicable(context, requestedPermissions, config) }
            .mapTo(statuses) { special ->
                PermissionStatus(
                    requirement = PermissionRequirement(
                        key = PermissionRequirement.SPECIAL_KEY_PREFIX + special.key,
                        label = context.getString(special.labelRes),
                        rootGrantable = true,
                    ),
                    granted = special.isGranted(context),
                )
            }
        return statuses.distinctBy { it.requirement.key }
    }


    /**
     * System settings intent for [requirement], or null for runtime permissions
     * (those are requested through the permission launcher) and for specials
     * whose page does not exist on this OS version.
     */
    fun buildSystemIntent(context: Context, requirement: PermissionRequirement): Intent? {
        if (!requirement.isSpecial) return null
        val special = specialByKey[requirement.key.removePrefix(PermissionRequirement.SPECIAL_KEY_PREFIX)]
            ?: return null
        return when (special) {
            StartupSpecialPermission.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    null
                } else {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }

            StartupSpecialPermission.INSTALL_UNKNOWN_APPS -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    null
                } else {
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    )
                }
            }

            StartupSpecialPermission.NOTIFICATION_LISTENER ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

            StartupSpecialPermission.ACCESSIBILITY_SERVICE -> {
                if (context.isAccessibilityServiceListed()) {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    val componentName = ComponentName(
                        context,
                        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
                    ).flattenToString()
                    intent.putExtra(":settings:fragment_args_key", componentName)
                    intent.putExtra(":settings:show_fragment_args", android.os.Bundle())
                    intent
                } else {
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                }
            }

            StartupSpecialPermission.BATTERY_OPTIMIZATION ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
        }
    }

    /** True when this device can run commands through a root shell. */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        return context.isComponentEnabledInSecureSetting(
            className = NOTIFICATION_LISTENER_SERVICE_CLASS_NAME,
            settingName = "enabled_notification_listeners",
        )
    }

    private val specialByKey = StartupSpecialPermission.values().associateBy { it.key }
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
 * Grants everything the root bridge can cover for this app in one pass.
 *
 * Every command is idempotent, so this is safe to re-run (retry button). The
 * remaining gap is whatever root cannot grant; callers re-collect afterwards.
 */
fun runRootGrants(
    context: Context,
    config: StartupPermissionConfig,
    log: ((String) -> Unit)? = null,
): Boolean {
    return PermissionBridge.runRoot(
        PermissionBridge.bridgeCommands(
            context = context,
            notificationListenerComponent = config.notificationListenerComponent,
            notificationListenerEnabled = StartupPermissionCollector.isNotificationListenerEnabled(context),
            accessibilityServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ),
            accessibilityComponent = config.autoInputAccessibilityComponent,
        ),
        log = log,
    )
}

/**
 * Version-level marker for the startup permission prompt: the prompt only fires
 * again after an app upgrade, never on every launch.
 */
object StartupPromptStore {

    fun hasPrompted(context: Context): Boolean {
        val promptedVersion = context.getSharedPreferences(STARTUP_PERMISSION_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PROMPTED_VERSION_CODE, Long.MIN_VALUE)
        return promptedVersion == context.currentVersionCode()
    }

    fun markPromptedForCurrentVersion(context: Context) {
        context.getSharedPreferences(STARTUP_PERMISSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PROMPTED_VERSION_CODE, context.currentVersionCode())
            .apply()
    }
}

enum class SetupProgress(val completed: Boolean, val rootSelected: Boolean) {
    ROOT_PENDING(false, false),
    GRANT_ROOT(false, true),
    GRANT_MANUAL(false, false),
    COMPLETE_ROOT(true, true),
    COMPLETE_MANUAL(true, false),
}

object SetupProgressPolicy {
    fun shouldShowWizard(privacyAccepted: Boolean, progress: SetupProgress?): Boolean =
        !privacyAccepted || progress?.completed != true
}

object SetupProgressStore {

    fun current(context: Context): SetupProgress? {
        val preferences = context.getSharedPreferences(STARTUP_PERMISSION_PREFS, Context.MODE_PRIVATE)
        if (preferences.getLong(SETUP_VERSION_CODE, Long.MIN_VALUE) != context.currentVersionCode()) {
            return null
        }
        return preferences.getString(SETUP_PROGRESS, null)
            ?.let { value -> runCatching { SetupProgress.valueOf(value) }.getOrNull() }
    }

    fun isComplete(context: Context): Boolean = current(context)?.completed == true

    fun mark(context: Context, progress: SetupProgress) {
        context.getSharedPreferences(STARTUP_PERMISSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(SETUP_VERSION_CODE, context.currentVersionCode())
            .putString(SETUP_PROGRESS, progress.name)
            .apply()
    }

    fun markComplete(context: Context) {
        val completed = if (current(context)?.rootSelected == true) {
            SetupProgress.COMPLETE_ROOT
        } else {
            SetupProgress.COMPLETE_MANUAL
        }
        mark(context, completed)
    }

    private const val SETUP_PROGRESS = "setup_progress"
    private const val SETUP_VERSION_CODE = "setup_progress_version_code"
}

private enum class StartupSpecialPermission(
    val key: String,
    @param:StringRes val labelRes: Int,
) {
    EXACT_ALARM("exact_alarm", R.string.startup_permission_exact_alarm),
    INSTALL_UNKNOWN_APPS("install_unknown_apps", R.string.startup_permission_install_unknown_apps),
    NOTIFICATION_LISTENER("notification_listener", R.string.startup_permission_notification_listener),
    ACCESSIBILITY_SERVICE("accessibility_service", R.string.startup_permission_accessibility_service),
    BATTERY_OPTIMIZATION("battery_optimization", R.string.startup_permission_battery_optimization),
}

private fun StartupSpecialPermission.isApplicable(
    context: Context,
    requestedPermissions: Set<String>,
    config: StartupPermissionConfig,
): Boolean {
    return when (this) {
        StartupSpecialPermission.EXACT_ALARM ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                Manifest.permission.SCHEDULE_EXACT_ALARM in requestedPermissions

        StartupSpecialPermission.INSTALL_UNKNOWN_APPS ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                Manifest.permission.REQUEST_INSTALL_PACKAGES in requestedPermissions

        StartupSpecialPermission.NOTIFICATION_LISTENER ->
            context.isServiceDeclared(NOTIFICATION_LISTENER_SERVICE_CLASS_NAME)

        StartupSpecialPermission.ACCESSIBILITY_SERVICE ->
            config.autoInputAccessibilityComponent != null &&
                context.isServiceDeclared(AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME)

        StartupSpecialPermission.BATTERY_OPTIMIZATION ->
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in requestedPermissions
    }
}

private fun StartupSpecialPermission.isGranted(context: Context): Boolean {
    return when (this) {
        StartupSpecialPermission.EXACT_ALARM -> context.canScheduleExactAlarms()
        StartupSpecialPermission.INSTALL_UNKNOWN_APPS -> context.canRequestPackageInstalls()
        StartupSpecialPermission.NOTIFICATION_LISTENER ->
            StartupPermissionCollector.isNotificationListenerEnabled(context)

        StartupSpecialPermission.ACCESSIBILITY_SERVICE -> context.isAccessibilityServiceEnabled()
        StartupSpecialPermission.BATTERY_OPTIMIZATION -> BatteryOptimizationHelper.isExempted(context)
    }
}

private fun Context.requestedPermissionSet(): Set<String> {
    return runCatching {
        selfPackageInfo(PackageManager.GET_PERMISSIONS).requestedPermissions?.toSet().orEmpty()
    }.getOrDefault(emptySet())
}

private fun Context.selfPackageInfo(flags: Int): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, flags)
    }
}

private fun String.isRuntimePermissionSupported(): Boolean {
    return when (this) {
        Manifest.permission.POST_NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        else -> true
    }
}

private fun String.isDangerousPermission(context: Context): Boolean {
    val permissionInfo = runCatching {
        context.packageManager.getPermissionInfo(this, 0)
    }.getOrNull() ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        permissionInfo.protection == PermissionInfo.PROTECTION_DANGEROUS
    } else {
        @Suppress("DEPRECATION")
        (permissionInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) ==
            PermissionInfo.PROTECTION_DANGEROUS
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun String.startupPermissionLabel(context: Context): String {
    return when (this) {
        Manifest.permission.SEND_SMS -> context.getString(R.string.startup_permission_send_sms)
        Manifest.permission.READ_PHONE_STATE -> context.getString(R.string.startup_permission_read_phone_state)
        Manifest.permission.POST_NOTIFICATIONS -> context.getString(R.string.startup_permission_post_notifications)
        Manifest.permission.READ_CONTACTS -> context.getString(R.string.startup_permission_read_contacts)
        else -> loadPermissionLabel(context)
    }
}

private fun String.loadPermissionLabel(context: Context): String {
    return runCatching {
        @Suppress("DEPRECATION")
        val permissionInfo = context.packageManager.getPermissionInfo(this, 0)
        permissionInfo.loadLabel(context.packageManager).toString()
    }.getOrDefault(this)
}

private fun Context.canScheduleExactAlarms(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
    return alarmManager.canScheduleExactAlarms()
}

private fun Context.canRequestPackageInstalls(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

private fun Context.isAccessibilityServiceEnabled(): Boolean {
    return isComponentEnabledInSecureSetting(
        className = AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
        settingName = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )
}

private fun Context.isComponentEnabledInSecureSetting(
    className: String,
    settingName: String,
): Boolean {
    val expectedService = ComponentName(packageName, className).flattenToString()
    val enabledServices = Settings.Secure.getString(contentResolver, settingName).orEmpty()
    if (enabledServices.isBlank()) return false
    return enabledServices.split(':').any { candidate ->
        candidate.equals(expectedService, ignoreCase = true)
    }
}

private fun Context.isAccessibilityServiceListed(): Boolean {
    return isServiceDeclared(AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME) &&
        runCatching {
            val accessibilityManager = getSystemService(AccessibilityManager::class.java)
            accessibilityManager?.getInstalledAccessibilityServiceList()?.any { serviceInfo ->
                val resolvedServiceInfo = serviceInfo.resolveInfo?.serviceInfo ?: return@any false
                resolvedServiceInfo.packageName == packageName &&
                    resolvedServiceInfo.name == AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME
            } == true
        }.getOrDefault(false)
}

private fun Context.isServiceDeclared(className: String): Boolean {
    val componentName = ComponentName(packageName, className)
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getServiceInfo(
                componentName,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getServiceInfo(componentName, PackageManager.GET_META_DATA)
        }
    }.isSuccess
}

private fun Context.currentVersionCode(): Long {
    return runCatching {
        val packageInfo = selfPackageInfo(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }.getOrDefault(BuildConfig.VERSION_CODE.toLong())
}

const val STARTUP_PERMISSION_PREFS = "startup_permission_prompt"
private const val KEY_PROMPTED_VERSION_CODE = "prompted_version_code"
private const val NOTIFICATION_LISTENER_SERVICE_CLASS_NAME =
    "io.github.magisk317.relay.service.AppNotificationListenerService"
private const val AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME =
    "io.github.magisk317.relay.service.AutoInputAccessibilityService"
