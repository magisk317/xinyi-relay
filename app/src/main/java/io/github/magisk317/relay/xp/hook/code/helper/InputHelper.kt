package io.github.magisk317.relay.xp.hook.code.helper

import io.github.magisk317.relay.common.utils.XLog

object InputHelper {

    @JvmStatic
    fun sendText(
        context: android.content.Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
        attemptId: Long? = null,
    ) {
        if (text == null) return
        val intent = android.content.Intent(
            io.github.magisk317.relay.xp.hook.system.SystemInputInjectorHook.ACTION_AUTO_INPUT,
        )
        intent.putExtra("code", text)
        intent.putExtra("autoEnter", autoEnter)
        intent.putExtra("inputIntervalMs", inputIntervalMs)
        attemptId?.let { intent.putExtra("attemptId", it) }
        // Broadcast without explicit package to avoid dropping delivery when
        // system_server receiver isn't bound to package.
        context.sendBroadcast(intent)
        XLog.w(
            "Diag broadcast ACTION_AUTO_INPUT sent: code_len=%d autoEnter=%s inputIntervalMs=%d attemptId=%s",
            text.length,
            autoEnter,
            inputIntervalMs,
            attemptId?.toString() ?: "<none>",
        )
        XLog.i(
            "Sent Broadcast ACTION_AUTO_INPUT with code: %s, autoEnter: %s, inputIntervalMs: %d",
            text,
            autoEnter,
            inputIntervalMs,
        )
    }
}
