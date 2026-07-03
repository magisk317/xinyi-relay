package io.github.magisk317.relay.feature.mode

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {
    fun isExempted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestExemption(context: Context) {
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val optimizationSettingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startFirstAvailable(context, requestIntent, optimizationSettingsIntent, appSettingsIntent)
    }

    private fun startFirstAvailable(context: Context, vararg intents: Intent) {
        var lastError: Throwable? = null
        for (intent in intents) {
            runCatching {
                context.startActivity(intent)
            }.onSuccess {
                return
            }.onFailure { error ->
                lastError = error
            }
        }
        throw lastError ?: ActivityNotFoundException("No battery optimization settings activity found")
    }
}
