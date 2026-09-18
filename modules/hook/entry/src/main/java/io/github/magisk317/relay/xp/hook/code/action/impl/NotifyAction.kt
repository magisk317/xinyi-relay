package io.github.magisk317.relay.xp.hook.code.action.impl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import io.github.magisk317.relay.hookentry.R
import io.github.magisk317.relay.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.runtime.verification.CodeNotificationPayload
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.runtime.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.runtime.verification.NotifyActionHelper
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * 显示验证码通知
 *
 * 优先使用 app-owned 通知（归属于模块应用进程，前台时体验更好）。
 * 当 app-owned 通知失败时（应用在后台/被杀等），自动 fallback 到
 * phone-owned 通知（直接从 hook 进程发送，不受后台活动限制）。
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
        val appResult = CodeNotificationDeliveryHelper.requestAppOwnedNotification(
            context = mPhoneContext,
            smsMsg = request.smsMsg,
            notificationId = request.notificationId,
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
            token = request.token,
            intentFactory = CodeNotificationBroadcastContract::createIntent,
        )
        if (appResult.success) return appResult

        // App-owned notification failed (app background/killed, broadcast timeout, etc.).
        // Fallback to phone-owned notification from the hook process directly.
        XLog.w(
            "App-owned code notification failed (reason=%s), falling back to phone-owned",
            appResult.reason,
        )
        return showPhoneOwnedNotification(request)
    }

    private fun showPhoneOwnedNotification(
        request: NotifyActionHelper.AppOwnedNotificationRequest<SmsMsg>,
    ): CodeNotificationPayload.DeliveryResult {
        val manager = mPhoneContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager
            ?: return CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = "no_notification_manager",
            )

        ensureNotificationChannel(manager)

        val copyIntent = CopyCodeReceiver.createIntent(
            mPhoneContext,
            request.smsMsg.smsCode,
            request.notificationId,
        )
        val pendingIntent = PendingIntent.getBroadcast(
            mPhoneContext,
            request.notificationId,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = CodeNotificationDeliveryHelper.buildCodeNotification(
            context = mPhoneContext,
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
                groupKey = XpNotificationBridge.GROUP_KEY_RELAY_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            title = mPhoneContext.getString(R.string.app_name),
            smsCode = request.smsMsg.smsCode,
            contentIntent = pendingIntent,
            contentTextProvider = { code ->
                mPhoneContext.getString(R.string.code_notification_content, code)
            },
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
        )

        return try {
            manager.notify(request.notificationId, notification)
            XLog.i("Phone-owned code notification posted id=%d", request.notificationId)
            CodeNotificationPayload.DeliveryResult(
                success = true,
                reason = "phone_owned",
            )
        } catch (e: Exception) {
            XLog.w("Phone-owned notification failed: %s", e.message)
            CodeNotificationPayload.DeliveryResult(
                success = false,
                reason = "phone_notify_exception",
            )
        }
    }

    private fun ensureNotificationChannel(manager: NotificationManager) {
        val channelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION
        if (manager.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(
            channelId,
            mPhoneContext.getString(R.string.channel_name_relay_notification),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }
}
