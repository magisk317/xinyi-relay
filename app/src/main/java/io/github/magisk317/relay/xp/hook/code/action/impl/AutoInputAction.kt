package io.github.magisk317.relay.xp.hook.code.action.impl

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.xp.XpAppConfigFacade
import io.github.magisk317.relay.xp.XpPrefs
import io.github.magisk317.relay.xp.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.relay.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * 自动输入验证码
 */
class AutoInputAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val deduplicateEnabled: Boolean? = null,
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {
    private val runtimeAppConfigFacade = XpAppConfigFacade(pluginContext)

    override fun action(): Bundle? {
        if (deduplicateEnabled ?: XpPrefs.deduplicateSms(mPluginContext)) {
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
            val autoEnter = XpPrefs.autoEnterCodeEnabled(mPluginContext)
            val inputIntervalMs = XpPrefs.getAutoInputCodeIntervalMs(mPluginContext)
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
        return runBlocking {
            runtimeAppConfigFacade.isPackageBlocked(packageName)
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
