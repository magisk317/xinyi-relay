package io.github.magisk317.relay.sender

import io.github.magisk317.relay.contract.json.RelayJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object SenderSettingJson {
    private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())

    fun <T> encode(serializer: SerializationStrategy<T>, value: T): String {
        return RelayJson.encode(serializer, value)
    }

    fun <T> decode(serializer: DeserializationStrategy<T>, raw: String): T {
        return RelayJson.decode(serializer, raw)
    }

    fun <T> decodeOrNull(serializer: DeserializationStrategy<T>, raw: String): T? {
        if (raw.isBlank()) return null
        return runCatching { decode(serializer, raw) }.getOrNull()
    }

    inline fun <reified T> encode(value: T): String {
        return RelayJson.format.encodeToString(value)
    }

    inline fun <reified T> decode(raw: String): T {
        return RelayJson.format.decodeFromString(raw)
    }

    inline fun <reified T> decodeOrNull(raw: String): T? {
        if (raw.isBlank()) return null
        return runCatching { decode<T>(raw) }.getOrNull()
    }

    fun parseObject(raw: String): JsonObject? {
        if (raw.isBlank()) return null
        return runCatching { RelayJson.parseElement(raw) as? JsonObject }.getOrNull()
    }

    fun encodeStringMap(value: Map<String, String>): String {
        return encode(stringMapSerializer, value)
    }

    fun decodeStringMapOrNull(raw: String): Map<String, String>? {
        return decodeOrNull(stringMapSerializer, raw)
    }

    fun decodeStringMapLenientOrNull(raw: String): Map<String, String>? {
        val json = parseObject(raw) ?: return null
        return buildMap {
            json.forEach { (key, value) ->
                if (key.isBlank() || value is JsonNull) return@forEach
                put(
                    key,
                    when (value) {
                        is JsonPrimitive -> value.content
                        else -> value.toString()
                    },
                )
            }
        }
    }
}
