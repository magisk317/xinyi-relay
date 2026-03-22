package io.github.magisk317.relay.xp.hook.code.action.impl

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.xp.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.core.utils.XLog

class CancelNotifyAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    private var mNotificationId = NOTIFICATION_NONE

    fun setNotificationId(notificationId: Int) {
        mNotificationId = notificationId
    }

    override fun action(): Bundle? {
        cancelNotification()
        return null
    }

    private fun cancelNotification() {
        if (mNotificationId != NOTIFICATION_NONE) {
            val manager = mPhoneContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.let {
                it.cancel(mNotificationId)
                XLog.d("Notification auto cancelled")
            }
        }
    }

    companion object {
        private const val NOTIFICATION_NONE = -0xff
    }
}
