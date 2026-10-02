package io.github.magisk317.relay.desktop.config

import io.github.magisk317.relay.contract.model.SenderActiveScheduleRule
import io.github.magisk317.relay.contract.model.SnapshotSender
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.sender.SenderSettingSchemas
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the sender defaults port: draft payloads, form round trips and the
 * normalization the console applies before queueing a replace_senders command.
 */
class SenderDefaultsTest {

    @Test
    fun everyContractSenderType_hasStructuredFields() {
        val types = SenderSettingSchemas.all.map { it.senderType }
        assertFalse(types.isEmpty(), "sender schema contract is empty")
        types.forEach { type ->
            assertTrue(getSenderFieldSchemas(type).isNotEmpty(), "Missing structured fields for sender type $type")
        }
    }

    @Test
    fun draftJson_carriesContractDefaults() {
        val wecom = parseObject(buildSenderDraftJson(4))
        assertEquals("text", wecom["msgType"]?.jsonPrimitive?.content)
        assertEquals("false", wecom["atAll"]?.jsonPrimitive?.content)

        val bark = parseObject(buildSenderDraftJson(2))
        assertEquals("active", bark["level"]?.jsonPrimitive?.content)

        val socket = parseObject(buildSenderDraftJson(15))
        assertEquals("MQTT", socket["method"]?.jsonPrimitive?.content)
        assertEquals("tcp", socket["uriType"]?.jsonPrimitive?.content)
        assertEquals("0", socket["port"]?.jsonPrimitive?.content)
    }

    @Test
    fun draftJson_keepsEmailTypesAndOverrides() {
        val email = parseObject(buildSenderDraftJson(1))
        assertEquals("465", email["port"]?.jsonPrimitive?.content)
        assertEquals("true", email["ssl"]?.jsonPrimitive?.content)
        assertEquals("Plain", email["encryptionProtocol"]?.jsonPrimitive?.content)
        assertEquals(JsonObject(emptyMap()), email["recipients"])
    }

    @Test
    fun selectOptions_comeFromTheContract() {
        val proxy = getSenderFieldSchemas(3).first { it.key == "proxyType" }
        assertEquals(SenderFieldKind.SELECT, proxy.kind)
        assertEquals(listOf("DIRECT", "HTTP", "SOCKS"), proxy.options.map { it.value })
        assertEquals(
            "Direct",
            resolveSenderText(DesktopLocale.EN, proxy.options[0].label),
        )
    }

    @Test
    fun normalizeJson_fillsDefaultsAndDropsUnknownKeys() {
        val normalized = parseObject(
            normalizeSenderJson(3, """{"webServer":"https://example.com/hook","unknownField":1}"""),
        )
        val expectedKeys = SenderSettingSchemas.fieldsFor(3).map { it.name }
        assertEquals(expectedKeys, normalized.keys.toList())
        assertEquals("https://example.com/hook", normalized["webServer"]?.jsonPrimitive?.content)
    }

    @Test
    fun normalizeJson_fallsBackToDirectProxy() {
        assertEquals("DIRECT", parseObject(normalizeSenderJson(3, "{}"))["proxyType"]?.jsonPrimitive?.content)
        assertEquals("HTTP", parseObject(normalizeSenderJson(3, """{"proxyType":"HTTP"}"""))["proxyType"]?.jsonPrimitive?.content)
        assertEquals("DIRECT", parseObject(normalizeSenderJson(3, """{"proxyType":"FTP"}"""))["proxyType"]?.jsonPrimitive?.content)
    }

    @Test
    fun formState_roundTripsThroughTheEditor() {
        val json = normalizeSenderJson(3, """{"webServer":"https://example.com/hook","method":"GET"}""")
        assertEquals(json, buildSenderJsonFromFormState(3, parseSenderFormState(3, json)))

        val feishu = normalizeSenderJson(13, """{"appId":"id","appSecret":"secret","receiveId":"chat"}""")
        assertEquals(feishu, buildSenderJsonFromFormState(13, parseSenderFormState(13, feishu)))
    }

    @Test
    fun formState_resolvesEmailAliases() {
        val formState = parseSenderFormState(
            1,
            """{"fromEmail":"from@example.com","nickname":"Nick","fromEmailAlias":""}""",
        )
        assertEquals("from@example.com", formState["authEmail"]?.jsonPrimitive?.content)
        assertEquals("Nick", formState["fromEmailAlias"]?.jsonPrimitive?.content)

        val built = parseObject(
            buildSenderJsonFromFormState(
                1,
                mapOf(
                    "authEmail" to JsonPrimitive("auth@example.com"),
                    "fromEmail" to JsonPrimitive("from@example.com"),
                    "fromEmailAlias" to JsonPrimitive("Alias"),
                ),
            ),
        )
        assertEquals("auth@example.com", built["authEmail"]?.jsonPrimitive?.content)
        assertEquals("Alias", built["fromEmailAlias"]?.jsonPrimitive?.content)
        assertEquals("Alias", built["nickname"]?.jsonPrimitive?.content)
    }

    @Test
    fun formState_dropsInvalidNumberInput() {
        val formState = parseSenderFormState(8, """{"simSlot":"abc","mobiles":"13800000000"}""")
        val built = parseObject(buildSenderJsonFromFormState(8, formState))
        assertEquals("0", built["simSlot"]?.jsonPrimitive?.content)

        val override = parseObject(
            buildSenderJsonFromFormState(
                8,
                formState + ("simSlot" to JsonPrimitive("2")),
            ),
        )
        assertEquals("2", override["simSlot"]?.jsonPrimitive?.content)
    }

    @Test
    fun typeChange_resetsUntouchedDraftsOnly() {
        val untouched = buildSenderDraftJson(4)
        assertEquals(
            buildSenderDraftJson(5),
            resolveSenderJsonForTypeChange(4, 5, untouched),
        )
        assertEquals(untouched, resolveSenderJsonForTypeChange(4, 4, untouched))
        val customized = """{"webHook":"https://example.com/hook"}"""
        assertEquals(customized, resolveSenderJsonForTypeChange(4, 5, customized))
        assertEquals(buildSenderDraftJson(5), resolveSenderJsonForTypeChange(4, 5, ""))
    }

    @Test
    fun nextSenderId_ignoresNonPositiveIds() {
        assertEquals(1L, nextSenderId(emptyList()))
        assertEquals(6L, nextSenderId(listOf(sender(id = 5), sender(id = -3), sender(id = 0))))
    }

    @Test
    fun normalizeSender_coercesIdsNamesFlagsAndSchedule() {
        val normalized = normalizeSnapshotSender(
            sender(id = -4, name = "  DingTalk  ").copy(
                receiveCode = 2,
                receiveCallNotify = 7,
            ),
        )
        assertEquals(0L, normalized.id)
        assertEquals("DingTalk", normalized.name)
        assertEquals(0, normalized.receiveCode)
        assertEquals(0, normalized.receiveCallNotify)
        assertEquals(1, normalized.receiveAppNotify)
        assertFalse(normalized.activeSchedule.sms.enabled)

        val withBadRange = normalizeSnapshotSender(
            sender(id = 3).copy(
                activeSchedule = io.github.magisk317.relay.contract.model.SenderActiveSchedule(
                    sms = io.github.magisk317.relay.contract.model.SenderActiveScheduleRule(
                        enabled = true,
                        weekdays = listOf(9, 2),
                        ranges = listOf(
                            io.github.magisk317.relay.contract.model.SenderActiveScheduleRange("25:00", "18:00"),
                            io.github.magisk317.relay.contract.model.SenderActiveScheduleRange("09:00", "18:00"),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(withBadRange.activeSchedule.sms.enabled)
        assertEquals(listOf(2), withBadRange.activeSchedule.sms.weekdays)
        assertEquals(1, withBadRange.activeSchedule.sms.ranges.size)
    }

    @Test
    fun defaultSchedule_isDisabled() {
        val schedule = buildDefaultSenderActiveSchedule()
        assertFalse(schedule.sms.enabled)
        assertFalse(schedule.appNotify.enabled)
        assertFalse(schedule.callNotify.enabled)
    }

    private fun sender(id: Long, name: String = "sender-$id"): SnapshotSender =
        SnapshotSender(id = id, name = name, type = 4)

    private fun parseObject(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject
}
