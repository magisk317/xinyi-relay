package io.github.magisk317.relay.engine.service

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender

interface SenderConfigSanitizer {
    fun sanitizeSenderLenient(sender: Sender): Sender

    fun sanitizeJsonLenient(type: Int, raw: String): String
}

interface ScheduledSmsSender {
    suspend fun sendSms(
        context: Context,
        simSlot: Int,
        mobiles: String,
        msgInfo: MsgInfo,
        waitForSentResult: Boolean = false,
    )
}

data class SenderRuntimeServices(
    val dispatcherFactory: (Context) -> SenderDispatcher,
    val configSanitizer: SenderConfigSanitizer,
    val scheduledSmsSender: ScheduledSmsSender,
) {
    fun createDispatcher(context: Context): SenderDispatcher = dispatcherFactory(context)
}

object SenderRuntimeServiceRegistry {
    @Volatile
    private var installed: SenderRuntimeServices? = null

    @Synchronized
    fun install(services: SenderRuntimeServices): SenderRuntimeServices {
        val existing = installed
        if (existing != null) return existing
        installed = services
        return services
    }

    @Synchronized
    fun resetForTest() {
        installed = null
    }

    fun requireInstalled(): SenderRuntimeServices {
        return installed ?: error("Sender runtime services are not installed")
    }

    fun installedOrNull(): SenderRuntimeServices? = installed
}
