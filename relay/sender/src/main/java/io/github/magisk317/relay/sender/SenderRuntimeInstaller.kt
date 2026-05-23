package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.service.ScheduledSmsSender
import io.github.magisk317.relay.engine.service.SenderConfigSanitizer
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import io.github.magisk317.relay.engine.service.SenderRuntimeServices
import io.github.magisk317.relay.sender.config.SmsSetting

object SenderRuntimeInstaller {
    fun install(): SenderRuntimeServices {
        return SenderRuntimeServiceRegistry.install(
            SenderRuntimeServices(
                dispatcherFactory = { context -> DefaultSenderDispatcher(context) },
                configSanitizer = DefaultSenderConfigSanitizer,
                scheduledSmsSender = DefaultScheduledSmsSender,
            ),
        )
    }
}

private object DefaultSenderConfigSanitizer : SenderConfigSanitizer {
    override fun sanitizeSenderLenient(sender: Sender): Sender =
        SenderSettingSanitizer.sanitizeSenderLenient(sender)

    override fun sanitizeJsonLenient(type: Int, raw: String): String =
        SenderSettingSanitizer.sanitizeJsonLenient(type, raw)
}

private object DefaultScheduledSmsSender : ScheduledSmsSender {
    override suspend fun sendSms(
        context: Context,
        simSlot: Int,
        mobiles: String,
        msgInfo: MsgInfo,
        waitForSentResult: Boolean,
    ) {
        SmsUtils.sendMsg(
            context = context,
            setting = SmsSetting(
                simSlot = simSlot,
                mobiles = mobiles,
                onlyNoNetwork = false,
            ),
            msgInfo = msgInfo,
            waitForSentResult = waitForSentResult,
        )
    }
}
