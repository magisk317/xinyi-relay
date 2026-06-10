package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.Date
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Property 9: 明文回退保持消息内容不变
 *
 * Feature: matrix-e2ee-support, Property 9: 明文回退保持消息内容不变
 *
 * **Validates: Requirements 5.3**
 *
 * For any message content that triggers a plaintext fallback due to encryption failure,
 * the content passed to Plaintext_Sender (MatrixUtils.sendMsg) SHALL be byte-for-byte
 * identical to the original message content provided by the caller.
 *
 * Test approach:
 * - Since MatrixUtils.sendMsg constructs the HTTP body from (MatrixSetting, MsgInfo) via
 *   buildMessageJson, we verify that the MsgInfo fields (from, content) are preserved
 *   through the message construction pipeline.
 * - The withE2ee variant's fallback calls MatrixUtils.sendMsg(setting, msgInfo) with the
 *   ORIGINAL setting and msgInfo objects (not sanitized copies), so if buildMessageJson
 *   faithfully includes from and content, the property holds.
 * - We parse the resulting JSON and extract the "body" field to verify content preservation
 *   without being affected by JSON serialization escaping.
 */
class PlaintextFallbackContentPropertyTest : FunSpec({

    // --- Generators ---

    /**
     * Generator for random MsgInfo with varied from and content fields.
     * These are the fields that must be preserved through plaintext fallback.
     */
    val msgInfoArb: Arb<MsgInfo> = arbitrary {
        val from = Arb.string(1..100).bind()
        val content = Arb.string(1..500).bind()
        MsgInfo(
            type = "sms",
            from = from,
            content = content,
            date = Date(),
            simInfo = "SIM1",
        )
    }

    /**
     * Generator for MatrixSetting with varied titleTemplate and messageType.
     */
    val settingArb: Arb<MatrixSetting> = arbitrary {
        val titleTemplate = Arb.element("", "Custom Title", "Test: ").bind()
        val messageType = Arb.element("text", "markdown").bind()
        MatrixSetting(
            homeserver = "https://matrix.example.com",
            accessToken = "test-token",
            roomId = "!room:example.com",
            messageType = messageType,
            titleTemplate = titleTemplate,
        )
    }

    // --- Property Tests ---

    test("Property 9: MsgInfo.content is preserved in plaintext message body") {
        /**
         * **Validates: Requirements 5.3**
         *
         * For any randomly generated MsgInfo, the content field must appear
         * unchanged in the message body produced by MatrixUtils.buildMessageJson.
         * We parse the JSON and extract the "body" field to verify content preservation.
         */
        checkAll(PropTestConfig(iterations = 100), settingArb, msgInfoArb) { setting, msgInfo ->
            val json = MatrixUtils.buildMessageJson(setting, msgInfo)
            val parsed = Json.parseToJsonElement(json).jsonObject
            val body = parsed["body"]!!.jsonPrimitive.content
            body shouldContain msgInfo.content
        }
    }

    test("Property 9: MsgInfo.from is preserved in plaintext message body when titleTemplate is blank") {
        /**
         * **Validates: Requirements 5.3**
         *
         * When titleTemplate is blank (default), the from field appears in the
         * generated title ("信息驿站: {from}") and thus in the body.
         * This verifies from is passed through unchanged to the plaintext sender.
         */
        val blankTitleSettingArb: Arb<MatrixSetting> = arbitrary {
            val messageType = Arb.element("text", "markdown").bind()
            MatrixSetting(
                homeserver = "https://matrix.example.com",
                accessToken = "test-token",
                roomId = "!room:example.com",
                messageType = messageType,
                titleTemplate = "", // blank → uses "信息驿站: {from}"
            )
        }

        checkAll(PropTestConfig(iterations = 100), blankTitleSettingArb, msgInfoArb) { setting, msgInfo ->
            val json = MatrixUtils.buildMessageJson(setting, msgInfo)
            val parsed = Json.parseToJsonElement(json).jsonObject
            val body = parsed["body"]!!.jsonPrimitive.content
            body shouldContain msgInfo.from
        }
    }

    test("Property 9: buildMessageJson is a pure function of (setting, msgInfo)") {
        /**
         * **Validates: Requirements 5.3**
         *
         * Calling buildMessageJson multiple times with the same (setting, msgInfo)
         * always produces the same output, ensuring no hidden state mutation
         * during the plaintext fallback path.
         */
        checkAll(PropTestConfig(iterations = 100), settingArb, msgInfoArb) { setting, msgInfo ->
            val json1 = MatrixUtils.buildMessageJson(setting, msgInfo)
            val json2 = MatrixUtils.buildMessageJson(setting, msgInfo)
            json1 shouldBe json2
        }
    }

    test("Property 9: original MsgInfo fields are not modified by buildMessageJson") {
        /**
         * **Validates: Requirements 5.3**
         *
         * After calling buildMessageJson, the original MsgInfo object's from and
         * content fields remain unchanged (data class immutability guarantee, but
         * verifying the contract explicitly).
         */
        checkAll(PropTestConfig(iterations = 100), settingArb, msgInfoArb) { setting, msgInfo ->
            val originalFrom = msgInfo.from
            val originalContent = msgInfo.content

            // Call the function that would be invoked during fallback
            MatrixUtils.buildMessageJson(setting, msgInfo)

            // Verify MsgInfo fields are unchanged after the call
            msgInfo.from shouldBe originalFrom
            msgInfo.content shouldBe originalContent
        }
    }
})
