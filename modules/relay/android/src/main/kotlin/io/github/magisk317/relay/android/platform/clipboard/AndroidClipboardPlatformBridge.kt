package io.github.magisk317.relay.android.platform.clipboard

import android.content.ClipboardManager
import android.content.Context
import io.github.magisk317.relay.contract.clipboard.ClipboardPlatformBridge
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils

object AndroidClipboardPlatformBridge : ClipboardPlatformBridge {
    override fun copyToClipboard(context: Context, text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        ClipboardUtils.copyToClipboard(context, text)
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return false
        return runCatching {
            clip.itemCount > 0 && clip.getItemAt(0).coerceToText(context).toString() == text
        }.getOrDefault(false)
    }
}
