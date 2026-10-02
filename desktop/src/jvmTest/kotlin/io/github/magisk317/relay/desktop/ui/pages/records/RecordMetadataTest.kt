package io.github.magisk317.relay.desktop.ui.pages.records

import io.github.magisk317.relay.contract.remote.RelayRecord
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RecordMetadataTest {

    @Test
    fun `known metadata fields are read and unknown ones ignored`() {
        val metadata = parseRecordMetadata(
            buildJsonObject {
                put("company", "银行")
                put("notifyChannelId", "channel-7")
                put("simSlot", 0)
                put("subId", 12)
                put("contactName", "张三")
                put("phoneArea", "杭州")
                put("forwardedBy", "agent")
            },
        )
        assertEquals("银行", metadata.company)
        assertEquals("channel-7", metadata.notifyChannelId)
        assertEquals(0, metadata.simSlot)
        assertEquals(12, metadata.subId)
        assertEquals("张三", metadata.contactName)
        assertEquals("杭州", metadata.phoneArea)
    }

    @Test
    fun `wrongly typed and missing fields normalize to null`() {
        val metadata = parseRecordMetadata(
            buildJsonObject {
                put("company", 5)
                put("simSlot", "1")
            },
        )
        assertEquals(null, metadata.company)
        assertEquals(null, metadata.simSlot)
        assertEquals(null, metadata.subId)
        assertEquals(null, metadata.contactName)
        assertEquals(null, metadata.phoneArea)
    }

    @Test
    fun `empty metadata yields an empty record metadata`() {
        val metadata = parseRecordMetadata(buildJsonObject { })
        assertEquals(RecordMetadata(), metadata)
    }

    @Test
    fun `sim labels are one based and skip missing slots`() {
        assertEquals(
            DesktopMessagesZhSimSlot1,
            resolveSimLabel(parseRecordMetadata(buildJsonObject { put("simSlot", 0) }), DesktopLocale.ZH_CN),
        )
        assertEquals("", resolveSimLabel(RecordMetadata(simSlot = -1), DesktopLocale.ZH_CN))
        assertEquals("", resolveSimLabel(RecordMetadata(), DesktopLocale.ZH_CN))
    }

    @Test
    fun `sms and notification records have no line label`() {
        val metadata = RecordMetadata(contactName = "张三")
        assertEquals("", resolveLineLabel(record("sms_code"), metadata))
        assertEquals("", resolveLineLabel(record("sms_plain"), metadata))
        assertEquals("", resolveLineLabel(record("app_notify"), metadata))
    }

    @Test
    fun `call records prefer the contact name over the raw sender`() {
        val metadata = RecordMetadata(company = "银行", contactName = "张三")
        assertEquals("张三", resolveLineLabel(record("call", sender = "95588"), metadata))
    }

    @Test
    fun `call records fall back to the sender when there is no contact name`() {
        val metadata = RecordMetadata(company = "银行")
        assertEquals("95588", resolveLineLabel(record("call", sender = "95588"), metadata))
    }

    @Test
    fun `call records hide the sender when it only repeats the company`() {
        val metadata = RecordMetadata(company = "95588", contactName = "95588")
        assertTrue(resolveLineLabel(record("call", sender = "95588"), metadata).isEmpty())
    }

    private fun record(recordType: String, sender: String = ""): RelayRecord = RelayRecord(
        id = 1,
        deviceId = 2,
        recordType = recordType,
        sender = sender,
        metadata = buildJsonObject { },
    )

    private companion object {
        /** Resolved through the message table so the expectation matches the port. */
        val DesktopMessagesZhSimSlot1 = io.github.magisk317.relay.desktop.i18n.DesktopMessages
            .t(DesktopLocale.ZH_CN, "records.simBadge", mapOf("slot" to 1))
    }
}
