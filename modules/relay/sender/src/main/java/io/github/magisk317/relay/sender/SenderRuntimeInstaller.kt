package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.service.ScheduledSmsSender
import io.github.magisk317.relay.engine.service.SenderConfigSanitizer
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import io.github.magisk317.relay.engine.service.SenderRuntimeServices
import io.github.magisk317.relay.sender.config.SmsSetting
import io.github.magisk317.xposed.logging.MagiskOtel

object SenderRuntimeInstaller {
    fun install(): SenderRuntimeServices {
        val startedAt = System.nanoTime()
        return runCatching {
            SenderRuntimeServiceRegistry.install(
                SenderRuntimeServices(
                    dispatcherFactory = { context -> DefaultSenderDispatcher(context) },
                    configSanitizer = DefaultSenderConfigSanitizer,
                    scheduledSmsSender = DefaultScheduledSmsSender,
                ),
            )
        }.fold(
            onSuccess = { services ->
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                MagiskOtel.event(
                    name = "sms.sender_runtime",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to durationMs.toString(),
                        "process" to "main",
                        "reason" to "installed",
                    ),
                    statusOk = true,
                )
                services
            },
            onFailure = { error ->
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                MagiskOtel.event(
                    name = "sms.sender_runtime",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to durationMs.toString(),
                        "process" to "main",
                        "reason" to error.javaClass.simpleName,
                    ),
                    statusOk = false,
                )
                throw error
            },
        )
    }

    /**
     * Initialize E2EE availability provider. Should be called early during app startup
     * so that the UI can query E2EE module status.
     */
    fun initE2eeAvailability(context: Context) {
        MatrixE2eeHostProvider.install(SenderMatrixE2eeHost)
        MatrixE2eeSetup.init(context)
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
