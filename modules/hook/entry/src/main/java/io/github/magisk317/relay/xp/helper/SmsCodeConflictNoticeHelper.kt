package io.github.magisk317.relay.xp.helper

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.hookentry.R
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.verification.ConflictNotificationHelper
import io.github.magisk317.smscode.verification.RecentEventIdTracker
import io.github.magisk317.smscode.xposed.utils.XLog

object SmsCodeConflictNoticeHelper {
    private const val MAX_TRACKED_EVENT_IDS = 64
    private val notifiedEventIds = RecentEventIdTracker(MAX_TRACKED_EVENT_IDS)

    fun initNotificationChannel(pluginContext: Context, phoneContext: Context) {
        XpNotificationBridge.createNotificationChannel(
            phoneContext,
            XpNotificationBridge.CHANNEL_ID_SMSCODE_CONFLICT,
            pluginContext.getString(R.string.channel_name_smscode_conflict_notification),
            NotificationManager.IMPORTANCE_HIGH,
        )
    }

    fun notifyConflictOnSms(pluginContext: Context, phoneContext: Context, eventId: String, source: String) {
        if (!markNotified(eventId)) {
            XLog.w("SmsCode conflict notice deduped: event_id=%s source=%s", eventId, source)
            return
        }
        showConflictNotification(pluginContext, phoneContext)
        showConflictToast(pluginContext, phoneContext)
        XLog.w(
            "SmsCode conflict notice sent: event_id=%s source=%s package=%s bypass=%s",
            eventId,
            source,
            ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            BuildConfig.ALLOW_CONFLICT_BYPASS,
        )
    }

    private fun markNotified(eventId: String): Boolean = notifiedEventIds.mark(eventId)

    private fun showConflictNotification(pluginContext: Context, phoneContext: Context) {
        val content = pluginContext.getString(
            R.string.smscode_conflict_notification_content,
            pluginContext.getString(R.string.smscode_conflict_other_app_name),
            ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            pluginContext.getString(R.string.app_name),
        )
        ConflictNotificationHelper.showConflictNotification(
            ConflictNotificationHelper.Request(
                phoneContext = phoneContext,
                pluginContext = pluginContext,
                applicationId = BuildConfig.APPLICATION_ID,
                visualConfig = ConflictNotificationHelper.VisualConfig(
                    channelId = XpNotificationBridge.CHANNEL_ID_SMSCODE_CONFLICT,
                    notificationId = XpNotificationBridge.NOTIFICATION_ID_SMSCODE_CONFLICT,
                    smallIconResId = R.drawable.ic_app_icon,
                    largeIconResId = R.drawable.ic_app_icon,
                    accentColorResId = R.color.ic_launcher_background,
                ),
                title = pluginContext.getString(R.string.smscode_conflict_dialog_title),
                content = content,
                activityPendingIntentImmutableMinSdk = Build.VERSION_CODES.M,
            ),
        )
    }

    private fun showConflictToast(pluginContext: Context, phoneContext: Context) {
        InputHelper.sendToast(
            phoneContext,
            pluginContext.getString(R.string.smscode_conflict_sms_toast),
            Toast.LENGTH_SHORT,
        )
    }

}
