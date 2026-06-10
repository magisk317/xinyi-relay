package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.sender.config.SmsSetting

internal object SmsSendBackend {
    fun send(
        context: Context,
        setting: SmsSetting,
        mobiles: List<String>,
        content: String,
        waitForSentResult: Boolean,
    ) {
        throw UnsupportedOperationException("当前发行版不支持短信发送")
    }
}
