package com.github.magisk317.smscode.forwarder.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.github.magisk317.smscode.forwarder.entity.ForwardCommonConfig
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.XLog
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ForwardCommonConfigStore {
    private const val CALL_TYPE_ANSWERED_EXTERNALLY = 7
    private const val TIME_PATTERN = "yyyy.MM.dd HH:mm:ss"
    private val EMPTY_VALUE_LINE_REGEX = Regex("^[^:：\\n]+[:：]\\s*$")
    private const val DEFAULT_TEMPLATE = """
来自：{{FROM}}
内容：{{SMS}}
卡槽：{{CARD_SLOT}}
SubId：{{CARD_SUBID}}
接收时间：{{RECEIVE_TIME}}
设备：{{DEVICE_NAME}}
"""
    private const val FULL_INFO_TEMPLATE = """
【基础信息】
来自：{{FROM}}
内容：{{SMS}}
接收时间：{{RECEIVE_TIME}}
当前时间：{{CURRENT_TIME}}
设备：{{DEVICE_NAME}}
软件版本：{{APP_VERSION}}

【卡槽与来源】
卡槽：{{CARD_SLOT}}
SubId：{{CARD_SUBID}}
来源姓名：{{CONTACT_NAME}}
来源归属：{{PHONE_AREA}}

【应用与通知】
APP包名：{{PACKAGE_NAME}}
APP应用名：{{APP_NAME}}
通知标题：{{TITLE}}
通知内容：{{MSG}}

【电池与网络】
电池电量：{{BATTERY_PCT}}
电池状态：{{BATTERY_STATUS}}
充电方式：{{BATTERY_PLUGGED}}
电池完整信息：{{BATTERY_INFO}}
电池简单信息：{{BATTERY_INFO_SIMPLE}}
公网IPv4：{{IPV4}}
公网IPv6：{{IPV6}}
IP地址列表：{{IP_LIST}}
网络状态：{{NET_TYPE}}
"""

    suspend fun load(context: Context): ForwardCommonConfig {
        val defaultDeviceName = DeviceIdentityUtils.resolveDefaultDeviceName()
        val configuredName = AppPreferencesDataStore.getStringCompat(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_DEVICE_NAME,
            defaultValue = defaultDeviceName,
        ).trim()
        val configuredTemplate = AppPreferencesDataStore.getStringCompat(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_TEMPLATE,
            defaultValue = "",
        )
        val includeTime = AppPreferencesDataStore.getBooleanCompat(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME,
            defaultValue = false,
        )
        val includeSender = AppPreferencesDataStore.getBooleanCompat(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER,
            defaultValue = false,
        )
        val includeDeviceName = AppPreferencesDataStore.getBooleanCompat(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME,
            defaultValue = true,
        )
        return ForwardCommonConfig(
            deviceName = configuredName.ifBlank { defaultDeviceName },
            messageTemplate = configuredTemplate,
            includeTime = includeTime,
            includeSender = includeSender,
            includeDeviceName = includeDeviceName,
        )
    }

    suspend fun save(context: Context, config: ForwardCommonConfig) {
        AppPreferencesDataStore.setString(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_DEVICE_NAME,
            value = config.deviceName.trim(),
        )
        AppPreferencesDataStore.setString(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_TEMPLATE,
            value = config.messageTemplate,
        )
        AppPreferencesDataStore.setBoolean(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME,
            value = config.includeTime,
        )
        AppPreferencesDataStore.setBoolean(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER,
            value = config.includeSender,
        )
        AppPreferencesDataStore.setBoolean(
            context = context,
            key = PrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME,
            value = config.includeDeviceName,
        )
    }

    suspend fun loadAppNotifyTemplate(context: Context): String {
        return AppPreferencesDataStore.getStringCompat(
            context = context,
            key = PrefConst.KEY_FORWARD_APP_NOTIFY_TEMPLATE,
            defaultValue = "",
        )
    }

    suspend fun saveAppNotifyTemplate(context: Context, template: String) {
        AppPreferencesDataStore.setString(
            context = context,
            key = PrefConst.KEY_FORWARD_APP_NOTIFY_TEMPLATE,
            value = template,
        )
    }

    suspend fun loadCallNotifyTemplate(context: Context): String {
        return AppPreferencesDataStore.getStringCompat(
            context = context,
            key = PrefConst.KEY_FORWARD_CALL_NOTIFY_TEMPLATE,
            defaultValue = "",
        )
    }

    suspend fun saveCallNotifyTemplate(context: Context, template: String) {
        AppPreferencesDataStore.setString(
            context = context,
            key = PrefConst.KEY_FORWARD_CALL_NOTIFY_TEMPLATE,
            value = template,
        )
    }

    fun defaultTemplate(): String = DEFAULT_TEMPLATE.trimIndent()
    fun fullInfoTemplate(): String = FULL_INFO_TEMPLATE.trimIndent()

    fun applyToMessage(context: Context, msgInfo: MsgInfo, config: ForwardCommonConfig): MsgInfo {
        val template = when {
            config.messageTemplate.isNotBlank() -> config.messageTemplate
            config.includeSender || config.includeTime || !config.includeDeviceName -> {
                buildString {
                    append("{{SMS}}")
                    if (config.includeSender) append("\n发件人: {{FROM}}")
                    if (config.includeTime) append("\n时间: {{RECEIVE_TIME}}")
                    if (config.includeDeviceName) append("\n来自{{DEVICE_NAME}}设备")
                }
            }
            else -> defaultTemplate()
        }

        val batterySnapshot = readBatterySnapshot(context)
        val networkSnapshot = readNetworkSnapshot(context)
        val packageName = msgInfo.packageName.ifBlank { context.packageName }
        val appName = if (msgInfo.appName.isNotBlank()) msgInfo.appName else resolveAppName(context, packageName)
        val receiveTime = SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(msgInfo.date)
        val currentTime = SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date())
        val cardSlot = resolveCardSlot(context, msgInfo)
        val variables = mapOf(
            "FROM" to msgInfo.from,
            "SMS" to msgInfo.content,
            "CARD_SLOT" to cardSlot,
            "CARD_SUBID" to if (msgInfo.subId > 0) msgInfo.subId.toString() else "",
            "CALL_TYPE" to resolveCallTypeLabel(msgInfo.callType),
            "CONTACT_NAME" to msgInfo.contactName,
            "PHONE_AREA" to msgInfo.phoneArea,
            "UID" to "",
            "PACKAGE_NAME" to packageName,
            "APP_NAME" to appName,
            "TITLE" to msgInfo.title.ifBlank { msgInfo.simInfo.ifBlank { msgInfo.from } },
            "MSG" to msgInfo.message.ifBlank { msgInfo.content },
            "BATTERY_PCT" to batterySnapshot.percent,
            "BATTERY_STATUS" to batterySnapshot.status,
            "BATTERY_PLUGGED" to batterySnapshot.plugged,
            "BATTERY_INFO" to batterySnapshot.fullInfo,
            "BATTERY_INFO_SIMPLE" to batterySnapshot.simpleInfo,
            "IPV4" to networkSnapshot.ipv4,
            "IPV6" to networkSnapshot.ipv6,
            "IP_LIST" to networkSnapshot.ipList,
            "NET_TYPE" to networkSnapshot.netType,
            "RECEIVE_TIME" to receiveTime,
            "CURRENT_TIME" to currentTime,
            "DEVICE_NAME" to config.deviceName,
            "APP_VERSION" to resolveAppVersion(context),
        )

        var rendered = template
        variables.forEach { (name, value) ->
            rendered = rendered.replace("{{$name}}", value)
        }
        if (msgInfo.type == "app_notify") {
            rendered = rendered
                .replace(Regex("(?m)^(\\s*)卡槽([:：])"), "$1应用$2")
                .replace("【卡槽与来源】", "【应用与来源】")
        } else if (msgInfo.type == "call_notify") {
            rendered = rendered
                .replace(Regex("(?m)^(\\s*)卡槽([:：])"), "$1通话$2")
                .replace("【卡槽与来源】", "【通话与来源】")
        }
        val cleaned = removeEmptyValueLines(rendered)
        return msgInfo.copy(content = cleaned)
    }

    private fun resolveCardSlot(context: Context, msgInfo: MsgInfo): String {
        if (msgInfo.simSlot >= 0) {
            val remark = PrefsReader.getSimSlotRemark(context, msgInfo.simSlot)
            if (remark.isNotBlank()) return remark
            return "SIM${msgInfo.simSlot + 1}"
        }
        if (msgInfo.type == "app_notify" && msgInfo.simInfo.isNotBlank()) return msgInfo.simInfo
        return ""
    }

    private fun resolveCallTypeLabel(callType: Int): String {
        return when (callType) {
            1 -> "来电"
            2 -> "去电"
            3 -> "未接"
            4 -> "语音信箱"
            5 -> "拒接"
            6 -> "拦截"
            CALL_TYPE_ANSWERED_EXTERNALLY -> "异地接听"
            else -> ""
        }
    }

    private fun removeEmptyValueLines(text: String): String {
        val removedLines = mutableListOf<String>()
        val keptLines = text.lineSequence().map { it.trimEnd() }.filter { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@filter true
            val shouldRemove = EMPTY_VALUE_LINE_REGEX.matches(trimmed)
            if (shouldRemove) removedLines.add(trimmed)
            !shouldRemove
        }.toList()
        if (removedLines.isNotEmpty()) {
            XLog.i(
                "Template cleanup removed empty-value lines(%d): %s",
                removedLines.size,
                removedLines.joinToString(" | "),
            )
        }
        return keptLines.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trimEnd()
    }

    private data class BatterySnapshot(
        val percent: String = "",
        val status: String = "",
        val plugged: String = "",
        val fullInfo: String = "",
        val simpleInfo: String = "",
    )

    private fun readBatterySnapshot(context: Context): BatterySnapshot {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return BatterySnapshot()

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) {
            "${((level * 100f) / scale).toInt()}%"
        } else {
            ""
        }

        val statusRaw = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pluggedRaw = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        var status = when (statusRaw) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            else -> "未知"
        }
        if (pluggedRaw != 0 && statusRaw != BatteryManager.BATTERY_STATUS_FULL) {
            status = "充电中"
        }

        val plugged = when {
            pluggedRaw and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "交流电"
            pluggedRaw and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            pluggedRaw and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "无线"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                pluggedRaw and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> "底座"
            else -> "未充电"
        }

        val fullInfo = listOf(
            if (percent.isNotBlank()) "电量: $percent" else "",
            if (status.isNotBlank()) "状态: $status" else "",
            if (plugged.isNotBlank()) "充电: $plugged" else "",
        ).filter { it.isNotBlank() }.joinToString(" | ")

        val simpleInfo = listOf(percent, status).filter { it.isNotBlank() }.joinToString(" ")
        return BatterySnapshot(
            percent = percent,
            status = status,
            plugged = plugged,
            fullInfo = fullInfo,
            simpleInfo = simpleInfo,
        )
    }

    private data class NetworkSnapshot(
        val netType: String = "",
        val ipv4: String = "",
        val ipv6: String = "",
        val ipList: String = "",
    )

    private fun readNetworkSnapshot(context: Context): NetworkSnapshot {
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

    private fun resolveAppName(context: Context, packageName: String): String {
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

    private fun resolveAppVersion(context: Context): String {
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
}
