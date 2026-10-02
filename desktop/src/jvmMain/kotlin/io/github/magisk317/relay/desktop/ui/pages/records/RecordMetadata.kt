package io.github.magisk317.relay.desktop.ui.pages.records

import io.github.magisk317.relay.contract.remote.RelayRecord
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Metadata fields the webUI reads out of the record payload. */
internal data class RecordMetadata(
    val company: String? = null,
    val notifyChannelId: String? = null,
    val simSlot: Int? = null,
    val subId: Int? = null,
    val contactName: String? = null,
    val phoneArea: String? = null,
)

/**
 * A record with the display labels the records page resolves once per
 * payload instead of in every badge.
 */
internal data class EnrichedRecord(
    val record: RelayRecord,
    val appLabel: String,
    val deviceLabel: String,
    val lineLabel: String,
    val simLabel: String,
)

/**
 * Reads the known metadata fields, ignoring anything the agent added later.
 * Field types are checked like the webUI does: strings only for the text
 * fields, numbers only for the SIM identifiers.
 */
internal fun parseRecordMetadata(metadata: JsonObject): RecordMetadata = RecordMetadata(
    company = metadata["company"]?.stringContentOrNull(),
    notifyChannelId = metadata["notifyChannelId"]?.stringContentOrNull(),
    simSlot = metadata["simSlot"]?.jsonPrimitive?.takeIf { !it.isString }?.intOrNull,
    subId = metadata["subId"]?.jsonPrimitive?.takeIf { !it.isString }?.intOrNull,
    contactName = metadata["contactName"]?.stringContentOrNull(),
    phoneArea = metadata["phoneArea"]?.stringContentOrNull(),
)

private fun kotlinx.serialization.json.JsonElement?.stringContentOrNull(): String? =
    (this?.jsonPrimitive)?.takeIf { it.isString }?.content

/** Line labels only exist for call records; SMS carries its sender in the body. */
internal fun resolveLineLabel(record: RelayRecord, metadata: RecordMetadata): String {
    if (record.recordType == "app_notify") return ""
    if (record.recordType == "sms_code" || record.recordType == "sms_plain") return ""
    val sender = record.sender.trim()
    val contactName = metadata.contactName?.trim().orEmpty()
    if (contactName.isNotEmpty() && contactName != sender) return contactName
    if (sender.isNotEmpty() && sender != metadata.company) return sender
    return ""
}

/** SIM labels are one-based; a missing or negative slot has no label. */
internal fun resolveSimLabel(metadata: RecordMetadata, locale: DesktopLocale): String {
    val slot = metadata.simSlot
    if (slot != null && slot >= 0) {
        return DesktopMessages.t(locale, "records.simBadge", mapOf("slot" to slot + 1))
    }
    return ""
}
