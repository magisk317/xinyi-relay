package io.github.magisk317.relay.xp

import android.content.Context
import io.github.magisk317.relay.domain.system.RuntimeAppConfigFacade

class XpAppConfigFacade(
    context: Context,
    private val delegate: RuntimeAppConfigFacade = RuntimeAppConfigFacade(context),
) {
    suspend fun isPackageBlocked(packageName: String): Boolean {
        return delegate.isPackageBlocked(packageName)
    }
}
