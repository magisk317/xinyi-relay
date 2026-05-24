package io.github.magisk317.relay.android.platform.clipboard

import android.content.Context
import io.github.magisk317.relay.contract.clipboard.ClipboardPlatformBridge
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils

object AndroidClipboardPlatformBridge : ClipboardPlatformBridge {
    override fun copyToClipboard(context: Context, text: String?) {
        ClipboardUtils.copyToClipboard(context, text)
    }
}
