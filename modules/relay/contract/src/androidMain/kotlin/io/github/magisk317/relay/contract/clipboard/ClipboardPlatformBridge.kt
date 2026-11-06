package io.github.magisk317.relay.contract.clipboard

import android.content.Context

interface ClipboardPlatformBridge {
    fun copyToClipboard(context: Context, text: String?): Boolean
}

object NoopClipboardPlatformBridge : ClipboardPlatformBridge {
    override fun copyToClipboard(context: Context, text: String?) = false
}
