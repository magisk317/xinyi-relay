package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.clipboard.ClipboardPlatformBridge
import io.github.magisk317.relay.contract.clipboard.NoopClipboardPlatformBridge

object XpClipboard {
    @Volatile
    private var platformBridge: ClipboardPlatformBridge = NoopClipboardPlatformBridge

    fun installPlatformBridge(bridge: ClipboardPlatformBridge?) {
        platformBridge = bridge ?: NoopClipboardPlatformBridge
    }

    fun copyToClipboard(context: Context, text: String?) {
        platformBridge.copyToClipboard(context, text)
    }
}
