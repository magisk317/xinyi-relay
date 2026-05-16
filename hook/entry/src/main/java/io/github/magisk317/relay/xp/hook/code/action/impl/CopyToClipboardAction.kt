package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpClipboard
import io.github.magisk317.relay.xp.hook.code.action.RunnableAction
import io.github.magisk317.smscode.verification.CopyToClipboardActionHelper

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
            CopyToClipboardActionHelper.copyCode(
                phoneContext = mPhoneContext,
                smsCode = mSmsMsg.smsCode,
                copyAction = XpClipboard::copyToClipboard,
            )
        }
        return null
    }
}
