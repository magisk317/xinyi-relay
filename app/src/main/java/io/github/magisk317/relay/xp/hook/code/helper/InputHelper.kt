package io.github.magisk317.relay.xp.hook.code.helper

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import io.github.magisk317.smscode.xposed.utils.XLog

object InputHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun sendText(
        context: android.content.Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
    ) {
        if (text == null) return
        val attemptId = SystemClock.elapsedRealtimeNanos()
        val intent = android.content.Intent(
            io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook.resolveActionAutoInput(),
        )
        intent.putExtra("code", text)
        intent.putExtra("autoEnter", autoEnter)
        intent.putExtra("inputIntervalMs", inputIntervalMs)
        intent.putExtra("attemptId", attemptId)
        // Ordered broadcast lets the accessibility path consume the request first,
        // while keeping the system-server injector as a lower-priority fallback.
        context.sendOrderedBroadcast(intent, null)
        XLog.i(
            "Dispatched ACTION_AUTO_INPUT request: attemptId=%d code_len=%d autoEnter=%s inputIntervalMs=%d",
            attemptId,
            text.length,
            autoEnter,
            inputIntervalMs,
        )
    }

    @JvmStatic
    fun sendToast(
        context: android.content.Context,
        text: String?,
        duration: Int = Toast.LENGTH_LONG,
    ) {
        if (text.isNullOrEmpty()) return
        mainHandler.post {
            Toast.makeText(context, text, duration).show()
        }
        XLog.i(
            "Show toast locally with textLength: %d, duration: %d",
            text.length,
            duration,
        )
    }
}
