package com.github.magisk317.smscode.forwarder.utils

import com.github.magisk317.smscode.forwarder.entity.Sender
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.Date

class SenderValidatorNullSafetyTest {

    @Test
    fun validateForEnable_withDirtyJson_neverThrows_andReturnsInvalid() {
        val dirtyCases = listOf(
            SenderType.DINGTALK_GROUP_ROBOT to """{"token":null,"msgtype":null}""",
            SenderType.EMAIL to """{"mailType":null,"fromEmail":null,"recipients":null,"toEmail":null}""",
            SenderType.BARK to """{"server":null,"level":null}""",
            SenderType.WEBHOOK to """{"method":null,"webServer":null,"headers":null}""",
            SenderType.WEWORK_ROBOT to """{"webHook":null,"msgType":null}""",
            SenderType.WEWORK_AGENT to """{"corpID":null,"agentID":null,"secret":null}""",
            SenderType.SERVERCHAN to """{"sendKey":null}""",
            SenderType.PUSHPLUS to """{"token":null}""",
            SenderType.TELEGRAM to """{"apiToken":null,"chatId":null,"method":null}""",
            SenderType.SMS to """{"mobiles":null,"simSlot":null}""",
            SenderType.FEISHU to """{"webhook":null}""",
            SenderType.GOTIFY to """{"webServer":null}""",
            SenderType.DINGTALK_INNER_ROBOT to """{"agentID":null,"appKey":null,"appSecret":null,"userIds":null}""",
            SenderType.FEISHU_APP to """{"appId":null,"appSecret":null,"receiveId":null}""",
            SenderType.URL_SCHEME to """{"urlScheme":null}""",
            SenderType.SOCKET to """{"address":null,"port":null,"method":null}""",
        )

        dirtyCases.forEach { (type, dirtyJson) ->
            val sender = newSender(type, dirtyJson)
            val result = SenderValidator.validateForEnable(sender)
            assertNotNull(result)
            assertFalse(result.valid, "type=$type should be invalid for dirty json")
        }
    }

    private fun newSender(type: Int, json: String): Sender {
        return Sender(
            id = 1L,
            type = type,
            name = "sender-$type",
            jsonSetting = json,
            status = 1,
            time = Date(),
            receiveCode = 1,
            receiveNonCode = 1,
            receiveAppNotify = 1,
        )
    }
}
