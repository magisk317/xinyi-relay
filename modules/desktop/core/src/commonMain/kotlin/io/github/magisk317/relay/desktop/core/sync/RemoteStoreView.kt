package io.github.magisk317.relay.desktop.core.sync

import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.store.RemoteStore
import kotlinx.serialization.json.JsonElement

/**
 * Narrows the HTTP [RemoteStore] down to the view [SyncEngine] pulls from.
 *
 * The engine deliberately depends on the small [SyncEngine.RemoteView] surface
 * so its own tests stay cheap, while production holds the superset because the
 * console needs the endpoints the sync loop never touches. Both interfaces live
 * in commonMain, so the bridge itself is platform free.
 */
fun RemoteStore.asRemoteView(): SyncEngine.RemoteView = object : SyncEngine.RemoteView {

    override suspend fun listDevices(): List<Device> = this@asRemoteView.listDevices()

    override suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState? =
        this@asRemoteView.getDeviceConfig(deviceId)

    override suspend fun listRecords(limit: Int, deviceId: Long?): Paginated<Record> =
        this@asRemoteView.listRecords(limit, deviceId)

    override suspend fun queueDeviceConfigCommand(
        deviceId: Long,
        baseRevision: Long,
        summary: String,
        mutation: JsonElement,
    ): DeviceConfigCommand = this@asRemoteView.queueDeviceConfigCommand(
        deviceId = deviceId,
        baseRevision = baseRevision,
        summary = summary,
        mutation = mutation,
    )
}
