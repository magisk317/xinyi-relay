package io.github.magisk317.relay.nonroot

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

data class DeviceOwnerApplyResult(
    val success: Boolean,
    val message: String,
)

/**
 * Optional device-owner enhancements for the GitHub build.
 *
 * Activation itself must happen through adb; the app only explains the command
 * and applies policies after the user explicitly asks for them.
 */
object DeviceOwnerController {

    private const val RECEIVER_CLASS = "io.github.magisk317.relay.admin.XinyiDeviceAdminReceiver"

    fun isDeviceOwner(context: Context): Boolean {
        return runCatching {
            devicePolicyManager(context).isDeviceOwnerApp(context.packageName)
        }.getOrDefault(false)
    }

    fun setupCommand(context: Context): String {
        return "adb shell dpm set-device-owner ${context.packageName}/$RECEIVER_CLASS"
    }

    fun removeCommand(context: Context): String {
        return "adb shell dpm remove-active-admin ${context.packageName}/$RECEIVER_CLASS"
    }

    fun applyPolicies(context: Context, runtimePermissions: List<String>): DeviceOwnerApplyResult {
        if (!isDeviceOwner(context)) {
            return DeviceOwnerApplyResult(false, "Device owner is not active")
        }
        val dpm = devicePolicyManager(context)
        val admin = ComponentName(context.packageName, RECEIVER_CLASS)
        return runCatching {
            // Scope the grant to this package; setPermissionPolicy(AUTO_GRANT) would
            // change the runtime-permission policy for every app on the device.
            runtimePermissions
                .filterNot { it.startsWith("special:") }
                .forEach { permission ->
                    dpm.setPermissionGrantState(
                        admin,
                        context.packageName,
                        permission,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                    )
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setUserControlDisabledPackages(admin, listOf(context.packageName))
            }
            DeviceOwnerApplyResult(true, "Device-owner policies applied")
        }.getOrElse { error ->
            DeviceOwnerApplyResult(
                false,
                "Device-owner policy failed: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun devicePolicyManager(context: Context): DevicePolicyManager {
        return context.getSystemService(DevicePolicyManager::class.java)
            ?: error("DevicePolicyManager is unavailable")
    }
}
