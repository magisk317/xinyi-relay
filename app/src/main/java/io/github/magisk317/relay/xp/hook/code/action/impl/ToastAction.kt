package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.RunnableAction

/**
 * 显示验证码Toast
 */
class ToastAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    RunnableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (PrefsReader.shouldShowToast(mPluginContext)) {
            showCodeToast()
        }
        return null
    }

    private fun showCodeToast() {
        val text = mPluginContext.getString(R.string.current_sms_code, mSmsMsg.smsCode)
        Toast.makeText(mPhoneContext, text, Toast.LENGTH_LONG).show()
    }
}
