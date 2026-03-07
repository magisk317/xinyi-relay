package com.github.magisk317.smscode.forwarder.utils.sender

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.setting.SmsSetting

object SmsUtils {
    private const val TAG = "SmsUtils"

    suspend fun sendMsg(context: Context, setting: SmsSetting, msgInfo: MsgInfo) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            SLog.e(TAG, "SEND_SMS permission denied")
            throw SecurityException("缺少 SEND_SMS 权限")
        }

        val mobiles = setting.mobiles
            .replace("[from]", msgInfo.from)
            .replace("{{来源号码}}", msgInfo.from)
            .replace("[,，;；]".toRegex(), ",")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (mobiles.isEmpty()) {
            SLog.e(TAG, "No target mobile configured")
            throw IllegalArgumentException("未配置目标手机号")
        }

        val smsManager = getSmsManager(context)
        val content = msgInfo.content
        runCatching {
            mobiles.forEach { mobile ->
                val parts = smsManager.divideMessage(content)
                smsManager.sendMultipartTextMessage(mobile, null, parts, null, null)
            }
            SLog.i(TAG, "SMS send success, targets=${mobiles.size}")
        }.onFailure {
            SLog.e(TAG, "SMS send failed", it)
        }.getOrElse { throw it }
    }

    private fun getSmsManager(context: Context): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)?.let { return it }
        }
        @Suppress("DEPRECATION")
        return SmsManager.getDefault()
    }
}
