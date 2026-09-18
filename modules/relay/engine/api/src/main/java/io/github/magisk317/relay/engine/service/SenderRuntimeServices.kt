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

/**
 * Runtime services shared across the sender subsystem.
 *
 * [emailOAuthService] is optional and provided by relay/sender when available.
 * It lives here (rather than behind an interface) so that the engine module
 * does not need a hard dependency on relay/sender's OAuth2 types.
 */
data class SenderRuntimeServices(
    val dispatcherFactory: (Context) -> SenderDispatcher,
    val configSanitizer: SenderConfigSanitizer,
    val scheduledSmsSender: ScheduledSmsSender,
    val emailOAuthService: Any? = null,
) {
    fun createDispatcher(context: Context): SenderDispatcher = dispatcherFactory(context)

    /** Accessor for the optional OAuth2 service; returns null if not installed. */
    fun emailOAuth(): Any? = emailOAuthService
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
