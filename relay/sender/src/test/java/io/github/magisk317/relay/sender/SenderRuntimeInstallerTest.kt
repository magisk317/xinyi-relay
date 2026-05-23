package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.service.ScheduledSmsSender
import io.github.magisk317.relay.engine.service.SenderConfigSanitizer
import io.github.magisk317.relay.engine.service.SenderDispatcher
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import io.github.magisk317.relay.engine.service.SenderRuntimeServices
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SenderRuntimeInstallerTest {

    @AfterEach
    fun tearDown() {
        SenderRuntimeServiceRegistry.resetForTest()
    }

    @Test
    fun install_registersRuntimeServicesOnce() {
        val first = SenderRuntimeServiceRegistry.install(fakeServices("first"))
        val second = SenderRuntimeServiceRegistry.install(fakeServices("second"))

        assertSame(first, second)
        assertSame(first, SenderRuntimeServiceRegistry.requireInstalled())
        assertSame(first, SenderRuntimeServiceRegistry.installedOrNull())
    }

    @Test
    fun install_fromSenderRuntimeInstaller_reusesExistingServices() {
        val installed = SenderRuntimeInstaller.install()
        val again = SenderRuntimeInstaller.install()

        assertNotNull(installed)
        assertSame(installed, again)
        assertSame(installed, SenderRuntimeServiceRegistry.requireInstalled())
    }

    private fun fakeServices(name: String): SenderRuntimeServices {
        return SenderRuntimeServices(
            dispatcherFactory = { _: Context -> fakeDispatcher(name) },
            configSanitizer = fakeSanitizer,
            scheduledSmsSender = fakeScheduledSmsSender,
        )
    }

    private fun fakeDispatcher(name: String): SenderDispatcher = object : SenderDispatcher {
        override suspend fun dispatchToSender(sender: Sender, msgInfo: MsgInfo, traceId: String?): io.github.magisk317.relay.engine.service.SenderDispatchResult {
            return io.github.magisk317.relay.engine.service.SenderDispatchResult(
                senderId = sender.id,
                senderType = sender.type,
                senderName = "$name-${sender.name}",
                success = true,
                message = "ok",
            )
        }
    }

    private val fakeSanitizer = object : SenderConfigSanitizer {
        override fun sanitizeSenderLenient(sender: Sender): Sender = sender
        override fun sanitizeJsonLenient(type: Int, raw: String): String = raw
    }

    private val fakeScheduledSmsSender = object : ScheduledSmsSender {
        override suspend fun sendSms(
            context: Context,
            simSlot: Int,
            mobiles: String,
            msgInfo: MsgInfo,
            waitForSentResult: Boolean,
        ) {
        }
    }
}
