package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.RunnableAction

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
        if (enabled) {
            showCodeToast()
        }
        return null
    }

    private fun showCodeToast() {
        val text = mPluginContext.getString(R.string.current_sms_code, mSmsMsg.smsCode)
        Toast.makeText(mPhoneContext, text, Toast.LENGTH_LONG).show()
    }
}
