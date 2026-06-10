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
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 8: 模板变量替换正确性
 *
 * Validates: Requirements 5.2, 5.3
 *
 * Verifies that the APP_ICON template variable substitution in MessageFormatter works correctly:
 * 1. When appIcon is non-empty, {{APP_ICON}} is replaced with the actual value and no placeholder remains
 * 2. When appIcon is empty, {{APP_ICON}} is replaced with empty string
 */
class MessageFormatterAppIconPropertyTest : FunSpec({

    val formatter = MessageFormatter(
        systemInfoProvider = object : SystemInfoProvider {
            override fun getSnapshot(deviceName: String): SystemEnvironment = testEnv
            override fun resolveAppName(packageName: String): String = "TestApp"
        },
    )

    // Generator for non-empty random strings that won't contain the placeholder itself
    // Also filter strings with trailing whitespace since removeEmptyValueLines calls trimEnd() on each line
    val nonEmptyAppIconArb: Arb<String> = Arb.string(minSize = 1, maxSize = 200)
        .filter { !it.contains("{{") && !it.contains("}}") && it == it.trimEnd() }

    // Generator for arbitrary random strings (including empty)
    val anyStringArb: Arb<String> = arbitrary { rs ->
        val length = Arb.int(0..200).bind()
        val base64Chars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('+', '/', '=')
        String(CharArray(length) { base64Chars[rs.random.nextInt(base64Chars.size)] })
    }

    test("Property 8: non-empty appIcon replaces {{APP_ICON}} with actual value and no placeholder remains") {
        /**
         * **Validates: Requirements 5.2**
         *
         * For any non-empty appIcon string, when a template contains {{APP_ICON}},
         * the formatted output should contain the appIcon value and should NOT contain
         * the {{APP_ICON}} placeholder.
         */
        checkAll(50, nonEmptyAppIconArb) { appIconValue ->
            val event = baseEvent.copy(appIcon = appIconValue)
            val result = formatter.format(
                event = event,
                payloadContext = DispatchPayloadContext.from(event),
                config = ForwardCommonConfig(messageTemplate = "icon:{{APP_ICON}}"),
                env = testEnv,
            )

            result shouldContain appIconValue
            result shouldNotContain "{{APP_ICON}}"
        }
    }

    test("Property 8: empty appIcon replaces {{APP_ICON}} with empty string") {
        /**
         * **Validates: Requirements 5.3**
         *
         * For an empty appIcon, when a template contains {{APP_ICON}},
         * the formatted output should NOT contain the {{APP_ICON}} placeholder.
         * The placeholder is replaced with empty string.
         */
        val event = baseEvent.copy(appIcon = "")
        val result = formatter.format(
            event = event,
            payloadContext = DispatchPayloadContext.from(event),
            config = ForwardCommonConfig(messageTemplate = "icon:{{APP_ICON}}\ncontent:{{SMS}}"),
            env = testEnv,
        )

        result shouldNotContain "{{APP_ICON}}"
    }

    test("Property 8: random Base64 appIcon values are correctly substituted") {
        /**
         * **Validates: Requirements 5.2, 5.3**
         *
         * For any random Base64-like appIcon string, the substitution should be correct:
         * - Non-empty values appear in output, placeholder removed
         * - Empty values result in placeholder removal
         */
        checkAll(50, anyStringArb) { appIconValue ->
            val event = baseEvent.copy(appIcon = appIconValue)
            val result = formatter.format(
                event = event,
                payloadContext = DispatchPayloadContext.from(event),
                config = ForwardCommonConfig(messageTemplate = "icon:{{APP_ICON}}\ncontent:{{SMS}}"),
                env = testEnv,
            )

            // Placeholder should never remain after formatting
            result shouldNotContain "{{APP_ICON}}"

            // Non-empty appIcon values should appear in the output (trimEnd is applied by removeEmptyValueLines)
            if (appIconValue.isNotEmpty()) {
                result shouldContain appIconValue.trimEnd()
            }
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
        )
    }
}
