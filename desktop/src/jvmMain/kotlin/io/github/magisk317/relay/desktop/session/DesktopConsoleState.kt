package io.github.magisk317.relay.desktop.session

import io.github.magisk317.relay.contract.remote.DeviceConfigCommandRequest
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import io.github.magisk317.relay.contract.remote.DeviceItem
import io.github.magisk317.relay.desktop.config.DesktopConfigRoot
import io.github.magisk317.relay.desktop.config.appendPendingCommand
import io.github.magisk317.relay.desktop.config.deriveEffectiveConfigRoot
import io.github.magisk317.relay.desktop.config.latestEffectiveRevision
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * Console device/config state mirroring the webUI DeviceConfigProvider:
 * the device list, the selected device (persisted like
 * relay-webui-selected-device-id) and that device's config state with the
 * pending commands folded into the effective root.
 */
class DesktopConsoleState(private val session: DesktopSessionState) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = Preferences.userNodeForPackage(DesktopConsoleState::class.java)

    var devices by mutableStateOf<List<DeviceItem>>(emptyList())
        private set

    var selectedDeviceId by mutableStateOf<Long?>(null)
        private set

    var config by mutableStateOf<DeviceConfigStateResponse?>(null)
        private set

    var root by mutableStateOf(DesktopConfigRoot())
        private set

    var loading by mutableStateOf(true)
        private set

    var saving by mutableStateOf(false)
        private set

    var error by mutableStateOf("")
        private set

    fun bootstrap() {
        scope.launch {
            runCatching { refreshDevicesInternal() }
        }
    }

    suspend fun refreshDevices() {
        loading = true
        refreshDevicesInternal()
    }

    private suspend fun refreshDevicesInternal() {
        val client = session.currentClient()
        if (client == null) {
            loading = false
            return
        }
        try {
            devices = client.devices().devices
            val preferred = prefs.get(SELECTED_DEVICE_KEY, null)?.toLongOrNull()
            val next = devices.firstOrNull { it.id == preferred } ?: devices.firstOrNull()
            selectedDeviceId = next?.id
            error = ""
            refreshConfigInternal()
        } catch (failure: Exception) {
            error = failure.message ?: "Failed to load devices."
            throw failure
        } finally {
            loading = false
        }
    }

    suspend fun refreshConfig() {
        try {
            refreshConfigInternal()
            error = ""
        } catch (failure: Exception) {
            error = failure.message ?: "Failed to load device config."
            throw failure
        }
    }

    private suspend fun refreshConfigInternal() {
        val client = session.currentClient() ?: return
        val deviceId = selectedDeviceId
        if (deviceId == null) {
            config = null
            root = DesktopConfigRoot()
            return
        }
        val next = client.deviceConfig(deviceId)
        config = next
        root = deriveEffectiveConfigRoot(next?.mirrorContent, next?.pendingCommands ?: emptyList())
    }

    fun selectDevice(deviceId: Long) {
        if (selectedDeviceId == deviceId) return
        selectedDeviceId = deviceId
        prefs.put(SELECTED_DEVICE_KEY, deviceId.toString())
        prefs.flush()
        scope.launch { runCatching { refreshConfig() } }
    }

    /** Queues a config command on the selected device and folds it into the local state. */
    suspend fun queueMutation(mutation: JsonObject, summary: String) {
        val client = session.currentClient() ?: error("Device config is not loaded yet.")
        val current = config ?: error("Device config is not loaded yet.")
        val deviceId = selectedDeviceId ?: error("Device config is not loaded yet.")
        saving = true
        try {
            val request = DeviceConfigCommandRequest(
                baseRevision = latestEffectiveRevision(current),
                summary = summary,
                mutation = mutation,
            )
            val command = client.queueDeviceConfigCommand(deviceId, request)
            val merged = current.copy(
                pendingCommands = appendPendingCommand(current.pendingCommands, command),
            )
            config = merged
            root = deriveEffectiveConfigRoot(merged.mirrorContent, merged.pendingCommands)
            error = ""
        } catch (failure: Exception) {
            error = failure.message ?: "Failed to queue device config command."
            throw failure
        } finally {
            saving = false
        }
    }

    private companion object {
        const val SELECTED_DEVICE_KEY = "relay-webui-selected-device-id"
    }
}
