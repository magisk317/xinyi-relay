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

object UrlSchemeUtils {
    private const val TAG = "UrlSchemeUtils"

    suspend fun sendMsg(context: Context, setting: UrlSchemeSetting, msgInfo: MsgInfo) {
        SenderTelemetry.trace(
            senderType = "urlscheme",
            stage = "urlscheme_send",
        ) {
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
        }
    }

    private fun urlEncode(value: String): String = SenderSigning.urlEncode(value)
}
