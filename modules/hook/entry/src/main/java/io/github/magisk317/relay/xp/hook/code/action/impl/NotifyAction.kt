package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.hookentry.R
import io.github.magisk317.relay.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.relay.xp.hook.code.AutoCancelReceiver
import io.github.magisk317.relay.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.relay.xpbridge.XpCodeNotificationOwner
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.verification.NotifyActionHelper

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
        return CodeNotificationDeliveryHelper.requestAppOwnedNotificationOrFallback(
            context = mPhoneContext,
            request = request,
            intentFactory = CodeNotificationBroadcastContract::createIntent,
            fallbackNotifier = ::showPhoneOwnedNotification,
        )
    }

    private fun showPhoneOwnedNotification(
        request: NotifyActionHelper.PhoneOwnedNotificationRequest<SmsMsg>,
    ): Bundle? {
        CodeNotificationDeliveryHelper.showPhoneOwnedNotification(
            phoneContext = mPhoneContext,
            pluginContext = mPluginContext,
            request = request,
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
                groupKey = XpNotificationBridge.GROUP_KEY_RELAY_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            fallbackTitle = mPluginContext.getString(R.string.app_name),
            contentTextProvider = { smsCode ->
                mPluginContext.getString(R.string.code_notification_content, smsCode)
            },
            copyCodeIntentFactory = CopyCodeReceiver::createIntent,
            autoCancelIntentFactory = AutoCancelReceiver::createIntent,
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

    private fun NotificationDeliveryDiagnostics.toShared(): NotifyActionHelper.DeliveryDiagnostics {
        return NotifyActionHelper.DeliveryDiagnostics(
            canPost = canPost,
            summary = summary(),
        )
    }

}
