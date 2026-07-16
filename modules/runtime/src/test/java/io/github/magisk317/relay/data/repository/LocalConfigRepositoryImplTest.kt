package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.datasource.PreferenceWriteScope
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.relay.contract.repository.LocalConfigMirrorRejectedException
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.testing.relaxedContext
import io.github.magisk317.smscode.runtime.common.prefs.PreferenceChange
import io.github.magisk317.smscode.runtime.common.prefs.PreferenceChangeSet
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalConfigRepositoryImplTest {

    @Test
    fun `getRevision migrates legacy remote revision when local revision is blank`() = runBlocking {
        val preferences = FakePreferenceDataSource(
            strings = mutableMapOf(
                RelayPrefConst.KEY_REMOTE_AGENT_LAST_REVISION to "7",
            ),
        )
        val repository = createRepository(preferences)

        val revision = repository.getRevision()

        assertEquals(7L, revision.value)
        assertEquals("7", preferences.requireString(RelayPrefConst.KEY_LOCAL_CONFIG_REVISION))
    }

    @Test
    fun `getDirtyState migrates legacy pending local changes when local key is blank`() = runBlocking {
        val preferences = FakePreferenceDataSource(
            strings = mutableMapOf(
                RelayPrefConst.KEY_LOCAL_CONFIG_REVISION to "3",
                RelayPrefConst.KEY_REMOTE_AGENT_PENDING_LOCAL_CHANGES to "2",
            ),
        )
        val repository = createRepository(preferences)

        val dirtyState = repository.getDirtyState()

        assertEquals(3L, dirtyState.revision.value)
        assertEquals(2, dirtyState.pendingLocalChanges)
        assertTrue(dirtyState.dirty)
        assertEquals("2", preferences.requireString(RelayPrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES))
    }

    @Test
    fun `noteLocalMutation increments revision and pending changes then markMirrorSynced clears dirty state`() = runBlocking {
        val preferences = FakePreferenceDataSource(
            strings = mutableMapOf(
                RelayPrefConst.KEY_LOCAL_CONFIG_REVISION to "4",
                RelayPrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES to "1",
            ),
        )
        val repository = createRepository(preferences)

        val afterMutation = repository.noteLocalMutation("settings.general")
        assertEquals(5L, afterMutation.revision.value)
        assertEquals(2, afterMutation.pendingLocalChanges)
        assertTrue(afterMutation.dirty)

        val afterSync = repository.markMirrorSynced(6L)
        assertEquals(6L, afterSync.revision.value)
        assertEquals(0, afterSync.pendingLocalChanges)
        assertFalse(afterSync.dirty)
    }

    @Test
    fun `applyMirror rejects dirty local state before mutating revision`() {
        val preferences = FakePreferenceDataSource(
            strings = mutableMapOf(
                RelayPrefConst.KEY_LOCAL_CONFIG_REVISION to "4",
                RelayPrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES to "1",
            ),
        )
        val repository = createRepository(preferences)

        val error = assertThrows(LocalConfigMirrorRejectedException::class.java) {
            runBlocking {
                repository.applyMirror(JsonObject(emptyMap()), revision = 5, source = "command_10")
            }
        }

        assertEquals("local_dirty", error.reason)
        assertEquals("4", preferences.requireString(RelayPrefConst.KEY_LOCAL_CONFIG_REVISION))
        assertEquals("1", preferences.requireString(RelayPrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES))
    }

    @Test
    fun `applyMirror rejects stale mirror revision before mutating revision`() {
        val preferences = FakePreferenceDataSource(
            strings = mutableMapOf(
                RelayPrefConst.KEY_LOCAL_CONFIG_REVISION to "5",
                RelayPrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES to "0",
            ),
        )
        val repository = createRepository(preferences)

        val error = assertThrows(LocalConfigMirrorRejectedException::class.java) {
            runBlocking {
                repository.applyMirror(JsonObject(emptyMap()), revision = 4, source = "command_10")
            }
        }

        assertEquals("stale_local_revision", error.reason)
        assertEquals("5", preferences.requireString(RelayPrefConst.KEY_LOCAL_CONFIG_REVISION))
        assertEquals("0", preferences.requireString(RelayPrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES))
    }

    private fun createRepository(preferences: PreferenceDataSource): LocalConfigRepositoryImpl {
        return LocalConfigRepositoryImpl(
            context = relaxedContext(),
            preferenceDataSource = preferences,
            settingsRepository = mockk<SettingsPreferencesRepository>(relaxed = true),
            configRepository = mockk<AppConfigRepository>(relaxed = true),
            database = mockk<AppDatabase>(relaxed = true),
        )
    }
}

private class FakePreferenceDataSource(
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

    override fun getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean> = flowOf(booleans[key] ?: defaultValue)

    override fun getStringFlow(key: String, defaultValue: String): Flow<String> = flowOf(strings[key] ?: defaultValue)

    override fun getIntFlow(key: String, defaultValue: Int): Flow<Int> = flowOf(ints[key] ?: defaultValue)

    override fun getFloatFlow(key: String, defaultValue: Float): Flow<Float> = flowOf(floats[key] ?: defaultValue)

    fun requireString(key: String): String = strings.getValue(key)
}
