package io.github.magisk317.relay.sender

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.UrlSchemeSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.magisk317.xposed.logging.MagiskOtel

object UrlSchemeUtils {
    private const val TAG = "UrlSchemeUtils"

    private fun emitForward(
        result: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "sms.forward",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "urlscheme_send",
                "reason" to reason,
                "sender_type" to "urlscheme",
            ),
            statusOk = statusOk,
        )
    }


    suspend fun sendMsg(context: Context, setting: UrlSchemeSetting, msgInfo: MsgInfo) {
        val startedAt = System.nanoTime()
        try {

        val timestamp = System.currentTimeMillis()
        val receiveTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val url = SenderTemplateRenderer.render(
            raw = setting.urlScheme,
            msgInfo = msgInfo,
            timestamp = timestamp,
            receiveTime = receiveTime,
            valueTransform = ::urlEncode,
        )
            .replace("\n", "%0A")

        withContext(Dispatchers.Main.immediate) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                SLog.i(TAG, "UrlScheme invoked")
            }.onFailure {
                SLog.e(TAG, "UrlScheme invoke failed", it)
            }.getOrElse { throw it }
        }
    
            emitForward(
                result = "ok",
                reason = "success",
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            )
        } catch (error: Exception) {
            emitForward(
                result = "error",
                reason = error.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
            throw error
        }
}

    private fun urlEncode(value: String): String = SenderSigning.urlEncode(value)
}
