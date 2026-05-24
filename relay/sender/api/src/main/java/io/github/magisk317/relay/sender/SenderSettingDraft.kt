package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.Sender
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class SenderSettingDraft(
    val senderType: Int,
    val values: Map<String, JsonElement> = emptyMap(),
) {
    val schema: SenderSettingSchema?
        get() = SenderSettingSchemas.schemaFor(senderType)

    fun element(name: String): JsonElement? = values[name]

    fun string(name: String): String {
        return when (val value = values[name]) {
            null, JsonNull -> ""
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
    }

    fun boolean(name: String, defaultValue: Boolean = false): Boolean {
        val value = values[name] ?: return defaultValue
        if (value is JsonPrimitive) {
            value.booleanOrNull?.let { return it }
        }
        return when (string(name).trim().lowercase(Locale.ROOT)) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> defaultValue
        }
    }

    fun int(name: String, defaultValue: Int = 0): Int {
        val value = values[name] ?: return defaultValue
        if (value is JsonPrimitive) {
            value.intOrNull?.let { return it }
        }
        return string(name).trim().toIntOrNull() ?: defaultValue
    }

    fun stringMap(name: String): Map<String, String> {
        val value = values[name] as? JsonObject ?: return emptyMap()
        return buildMap {
            value.forEach { (key, element) ->
                if (key.isBlank() || element is JsonNull) return@forEach
                put(
                    key,
                    when (element) {
                        is JsonPrimitive -> element.content
                        else -> element.toString()
                    },
                )
            }
        }
    }

    fun withString(name: String, value: String): SenderSettingDraft {
        return withElement(name, JsonPrimitive(value))
    }

    fun withBoolean(name: String, value: Boolean): SenderSettingDraft {
        return withElement(name, JsonPrimitive(value))
    }

    fun withInt(name: String, value: Int): SenderSettingDraft {
        return withElement(name, JsonPrimitive(value))
    }

    fun withStringMap(name: String, value: Map<String, String>): SenderSettingDraft {
        val objectValue = JsonObject(
            value
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, entryValue) -> JsonPrimitive(entryValue) },
        )
        return withElement(name, objectValue)
    }

    fun withElement(name: String, value: JsonElement?): SenderSettingDraft {
        requireKnownField(name)
        val nextValues = LinkedHashMap(values)
        if (value == null || value is JsonNull) {
            nextValues.remove(name)
        } else {
            nextValues[name] = value
        }
        return copy(values = orderValues(nextValues))
    }

    fun without(name: String): SenderSettingDraft {
        requireKnownField(name)
        if (name !in values) return this
        return copy(values = orderValues(values - name))
    }

    fun withSchemaDefaults(): SenderSettingDraft {
        var nextDraft = this
        schema?.fields.orEmpty().forEach { field ->
            val defaultValue = field.defaultValue ?: return@forEach
            if (field.name !in nextDraft.values) {
                nextDraft = nextDraft.withString(field.name, defaultValue)
            }
        }
        return nextDraft
    }

    fun toJsonObject(): JsonObject = JsonObject(orderValues(values))

    fun toJson(): String = toJsonObject().toString()

    private fun orderValues(source: Map<String, JsonElement>): Map<String, JsonElement> {
        val schemaFields = schema?.fields
        if (schemaFields.isNullOrEmpty()) return source
        return buildMap {
            schemaFields.forEach { field ->
                val value = source[field.name] ?: return@forEach
                if (value !is JsonNull) put(field.name, value)
            }
        }
    }

    private fun requireKnownField(name: String) {
        val schemaFields = schema?.fields ?: return
        require(schemaFields.any { it.name == name }) {
            "Unknown sender setting field '$name' for sender type $senderType"
        }
    }
}

object SenderSettingDrafts {
    fun empty(senderType: Int): SenderSettingDraft = SenderSettingDraft(senderType)

    fun emptyWithDefaults(senderType: Int): SenderSettingDraft = empty(senderType).withSchemaDefaults()

    fun fromSender(sender: Sender): SenderSettingDraft {
        return fromJson(sender.type, sender.jsonSetting)
    }

    fun fromSenderWithDefaults(sender: Sender): SenderSettingDraft {
        return fromSender(sender).withSchemaDefaults()
    }

    fun fromJson(senderType: Int, rawJson: String): SenderSettingDraft {
        val canonicalJson = SenderSettingSanitizer.sanitizeJsonLenient(senderType, rawJson)
        val rawObject = SenderSettingJson.parseObject(canonicalJson) ?: return empty(senderType)
        val fields = SenderSettingSchemas.fieldsFor(senderType)
        val values = if (fields.isEmpty()) {
            rawObject
        } else {
            buildMap {
                fields.forEach { field ->
                    val value = rawObject[field.name] ?: return@forEach
                    if (value !is JsonNull) put(field.name, value)
                }
            }
        }
        return SenderSettingDraft(senderType, values)
    }

    fun fromJsonWithDefaults(senderType: Int, rawJson: String): SenderSettingDraft {
        return fromJson(senderType, rawJson).withSchemaDefaults()
    }

    fun toJson(draft: SenderSettingDraft): String = draft.toJson()
}
