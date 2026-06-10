package io.github.magisk317.relay.android.common.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.contract.notification.NotificationDeliveryDiagnostics
import io.github.magisk317.relay.contract.notification.NotificationImportanceLabel

object NotificationUtils {

    @JvmStatic
    fun createNotificationChannel(context: Context, channelId: String, channelName: String, importance: Int) {
        val channel = NotificationChannel(channelId, channelName, importance)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        manager?.createNotificationChannel(channel)
    }

    @JvmStatic
    fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.getNotificationChannel(channelId)?.importance
        } else {
            null
        }
        return NotificationDeliveryDiagnostics(
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            postNotificationsGranted = hasPostNotificationsPermission(context),
            channelImportance = channelImportance,
        )
    }

    @JvmStatic
    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun importanceLabel(importance: Int?): String {
        return NotificationImportanceLabel.label(importance)
    }
}
