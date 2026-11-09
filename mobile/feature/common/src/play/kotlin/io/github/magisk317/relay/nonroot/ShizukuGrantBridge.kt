package io.github.magisk317.relay.nonroot

import android.content.Context
import io.github.magisk317.xposed.permission.StartupPermissionConfig

/** Play builds keep the non-root flow disabled. */
object ShizukuGrantBridge {
    fun isSupported(): Boolean = false

    fun isAuthorized(): Boolean = false

    fun requestPermission(context: Context, onResult: (Boolean) -> Unit) = onResult(false)

    suspend fun grant(
        context: Context,
        config: StartupPermissionConfig,
        log: (String) -> Unit,
    ): Boolean = false
}
