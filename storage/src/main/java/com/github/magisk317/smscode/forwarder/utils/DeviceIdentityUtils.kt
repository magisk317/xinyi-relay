package com.github.magisk317.smscode.forwarder.utils

import android.os.Build
import com.github.magisk317.smscode.common.utils.XLog

object DeviceIdentityUtils {
    private val propertyKeys = listOf(
        "ro.product.marketname",
        "ro.product.vendor.marketname",
        "ro.product.model",
        "ro.product.system.model",
    )

    fun resolveDefaultDeviceName(): String {
        propertyKeys.forEach { key ->
            val value = readSystemProperty(key)
            if (value.isNotBlank()) return value
        }
        return Build.MODEL?.trim().takeUnless { it.isNullOrBlank() } ?: "Android"
    }

    private fun readSystemProperty(key: String): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            (method.invoke(null, key, "") as? String).orEmpty().trim()
        }.onFailure {
            XLog.d("Read system property failed: key=%s err=%s", key, it.message ?: "unknown")
        }.getOrDefault("")
    }
}
