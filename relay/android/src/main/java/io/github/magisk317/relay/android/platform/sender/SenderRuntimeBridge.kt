package io.github.magisk317.relay.android.platform.sender

import io.github.magisk317.relay.sender.SenderRuntimeInstaller

/**
 * Wires relay/sender runtime services into relay/engine:api contracts.
 */
object SenderRuntimeBridge {
    fun install() {
        SenderRuntimeInstaller.install()
    }
}
