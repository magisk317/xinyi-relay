package io.github.magisk317.relay.xp.hook.code.action.impl

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.data.db.DBProvider
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.domain.pipeline.StorageRuntimeGraph
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
        XLog.w(
            "Diag AutoInputAction start: sender_hash=%s code_len=%d pkg=%s",
            senderHash(mSmsMsg.sender),
            mSmsMsg.smsCode?.length ?: 0,
            mSmsMsg.packageName ?: "",
        )
        prepareAutoInputCode(mSmsMsg.smsCode)
        return null
    }

    private fun prepareAutoInputCode(code: String?) {
        val blockedReason = autoInputBlockedReason()
        if (blockedReason == null) {
            autoInputCode(code)
        } else {
            XLog.w(
                "Diag auto input blocked here: reason=%s sender_hash=%s pkg=%s",
                blockedReason,
                senderHash(mSmsMsg.sender),
                mSmsMsg.packageName ?: "",
            )
        }
    }

    // auto-input
    @Suppress("TooGenericExceptionCaught")
    private fun autoInputCode(code: String?) {
        try {
            val autoEnter = PrefsReader.autoEnterCodeEnabled(mPluginContext)
            val inputIntervalMs = PrefsReader.getAutoInputCodeIntervalMs(mPluginContext)
            val analyticsEnabled = PrefsReader.analyticsEnabled(mPluginContext)
            val attemptId = if (analyticsEnabled) recordAttempt(code) else null
            XLog.w(
                "Diag auto input request: code_len=%d autoEnter=%s inputIntervalMs=%d analytics=%s attemptId=%s pkg=%s",
                code?.length ?: 0,
                autoEnter,
                inputIntervalMs,
                analyticsEnabled,
                attemptId?.toString() ?: "<none>",
                mSmsMsg.packageName ?: "",
            )
            if (analyticsEnabled) {
                AnalyticsTracker.logEvent(
                    "auto_input_attempt",
                    mapOf(
                        "code_length" to (code?.length ?: 0),
                        "package_name" to (mSmsMsg.packageName ?: ""),
                    ),
                )
            }
            InputHelper.sendText(mPhoneContext, code, autoEnter, inputIntervalMs, attemptId)
            XLog.d("Auto input code succeed, autoEnter: $autoEnter")
        } catch (throwable: Throwable) {
            XLog.e("Error occurs when auto input code", throwable)
        }
    }

    private fun recordAttempt(code: String?): Long? {
        val text = code ?: return null
        val runtimeRecordFacade = StorageRuntimeGraph.from(mPluginContext).runtimeRecordFacade
        val recordId = runCatching {
            kotlinx.coroutines.runBlocking {
                runtimeRecordFacade.findSmsRecordIdByFingerprint(
                    sender = mSmsMsg.sender,
                    body = mSmsMsg.body,
                    date = mSmsMsg.date,
                    msgType = SmsMsg.MSG_TYPE_SMS,
                )
            }
        }.getOrNull()
        return runCatching {
            kotlinx.coroutines.runBlocking {
                runtimeRecordFacade.insertAutoInputAttempt(
                    recordId = recordId,
                    packageName = mSmsMsg.packageName,
                    codeLength = text.length,
                )
            }
        }.onFailure { error ->
            XLog.w("AutoInput attempt persist failed: %s", error.message ?: error.javaClass.simpleName)
        }.getOrNull()
    }

    // 是否屏蔽自动输入
    @Suppress("TooGenericExceptionCaught")
    private fun autoInputBlockedReason(): String? {
        try {
            val runningTasks = getRunningTasks(mPhoneContext)
            var topPkgPrimary: String? = null
            if (runningTasks != null && runningTasks.isNotEmpty()) {
                topPkgPrimary = runningTasks[0].topActivity?.packageName
                XLog.d("topPackagePrimary: %s", topPkgPrimary)
            }

            if (!topPkgPrimary.isNullOrBlank() && isPackageBlocked(topPkgPrimary)) {
                return "blocked_top_task:$topPkgPrimary"
            }

            // RunningAppProcess 判断当前的进程不是很准确，所以用作次要参考
            val appProcesses = getRunningAppProcesses(mPhoneContext) ?: return null

            val topPkgSecondary = appProcesses[0].pkgList
            val topProcessSecondary = appProcesses[0].processName
            XLog.d("topProcessSecondary: %s, topPackages: %s", topProcessSecondary, Arrays.toString(topPkgSecondary))

            if (!topProcessSecondary.isNullOrBlank() && isPackageBlocked(topProcessSecondary)) {
                return "blocked_top_process:$topProcessSecondary"
            }
            for (topPackage in topPkgSecondary) {
                if (isPackageBlocked(topPackage)) {
                    return "blocked_pkg_list:$topPackage"
                }
            }
        } catch (t: Throwable) {
            XLog.e("", t)
            return "inspect_error:${t.javaClass.simpleName}"
        }
        return null
    }

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

    private fun senderHash(sender: String?): String {
        if (sender.isNullOrBlank()) return "<empty>"
        return sender.takeLast(4).padStart(sender.length.coerceAtMost(4), '*')
    }
}
