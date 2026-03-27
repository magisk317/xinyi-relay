package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.RunnableAction
import io.github.magisk317.relay.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.verification.SmsMessageDedupKeys
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 显示验证码 Toast（唯一保留的 Toast 入口）
 */
class ToastAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean,
) :
    RunnableAction(pluginContext, phoneContext, smsMsg) {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun action(): Bundle? {
        if (enabled) {
            showCodeToast()
        }
        return null
    }

    private fun showCodeToast() {
        val smsCode = mSmsMsg.smsCode.orEmpty()
        val text = mPluginContext.getString(R.string.current_sms_code, smsCode)
        val toastKey = SmsMessageDedupKeys.buildMessageKey(mSmsMsg)
        val ownerToken = acquireToastOwner(toastKey)
        if (ownerToken == null) {
            return
        }
        val toast = Toast.makeText(mPhoneContext, text, Toast.LENGTH_LONG)
        XLog.w(
            "Diag toast request: key=%s pkg=%s uid=%d code_len=%d text_len=%d",
            toastKey.ifBlank { "<none>" },
            mPhoneContext.packageName,
            android.os.Process.myUid(),
            smsCode.length,
            text.length,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val shown = AtomicBoolean(false)
            toast.addCallback(
                object : Toast.Callback() {
                    override fun onToastShown() {
                        shown.set(true)
                        touchToastOwner(toastKey, ownerToken)
                        XLog.w(
                            "Diag toast shown: key=%s pkg=%s code_len=%d",
                            toastKey.ifBlank { "<none>" },
                            mPhoneContext.packageName,
                            smsCode.length,
                        )
                    }

                    override fun onToastHidden() {
                        XLog.w(
                            "Diag toast hidden: key=%s pkg=%s code_len=%d",
                            toastKey.ifBlank { "<none>" },
                            mPhoneContext.packageName,
                            smsCode.length,
                        )
                    }
                },
            )
            mainHandler.postDelayed(
                {
                    if (!shown.get() && ownsToastOwner(toastKey, ownerToken)) {
                        touchToastOwner(toastKey, ownerToken)
                        XLog.w(
                            "Diag toast fallback via system receiver: key=%s pkg=%s code_len=%d",
                            toastKey.ifBlank { "<none>" },
                            mPhoneContext.packageName,
                            smsCode.length,
                        )
                        InputHelper.sendToast(mPhoneContext, text, Toast.LENGTH_LONG)
                    }
                },
                TOAST_FALLBACK_DELAY_MS,
            )
        }
        toast.show()
    }

    companion object {
        private const val TOAST_FALLBACK_DELAY_MS = 1500L
        private const val TOAST_DEDUP_WINDOW_MS = 5_000L
        private const val MAX_TOAST_CACHE_SIZE = 128
        private val TOAST_CACHE_LOCK = Any()
        private val recentToasts = LinkedHashMap<String, ToastOwner>(MAX_TOAST_CACHE_SIZE, 0.75f, true)

        private data class ToastOwner(
            val token: String,
            var timestamp: Long,
        )

        private fun acquireToastOwner(key: String): String? {
            if (key.isBlank()) return UUID.randomUUID().toString()

            val now = System.currentTimeMillis()
            val token = UUID.randomUUID().toString()
            synchronized(TOAST_CACHE_LOCK) {
                trimToastCache(now)
                val last = recentToasts[key]
                if (last != null && now - last.timestamp <= TOAST_DEDUP_WINDOW_MS) {
                    XLog.w(
                        "Diag toast dedup skip: key=%s ageMs=%d",
                        key,
                        now - last.timestamp,
                    )
                    return null
                }
                recentToasts[key] = ToastOwner(token, now)
                trimToastCacheSize()
            }
            return token
        }

        private fun ownsToastOwner(key: String, token: String): Boolean {
            if (key.isBlank()) return true
            synchronized(TOAST_CACHE_LOCK) {
                val current = recentToasts[key] ?: return false
                return current.token == token
            }
        }

        private fun touchToastOwner(key: String, token: String) {
            if (key.isBlank()) return
            val now = System.currentTimeMillis()
            synchronized(TOAST_CACHE_LOCK) {
                val current = recentToasts[key] ?: return
                if (current.token != token) return
                current.timestamp = now
            }
        }

        private fun trimToastCache(now: Long) {
            val iterator = recentToasts.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.timestamp > TOAST_DEDUP_WINDOW_MS) {
                    iterator.remove()
                }
            }
        }

        private fun trimToastCacheSize() {
            while (recentToasts.size > MAX_TOAST_CACHE_SIZE) {
                val firstKey = recentToasts.entries.firstOrNull()?.key ?: break
                recentToasts.remove(firstKey)
            }
        }
    }
}
