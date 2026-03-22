package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.xp.SmsMsg
import io.github.magisk317.relay.xp.XpClipboard
import io.github.magisk317.relay.xp.hook.code.action.RunnableAction
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * 将验证码复制到剪切板
 */
class CopyToClipboardAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean,
) :
    RunnableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (enabled) {
            copyToClipboard()
        }
        return null
    }

    private fun copyToClipboard() {
        try {
            XLog.d("Attempting to copy code to clipboard with context: $mPhoneContext")
            XpClipboard.copyToClipboard(mPhoneContext, mSmsMsg.smsCode)
        } catch (e: Exception) {
            XLog.e("Failed to copy to clipboard", e)
        }
    }
}
