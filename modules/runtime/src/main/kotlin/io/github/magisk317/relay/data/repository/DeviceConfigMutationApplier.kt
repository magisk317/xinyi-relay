package io.github.magisk317.relay.data.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal fun applyConfigMutationBatch(
    base: JsonObject,
    mutation: JsonObject,
): JsonObject? {
    val operations = mutation["operations"] as? JsonArray ?: return null
    var current = base
    for (element in operations) {
        val operation = element as? JsonObject ?: return null
        current = applyConfigMutationOperation(current, operation) ?: return null
    }
    return current
}

private fun applyConfigMutationOperation(
    base: JsonObject,
    operation: JsonObject,
): JsonObject? {
    return when (operation["type"]?.jsonPrimitive?.contentOrNull) {
        "replace_senders" -> applyReplaceSendersMutation(base, operation)
        "replace_device_apps" -> applyReplaceDeviceAppsMutation(base, operation)
        else -> null
    }
}

private fun applyReplaceSendersMutation(
    base: JsonObject,
    operation: JsonObject,
): JsonObject? {
    val senders = operation["senders"] as? JsonArray ?: return null
    val removedSenderIds = operation["removedSenderIds"]?.let(::parseSenderIds).orEmpty()
    return buildJsonObject {
        base.forEach { (key, value) ->
            when (key) {
                "senders" -> put(key, senders)
                "rules", "notifyRoutes", "forwardFilters" ->
                    put(key, pruneSenderScopedArray(value, removedSenderIds))
                else -> put(key, value)
            }
        }
        if (!base.containsKey("senders")) {
            put("senders", senders)
        }
    }
}

private fun applyReplaceDeviceAppsMutation(
    base: JsonObject,
    operation: JsonObject,
): JsonObject? {
    val deviceId = operation["deviceId"]?.jsonPrimitive?.contentOrNull ?: return null
    val apps = operation["apps"] as? JsonArray ?: return null
    return buildJsonObject {
        base.forEach { (key, value) ->
            if (key == "deviceAppInfos") {
                putJsonObject(key) {
                    val existing = (value as? JsonObject).orEmpty()
                    existing.forEach { (appKey, appValue) -> put(appKey, appValue) }
                    put(deviceId, apps)
                }
            } else {
                put(key, value)
            }
        }
        if (!base.containsKey("deviceAppInfos")) {
            putJsonObject("deviceAppInfos") {
                put(deviceId, apps)
            }
        }
    }
}

private fun parseSenderIds(value: JsonElement): Set<Long> {
    return when (value) {
        is JsonArray -> value.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.toLongOrNull()
                ?: element.jsonPrimitive.intOrNull?.toLong()
        }.toSet()
        else -> emptySet()
    }
}

private fun pruneSenderScopedArray(value: JsonElement, removedSenderIds: Set<Long>): JsonArray {
    if (removedSenderIds.isEmpty()) {
        return (value as? JsonArray) ?: JsonArray(emptyList())
    }
    val array = (value as? JsonArray) ?: return JsonArray(emptyList())
    return buildJsonArray {
        array.forEach { element ->
            val senderId = (element as? JsonObject)
                ?.get("senderId")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toLongOrNull()
            if (senderId == null || !removedSenderIds.contains(senderId)) {
                add(element)
            }
        }
    }
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
