package io.github.magisk317.relay.desktop.config

import io.github.magisk317.relay.contract.model.SnapshotAppInfo
import io.github.magisk317.relay.contract.model.SnapshotForwardFilterRule
import io.github.magisk317.relay.contract.model.SnapshotNotifyRouteRule
import io.github.magisk317.relay.contract.model.SnapshotRule
import io.github.magisk317.relay.contract.model.SnapshotSender
import io.github.magisk317.relay.contract.model.SnapshotSmsCodeRule
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Effective config root: the device mirror with every pending command
 * applied, mirroring frontend/shared/deviceConfigCommands.ts. Pages read
 * this instead of the raw mirror so queued commands are visible before the
 * agent pulls them.
 */
data class DesktopConfigRoot(
    val senders: List<SnapshotSender> = emptyList(),
    val rules: List<SnapshotRule> = emptyList(),
    val smsCodeRules: List<SnapshotSmsCodeRule> = emptyList(),
    val notifyRoutes: List<SnapshotNotifyRouteRule> = emptyList(),
    val forwardFilters: List<SnapshotForwardFilterRule> = emptyList(),
    val deviceAppInfos: Map<String, List<SnapshotAppInfo>> = emptyMap(),
)

private val configJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private inline fun <reified T> decodeList(element: JsonElement?): List<T> {
    val array = element as? JsonArray ?: return emptyList()
    return runCatching { configJson.decodeFromJsonElement<List<T>>(array) }.getOrElse { emptyList() }
}

/** Arrays that are missing or malformed fall back to empty, like the TS normalizeArray. */
fun normalizeConfigRoot(mirrorContent: JsonObject?): DesktopConfigRoot {
    if (mirrorContent == null) return DesktopConfigRoot()
    return DesktopConfigRoot(
        senders = decodeList(mirrorContent["senders"]),
        rules = decodeList(mirrorContent["rules"]),
        smsCodeRules = decodeList(mirrorContent["smsCodeRules"]),
        notifyRoutes = decodeList(mirrorContent["notifyRoutes"]),
        forwardFilters = decodeList(mirrorContent["forwardFilters"]),
        deviceAppInfos = decodeAppInfos(mirrorContent["deviceAppInfos"]),
    )
}

private fun decodeAppInfos(element: JsonElement?): Map<String, List<SnapshotAppInfo>> {
    val obj = element as? JsonObject ?: return emptyMap()
    return obj.mapValues { (_, value) -> decodeList<SnapshotAppInfo>(value) }
}

/** Applies a queued command batch to the effective root, like the TS fold. */
fun applyMutationBatchToConfigRoot(
    baseRoot: DesktopConfigRoot,
    mutation: JsonObject,
): DesktopConfigRoot {
    var next = baseRoot
    val operations = mutation["operations"] as? JsonArray ?: return next
    for (operation in operations) {
        val op = operation as? JsonObject ?: continue
        next = when (op["type"]?.jsonPrimitive?.contentOrNull) {
            "replace_senders" -> {
                val removed = (op["removedSenderIds"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }
                    ?.toSet()
                    ?: emptySet()
                next.copy(
                    senders = decodeList(op["senders"]),
                    rules = pruneBySender(next.rules, removed) { it.senderId },
                    notifyRoutes = pruneBySender(next.notifyRoutes, removed) { it.senderId },
                    forwardFilters = pruneBySender(next.forwardFilters, removed) { it.senderId },
                )
            }

            "replace_device_apps" -> {
                val deviceId = op["deviceId"]?.jsonPrimitive?.longOrNull ?: continue
                next.copy(
                    deviceAppInfos = next.deviceAppInfos + (deviceId.toString() to decodeList(op["apps"])),
                )
            }

            else -> next
        }
    }
    return next
}

private inline fun <T> pruneBySender(
    items: List<T>,
    removedSenderIds: Set<Long>,
    senderId: (T) -> Long,
): List<T> = if (removedSenderIds.isEmpty()) items else items.filter { senderId(it) !in removedSenderIds }

/** Folds the sorted pending commands over the mirror content, like deriveEffectiveConfigRoot. */
fun deriveEffectiveConfigRoot(
    mirrorContent: JsonObject?,
    pendingCommands: List<DeviceConfigCommandResponse>,
): DesktopConfigRoot {
    var current = normalizeConfigRoot(mirrorContent)
    for (command in pendingCommands.sortedBy { it.targetRevision }) {
        current = runCatching { applyMutationBatchToConfigRoot(current, command.mutation) }.getOrDefault(current)
    }
    return current
}

fun latestEffectiveRevision(config: DeviceConfigStateResponse): Long =
    config.pendingCommands.fold(config.revision) { revision, command -> maxOf(revision, command.targetRevision) }

fun appendPendingCommand(
    pendingCommands: List<DeviceConfigCommandResponse>,
    command: DeviceConfigCommandResponse,
): List<DeviceConfigCommandResponse> = (pendingCommands + command).sortedBy { it.targetRevision }

fun buildReplaceSendersMutation(
    senders: List<SnapshotSender>,
    removedSenderIds: List<Long> = emptyList(),
): JsonObject = buildJsonObject {
    put("operations", buildJsonArray {
        add(
            buildJsonObject {
                put("type", "replace_senders")
                put("senders", configJson.encodeToJsonElement(ListSerializer(SnapshotSender.serializer()), senders))
                put("removedSenderIds", buildJsonArray {
                    removedSenderIds.forEach { add(JsonPrimitive(it)) }
                })
            },
        )
    })
}

fun buildReplaceDeviceAppsMutation(
    deviceId: Long,
    apps: List<SnapshotAppInfo>,
): JsonObject = buildJsonObject {
    put("operations", buildJsonArray {
        add(
            buildJsonObject {
                put("type", "replace_device_apps")
                put("deviceId", JsonPrimitive(deviceId))
                put("apps", configJson.encodeToJsonElement(ListSerializer(SnapshotAppInfo.serializer()), apps))
            },
        )
    })
}
