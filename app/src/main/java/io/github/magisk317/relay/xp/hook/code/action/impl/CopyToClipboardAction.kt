package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.common.utils.ClipboardUtils
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.RunnableAction

/**
 * 将验证码复制到剪切板
 */
class CopyToClipboardAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    RunnableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (PrefsReader.copyToClipboardEnabled(mPluginContext)) {
            copyToClipboard()
        }
        return null
    }

    private fun copyToClipboard() {
        try {
            XLog.d("Attempting to copy code to clipboard with context: $mPhoneContext")
            ClipboardUtils.copyToClipboard(mPhoneContext, mSmsMsg.smsCode)
        } catch (e: Exception) {
            XLog.e("Failed to copy to clipboard", e)
        }
    }
}
