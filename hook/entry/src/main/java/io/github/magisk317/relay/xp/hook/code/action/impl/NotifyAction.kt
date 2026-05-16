package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.hookentry.R
import io.github.magisk317.relay.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.relay.xpbridge.XpCodeNotificationOwner
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.verification.CodeNotificationPayload
import io.github.magisk317.smscode.verification.NotifyActionHelper
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
        return NotifyActionHelper(
            pluginContext = mPluginContext,
            smsMsg = mSmsMsg,
            enabled = enabled,
            ownerReader = XpPrefs::getCodeNotificationOwner,
            appOwnedValue = XpCodeNotificationOwner.APP,
            phoneOwnedValue = XpCodeNotificationOwner.PHONE,
            autoCancelEnabledProvider = { autoCancelEnabled },
            retentionTimeMsProvider = { retentionTimeMs },
            tokenProvider = { XpPrefs.getIpcToken(it).takeIf(String::isNotBlank) },
            appOwnedChannelInitializer = ::ensureNotificationChannel,
            phoneOwnedChannelInitializer = { ensureNotificationChannel(mPhoneContext) },
            appOwnedDiagnostics = { context -> XpNotificationBridge.inspectDelivery(context, XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION).toShared() },
            appOwnedNotifier = ::showAppOwnedNotification,
            phoneOwnedNotifier = ::showPhoneOwnedNotification,
        ).run()
    }

    private fun showAppOwnedNotification(
        request: NotifyActionHelper.AppOwnedNotificationRequest<SmsMsg>,
    ): Bundle? {
        CodeNotificationDeliveryHelper.requestAppOwnedNotification(
            context = mPhoneContext,
            smsMsg = request.smsMsg,
            notificationId = request.notificationId,
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
            token = request.token,
            intentFactory = CodeNotificationBroadcastContract::createIntent,
        )
        return null
    }

    private fun showPhoneOwnedNotification(
        request: NotifyActionHelper.PhoneOwnedNotificationRequest<SmsMsg>,
    ): Bundle? {
        val manager = mPhoneContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager?
            ?: return null
        val copyIntent = CopyCodeReceiver.createIntent(mPluginContext, request.smsMsg.smsCode, request.notificationId)
        val contentIntent = android.app.PendingIntent.getBroadcast(
            mPhoneContext,
            request.notificationId,
            copyIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )
        val title = CodeNotificationPayload.resolveTitle(
            company = request.smsMsg.company,
            sender = request.smsMsg.sender,
            fallbackTitle = mPluginContext.getString(R.string.app_name),
        )
        val notification = CodeNotificationDeliveryHelper.buildCodeNotification(
            context = mPluginContext,
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
                groupKey = XpNotificationBridge.GROUP_KEY_RELAY_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            title = title,
            smsCode = request.smsMsg.smsCode,
            contentIntent = contentIntent,
            contentTextProvider = { smsCode ->
                mPluginContext.getString(R.string.code_notification_content, smsCode)
            },
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
        )
        manager.notify(request.notificationId, notification)
        XLog.i(
            "Posted phone-owned code notification id=%d autoCancel=%s retentionMs=%d",
            request.notificationId,
            request.autoCancelEnabled,
            request.retentionTimeMs,
        )
        return null
    }

    private fun ensureNotificationChannel(context: Context) {
        XpNotificationBridge.createNotificationChannel(
            context,
            XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
            mPluginContext.getString(R.string.channel_name_relay_notification),
            android.app.NotificationManager.IMPORTANCE_HIGH,
        )
    }

    private fun XpNotificationBridge.DeliveryDiagnostics.toShared(): NotifyActionHelper.DeliveryDiagnostics {
        return NotifyActionHelper.DeliveryDiagnostics(
            canPost = canPost,
            summary = summary(),
        )
    }
}
