package io.github.magisk317.relay.app.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.service.SenderDispatchResult
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry

class SenderTestService(context: Context) {
    private val appContext = context.applicationContext

    suspend fun sendTestSender(sender: Sender, msgInfo: MsgInfo): SenderDispatchResult {
        val dispatcher = SenderRuntimeServiceRegistry.requireInstalled().createDispatcher(appContext)
        val result = dispatcher.dispatchToSender(
            sender = sender,
            msgInfo = msgInfo,
            traceId = TEST_TRACE_ID,
        )
        if (!result.success) {
            throw SenderTestException(result.message.ifBlank { "Sender test failed" })
        }
        return result
    }

    suspend fun sendScheduledSms(
        simSlot: Int,
        mobiles: String,
        msgInfo: MsgInfo,
        waitForSentResult: Boolean = true,
    ) {
        SenderRuntimeServiceRegistry.requireInstalled().scheduledSmsSender.sendSms(
            context = appContext,
            simSlot = simSlot,
            mobiles = mobiles,
            msgInfo = msgInfo,
            waitForSentResult = waitForSentResult,
        )
    }

    companion object {
        private const val TEST_TRACE_ID = "ui-sender-test"
    }
}

class SenderTestException(message: String) : RuntimeException(message)
