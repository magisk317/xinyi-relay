package io.github.magisk317.relay.desktop.core.store

import io.github.magisk317.relay.desktop.core.model.BindCode
import io.github.magisk317.relay.desktop.core.model.COMMAND_STATUS_APPLIED
import io.github.magisk317.relay.desktop.core.model.COMMAND_STATUS_FAILED
import io.github.magisk317.relay.desktop.core.model.COMMAND_STATUS_PENDING
import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigAuditLog
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.model.RecordSyncResult
import io.github.magisk317.relay.desktop.core.model.SystemInfo
import io.github.magisk317.relay.desktop.data.DesktopDatabase
import io.github.magisk317.relay.desktop.data.dao.RelayRecordIdEvent
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigAuditLogEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigCommandEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceConfigMirrorEntity
import io.github.magisk317.relay.desktop.data.entity.DeviceEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceBindCodeEntity
import io.github.magisk317.relay.desktop.data.entity.LocalDeviceTokenEntity
import io.github.magisk317.relay.desktop.data.entity.RelayRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Kotlin port of the Rust `SqliteStore`.
 *
 * Behaviour differences against the Rust original are deliberate and listed
 * here so parity testing can target them explicitly:
 *
 * 1. Queued commands whose base revision falls behind the mirror are dropped
 *    when the mirror advances, instead of lingering in the table forever.
 * 2. `user_id` is written as `1` on every local write path; the Rust store used
 *    `1` for registration and record sync but `0` for remote upserts.
 * 3. Device ids are allocated with `MAX(id) + 1`, unchanged from the original.
 */
class DesktopLocalStore(
    private val database: DesktopDatabase,
    private val clock: Clock,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Store, LocalDeviceRegistry {

    private val devices = database.deviceDao()
    private val configs = database.deviceConfigDao()
    private val records = database.relayRecordDao()
    private val localDevices = database.localDeviceDao()

    private val emptyObject: JsonElement = JsonObject(emptyMap<String, JsonElement>())

    private val bindCodeTtlMillis: Long = 10 * 60 * 1000L

    // ------------------------------------------------------------------ config

    override suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState {
        val mirror = configs.findMirror(deviceId)
        return DeviceConfigState(
            deviceId = deviceId,
            revision = mirror?.revision ?: 0L,
            snapshot = decode(mirror?.snapshot),
            pendingCommands = configs.listPendingCommands(deviceId).map { it.toModel() },
            updatedAt = mirror?.updatedAt,
        )
    }

    override suspend fun upsertDeviceConfigMirror(
        deviceId: Long,
        revision: Long,
        snapshot: JsonElement,
        updatedAt: String?,
    ): DeviceConfigState {
        val now = updatedAt ?: clock.nowRfc3339()
        configs.dropStalePendingCommands(deviceId, revision)
        configs.upsertMirror(
            DeviceConfigMirrorEntity(
                deviceId = deviceId,
                revision = revision,
                snapshot = encode(snapshot),
                updatedAt = now,
            ),
        )
        return DeviceConfigState(
            deviceId = deviceId,
            revision = revision,
            snapshot = snapshot,
            pendingCommands = configs.listPendingCommands(deviceId).map { it.toModel() },
            updatedAt = now,
        )
    }

    override suspend fun queueDeviceConfigCommand(
        deviceId: Long,
        baseRevision: Long,
        summary: String,
        mutation: JsonElement,
    ): DeviceConfigCommand {
        val mirrorRevision = configs.findMirror(deviceId)?.revision ?: 0L
        val latestPendingTarget = configs.latestPendingTargetRevision(deviceId)
        val expectedBase = maxOf(mirrorRevision, latestPendingTarget)
        if (expectedBase != baseRevision) {
            throw StoreError.Conflict(local = expectedBase, remote = baseRevision)
        }
        val targetRevision = expectedBase + 1
        val now = clock.nowRfc3339()
        val commandId = configs.insertCommand(
            DeviceConfigCommandEntity(
                deviceId = deviceId,
                baseRevision = baseRevision,
                targetRevision = targetRevision,
                mutation = encode(mutation),
                summary = summary,
                createdAt = now,
                updatedAt = now,
            ),
        )
        configs.insertAuditLog(
            DeviceConfigAuditLogEntity(
                deviceId = deviceId,
                commandId = commandId,
                revision = targetRevision,
                eventType = "command.queued",
                summary = summary,
                createdAt = now,
            ),
        )
        return DeviceConfigCommand(
            id = commandId,
            baseRevision = baseRevision,
            targetRevision = targetRevision,
            mutation = mutation,
            summary = summary,
            status = COMMAND_STATUS_PENDING,
            createdAt = now,
            updatedAt = now,
        )
    }

    override suspend fun listDeviceConfigAuditLogs(
        deviceId: Long,
        limit: Int,
        offset: Int,
    ): Paginated<DeviceConfigAuditLog> {
        val logs = configs.listAuditLogs(deviceId, limit, offset).map { it.toModel() }
        return Paginated(items = logs, limit = limit, offset = offset)
    }

    override suspend fun replaceDeviceConfigPendingCommands(
        deviceId: Long,
        commands: List<DeviceConfigCommand>,
    ) {
        configs.replacePendingCommands(deviceId, commands.map { it.toEntity(deviceId) })
    }

    override suspend fun clearDeviceConfigPendingCommands(deviceId: Long) {
        configs.clearPendingCommands(deviceId)
    }

    // ----------------------------------------------------------------- devices

    override suspend fun listDevices(): List<Device> = devices.listAll().map { it.toModel() }

    override suspend fun patchDevice(deviceId: Long, displayName: String?, enabled: Boolean?) {
        val device = devices.findById(deviceId) ?: return
        devices.update(
            device.copy(
                displayName = displayName ?: device.displayName,
                enabled = enabled ?: device.enabled,
                updatedAt = clock.nowRfc3339(),
            ),
        )
    }

    override suspend fun revokeDevice(deviceId: Long) {
        val now = clock.nowRfc3339()
        devices.revoke(deviceId, now)
        localDevices.revokeToken(deviceId, now)
    }

    override suspend fun upsertDevices(items: List<Device>) {
        devices.upsertAll(items.map { it.toEntity() })
    }

    // ----------------------------------------------------------------- records

    override suspend fun listRecords(limit: Int, deviceId: Long?): Paginated<Record> {
        val items = if (deviceId == null) {
            records.listPage(limit, 0)
        } else {
            records.listPageForDevice(deviceId, limit, 0)
        }
        return Paginated(items = items.map { it.toModel() }, limit = limit, offset = 0)
    }

    override suspend fun getRecord(recordId: Long): Record? = records.findById(recordId)?.toModel()

    override suspend fun upsertRecords(items: List<Record>) {
        records.upsertAll(items.map { it.toEntity() })
    }

    // ------------------------------------------------------------------ system

    override suspend fun getSystemInfo(): SystemInfo =
        SystemInfo(
            service = "xinyi-relay-desktop",
            appEnv = "desktop",
            localBaseUrl = "local://sqlite",
            userCount = devices.countActive().toLong(),
            databaseReady = true,
            time = clock.nowRfc3339(),
        )

    // -------------------------------------------------------- local device API

    override suspend fun createBindCode(): BindCode {
        val now = clock.nowMillis()
        localDevices.purgeExpiredBindCodes(clock.toRfc3339(now))
        val code = randomUuid()
        val expiresAt = clock.toRfc3339(now + bindCodeTtlMillis)
        localDevices.insertBindCode(
            LocalDeviceBindCodeEntity(
                codeHash = sha256Hex(code),
                expiresAt = expiresAt,
            ),
        )
        return BindCode(code = code, expiresAt = expiresAt)
    }

    override suspend fun registerLocalDevice(
        bindCode: String,
        deviceName: String,
        deviceModel: String,
        platform: String,
        appVersion: String,
        deviceToken: String,
    ): Device? {
        val now = clock.nowRfc3339()
        if (localDevices.consumeBindCode(sha256Hex(bindCode), now) != 1) {
            return null
        }
        val deviceId = devices.nextDeviceId()
        val device = DeviceEntity(
            id = deviceId,
            userId = 1L,
            deviceName = deviceName,
            deviceModel = deviceModel,
            platform = platform,
            appVersion = appVersion,
            displayName = deviceName,
            createdAt = now,
            updatedAt = now,
        )
        devices.insert(device)
        localDevices.insertToken(
            LocalDeviceTokenEntity(
                deviceId = deviceId,
                tokenHash = sha256Hex(deviceToken),
            ),
        )
        return device.toModel()
    }

    override suspend fun authenticateLocalDevice(token: String): Long? =
        localDevices.authenticate(sha256Hex(token))

    override suspend fun updateLocalDeviceHeartbeat(
        deviceId: Long,
        appVersion: String,
        localAddresses: JsonElement,
        capabilities: JsonElement,
    ) {
        devices.applyHeartbeat(
            deviceId = deviceId,
            appVersion = appVersion,
            localAddresses = encode(localAddresses),
            capabilities = encode(capabilities),
            now = clock.nowRfc3339(),
        )
    }

    override suspend fun ackLocalDeviceConfigCommand(
        deviceId: Long,
        commandId: Long,
        status: String,
        appliedRevision: Long,
        failureReason: String,
        snapshot: JsonElement,
    ): DeviceConfigCommand {
        if (status != COMMAND_STATUS_APPLIED && status != COMMAND_STATUS_FAILED) {
            throw StoreError.Internal("unsupported command status: $status")
        }
        val now = clock.nowRfc3339()
        val changed = configs.ackCommand(
            deviceId = deviceId,
            commandId = commandId,
            status = status,
            failureReason = failureReason,
            now = now,
            appliedAt = if (status == COMMAND_STATUS_APPLIED) now else null,
        )
        if (changed != 1) {
            throw StoreError.Conflict(local = commandId, remote = commandId)
        }
        if (status == COMMAND_STATUS_APPLIED) {
            configs.upsertMirror(
                DeviceConfigMirrorEntity(
                    deviceId = deviceId,
                    revision = appliedRevision,
                    snapshot = encode(snapshot),
                    updatedAt = now,
                ),
            )
            configs.dropStalePendingCommands(deviceId, appliedRevision)
        }
        return configs.findCommand(deviceId, commandId)?.toModel()
            ?: throw StoreError.Internal("command $commandId disappeared after ack")
    }

    override suspend fun syncLocalDeviceRecords(
        deviceId: Long,
        incoming: List<Record>,
        replaceExisting: Boolean,
    ): RecordSyncResult {
        val knownEventIds = records.listEventIds(deviceId).toMutableSet()
        val incomingEventIds = incoming.mapNotNull { it.eventId }.toSet()

        var deleted = 0
        if (replaceExisting) {
            val staleIds = records.listIdEventIdPairs(deviceId)
                .filter { pair -> pair.eventId == null || pair.eventId !in incomingEventIds }
                .map(RelayRecordIdEvent::id)
            staleIds.forEach { records.deleteById(it) }
            deleted = staleIds.size
        }

        var inserted = 0
        var updated = 0
        incoming.forEach { record ->
            val existingId = record.eventId?.let { eventId ->
                knownEventIds.takeIf { eventId in it }?.let {
                    records.listIdEventIdPairs(deviceId).firstOrNull { pair -> pair.eventId == eventId }?.id
                }
            }
            val wasExisting = existingId != null
            if (existingId == null) {
                records.insert(record.toEntity(deviceId = deviceId, id = 0L))
            } else {
                records.update(record.toEntity(deviceId = deviceId, id = existingId))
            }
            if (wasExisting) {
                updated += 1
            } else {
                inserted += 1
            }
            if (record.eventId != null) {
                knownEventIds.add(record.eventId)
            }
        }
        return RecordSyncResult(inserted = inserted, updated = updated, deleted = deleted)
    }

    // ---------------------------------------------------------------- helpers

    private fun encode(value: JsonElement): String =
        runCatching { json.encodeToString(value) }.getOrDefault("{}")

    private fun decode(value: String?): JsonElement {
        if (value.isNullOrBlank()) return emptyObject
        return runCatching { json.parseToJsonElement(value) }.getOrDefault(emptyObject)
    }

    private fun DeviceEntity.toModel(): Device =
        Device(
            id = id,
            userId = userId,
            deviceName = deviceName,
            deviceModel = deviceModel,
            platform = platform,
            appVersion = appVersion,
            displayName = displayName,
            enabled = enabled,
            revokedAt = revokedAt,
            lastSeenAt = lastSeenAt,
            localAddresses = decode(localAddresses),
            capabilities = decode(capabilities),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun Device.toEntity(): DeviceEntity =
        DeviceEntity(
            id = id,
            userId = userId,
            deviceName = deviceName,
            deviceModel = deviceModel,
            platform = platform,
            appVersion = appVersion,
            displayName = displayName,
            enabled = enabled,
            revokedAt = revokedAt,
            lastSeenAt = lastSeenAt,
            localAddresses = encode(localAddresses),
            capabilities = encode(capabilities),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun DeviceConfigCommandEntity.toModel(): DeviceConfigCommand =
        DeviceConfigCommand(
            id = id,
            baseRevision = baseRevision,
            targetRevision = targetRevision,
            mutation = decode(mutation),
            summary = summary,
            actorType = actorType,
            actorId = actorId,
            status = status,
            failureReason = failureReason,
            createdAt = createdAt,
            updatedAt = updatedAt,
            appliedAt = appliedAt,
        )

    private fun DeviceConfigCommand.toEntity(deviceId: Long): DeviceConfigCommandEntity =
        DeviceConfigCommandEntity(
            id = 0L,
            deviceId = deviceId,
            baseRevision = baseRevision,
            targetRevision = targetRevision,
            mutation = encode(mutation),
            summary = summary,
            actorType = actorType,
            actorId = actorId,
            status = status,
            failureReason = failureReason,
            createdAt = createdAt,
            updatedAt = updatedAt,
            appliedAt = appliedAt,
        )

    private fun DeviceConfigAuditLogEntity.toModel(): DeviceConfigAuditLog =
        DeviceConfigAuditLog(
            id = id,
            deviceId = deviceId,
            commandId = commandId,
            revision = revision,
            eventType = eventType,
            actorType = actorType,
            actorId = actorId,
            summary = summary,
            createdAt = createdAt,
        )

    private fun RelayRecordEntity.toModel(): Record =
        Record(
            id = id,
            deviceId = deviceId,
            eventId = eventId,
            recordType = recordType,
            sender = sender,
            body = body,
            smsCode = smsCode,
            packageName = packageName,
            metadata = decode(metadata),
            msgType = msgType.toInt(),
            callType = callType.toInt(),
            occurredAt = occurredAt,
            uploadedAt = uploadedAt,
        )

    private fun Record.toEntity(
        deviceId: Long = this.deviceId,
        id: Long = this.id,
    ): RelayRecordEntity =
        RelayRecordEntity(
            id = id,
            userId = 1L,
            deviceId = deviceId,
            eventId = eventId,
            recordType = recordType,
            sender = sender,
            body = body,
            smsCode = smsCode,
            packageName = packageName,
            msgType = msgType.toLong(),
            callType = callType.toLong(),
            occurredAt = occurredAt,
            uploadedAt = uploadedAt,
            metadata = encode(metadata),
        )
}
