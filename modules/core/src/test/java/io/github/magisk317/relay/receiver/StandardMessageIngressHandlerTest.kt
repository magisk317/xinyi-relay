package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class StandardMessageIngressHandlerTest : FunSpec({
    beforeSpec {
        XLog.setTestSink { _, _ -> }
    }

    afterSpec {
        XLog.setTestSink(null)
    }

    test("buildSmsPayload enriches standard SMS with parsed verification code") {
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        val smsMsg = SmsMsg(
            sender = "1068",
            body = "Bank code 123456",
            date = 100L,
        )

        every { context.applicationContext } returns context
        every { context.packageManager } returns packageManager
        every { packageManager.getInstalledApplications(PackageManager.MATCH_ALL) } returns emptyList()
        every { intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID) } returns "sms_existing"
        every { intent.hasExtra(any()) } returns false

        val payload = StandardMessageIngressHandler.buildSmsPayload(
            context = context,
            smsMsg = smsMsg,
            intent = intent,
            smsCodeParser = { _, body ->
                if (body == smsMsg.body) "123456" else ""
            },
        )

        requireNotNull(payload)
        payload.eventId shouldBe "sms_existing"
        payload.smsCode shouldBe "123456"
        payload.resolveRelayMessageType() shouldBe MessageType.SMS_CODE
    }
})
