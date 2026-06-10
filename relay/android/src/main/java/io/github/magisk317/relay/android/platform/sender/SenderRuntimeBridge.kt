package io.github.magisk317.relay.android.platform.sender

import android.content.Context
import io.github.magisk317.relay.sender.SenderRuntimeInstaller

/**
 * Wires relay/sender runtime services into relay/engine:api contracts.
 */
object SenderRuntimeBridge {
    fun install(context: Context? = null) {
        SenderRuntimeInstaller.install()
        if (context != null) {
            SenderRuntimeInstaller.initE2eeAvailability(context)
        }
    }
}
