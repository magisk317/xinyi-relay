package io.github.magisk317.relay.xp.hook.code.action.impl

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
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
import io.github.magisk317.smscode.runtime.verification.PhoneOwnedNotificationDispatcher
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * 显示验证码通知
 *
 * 优先使用 app-owned 通知（归属于模块应用进程，前台时体验更好）。
 * 当 app-owned 通知失败时（应用在后台/被杀等），自动 fallback 到
 * phone-owned 通知（直接从 hook 进程发送，不受后台活动限制）。
 *
 * phone-owned 路径的实现已下沉到共享库的 [PhoneOwnedNotificationDispatcher]（渠道解析与轮换、
 * 按投递包自检、投递后反查系统是否接受）。本类只负责选择路径并提供通知本体。
 *
 * 注意：诊断必须走 [PhoneOwnedNotificationDispatcher.deliveryDiagnostics]，不能把
 * channelId + notificationBridge 交给 [NotifyActionHelper] —— 后者会按 pluginContext（模块包）
 * 解析渠道，而 phone-owned 实际投递到 phone 包的同名渠道，两个包的同名渠道互不相干。
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

    private val phoneOwned = PhoneOwnedNotificationDispatcher(
        phoneContext = mPhoneContext,
        bridge = XpNotificationBridge,
        modulePackageName = modulePackageName(),
        primaryChannelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
        fallbackChannelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION_FALLBACK,
        channelName = mPhoneContext.getString(R.string.channel_name_relay_notification),
    )

    override fun action(): Bundle? {
        val phoneChannelId = phoneOwned.resolveChannelId().channelId
        val result = NotifyActionHelper<SmsMsg, CodeNotificationPayload.DeliveryResult>(
            pluginContext = mPluginContext,
            smsMsg = mSmsMsg,
            enabled = enabled,
            autoCancelEnabledProvider = { autoCancelEnabled },
            retentionTimeMsProvider = { retentionTimeMs },
            tokenProvider = { XpPrefs.getIpcToken(it).takeIf(String::isNotBlank) },
            diagnostics = { phoneOwned.deliveryDiagnostics(phoneChannelId) },
            notifier = { request -> notifyCode(request, phoneChannelId) },
        ).run()
        return result?.let {
            Bundle().apply {
                putBoolean("success", it.success)
                putString("reason", it.reason)
            }
        }
    }

    private fun notifyCode(
        request: NotifyActionHelper.AppOwnedNotificationRequest<SmsMsg>,
        phoneChannelId: String,
    ): CodeNotificationPayload.DeliveryResult {
        if (PhoneOwnedNotificationDispatcher.isPackageAllowedToPost(mPhoneContext, modulePackageName())) {
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
        } else {
            XLog.w(
                "Skipping app-owned code notification: POST_NOTIFICATIONS is not granted to %s",
                modulePackageName(),
            )
        }

        val outcome = phoneOwned.post(
            notificationId = request.notificationId,
            channelId = phoneChannelId,
            build = { channelId -> buildPhoneOwnedNotification(channelId, request) },
        )
        return CodeNotificationPayload.DeliveryResult(
            success = outcome.delivered,
            reason = outcome.id,
        )
    }

    private fun buildPhoneOwnedNotification(
        channelId: String,
        request: NotifyActionHelper.AppOwnedNotificationRequest<SmsMsg>,
    ): Notification {
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

        return CodeNotificationDeliveryHelper.buildCodeNotification(
            context = mPhoneContext,
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = channelId,
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
    }

    private fun modulePackageName(): String =
        runCatching { mPluginContext.packageName }.getOrDefault("unknown")
}
