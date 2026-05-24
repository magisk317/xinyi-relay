package io.github.magisk317.relay.contract.xpbridge

import android.content.Context

interface XpAppConfigRuntimeBridge {
    suspend fun isPackageBlocked(context: Context, packageName: String): Boolean
}

object NoopXpAppConfigRuntimeBridge : XpAppConfigRuntimeBridge {
    override suspend fun isPackageBlocked(context: Context, packageName: String): Boolean = false
}
