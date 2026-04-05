package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils

object XpClipboard {
    fun copyToClipboard(context: Context, text: String?) {
        ClipboardUtils.copyToClipboard(context, text)
    }
}
