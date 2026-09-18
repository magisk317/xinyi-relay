package io.github.magisk317.relay.feature.mode

import io.github.magisk317.relay.android.platform.compat.PlatformCompat
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object StandardModePermissions {
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.RECEIVE_MMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
    )

    fun allGranted(context: Context): Boolean =
        requiredPermissions(context).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun missingPermissions(context: Context): List<String> =
        requiredPermissions(context).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    /**
     * Only request capabilities declared by the active distribution manifest.
     * For example, Play deliberately omits telephony permissions but still has
     * a valid Standard mode for notification forwarding and other system APIs.
     */
    fun requiredPermissions(context: Context): List<String> {
        val declaredPermissions = runCatching {
            PlatformCompat.getPackageInfo(
                context.packageManager,
                context.packageName,
                PackageManager.GET_PERMISSIONS.toLong(),
            ).requestedPermissions?.toSet().orEmpty()
        }.getOrNull()
        return requiredPermissionsFromDeclared(declaredPermissions)
    }

    internal fun requiredPermissionsFromDeclared(declaredPermissions: Set<String>?): List<String> {
        if (declaredPermissions == null) return REQUIRED_PERMISSIONS.toList()
        return REQUIRED_PERMISSIONS.filter(declaredPermissions::contains)
    }
}
