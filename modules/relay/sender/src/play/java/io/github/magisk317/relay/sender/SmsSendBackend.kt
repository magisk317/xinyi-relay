package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.sender.config.SmsSetting
import io.github.magisk317.xposed.logging.MagiskOtel

internal object SmsSendBackend {
    fun send(
        context: Context,
        setting: SmsSetting,
        mobiles: List<String>,
        content: String,
        waitForSentResult: Boolean,
    ) {
        MagiskOtel.event(
            name = "sms.forward",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "sms_send_backend",
                "reason" to "unsupported_flavor",
                "sender_type" to "sms",
                "target_count" to mobiles.size.toString(),
                "content_length" to content.length.toString(),
                "wait_for_result" to waitForSentResult.toString(),
                "sim_slot" to setting.simSlot.toString(),
            ),
            statusOk = false,
        )
        throw UnsupportedOperationException("当前发行版不支持短信发送")
    }
}
