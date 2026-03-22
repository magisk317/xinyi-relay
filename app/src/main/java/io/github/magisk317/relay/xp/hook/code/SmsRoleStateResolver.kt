package io.github.magisk317.relay.xp.hook.code

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony

internal data class SmsRoleState(
    val defaultSms: String?,
    val roleHolders: List<String>,
)

internal class SmsRoleStateResolver(
    private val defaultSmsProvider: (Context) -> String? = { context ->
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
    },
    private val roleHoldersProvider: (Context) -> List<String> = { context ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            emptyList()
        } else {
            runCatching {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager == null) {
                    emptyList()
                } else {
                    val method = roleManager.javaClass.getMethod("getRoleHolders", String::class.java)
                    @Suppress("UNCHECKED_CAST")
                    (method.invoke(roleManager, RoleManager.ROLE_SMS) as? List<*>)?.filterIsInstance<String>()
                        .orEmpty()
                }
            }.getOrDefault(emptyList())
        }
    },
) {
    fun resolve(context: Context): SmsRoleState {
        return SmsRoleState(
            defaultSms = defaultSmsProvider(context),
            roleHolders = roleHoldersProvider(context),
        )
    }
}
