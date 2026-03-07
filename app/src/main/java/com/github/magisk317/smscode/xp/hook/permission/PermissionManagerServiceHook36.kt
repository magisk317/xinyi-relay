package com.github.magisk317.smscode.xp.hook.permission

import android.os.Build
import android.os.UserHandle
import com.github.magisk317.smscode.common.constant.PermConst.PACKAGE_PERMISSIONS
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.helper.MethodHookWrapper
import com.github.magisk317.smscode.xp.hook.BaseSubHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Since Android 16 (API 36+)
 *
 * In Android 16 QPR2, PermissionManagerServiceImpl was removed.
 * The implementation was merged into PermissionManagerService which
 * now extends IPermissionManager.Stub directly and delegates to
 * PermissionManagerServiceInterface via mPermissionManagerServiceImpl.
 *
 * Key changes from Android 14:
 * - Class: PermissionManagerServiceImpl -> PermissionManagerService
 * - Method: restorePermissionState() -> removed (no equivalent)
 * - Fields: mState, mRegistry -> removed
 * - PermissionCallback inner class -> removed
 * - grantRuntimePermission now takes a persistentDeviceId parameter
 *
 * Strategy: Hook onPackageInstalled() and use grantRuntimePermission()
 * to grant the needed permissions after a package is installed/updated.
 * Also hook onSystemReady() to grant permissions for already-installed packages.
 */
class PermissionManagerServiceHook36(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    override fun startHook() {
        try {
            hookOnSystemReady()
            hookOnPackageInstalled()
        } catch (e: Throwable) {
            XLog.e("Failed to hook PermissionManagerService for Android 16+", e)
        }
    }

    /**
     * Hook onSystemReady() to grant permissions for already-installed packages at boot.
     */
    private fun hookOnSystemReady() {
        XLog.d("Hooking onSystemReady() for Android 36+")
        val pmsClass = XposedHelpers.findClass(CLASS_PMS, mClassLoader)
        val methods = pmsClass.declaredMethods.filter { it.name == "onSystemReady" || it.name == "systemReady" }
        if (methods.isEmpty()) {
            XLog.w("Cannot find onSystemReady/systemReady in PermissionManagerService")
            return
        }
        methods.forEach { method ->
            XposedBridge.hookMethod(
                method,
                object : MethodHookWrapper() {
                    @Throws(Throwable::class)
                    override fun after(param: MethodHookParam) {
                        grantAllTargetPermissions(param.thisObject)
                    }
                },
            )
        }
    }

    /**
     * Hook onPackageInstalled() to grant permissions when a target package
     * is installed or updated after boot.
     */
    private fun hookOnPackageInstalled() {
        XLog.d("Hooking onPackageInstalled() for Android 36+")
        val pmsClass = XposedHelpers.findClass(CLASS_PMS, mClassLoader)
        val methods = pmsClass.declaredMethods.filter { method ->
            method.name in PACKAGE_INSTALL_CALLBACK_NAMES &&
                method.parameterTypes.isNotEmpty() &&
                method.parameterTypes.any { it.name == CLASS_ANDROID_PACKAGE || it.name.contains("AndroidPackage") }
        }
        if (methods.isEmpty()) {
            XLog.w("Cannot find package-installed callback in PermissionManagerService")
            return
        }
        methods.forEach { method ->
            XposedBridge.hookMethod(
                method,
                object : MethodHookWrapper() {
                    @Throws(Throwable::class)
                    override fun after(param: MethodHookParam) {
                        afterOnPackageInstalled(param)
                    }
                },
            )
        }
    }

    /**
     * Grant permissions for all target packages.
     * Called once after system is ready.
     */
    private fun grantAllTargetPermissions(pms: Any) {
        XLog.d("System ready - granting permissions for target packages")
        val userIds = try {
            getAllUserIds(pms)
        } catch (e: Throwable) {
            XLog.w("Cannot get user IDs, using default user 0", e)
            intArrayOf(0)
        }

        for ((packageName, permissions) in PACKAGE_PERMISSIONS) {
            for (userId in userIds) {
                grantPermissionsForPackage(pms, packageName, permissions, userId)
            }
        }
    }

    /**
     * After a package is installed, check if it's a target and grant permissions.
     */
    private fun afterOnPackageInstalled(param: XC_MethodHook.MethodHookParam) {
        val packageName = resolvePackageName(param.args) ?: return
        val permissions = PACKAGE_PERMISSIONS[packageName] ?: return
        val rawUserId = resolveRawUserId(param.args)
        val pms = param.thisObject

        val userIds = if (rawUserId == USER_ALL) {
            try {
                getAllUserIds(pms)
            } catch (e: Throwable) {
                XLog.w("Cannot get user IDs, using default user 0", e)
                intArrayOf(0)
            }
        } else {
            intArrayOf(rawUserId)
        }

        for (userId in userIds) {
            grantPermissionsForPackage(pms, packageName, permissions, userId)
        }
    }

    private fun resolvePackageName(args: Array<Any?>): String? {
        for (arg in args) {
            if (arg == null) continue
            val pkgName = try {
                XposedHelpers.callMethod(arg, "getPackageName") as? String
            } catch (_: Throwable) {
                null
            }
            if (!pkgName.isNullOrEmpty()) {
                return pkgName
            }
        }
        XLog.w("onPackageInstalled: cannot resolve package name from args")
        return null
    }

    private fun resolveRawUserId(args: Array<Any?>): Int {
        var candidate: Int? = null
        args.forEach { arg ->
            when (arg) {
                is Int -> candidate = arg
                is UserHandle -> {
                    candidate = try {
                        XposedHelpers.callMethod(arg, "getIdentifier") as Int
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        }
        return candidate ?: 0
    }

    /**
     * Grant a list of permissions to a package via grantRuntimePermission().
     * Uses the mPermissionManagerServiceImpl field to access the actual implementation.
     */
    private fun grantPermissionsForPackage(
        pms: Any,
        packageName: String,
        permissions: List<String>,
        userId: Int,
    ) {
        val impl = try {
            XposedHelpers.getObjectField(pms, "mPermissionManagerServiceImpl")
        } catch (e: Throwable) {
            XLog.w("Cannot access mPermissionManagerServiceImpl, using PMS directly", e)
            pms
        }

        for (permission in permissions) {
            try {
                // Android 16 grantRuntimePermission signature:
                // grantRuntimePermission(String packageName, String permName,
                //     String persistentDeviceId, int userId)
                XposedHelpers.callMethod(
                    impl,
                    "grantRuntimePermission",
                    packageName,
                    permission,
                    PERSISTENT_DEVICE_ID_DEFAULT,
                    userId,
                )
                XLog.d("Granted $permission to $packageName (user $userId)")
            } catch (e: Throwable) {
                // Permission might already be granted, or it's a signature permission
                // that can't be granted via grantRuntimePermission. This is expected
                // for some permission types.
                XLog.w("Cannot grant $permission to $packageName: ${e.message}")
            }
        }
    }

    /**
     * Get all user IDs via PackageManagerInternal.
     * Check if it returns IntArray or List.
     */
    private fun getAllUserIds(pms: Any): IntArray {
        val pmInt = XposedHelpers.getObjectField(pms, "mPackageManagerInt")
        val result = XposedHelpers.callMethod(pmInt, "getUsers", true)

        if (result is IntArray) {
            return result
        }

        if (result is List<*>) {
            val list = ArrayList<Int>()
            for (item in result) {
                if (item != null) {
                    // item is android.content.pm.UserInfo
                    val id = XposedHelpers.getIntField(item, "id")
                    list.add(id)
                }
            }
            return list.toIntArray()
        }

        return intArrayOf(0)
    }

    companion object {
        private const val CLASS_PMS =
            "com.android.server.pm.permission.PermissionManagerService"
        private const val CLASS_ANDROID_PACKAGE =
            "com.android.server.pm.pkg.AndroidPackage"
        private val PACKAGE_INSTALL_CALLBACK_NAMES = setOf(
            "onPackageInstalled",
            "onPackageAdded",
        )
        // VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
        private const val PERSISTENT_DEVICE_ID_DEFAULT = "default:0"
        private const val USER_ALL = -1
    }
}
