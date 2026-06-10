package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import io.github.magisk317.relay.engine.event.RelayEvent
import io.github.magisk317.relay.engine.model.BatterySnapshot
import io.github.magisk317.relay.engine.model.NetworkSnapshot
import io.github.magisk317.relay.engine.model.SystemEnvironment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property 10: 空 appIcon 对消息格式无副作用
 *
 * Validates: Requirements 5.3, 5.4
 *
 * Verifies that when appIcon is empty:
 * 1. The formatted output does not contain the literal text "{{APP_ICON}}"
 * 2. Lines containing only {{APP_ICON}} placeholder (key:value pattern with empty value) are removed
 * 3. Other template variables and content lines are not affected
 */
class EmptyAppIconPropertyTest : FunSpec({

    val formatter = MessageFormatter(
        systemInfoProvider = object : SystemInfoProvider {
            override fun getSnapshot(deviceName: String): SystemEnvironment = testEnv
            override fun resolveAppName(packageName: String): String = "TestApp"
        },
    )

    // Generator for random templates that include {{APP_ICON}} in a "label：value" line pattern
    val templateWithAppIconLineArb: Arb<String> = arbitrary { rs ->
        val lineCount = Arb.int(2..5).bind()
        val appIconLineIndex = Arb.int(0 until lineCount).bind()
        val lines = (0 until lineCount).map { i ->
            if (i == appIconLineIndex) {
                // Line with APP_ICON as a key:value pattern — will match EMPTY_VALUE_LINE_REGEX when empty
                "图标：{{APP_ICON}}"
            } else {
                val options = listOf(
                    "来自：{{FROM}}",
                    "内容：{{SMS}}",
                    "设备：{{DEVICE_NAME}}",
                    "时间：{{RECEIVE_TIME}}",
                    "版本：{{APP_VERSION}}",
                )
                Arb.element(options).bind()
            }
        }
        lines.joinToString("\n")
    }

    // Generator for templates with {{APP_ICON}} in various positions (including inline)
    val templateWithInlineAppIconArb: Arb<String> = arbitrary { rs ->
        val lineCount = Arb.int(2..5).bind()
        val appIconLineIndex = Arb.int(0 until lineCount).bind()
        val lines = (0 until lineCount).map { i ->
            if (i == appIconLineIndex) {
                // Inline usage: APP_ICON embedded in content (not a pure key:value line)
                "Logo {{APP_ICON}} here"
            } else {
                val options = listOf(
                    "来自：{{FROM}}",
                    "内容：{{SMS}}",
                    "设备：{{DEVICE_NAME}}",
                )
                Arb.element(options).bind()
            }
        }
        lines.joinToString("\n")
    }

    test("Property 10: empty appIcon does not leave {{APP_ICON}} placeholder in output") {
        /**
         * **Validates: Requirements 5.3, 5.4**
         *
         * For any message template containing {{APP_ICON}}, when appIcon is empty,
         * the formatted output should not contain the literal text "{{APP_ICON}}".
         */
        checkAll(50, templateWithAppIconLineArb) { template ->
            val event = baseEvent.copy(appIcon = "")
            val result = formatter.format(
                event = event,
                payloadContext = DispatchPayloadContext.from(event),
                config = ForwardCommonConfig(),
                env = testEnv,
                customTemplate = template,
            )

            result shouldNotContain "{{APP_ICON}}"
        }
    }

    test("Property 10: lines with only APP_ICON empty value are removed by removeEmptyValueLines") {
        /**
         * **Validates: Requirements 5.3, 5.4**
         *
         * When appIcon is empty, lines matching "label：{{APP_ICON}}" become "label："
         * which matches the EMPTY_VALUE_LINE_REGEX pattern and gets removed.
         */
        checkAll(50, templateWithAppIconLineArb) { template ->
            val event = baseEvent.copy(appIcon = "")
            val result = formatter.format(
                event = event,
                payloadContext = DispatchPayloadContext.from(event),
                config = ForwardCommonConfig(),
                env = testEnv,
                customTemplate = template,
            )

            // The line "图标：{{APP_ICON}}" becomes "图标：" after substitution (empty value)
            // This matches EMPTY_VALUE_LINE_REGEX "^[^:：\n]+[:：]\s*$" so it's removed
            result shouldNotContain "图标："
        }
    }

    test("Property 10: other content lines are preserved when appIcon is empty") {
        /**
         * **Validates: Requirements 5.3, 5.4**
         *
         * For any template with mixed content, lines containing non-empty variable
         * substitutions should be preserved in the output even when appIcon is empty.
         */
        checkAll(50, templateWithAppIconLineArb) { template ->
            val event = baseEvent.copy(appIcon = "")
            val result = formatter.format(
                event = event,
                payloadContext = DispatchPayloadContext.from(event),
                config = ForwardCommonConfig(),
                env = testEnv,
                customTemplate = template,
            )

            // FROM variable is always populated with "TestSender"
            if (template.contains("{{FROM}}")) {
                result shouldContain "TestSender"
            }
            // DEVICE_NAME is always populated with "TestDevice"
            if (template.contains("{{DEVICE_NAME}}")) {
                result shouldContain "TestDevice"
            }
            // APP_VERSION is always populated with "1.0.0"
            if (template.contains("{{APP_VERSION}}")) {
                result shouldContain "1.0.0"
            }
        }
    }

    test("Property 10: inline {{APP_ICON}} with empty value does not leave placeholder in output") {
        /**
         * **Validates: Requirements 5.3, 5.4**
         *
         * When {{APP_ICON}} appears inline (not as a pure key:value line), with empty appIcon,
         * the placeholder is still replaced with empty string. The line itself is preserved
         * (since it has other content), but no placeholder literal remains.
         */
        checkAll(50, templateWithInlineAppIconArb) { template ->
            val event = baseEvent.copy(appIcon = "")
            val result = formatter.format(
                event = event,
                payloadContext = DispatchPayloadContext.from(event),
                config = ForwardCommonConfig(),
                env = testEnv,
                customTemplate = template,
            )

            result shouldNotContain "{{APP_ICON}}"
        }
    }
}) {
    companion object {
        private val testEnv = SystemEnvironment(
            battery = BatterySnapshot(
                percent = "80%",
                status = "充电中",
                plugged = "USB",
                fullInfo = "80%, charging",
                simpleInfo = "80%",
            ),
            network = NetworkSnapshot(
                ipv4 = "127.0.0.1",
                ipv6 = "::1",
                ipList = "127.0.0.1,::1",
                netType = "Wi-Fi",
            ),
            currentTime = 1_700_000_000_000L,
            deviceName = "TestDevice",
            appVersion = "1.0.0",
        )

        private val baseEvent = RelayEvent(
            messageType = MessageType.APP_NOTIFY,
            sourceType = "test",
            sender = "TestSender",
            body = "Test body content",
            timestamp = 1_700_000_000_000L,
            packageName = "com.test.app",
            notifyChannelId = "default",
            companyOrAppName = "TestApp",
            smsCode = null,
            callType = 0,
            callStage = "",
            simSlot = -1,
            subId = 0,
            appIcon = "",
        )
    }
}
