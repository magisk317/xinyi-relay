package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import com.github.magisk317.smscode.common.utils.ClipboardUtils
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.RunnableAction

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
