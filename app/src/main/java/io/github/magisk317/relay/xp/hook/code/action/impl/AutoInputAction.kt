package io.github.magisk317.relay.xp.hook.code.action.impl

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.smscode.core.utils.XLog
import io.github.magisk317.relay.data.db.DBProvider
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.feature.store.EntityStoreManager
import io.github.magisk317.relay.feature.store.EntityType
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.relay.xp.hook.code.helper.InputHelper
import java.util.*

/**
 * 自动输入验证码
 */
class AutoInputAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (PrefsReader.deduplicateSms(mPluginContext)) {
            if (shouldSkipByRecentAutoInput(mSmsMsg)) {
                return null
            }
        }
        prepareAutoInputCode(mSmsMsg.smsCode)
        return null
    }

    private fun prepareAutoInputCode(code: String?) {
        if (!autoInputBlockedHere()) {
            autoInputCode(code)
        }
    }

    // auto-input
    @Suppress("TooGenericExceptionCaught")
    private fun autoInputCode(code: String?) {
        try {
            val autoEnter = PrefsReader.autoEnterCodeEnabled(mPluginContext)
            val inputIntervalMs = PrefsReader.getAutoInputCodeIntervalMs(mPluginContext)
            InputHelper.sendText(mPhoneContext, code, autoEnter, inputIntervalMs)
            XLog.d("Auto input code succeed, autoEnter: $autoEnter")
        } catch (throwable: Throwable) {
            XLog.e("Error occurs when auto input code", throwable)
        }
    }

    // 是否屏蔽自动输入
    @Suppress("TooGenericExceptionCaught")
    private fun autoInputBlockedHere(): Boolean {
        try {
            val runningTasks = getRunningTasks(mPhoneContext)
            var topPkgPrimary: String? = null
            if (runningTasks != null && runningTasks.isNotEmpty()) {
                topPkgPrimary = runningTasks[0].topActivity?.packageName
                XLog.d("topPackagePrimary: %s", topPkgPrimary)
            }

            if (!topPkgPrimary.isNullOrBlank() && isPackageBlocked(topPkgPrimary)) {
                return true
            }

            // RunningAppProcess 判断当前的进程不是很准确，所以用作次要参考
            val appProcesses = getRunningAppProcesses(mPhoneContext) ?: return false

            val topPkgSecondary = appProcesses[0].pkgList
            val topProcessSecondary = appProcesses[0].processName
            XLog.d("topProcessSecondary: %s, topPackages: %s", topProcessSecondary, Arrays.toString(topPkgSecondary))

            if (!topProcessSecondary.isNullOrBlank() && isPackageBlocked(topProcessSecondary)) {
                return true
            }
            for (topPackage in topPkgSecondary) {
                if (isPackageBlocked(topPackage)) {
                    return true
                }
            }
        } catch (t: Throwable) {
            XLog.e("", t)
        }
        return false
    }

    private fun shouldSkipByRecentAutoInput(smsMsg: SmsMsg): Boolean {
        val key = buildAutoInputKey(smsMsg)
        if (key.isBlank()) return false
        val now = System.currentTimeMillis()
        synchronized(AUTO_INPUT_CACHE_LOCK) {
            val iterator = recentAutoInputs.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > AUTO_INPUT_DEDUP_WINDOW_MS) {
                    iterator.remove()
                }
            }
            val last = recentAutoInputs[key]
            if (last != null && now - last <= AUTO_INPUT_DEDUP_WINDOW_MS) {
                XLog.w(
                    "Diag auto-input dedup skip: key=%s ageMs=%d",
                    key,
                    now - last,
                )
                return true
            }
            recentAutoInputs[key] = now
            while (recentAutoInputs.size > MAX_AUTO_INPUT_CACHE_SIZE) {
                val firstKey = recentAutoInputs.entries.firstOrNull()?.key ?: break
                recentAutoInputs.remove(firstKey)
            }
        }
        return false
    }

    private fun buildAutoInputKey(smsMsg: SmsMsg): String {
        val sender = smsMsg.sender.orEmpty()
        val body = smsMsg.body.orEmpty()
        val code = smsMsg.smsCode.orEmpty()
        if (sender.isBlank() && body.isBlank() && code.isBlank()) return ""

        val parts = ArrayList<String>(4)
        if (sender.isNotBlank() && body.isNotBlank()) {
            parts += "fp:${hash(sender)}:${hash(body)}"
        }
        if (code.isNotBlank()) {
            val channel = when {
                !smsMsg.packageName.isNullOrBlank() -> "pkg:${smsMsg.packageName}"
                !smsMsg.company.isNullOrBlank() -> "co:${smsMsg.company}"
                else -> "co:unknown"
            }
            parts += "code:${code}|$channel"
        }
        return parts.joinToString("|")
    }

    private fun hash(value: String): String = Integer.toHexString(value.hashCode())

    private fun isPackageBlocked(packageName: String): Boolean {
        queryBlockedStateByProvider(packageName)?.let { return it }
        val appInfoList = EntityStoreManager.loadEntitiesFromFile(
            mPluginContext,
            EntityType.APP_CONFIG,
            AppInfo::class.java,
        )
        val blocked = appInfoList.any { it.packageName == packageName && it.blocked }
        XLog.d("AutoInput fallback file check: pkg=%s blocked=%s", packageName, blocked)
        return blocked
    }

    private fun queryBlockedStateByProvider(packageName: String): Boolean? {
        return try {
            val uri: Uri = Uri.withAppendedPath(DBProvider.APP_INFO_URI, packageName)
            mPluginContext.contentResolver.query(uri, arrayOf("blocked"), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return false
                }
                val index = cursor.getColumnIndex("blocked")
                if (index < 0) return false
                val blocked = when (cursor.getType(index)) {
                    android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(index) != 0
                    android.database.Cursor.FIELD_TYPE_STRING -> {
                        val raw = cursor.getString(index).orEmpty()
                        raw == "1" || raw.equals("true", ignoreCase = true)
                    }
                    else -> false
                }
                XLog.d("AutoInput provider check: pkg=%s blocked=%s", packageName, blocked)
                blocked
            } ?: false
        } catch (e: Exception) {
            XLog.w("AutoInput provider check failed: pkg=%s err=%s", packageName, e.message ?: e.javaClass.simpleName)
            null
        }
    }

    private fun getRunningAppProcesses(context: Context): List<ActivityManager.RunningAppProcessInfo>? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        return am?.runningAppProcesses
    }

    private fun getRunningTasks(context: Context): List<ActivityManager.RunningTaskInfo>? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        am ?: return null
        return runCatching {
            val method = ActivityManager::class.java.getMethod("getRunningTasks", Int::class.javaPrimitiveType)
            val result = method.invoke(am, 10) as? List<*>
            result?.filterIsInstance<ActivityManager.RunningTaskInfo>()
        }.getOrNull()
    }

    companion object {
        private const val AUTO_INPUT_DEDUP_WINDOW_MS = 5_000L
        private const val MAX_AUTO_INPUT_CACHE_SIZE = 128
        private val AUTO_INPUT_CACHE_LOCK = Any()
        private val recentAutoInputs = LinkedHashMap<String, Long>(MAX_AUTO_INPUT_CACHE_SIZE, 0.75f, true)
    }
}
