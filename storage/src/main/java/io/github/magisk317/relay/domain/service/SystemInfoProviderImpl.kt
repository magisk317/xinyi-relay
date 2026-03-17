package io.github.magisk317.relay.domain.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import io.github.magisk317.relay.domain.model.BatterySnapshot
import io.github.magisk317.relay.domain.model.NetworkSnapshot
import io.github.magisk317.relay.domain.model.SystemEnvironment
import java.net.NetworkInterface

class SystemInfoProviderImpl(private val context: Context) : SystemInfoProvider {

    override fun getSnapshot(deviceName: String): SystemEnvironment {
        return SystemEnvironment(
            deviceName = deviceName,
            appVersion = resolveAppVersion(),
            battery = readBatterySnapshot(),
            network = readNetworkSnapshot(),
            currentTime = System.currentTimeMillis(),
        )
    }

    override fun resolveAppName(packageName: String): String {
        if (packageName.isBlank()) return ""
        val pm = context.packageManager
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault("")
    }

    private fun resolveAppVersion(): String {
        val pm = context.packageManager
        return runCatching {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            pkgInfo.versionName.orEmpty()
        }.getOrDefault("")
    }

    private fun readBatterySnapshot(): BatterySnapshot {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return BatterySnapshot()

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentValue = if (level >= 0 && scale > 0) {
            "${((level * 100f) / scale).toInt()}%"
        } else {
            ""
        }

        val statusRaw = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pluggedRaw = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        var statusStr = when (statusRaw) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            else -> "未知"
        }
        if (pluggedRaw != 0 && statusRaw != BatteryManager.BATTERY_STATUS_FULL) {
            statusStr = "充电中"
        }

        val pluggedStr = when {
            pluggedRaw and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "交流电"
            pluggedRaw and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            pluggedRaw and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "无线"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                pluggedRaw and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> "底座"
            else -> "未充电"
        }

        val fullInfo = listOf(
            if (percentValue.isNotBlank()) "电量: $percentValue" else "",
            if (statusStr.isNotBlank()) "状态: $statusStr" else "",
            if (pluggedStr.isNotBlank()) "充电: $pluggedStr" else "",
        ).filter { it.isNotBlank() }.joinToString(" | ")

        val simpleInfo = listOf(percentValue, statusStr).filter { it.isNotBlank() }.joinToString(" ")
        return BatterySnapshot(
            percent = percentValue,
            status = statusStr,
            plugged = pluggedStr,
            fullInfo = fullInfo,
            simpleInfo = simpleInfo,
        )
    }

    private fun readNetworkSnapshot(): NetworkSnapshot {
        val netType = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val capabilities = cm?.getNetworkCapabilities(cm.activeNetwork)
            when {
                capabilities == null -> ""
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
                else -> "OTHER"
            }
        }.getOrDefault("")

        var ipv4 = ""
        var ipv6 = ""
        val ipValues = mutableListOf<String>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
        while (interfaces != null && interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement() ?: continue
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val raw = addresses.nextElement().hostAddress.orEmpty()
                val normalized = raw.substringBefore('%').trim()
                if (normalized.isBlank() || normalized.startsWith("127.") || normalized == "::1") continue
                if (!ipValues.contains(normalized)) ipValues.add(normalized)
                if (normalized.contains(".") && ipv4.isBlank()) ipv4 = normalized
                if (normalized.contains(":") && ipv6.isBlank()) ipv6 = normalized
            }
        }

        return NetworkSnapshot(
            netType = netType,
            ipv4 = ipv4,
            ipv6 = ipv6,
            ipList = ipValues.joinToString(","),
        )
    }
}
