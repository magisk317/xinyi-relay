package io.github.magisk317.relay.desktop.config

import io.github.magisk317.relay.contract.model.SnapshotAppInfo
import io.github.magisk317.relay.contract.model.SnapshotForwardFilterRule
import io.github.magisk317.relay.contract.model.SnapshotSender
import io.github.magisk317.relay.contract.model.SnapshotScheduledTask
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long

class ConfigMutationsTest {

    private fun command(
        id: Long,
        targetRevision: Long,
        mutation: JsonObject,
    ): DeviceConfigCommandResponse = DeviceConfigCommandResponse(
        id = id,
        targetRevision = targetRevision,
        mutation = mutation,
        summary = "summary-$id",
        status = "pending",
    )

    @Test
    fun `missing mirror arrays normalize to empty lists`() {
        val root = normalizeConfigRoot(buildJsonObject { put("revision", JsonPrimitive(3)) })
        assertTrue(root.senders.isEmpty())
        assertTrue(root.rules.isEmpty())
        assertTrue(root.smsCodeRules.isEmpty())
        assertTrue(root.notifyRoutes.isEmpty())
        assertTrue(root.forwardFilters.isEmpty())
        assertTrue(root.deviceAppInfos.isEmpty())
    }

    @Test
    fun `null mirror content normalizes to an empty root`() {
        val root = normalizeConfigRoot(null)
        assertTrue(root.senders.isEmpty())
        assertTrue(root.deviceAppInfos.isEmpty())
        assertTrue(root.scheduledTasks.isEmpty())
    }

    @Test
    fun `scheduled task mirror keeps tombstones and conflict variants`() {
        val root = normalizeConfigRoot(
            buildJsonObject {
                put(
                    "scheduledTasks",
                    kotlinx.serialization.json.Json.parseToJsonElement(
                        """[{"syncId":"task-local","name":"Local","taskType":"sms","cronExpression":"0 9 * * *","simSlot":1,"mobiles":"10086","content":"code","status":0,"createdAt":7,"conflictId":"c1","conflictSide":"local","conflictPreviousStatus":1,"syncDeleted":false}]""",
                    ),
                )
            },
        )

        val task = root.scheduledTasks.single()
        assertEquals("task-local", task.syncId)
        assertEquals("c1", task.conflictId)
        assertEquals("local", task.conflictSide)
        assertEquals(1, task.conflictPreviousStatus)
        assertEquals(7L, task.createdAt)
    }

    @Test
    fun `replace_scheduled_tasks previews the full list from a pending command`() {
        val original = SnapshotScheduledTask(syncId = "task-1", name = "Original")
        val updated = SnapshotScheduledTask(
            syncId = "task-2",
            name = "Remote choice",
            conflictPreviousStatus = 1,
        )
        val mirror = buildJsonObject {
            put(
                "scheduledTasks",
                kotlinx.serialization.json.Json.parseToJsonElement("""[{"syncId":"task-1","name":"Original"}]"""),
            )
        }
        val mutation = buildReplaceScheduledTasksMutation(listOf(updated))
        val pending = command(3, targetRevision = 8, mutation = mutation)

        val root = deriveEffectiveConfigRoot(mirror, listOf(pending))

        assertEquals(listOf("task-2"), root.scheduledTasks.map { it.syncId })
        assertEquals(1, root.scheduledTasks.single().conflictPreviousStatus)
        assertEquals(listOf("task-1"), normalizeConfigRoot(mirror).scheduledTasks.map { it.syncId })
    }

    @Test
    fun `scheduled task mutation serializes the exact replacement operation`() {
        val task = SnapshotScheduledTask(syncId = "task-a", name = "Daily report", createdAt = 123L)

        val operation = (buildReplaceScheduledTasksMutation(listOf(task))["operations"] as kotlinx.serialization.json.JsonArray)
            .single()
            .jsonObject

        assertEquals("replace_scheduled_tasks", operation["type"]?.toString()?.trim('"'))
        assertEquals("task-a", (operation["tasks"] as kotlinx.serialization.json.JsonArray).single().jsonObject["syncId"]?.toString()?.trim('"'))
    }

    @Test
    fun `replace_senders swaps the list and prunes dependent rules`() {
        val base = DesktopConfigRoot(
            senders = listOf(SnapshotSender(id = 1), SnapshotSender(id = 2)),
            rules = emptyList(),
            forwardFilters = listOf(
                SnapshotForwardFilterRule(
                    id = 9,
                    msgType = "sms",
                    scopeType = "sender",
                    pattern = ".*",
                    senderId = 1,
                    policy = "forward",
                    matchMode = "contains",
                ),
            ),
        )
        val mutation = buildJsonObject {
            put(
                "operations",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("replace_senders"))
                            put(
                                "senders",
                                kotlinx.serialization.json.Json.parseToJsonElement(
                                    """[{"id":7,"type":3,"name":"telegram"}]""",
                                ),
                            )
                            put("removedSenderIds", buildJsonArray { add(JsonPrimitive(1)) })
                        },
                    )
                },
            )
        }
        val next = applyMutationBatchToConfigRoot(base, mutation)
        assertEquals(listOf(7L), next.senders.map { it.id })
        assertEquals(listOf("telegram"), next.senders.map { it.name })
        assertTrue(next.forwardFilters.isEmpty(), "rules of the removed sender must be pruned")
    }

    @Test
    fun `replace_device_apps writes the apps under the device key`() {
        val mutation = buildJsonObject {
            put(
                "operations",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("replace_device_apps"))
                            put("deviceId", JsonPrimitive(42))
                            put(
                                "apps",
                                kotlinx.serialization.json.Json.parseToJsonElement(
                                    """[{"packageName":"com.example","label":"Example"}]""",
                                ),
                            )
                        },
                    )
                },
            )
        }
        val next = applyMutationBatchToConfigRoot(DesktopConfigRoot(), mutation)
        val apps = next.deviceAppInfos["42"]
        assertEquals(1, apps?.size)
        assertEquals("com.example", apps?.first()?.packageName)
    }

    @Test
    fun `pending commands fold onto the mirror in revision order`() {
        val mirror = buildJsonObject {
            put(
                "senders",
                kotlinx.serialization.json.Json.parseToJsonElement("""[{"id":1,"name":"base"}]"""),
            )
        }
        val first = command(1, targetRevision = 2, mutation = buildReplaceSendersMutation(listOf(SnapshotSender(id = 1, name = "first"))))
        val second = command(2, targetRevision = 3, mutation = buildReplaceSendersMutation(listOf(SnapshotSender(id = 1, name = "second"))))
        val root = deriveEffectiveConfigRoot(mirror, listOf(second, first))
        assertEquals(listOf("second"), root.senders.map { it.name })
    }

    @Test
    fun `unsupported operations leave the root untouched`() {
        val mutation = buildJsonObject {
            put(
                "operations",
                buildJsonArray {
                    add(buildJsonObject { put("type", JsonPrimitive("unknown_operation")) })
                },
            )
        }
        val base = DesktopConfigRoot(senders = listOf(SnapshotSender(id = 5)))
        assertEquals(base, applyMutationBatchToConfigRoot(base, mutation))
    }

    @Test
    fun `latest effective revision accounts for pending commands`() {
        val config = io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse(
            revision = 4,
            pendingCommands = listOf(
                command(1, targetRevision = 6, mutation = buildJsonObject {}),
                command(2, targetRevision = 5, mutation = buildJsonObject {}),
            ),
        )
        assertEquals(6L, latestEffectiveRevision(config))
    }

    @Test
    fun `pending commands stay sorted by target revision when appended`() {
        val pending = appendPendingCommand(
            listOf(command(1, targetRevision = 7, mutation = buildJsonObject {})),
            command(2, targetRevision = 5, mutation = buildJsonObject {}),
        )
        assertEquals(listOf(5L, 7L), pending.map { it.targetRevision })
    }

    @Test
    fun `built mutations carry the operation payload`() {
        val mutation = buildReplaceSendersMutation(listOf(SnapshotSender(id = 3)), removedSenderIds = listOf(1, 2))
        val operation = (mutation["operations"] as kotlinx.serialization.json.JsonArray)
            .first()
            .jsonObject
        assertEquals("replace_senders", operation["type"]?.toString()?.trim('"'))
        assertEquals(2, (operation["removedSenderIds"] as kotlinx.serialization.json.JsonArray).size)

        val appsMutation = buildReplaceDeviceAppsMutation(11, listOf(SnapshotAppInfo(packageName = "com.example")))
        val appsOperation = (appsMutation["operations"] as kotlinx.serialization.json.JsonArray)
            .first()
            .jsonObject
        assertEquals("replace_device_apps", appsOperation["type"]?.toString()?.trim('"'))
        assertEquals(11L, (appsOperation["deviceId"] as JsonPrimitive).long)
    }
}
