package io.github.magisk317.relay.forwarder.utils.sender

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.relay.model.MsgInfo
import io.github.magisk317.relay.model.setting.UrlSchemeSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UrlSchemeUtils {
    private const val TAG = "UrlSchemeUtils"

    suspend fun sendMsg(context: Context, setting: UrlSchemeSetting, msgInfo: MsgInfo) {
        val timestamp = System.currentTimeMillis()
        val receiveTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        var url = setting.urlScheme
            .replace("[from]", urlEncode(msgInfo.from))
            .replace("[content]", urlEncode(msgInfo.content))
            .replace("[msg]", urlEncode(msgInfo.content))
            .replace("[org_content]", urlEncode(msgInfo.content))
            .replace("[title]", urlEncode(msgInfo.simInfo))
            .replace("[card_slot]", urlEncode(msgInfo.simInfo))
            .replace("[receive_time]", urlEncode(receiveTime))
            .replace("[timestamp]", timestamp.toString())
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

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
