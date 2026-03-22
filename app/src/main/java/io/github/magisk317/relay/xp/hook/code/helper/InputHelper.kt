package io.github.magisk317.relay.xp.hook.code.helper

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.magisk317.smscode.core.utils.XLog

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
        val intent = android.content.Intent(
            io.github.magisk317.smscode.core.hook.system.SystemInputInjectorHook.resolveActionAutoInput(),
        )
        intent.putExtra("code", text)
        intent.putExtra("autoEnter", autoEnter)
        intent.putExtra("inputIntervalMs", inputIntervalMs)
        // Broadcast without explicit package to avoid dropping delivery when
        // system_server receiver isn't bound to package.
        context.sendBroadcast(intent)
        XLog.i(
            "Sent Broadcast ACTION_AUTO_INPUT with code: %s, autoEnter: %s, inputIntervalMs: %d",
            text,
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
