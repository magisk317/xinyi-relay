package io.github.magisk317.relay.sender

import io.github.magisk317.relay.contract.json.RelayJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal object SenderWireJson {
    inline fun <reified T> decode(raw: String): T {
        return RelayJson.format.decodeFromString(raw)
    }

    inline fun <reified T> decodeOrNull(raw: String): T? {
        if (raw.isBlank()) return null
        return runCatching { decode<T>(raw) }.getOrNull()
    }

    fun encode(element: JsonElement): String {
        return element.toString()
    }

    fun parseElement(raw: String): JsonElement {
        return RelayJson.parseElement(raw)
    }

    fun escapeStringContent(text: String): String {
        val encoded = JsonPrimitive(text).toString()
        return if (encoded.length >= 2) encoded.substring(1, encoded.length - 1) else encoded
    }
}
