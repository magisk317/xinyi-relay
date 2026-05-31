package io.github.magisk317.relay.ui.home

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import kotlinx.coroutines.launch

@Composable
internal fun StartupPermissionPrompt(enabled: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var promptStarted by remember { mutableStateOf(false) }
    var pendingSpecialPermissions by remember { mutableStateOf<List<StartupSpecialPermission>>(emptyList()) }
    val launchNextSpecialPermission = remember { arrayOf<() -> Unit>({}) }

    fun finishPrompt() {
        context.markStartupPermissionsPromptedForCurrentVersion()
        val stillMissing = context.collectMissingStartupPermissionLabels()
        if (stillMissing.isNotEmpty()) {
            scope.launch {
                snackbarHostState.showLatestSnackbar(
                    context.getString(
                        R.string.startup_permission_missing_warning,
                        stillMissing.joinToString(context.getString(R.string.scheduled_task_permission_separator)),
                    ),
                )
            }
        }
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        launchNextSpecialPermission[0]()
    }

    val specialPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        launchNextSpecialPermission[0]()
    }

    launchNextSpecialPermission[0] = {
        val next = pendingSpecialPermissions.firstOrNull()
        if (next == null) {
            finishPrompt()
        } else {
            pendingSpecialPermissions = pendingSpecialPermissions.drop(1)
            val intent = next.buildIntent(context)
            if (intent == null) {
                launchNextSpecialPermission[0]()
            } else {
                runCatching {
                    specialPermissionLauncher.launch(intent)
                }.onFailure {
                    launchNextSpecialPermission[0]()
                }
            }
        }
    }

    fun startPromptIfNeeded() {
        if (promptStarted || !enabled || context.hasPromptedStartupPermissionsForCurrentVersion()) {
            return
        }
        promptStarted = true
        val runtimePermissions = context.collectMissingStartupRuntimePermissions()
        val specialPermissions = context.collectMissingStartupSpecialPermissions()
        if (runtimePermissions.isEmpty() && specialPermissions.isEmpty()) {
            context.markStartupPermissionsPromptedForCurrentVersion()
            return
        }
        pendingSpecialPermissions = specialPermissions
        if (runtimePermissions.isNotEmpty()) {
            runtimePermissionLauncher.launch(runtimePermissions.toTypedArray())
        } else {
            launchNextSpecialPermission[0]()
        }
    }

    LaunchedEffect(enabled) {
        startPromptIfNeeded()
    }
}

private enum class StartupSpecialPermission(@param:StringRes val labelRes: Int) {
    EXACT_ALARM(R.string.startup_permission_exact_alarm),
    USAGE_ACCESS(R.string.startup_permission_usage_access),
    INSTALL_UNKNOWN_APPS(R.string.startup_permission_install_unknown_apps),
    NOTIFICATION_LISTENER(R.string.startup_permission_notification_listener),
    ACCESSIBILITY_SERVICE(R.string.startup_permission_accessibility_service),
}

private fun Context.collectMissingStartupPermissionLabels(): List<String> {
    val runtimeLabels = collectMissingStartupRuntimePermissions().map { it.startupPermissionLabel(this) }
    val specialLabels = collectMissingStartupSpecialPermissions().map { getString(it.labelRes) }
    return (runtimeLabels + specialLabels).distinct()
}

private fun Context.collectMissingStartupRuntimePermissions(): List<String> {
    return requestedPermissionSet()
        .filter { permission ->
            permission.isRuntimePermissionSupported() &&
                permission.isDangerousPermission(this) &&
                !hasPermission(permission)
        }
        .distinct()
}

private fun Context.collectMissingStartupSpecialPermissions(): List<StartupSpecialPermission> {
    val requestedPermissions = requestedPermissionSet()
    return StartupSpecialPermission.values()
        .filter { it.isApplicable(this, requestedPermissions) && !it.isGranted(this) }
}

private fun StartupSpecialPermission.isApplicable(
    context: Context,
    requestedPermissions: Set<String>,
): Boolean {
    return when (this) {
        StartupSpecialPermission.EXACT_ALARM ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                Manifest.permission.SCHEDULE_EXACT_ALARM in requestedPermissions

        StartupSpecialPermission.USAGE_ACCESS ->
            Manifest.permission.PACKAGE_USAGE_STATS in requestedPermissions

        StartupSpecialPermission.INSTALL_UNKNOWN_APPS ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                Manifest.permission.REQUEST_INSTALL_PACKAGES in requestedPermissions

        StartupSpecialPermission.NOTIFICATION_LISTENER ->
            context.isServiceDeclared(NOTIFICATION_LISTENER_SERVICE_CLASS_NAME)

        StartupSpecialPermission.ACCESSIBILITY_SERVICE ->
            BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT &&
                context.isServiceDeclared(AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME)
    }
}

private fun StartupSpecialPermission.isGranted(context: Context): Boolean {
    return when (this) {
        StartupSpecialPermission.EXACT_ALARM -> context.canScheduleExactAlarms()
        StartupSpecialPermission.USAGE_ACCESS -> context.hasUsageAccess()
        StartupSpecialPermission.INSTALL_UNKNOWN_APPS -> context.canRequestPackageInstalls()
        StartupSpecialPermission.NOTIFICATION_LISTENER -> context.isNotificationListenerEnabled()
        StartupSpecialPermission.ACCESSIBILITY_SERVICE -> context.isAccessibilityServiceEnabled()
    }
}

private fun StartupSpecialPermission.buildIntent(context: Context): Intent? {
    return when (this) {
        StartupSpecialPermission.EXACT_ALARM -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                null
            } else {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }

        StartupSpecialPermission.USAGE_ACCESS ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

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
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            }
        }
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
        Manifest.permission.CAMERA -> context.getString(R.string.startup_permission_camera)
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

private fun Context.hasUsageAccess(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun Context.canRequestPackageInstalls(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

private fun Context.isNotificationListenerEnabled(): Boolean {
    return isComponentEnabledInSecureSetting(
        className = NOTIFICATION_LISTENER_SERVICE_CLASS_NAME,
        settingName = "enabled_notification_listeners",
    )
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
            val accessibilityManager = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
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

private fun Context.hasPromptedStartupPermissionsForCurrentVersion(): Boolean {
    val promptedVersion = getSharedPreferences(STARTUP_PERMISSION_PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_PROMPTED_VERSION_CODE, Long.MIN_VALUE)
    return promptedVersion == currentVersionCode()
}

private fun Context.markStartupPermissionsPromptedForCurrentVersion() {
    getSharedPreferences(STARTUP_PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_PROMPTED_VERSION_CODE, currentVersionCode())
        .apply()
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

private const val STARTUP_PERMISSION_PREFS = "startup_permission_prompt"
private const val KEY_PROMPTED_VERSION_CODE = "prompted_version_code"
private const val NOTIFICATION_LISTENER_SERVICE_CLASS_NAME =
    "io.github.magisk317.relay.service.AppNotificationListenerService"
private const val AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME =
    "io.github.magisk317.relay.service.AutoInputAccessibilityService"
