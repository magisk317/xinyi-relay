package io.github.magisk317.relay.platform.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.xpbridge.XpAppConfigRuntimeBridge
import io.github.magisk317.relay.domain.system.RuntimeAppConfigFacade

object RuntimeXpAppConfigBridge : XpAppConfigRuntimeBridge {
    override suspend fun isPackageBlocked(context: Context, packageName: String): Boolean {
        return RuntimeAppConfigFacade(context).isPackageBlocked(packageName)
    }
}
