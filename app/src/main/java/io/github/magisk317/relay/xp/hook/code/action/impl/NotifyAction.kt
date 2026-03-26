package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.relay.xpbridge.XpCodeNotificationOwner
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * 显示验证码通知
 */
class NotifyAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean,
    private val autoCancelEnabled: Boolean,
    private val retentionTimeMs: Long,
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (enabled) {
            return showCodeNotification(mSmsMsg)
        }
        return null
    }

    private fun showCodeNotification(smsMsg: SmsMsg): Bundle? {
        return when (XpPrefs.getCodeNotificationOwner(mPluginContext)) {
            XpCodeNotificationOwner.PHONE -> showPhoneOwnedNotification(smsMsg)
            XpCodeNotificationOwner.APP -> showAppOwnedNotification(smsMsg)
            else -> {
                XLog.w("Skip code notification: owner not selected")
                null
            }
        }
    }

    private fun showAppOwnedNotification(smsMsg: SmsMsg): Bundle? {
        XpNotificationBridge.createNotificationChannel(
            mPluginContext,
            XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
            mPluginContext.getString(R.string.channel_name_relay_notification),
            android.app.NotificationManager.IMPORTANCE_HIGH,
        )
        val diagnostics = XpNotificationBridge.inspectDelivery(
            mPluginContext,
            XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
        )
        if (!diagnostics.canPost) {
            XLog.w(
                "App-owned code notification unavailable, fallback to phone-owned: %s",
                diagnostics.summary(),
            )
            return showPhoneOwnedNotification(smsMsg)
        }
        val notificationId = smsMsg.hashCode()
        val token = XpPrefs.getIpcToken(mPluginContext).takeIf { it.isNotBlank() }
        val intent = CodeNotificationBroadcastContract.createIntent(
            sender = smsMsg.sender,
            company = smsMsg.company,
            smsCode = smsMsg.smsCode,
            notificationId = notificationId,
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
            token = token,
        )
        mPhoneContext.sendBroadcast(intent)
        XLog.i(
            "Requested app-owned code notification id=%d autoCancel=%s retentionMs=%d tokenPresent=%s",
            notificationId,
            autoCancelEnabled,
            retentionTimeMs,
            token != null,
        )
        return null
    }

    private fun showPhoneOwnedNotification(smsMsg: SmsMsg): Bundle? {
        val manager = mPhoneContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager?
            ?: return null
        XpNotificationBridge.createNotificationChannel(
            mPhoneContext,
            XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
            mPluginContext.getString(R.string.channel_name_relay_notification),
            android.app.NotificationManager.IMPORTANCE_HIGH,
        )
        val notificationId = smsMsg.hashCode()
        val copyIntent = CopyCodeReceiver.createIntent(mPluginContext, smsMsg.smsCode, notificationId)
        val contentIntent = android.app.PendingIntent.getBroadcast(
            mPhoneContext,
            notificationId,
            copyIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag(),
        )
        val title = smsMsg.company?.takeIf { it.isNotBlank() }
            ?: smsMsg.sender?.takeIf { it.isNotBlank() }
            ?: mPluginContext.getString(R.string.app_name)
        val builder = NotificationCompat.Builder(mPluginContext, XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(BitmapFactory.decodeResource(mPluginContext.resources, R.drawable.ic_app_icon))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(title)
            .setContentText(mPluginContext.getString(R.string.code_notification_content, smsMsg.smsCode))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(mPluginContext, R.color.ic_launcher_background))
            .setGroup(XpNotificationBridge.GROUP_KEY_RELAY_NOTIFICATION)
        if (autoCancelEnabled && retentionTimeMs > 0L) {
            builder.setTimeoutAfter(retentionTimeMs)
        }
        manager.notify(notificationId, builder.build())
        XLog.i(
            "Posted phone-owned code notification id=%d autoCancel=%s retentionMs=%d",
            notificationId,
            autoCancelEnabled,
            retentionTimeMs,
        )
        return null
    }

    private fun pendingIntentFlag(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}
