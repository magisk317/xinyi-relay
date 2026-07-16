package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.datasource.PreferenceWriteScope
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.contract.model.LocalConfigMirror
import io.github.magisk317.relay.contract.model.LocalConfigRevision
import io.github.magisk317.relay.contract.model.LocalDirtyState
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsAckRequest
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsPullRequest
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsPullResponse
import io.github.magisk317.relay.contract.remote.AgentConfigMirrorRequest
import io.github.magisk317.relay.contract.remote.AgentRegisterRequest
import io.github.magisk317.relay.contract.remote.AgentRegisterResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import io.github.magisk317.relay.contract.remote.HeartbeatRequest
import io.github.magisk317.relay.contract.remote.RelayRecordsBatchRequest
import io.github.magisk317.relay.contract.remote.RelayRecordWire
import io.github.magisk317.relay.contract.repository.LocalConfigRepository
import io.github.magisk317.relay.android.data.secret.InternalSecretStore
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.data.remote.RemoteAgentApi
import io.github.magisk317.relay.data.remote.DeviceTokenExpiredException
import io.github.magisk317.relay.testing.relaxedContext
import io.github.magisk317.smscode.runtime.common.prefs.PreferenceChange
import io.github.magisk317.smscode.runtime.common.prefs.PreferenceChangeSet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RemoteAgentRepositoryTest {

    @BeforeEach
    fun setup() {
        mockkObject(InternalSecretStore)
        mockkObject(HookPreferenceMirror)
        every { InternalSecretStore.putString(any(), any(), any()) } returns Unit
        every { InternalSecretStore.getString(any(), any(), any()) } returns "device-token"
        coEvery { HookPreferenceMirror.publish(any()) } returns true
    }

    @AfterEach
    fun teardown() {
        unmockkObject(InternalSecretStore)
        unmockkObject(HookPreferenceMirror)
    }

    @Test
    fun `clearBinding clears security and tracking caches`() = runBlocking {
        val context = relaxedContext()
        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        val localConfigRepository = mockk<LocalConfigRepository>(relaxed = true)
        val repository = RemoteAgentRepository(context, preferences, localConfigRepository)

        repository.clearBinding()

        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_USER_ID, "0") }
        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_DEVICE_ID, "0") }
        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_DEVICE_APP_INFOS, "") }
        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, "") }
        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_LAST_RECORD_SNAPSHOT_DIGEST, "") }
        coVerify { InternalSecretStore.putString(context, RelayPrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "") }
        coVerify { HookPreferenceMirror.publish(context) }
    }

    @Test
    fun `record snapshot digest is stable and includes record content`() {
        val base = RelayRecordWire(
            eventId = "local-record-1",
            recordType = "sms_code",
            sender = "10086",
            body = "code 123456",
            smsCode = "123456",
            packageName = "com.android.mms",
            msgType = 0,
            callType = 0,
            occurredAt = "2026-07-16T00:00:00Z",
            metadata = JsonObject(emptyMap()),
        )

        assertEquals(computeRecordSnapshotDigest(listOf(base)), computeRecordSnapshotDigest(listOf(base)))
        assertFalse(
            computeRecordSnapshotDigest(listOf(base)) ==
                computeRecordSnapshotDigest(listOf(base.copy(body = "code 654321"))),
        )
        assertFalse(computeRecordSnapshotDigest(emptyList()) == computeRecordSnapshotDigest(listOf(base)))
    }

    @Test
    fun `record snapshot rejects an oversized set instead of truncating a replace`() {
        val record = RelayRecordWire(
            eventId = "local-record-1",
            recordType = "sms_plain",
            sender = "sender",
            body = "body",
            smsCode = "",
            packageName = "",
            msgType = 0,
            callType = 0,
            occurredAt = "2026-07-16T00:00:00Z",
            metadata = JsonObject(emptyMap()),
        )

        val error = assertThrows<IllegalArgumentException> {
            computeRecordSnapshotDigest(List(201) { index -> record.copy(eventId = "local-record-$index") })
        }
        assertTrue(error.message.orEmpty().contains("nothing uploaded"))
    }

    @Test
    fun `record snapshot rejects an oversized payload before networking`() {
        val record = RelayRecordWire(
            eventId = "local-record-1",
            recordType = "sms_plain",
            sender = "sender",
            body = "x".repeat(8 * 1024 * 1024),
            smsCode = "",
            packageName = "",
            msgType = 0,
            callType = 0,
            occurredAt = "2026-07-16T00:00:00Z",
            metadata = JsonObject(emptyMap()),
        )

        val error = assertThrows<IllegalArgumentException> {
            computeRecordSnapshotDigest(listOf(record))
        }
        assertTrue(error.message.orEmpty().contains("nothing uploaded"))
    }

    @Test
    fun `record upload uses caller window and caps oversized requests`() {
        assertEquals(100, normalizeRecordSnapshotLimit(100))
        assertEquals(200, normalizeRecordSnapshotLimit(200))
        assertEquals(200, normalizeRecordSnapshotLimit(500))
        assertThrows<IllegalArgumentException> { normalizeRecordSnapshotLimit(0) }
    }

    @Test
    fun `pullPendingCommands imports bootstrap mirror only for clean zero revision local config`() = runBlocking {
        val preferences = remotePreferences()
        val remoteMirror = jsonObject(
            """
            {
              "general": {"moduleEnabled": false},
              "senders": [{"id": 9}]
            }
            """,
        )
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 0,
            pendingLocalChanges = 0,
            content = baseMirror(),
        )
        val api = FakeRemoteAgentApi(
            pullResponse = AgentConfigCommandsPullResponse(
                deviceId = 42,
                revision = 7,
                mirrorContent = remoteMirror,
            ),
        )
        val repository = repository(preferences, localConfigRepository, api)

        val mirror = repository.pullPendingCommands()

        assertEquals(remoteMirror, mirror.content)
        assertEquals(7L, mirror.revision.value)
        assertEquals(listOf(AppliedMirror(remoteMirror, 7, "bootstrap_import")), localConfigRepository.appliedMirrors)
        assertEquals(listOf(AgentConfigCommandsPullRequest(localRevision = 0)), api.pullRequests)
        assertTrue(api.ackRequests.isEmpty())
        assertEquals("idle", preferences.getString(RelayPrefConst.KEY_REMOTE_AGENT_SYNC_STATE, ""))
        assertTrue(preferences.getString(RelayPrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, "0").toLong() > 0L)
    }

    @Test
    fun `pullPendingCommands keeps dirty zero revision local config instead of importing bootstrap mirror`() = runBlocking {
        val localMirror = baseMirror()
        val remoteMirror = jsonObject("""{"senders": [{"id": 99}]}""")
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 0,
            pendingLocalChanges = 2,
            content = localMirror,
        )
        val api = FakeRemoteAgentApi(
            pullResponse = AgentConfigCommandsPullResponse(
                deviceId = 42,
                revision = 8,
                mirrorContent = remoteMirror,
            ),
        )
        val repository = repository(remotePreferences(), localConfigRepository, api)

        val mirror = repository.pullPendingCommands()

        assertEquals(localMirror, mirror.content)
        assertEquals(0L, mirror.revision.value)
        assertTrue(mirror.dirtyState.dirty)
        assertTrue(localConfigRepository.appliedMirrors.isEmpty())
        assertEquals(listOf(AgentConfigCommandsPullRequest(localRevision = 0)), api.pullRequests)
        assertTrue(api.ackRequests.isEmpty())
    }

    @Test
    fun `pullPendingCommands keeps existing local revision instead of importing bootstrap mirror`() = runBlocking {
        val localMirror = baseMirror()
        val remoteMirror = jsonObject("""{"senders": [{"id": 99}]}""")
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 3,
            pendingLocalChanges = 0,
            content = localMirror,
        )
        val api = FakeRemoteAgentApi(
            pullResponse = AgentConfigCommandsPullResponse(
                deviceId = 42,
                revision = 8,
                mirrorContent = remoteMirror,
            ),
        )
        val repository = repository(remotePreferences(), localConfigRepository, api)

        val mirror = repository.pullPendingCommands()

        assertEquals(localMirror, mirror.content)
        assertEquals(3L, mirror.revision.value)
        assertFalse(mirror.dirtyState.dirty)
        assertTrue(localConfigRepository.appliedMirrors.isEmpty())
        assertEquals(listOf(AgentConfigCommandsPullRequest(localRevision = 3)), api.pullRequests)
        assertTrue(api.ackRequests.isEmpty())
    }

    @Test
    fun `pullPendingCommands applies supported command and acks new revision with updated mirror`() = runBlocking {
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 4,
            pendingLocalChanges = 0,
            content = baseMirror(),
        )
        val command = DeviceConfigCommandResponse(
            id = 10,
            baseRevision = 4,
            targetRevision = 5,
            mutation = jsonObject(
                """
                {
                  "operations": [
                    {
                      "type": "replace_senders",
                      "senders": [{"id": 2}],
                      "removedSenderIds": [1]
                    }
                  ]
                }
                """,
            ),
        )
        val api = FakeRemoteAgentApi(
            pullResponse = AgentConfigCommandsPullResponse(
                deviceId = 42,
                revision = 4,
                pendingCommands = listOf(command),
            ),
        )
        val repository = repository(remotePreferences(), localConfigRepository, api)

        val mirror = repository.pullPendingCommands()

        assertEquals(5L, mirror.revision.value)
        assertEquals("2", mirror.content.getValue("senders").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(1, mirror.content.getValue("rules").jsonArray.size)
        assertFalse(mirror.content.getValue("rules").toString().contains("\"senderId\":1"))
        assertEquals(1, localConfigRepository.appliedMirrors.size)
        assertEquals("command_10", localConfigRepository.appliedMirrors.single().source)
        assertEquals(1, api.ackRequests.size)
        val ack = api.ackRequests.single()
        assertEquals(10L, ack.commandId)
        assertEquals("applied", ack.status)
        assertEquals(5L, ack.appliedRevision)
        assertEquals("", ack.failureReason)
        assertEquals(mirror.content, ack.mirrorContent)
    }

    @Test
    fun `pullPendingCommands fails unsupported command without clobbering local mirror`() = runBlocking {
        val localMirror = baseMirror()
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 4,
            pendingLocalChanges = 0,
            content = localMirror,
        )
        val command = DeviceConfigCommandResponse(
            id = 11,
            baseRevision = 4,
            targetRevision = 5,
            mutation = jsonObject(
                """
                {
                  "operations": [
                    {
                      "type": "replace_root",
                      "snapshot": {"senders": []}
                    }
                  ]
                }
                """,
            ),
        )
        val api = FakeRemoteAgentApi(
            pullResponse = AgentConfigCommandsPullResponse(
                deviceId = 42,
                revision = 4,
                pendingCommands = listOf(command),
            ),
        )
        val repository = repository(remotePreferences(), localConfigRepository, api)

        val mirror = repository.pullPendingCommands()

        assertEquals(localMirror, mirror.content)
        assertEquals(4L, mirror.revision.value)
        assertTrue(localConfigRepository.appliedMirrors.isEmpty())
        assertEquals(1, api.ackRequests.size)
        val ack = api.ackRequests.single()
        assertEquals(11L, ack.commandId)
        assertEquals("failed", ack.status)
        assertEquals(4L, ack.appliedRevision)
        assertEquals("unsupported_mutation", ack.failureReason)
        assertEquals(localMirror, ack.mirrorContent)
    }

    @Test
    fun `pullPendingCommands fails command when local config is dirty`() = runBlocking {
        val localMirror = baseMirror()
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 4,
            pendingLocalChanges = 1,
            content = localMirror,
        )
        val command = DeviceConfigCommandResponse(
            id = 12,
            baseRevision = 4,
            targetRevision = 5,
            mutation = jsonObject(
                """
                {
                  "operations": [
                    {
                      "type": "replace_senders",
                      "senders": [{"id": 2}]
                    }
                  ]
                }
                """,
            ),
        )
        val api = FakeRemoteAgentApi(
            pullResponse = AgentConfigCommandsPullResponse(
                deviceId = 42,
                revision = 4,
                pendingCommands = listOf(command),
            ),
        )
        val repository = repository(remotePreferences(), localConfigRepository, api)

        val mirror = repository.pullPendingCommands()

        assertEquals(localMirror, mirror.content)
        assertEquals(4L, mirror.revision.value)
        assertTrue(localConfigRepository.appliedMirrors.isEmpty())
        val ack = api.ackRequests.single()
        assertEquals(12L, ack.commandId)
        assertEquals("failed", ack.status)
        assertEquals(4L, ack.appliedRevision)
        assertEquals("local_dirty", ack.failureReason)
        assertEquals(localMirror, ack.mirrorContent)
    }

    @Test
    fun `pullPendingCommands fails command when target revision is not newer than local`() = runBlocking {
        val localMirror = baseMirror()
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 5,
            pendingLocalChanges = 0,
            content = localMirror,
        )
        val command = DeviceConfigCommandResponse(
            id = 13,
            baseRevision = 4,
            targetRevision = 5,
            mutation = jsonObject(
                """
                {
                  "operations": [
                    {
                      "type": "replace_senders",
                      "senders": [{"id": 2}]
                    }
                  ]
                }
                """,
            ),
        )
        val api = FakeRemoteAgentApi(
            pullResponse = AgentConfigCommandsPullResponse(
                deviceId = 42,
                revision = 5,
                pendingCommands = listOf(command),
            ),
        )
        val repository = repository(remotePreferences(), localConfigRepository, api)

        val mirror = repository.pullPendingCommands()

        assertEquals(localMirror, mirror.content)
        assertEquals(5L, mirror.revision.value)
        assertTrue(localConfigRepository.appliedMirrors.isEmpty())
        val ack = api.ackRequests.single()
        assertEquals(13L, ack.commandId)
        assertEquals("failed", ack.status)
        assertEquals(5L, ack.appliedRevision)
        assertEquals("stale_local_revision", ack.failureReason)
        assertEquals(localMirror, ack.mirrorContent)
    }

    @Test
    fun `pushLocalMirror preserves token expired sync state`() = runBlocking {
        val preferences = remotePreferences()
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 4,
            pendingLocalChanges = 1,
            content = baseMirror(),
        )
        val repository = repository(
            preferences = preferences,
            localConfigRepository = localConfigRepository,
            remoteApi = FakeRemoteAgentApi(pushError = DeviceTokenExpiredException("expired")),
        )

        val error = runCatching { repository.pushLocalMirror() }.exceptionOrNull()

        assertTrue(error is DeviceTokenExpiredException)
        assertEquals("token_expired", preferences.getString(RelayPrefConst.KEY_REMOTE_AGENT_SYNC_STATE, ""))
        assertEquals("设备令牌已过期，请重新绑定设备", preferences.getString(RelayPrefConst.KEY_REMOTE_AGENT_LAST_ERROR, ""))
    }

    @Test
    fun `pullPendingCommands preserves token expired sync state`() = runBlocking {
        val preferences = remotePreferences()
        val localConfigRepository = FakeLocalConfigRepository(
            revision = 4,
            pendingLocalChanges = 0,
            content = baseMirror(),
        )
        val repository = repository(
            preferences = preferences,
            localConfigRepository = localConfigRepository,
            remoteApi = FakeRemoteAgentApi(pullError = DeviceTokenExpiredException("expired")),
        )

        val error = runCatching { repository.pullPendingCommands() }.exceptionOrNull()

        assertTrue(error is DeviceTokenExpiredException)
        assertEquals("token_expired", preferences.getString(RelayPrefConst.KEY_REMOTE_AGENT_SYNC_STATE, ""))
        assertEquals("设备令牌已过期，请重新绑定设备", preferences.getString(RelayPrefConst.KEY_REMOTE_AGENT_LAST_ERROR, ""))
        assertTrue(localConfigRepository.appliedMirrors.isEmpty())
    }


    private fun repository(
        preferences: PreferenceDataSource,
        localConfigRepository: LocalConfigRepository,
        remoteApi: RemoteAgentApi,
    ): RemoteAgentRepository {
        return RemoteAgentRepository(
            appContext = relaxedContext(),
            preferenceDataSource = preferences,
            localConfigRepository = localConfigRepository,
            remoteApiClient = remoteApi,
        )
    }

    private fun remotePreferences(): FakeRemotePreferenceDataSource {
        return FakeRemotePreferenceDataSource(
            strings = mutableMapOf(
                RelayPrefConst.KEY_REMOTE_AGENT_BASE_URL to "https://relay.example.test",
                RelayPrefConst.KEY_REMOTE_AGENT_USER_ID to "7",
                RelayPrefConst.KEY_REMOTE_AGENT_DEVICE_ID to "42",
                RelayPrefConst.KEY_REMOTE_AGENT_SYNC_STATE to "idle",
            ),
        )
    }

    private fun baseMirror(): JsonObject {
        return jsonObject(
            """
            {
              "senders": [{"id": 1}, {"id": 2}],
              "rules": [{"senderId": 1}, {"senderId": 2}],
              "notifyRoutes": [{"senderId": 1}, {"senderId": 2}],
              "forwardFilters": [{"senderId": 1}, {"senderId": 2}],
              "deviceAppInfos": {
                "42": [{"packageName": "base.app"}]
              }
            }
            """,
        )
    }

    private fun jsonObject(content: String): JsonObject {
        return RelayJson.parseElement(content.trimIndent()).jsonObject
    }
}

private data class AppliedMirror(
    val content: JsonObject,
    val revision: Long,
    val source: String,
)

private class FakeLocalConfigRepository(
    revision: Long,
    private var pendingLocalChanges: Int,
    private var content: JsonObject,
) : LocalConfigRepository {
    private var revision = revision
    val appliedMirrors = mutableListOf<AppliedMirror>()

    override suspend fun exportMirror(): LocalConfigMirror = mirror()

    override suspend fun getRevision(): LocalConfigRevision = LocalConfigRevision(revision)

    override suspend fun getDirtyState(): LocalDirtyState = dirtyState()

    override suspend fun noteLocalMutation(source: String): LocalDirtyState {
        revision += 1
        pendingLocalChanges += 1
        return dirtyState()
    }

    override suspend fun markMirrorSynced(revision: Long): LocalDirtyState {
        this.revision = maxOf(this.revision, revision)
        pendingLocalChanges = 0
        return dirtyState()
    }

    override suspend fun applyMirror(
        mirrorContent: JsonObject,
        revision: Long,
        source: String,
    ): LocalConfigMirror {
        content = mirrorContent
        this.revision = revision
        pendingLocalChanges = 0
        appliedMirrors += AppliedMirror(mirrorContent, revision, source)
        return mirror()
    }

    override fun observeMutationSources(): Flow<String> = emptyFlow()

    private fun mirror(): LocalConfigMirror {
        return LocalConfigMirror(
            revision = LocalConfigRevision(revision),
            dirtyState = dirtyState(),
            content = content,
        )
    }

    private fun dirtyState(): LocalDirtyState {
        val localRevision = LocalConfigRevision(revision)
        return LocalDirtyState(
            revision = localRevision,
            pendingLocalChanges = pendingLocalChanges,
            dirty = pendingLocalChanges > 0,
        )
    }
}

private class FakeRemoteAgentApi(
    private val pullResponse: AgentConfigCommandsPullResponse = AgentConfigCommandsPullResponse(),
    private val pullError: RuntimeException? = null,
    private val pushError: RuntimeException? = null,
) : RemoteAgentApi {
    val pullRequests = mutableListOf<AgentConfigCommandsPullRequest>()
    val ackRequests = mutableListOf<AgentConfigCommandsAckRequest>()

    override fun registerDevice(baseUrl: String, request: AgentRegisterRequest): AgentRegisterResponse {
        throw UnsupportedOperationException("registerDevice is not used in this test")
    }

    override fun sendHeartbeat(
        baseUrl: String,
        deviceToken: String,
        request: HeartbeatRequest,
    ) {
        throw UnsupportedOperationException("sendHeartbeat is not used in this test")
    }

    override fun pushConfigMirror(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigMirrorRequest,
    ): DeviceConfigStateResponse {
        pushError?.let { throw it }
        return DeviceConfigStateResponse(
            deviceId = 42,
            revision = request.localRevision,
            mirrorContent = request.mirrorContent,
        )
    }

    override fun pullConfigCommands(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigCommandsPullRequest,
    ): AgentConfigCommandsPullResponse {
        pullRequests += request
        pullError?.let { throw it }
        return pullResponse
    }

    override fun ackConfigCommand(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigCommandsAckRequest,
    ): DeviceConfigCommandResponse {
        ackRequests += request
        return DeviceConfigCommandResponse(
            id = request.commandId,
            targetRevision = request.appliedRevision,
            mutation = JsonObject(emptyMap()),
            status = request.status,
            failureReason = request.failureReason,
        )
    }

    override fun uploadRelayRecords(
        baseUrl: String,
        deviceToken: String,
        request: RelayRecordsBatchRequest,
    ) {
        throw UnsupportedOperationException("uploadRelayRecords is not used in this test")
    }
}

private class FakeRemotePreferenceDataSource(
    private val booleans: MutableMap<String, Boolean> = mutableMapOf(),
    private val strings: MutableMap<String, String> = mutableMapOf(),
    private val ints: MutableMap<String, Int> = mutableMapOf(),
    private val floats: MutableMap<String, Float> = mutableMapOf(),
) : PreferenceDataSource {
    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue

    override suspend fun setBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }

    override suspend fun getString(key: String, defaultValue: String): String = strings[key] ?: defaultValue

    override suspend fun setString(key: String, value: String) {
        strings[key] = value
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int = ints[key] ?: defaultValue

    override suspend fun setInt(key: String, value: Int) {
        ints[key] = value
    }

    override suspend fun getFloat(key: String, defaultValue: Float): Float = floats[key] ?: defaultValue

    override suspend fun setFloat(key: String, value: Float) {
        floats[key] = value
    }

    override suspend fun batchEdit(block: suspend PreferenceWriteScope.() -> Unit) {
        val scope = object : PreferenceWriteScope {
            override suspend fun setBoolean(key: String, value: Boolean) {
                booleans[key] = value
            }

            override suspend fun setString(key: String, value: String) {
                strings[key] = value
            }

            override suspend fun setInt(key: String, value: Int) {
                ints[key] = value
            }

            override suspend fun setFloat(key: String, value: Float) {
                floats[key] = value
            }
        }
        scope.block()
    }

    override suspend fun persist(changes: PreferenceChangeSet): Boolean {
        changes.changes.forEach { change ->
            when (change) {
                is PreferenceChange.PutBoolean -> booleans[change.key] = change.value
                is PreferenceChange.PutString -> strings[change.key] = change.value
                is PreferenceChange.PutInt -> ints[change.key] = change.value
                is PreferenceChange.PutFloat -> floats[change.key] = change.value
                is PreferenceChange.Remove -> {
                    booleans.remove(change.key)
                    strings.remove(change.key)
                    ints.remove(change.key)
                    floats.remove(change.key)
                }
            }
        }
        return true
    }

    override fun getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean> = emptyFlow()

    override fun getStringFlow(key: String, defaultValue: String): Flow<String> = emptyFlow()

    override fun getIntFlow(key: String, defaultValue: Int): Flow<Int> = emptyFlow()

    override fun getFloatFlow(key: String, defaultValue: Float): Flow<Float> = emptyFlow()
}
