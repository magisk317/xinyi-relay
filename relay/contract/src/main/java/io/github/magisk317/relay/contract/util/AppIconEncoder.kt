package io.github.magisk317.relay.contract.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 应用图标编码工具 + 默认包名解析。
 * 与记录界面 AppIconImage 使用相同逻辑。
 */
object AppIconEncoder {
    private const val ICON_SIZE = 128

    private val SMS_FALLBACK_PACKAGES = listOf(
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.android.messaging",
    )

    /**
     * 按消息类型解析图标 packageName，与记录界面逻辑一致。
     * - App 通知：直接用 packageName
     * - SMS/验证码：packageName 为空时用默认短信应用
     * - 通话通知：packageName 为空时用默认拨号应用
     */
    fun resolveIconPackageName(context: Context, packageName: String?, msgType: String?): String? {
        if (!packageName.isNullOrBlank()) return packageName
        return when (msgType) {
            "sms", "sms_code" -> resolveDefaultSmsPackage(context)
            "call_notify" -> resolveDefaultDialerPackage(context)
            else -> null
        }
    }

    /**
     * 按消息类型解析图标 packageName（Int 版本，与 SmsMsg.msgType 对应）。
     * 0 = SMS, 1 = APP_NOTIFY, 2 = CALL_NOTIFY
     */
    fun resolveIconPackageName(context: Context, packageName: String?, msgType: Int): String? {
        if (!packageName.isNullOrBlank()) return packageName
        return when (msgType) {
            0 -> resolveDefaultSmsPackage(context)   // MSG_TYPE_SMS
            2 -> resolveDefaultDialerPackage(context) // MSG_TYPE_CALL_NOTIFY
            else -> null
        }
    }

    fun encodeFromPackage(context: Context, packageName: String): String {
        if (packageName.isBlank()) return ""
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val drawable = pm.getApplicationIcon(info)
            encodeDrawableToBase64Png(drawable, ICON_SIZE)
        }.getOrDefault("")
    }

    fun resolveDefaultSmsPackage(context: Context): String? {
        val pm = context.packageManager
        Telephony.Sms.getDefaultSmsPackage(context)?.takeIf { it.isNotBlank() }?.let { return it }
        resolveSmsIntentPackage(pm)?.let { return it }
        return SMS_FALLBACK_PACKAGES.firstOrNull { packageName ->
            runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess
        }
    }

    fun resolveDefaultDialerPackage(context: Context): String? {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(TelecomManager::class.java)
                ?.defaultDialerPackage
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return resolveDialIntentPackage(pm)
    }

    private fun resolveSmsIntentPackage(pm: PackageManager): String? {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:10086"))
        return runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun resolveDialIntentPackage(pm: PackageManager): String? {
        val intent = Intent(Intent.ACTION_DIAL)
        return runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun encodeDrawableToBase64Png(drawable: Drawable, targetSize: Int): String {
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, targetSize, targetSize)
            drawable.draw(canvas)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }
}
