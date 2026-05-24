package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.xpbridge.NoopXpAppConfigRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpAppConfigRuntimeBridge

class XpAppConfigFacade(
    context: Context,
    private val bridge: XpAppConfigRuntimeBridge = runtimeBridge,
) {
    private val appContext = context.applicationContext ?: context

    suspend fun isPackageBlocked(packageName: String): Boolean {
        return bridge.isPackageBlocked(appContext, packageName)
    }

    companion object {
        @Volatile
        private var runtimeBridge: XpAppConfigRuntimeBridge = NoopXpAppConfigRuntimeBridge

        fun installRuntimeBridge(bridge: XpAppConfigRuntimeBridge?) {
            runtimeBridge = bridge ?: NoopXpAppConfigRuntimeBridge
        }
    }
}
