package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.hookentry.R
import io.github.magisk317.relay.xp.hook.code.AutoCancelReceiver
import io.github.magisk317.relay.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.verification.CodeNotificationPayload
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
        val result = NotifyActionHelper(
            pluginContext = mPluginContext,
            smsMsg = mSmsMsg,
            enabled = enabled,
            autoCancelEnabledProvider = { autoCancelEnabled },
            retentionTimeMsProvider = { retentionTimeMs },
            tokenProvider = { XpPrefs.getIpcToken(it).takeIf(String::isNotBlank) },
            channelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
            notificationBridge = XpNotificationBridge,
            notifier = ::showAppOwnedNotification,
        ).run()
        return result?.let {
            Bundle().apply {
                putBoolean("success", it.success)
                putString("reason", it.reason)
            }
        }
    }

    private fun showAppOwnedNotification(
        request: NotifyActionHelper.AppOwnedNotificationRequest<SmsMsg>,
    ): CodeNotificationPayload.DeliveryResult {
        return CodeNotificationDeliveryHelper.requestAppOwnedNotification(
            context = mPhoneContext,
            smsMsg = request.smsMsg,
            notificationId = request.notificationId,
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
            token = request.token,
            intentFactory = CodeNotificationBroadcastContract::createIntent,
        )
    }

}
