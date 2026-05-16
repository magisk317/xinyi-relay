package io.github.magisk317.relay.xp.helper

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.LinkedHashSet

object SmsCodeConflictNoticeHelper {
    private const val MAX_TRACKED_EVENT_IDS = 64
    private val notifiedEventIds = LinkedHashSet<String>()

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

    private fun markNotified(eventId: String): Boolean = synchronized(notifiedEventIds) {
        if (!notifiedEventIds.add(eventId)) {
            return false
        }
        while (notifiedEventIds.size > MAX_TRACKED_EVENT_IDS) {
            val first = notifiedEventIds.firstOrNull() ?: break
            notifiedEventIds.remove(first)
        }
        true
    }

    @SuppressLint("NotificationPermission", "UnspecifiedImmutableFlag")
    private fun showConflictNotification(pluginContext: Context, phoneContext: Context) {
        val manager = phoneContext.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager? ?: return
        val launchIntent = pluginContext.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                phoneContext,
                0,
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag(),
            )
        }
        val content = pluginContext.getString(
            R.string.smscode_conflict_notification_content,
            pluginContext.getString(R.string.smscode_conflict_other_app_name),
            ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            pluginContext.getString(R.string.app_name),
        )
        val builder = NotificationCompat.Builder(pluginContext, XpNotificationBridge.CHANNEL_ID_SMSCODE_CONFLICT)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(BitmapFactory.decodeResource(pluginContext.resources, R.drawable.ic_app_icon))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(pluginContext.getString(R.string.smscode_conflict_dialog_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(pluginContext, R.color.ic_launcher_background))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }
        manager.notify(XpNotificationBridge.NOTIFICATION_ID_SMSCODE_CONFLICT, builder.build())
    }

    private fun showConflictToast(pluginContext: Context, phoneContext: Context) {
        InputHelper.sendToast(
            phoneContext,
            pluginContext.getString(R.string.smscode_conflict_sms_toast),
            Toast.LENGTH_SHORT,
        )
    }

    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}
