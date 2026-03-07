package io.github.magisk317.relay.xp.runtime

import io.github.magisk317.relay.common.xp.NoopXpRuntimeBridge
import io.github.magisk317.relay.common.xp.XpRuntimeBridge

object RuntimeBridgeFactory {
    fun create(runtimeHandle: Any? = null): XpRuntimeBridge {
        if (runtimeHandle != null && LibXposedRuntimeBridge.looksLikeLibXposedInterface(runtimeHandle)) {
            return LibXposedRuntimeBridge(runtimeHandle)
        }
        return NoopXpRuntimeBridge
    }
}
