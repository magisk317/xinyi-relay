package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.RunnableAction
import io.github.magisk317.relay.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.verification.ToastActionHelper

/**
 * 显示验证码 Toast（唯一保留的 Toast 入口）
 */
class ToastAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean,
) :
    RunnableAction(pluginContext, phoneContext, smsMsg) {
    override fun action(): Bundle? {
        ToastActionHelper.showCodeToast(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsMsg = mSmsMsg,
            enabled = enabled,
            messageTextProvider = { context, smsCode ->
                context.getString(R.string.current_sms_code, smsCode)
            },
            fallbackToastSender = InputHelper::sendToast,
            duration = Toast.LENGTH_LONG,
        )
        return null
    }
}
