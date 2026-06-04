package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.relay.android.data.secret.InternalSecretStore
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.testing.relaxedContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RemoteAgentRepositoryTest {

    @BeforeEach
    fun setup() {
        mockkObject(InternalSecretStore)
        mockkObject(HookPreferenceMirror)
        every { InternalSecretStore.putString(any(), any(), any()) } returns Unit
        every { InternalSecretStore.getString(any(), any(), any()) } returns ""
        coEvery { HookPreferenceMirror.publish(any()) } returns Unit
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
        val repository = RemoteAgentRepository(context, preferences)

        repository.clearBinding()

        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_USER_ID, "0") }
        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_DEVICE_ID, "0") }
        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_DEVICE_APP_INFOS, "") }
        coVerify { preferences.setString(RelayPrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, "") }
        coVerify { InternalSecretStore.putString(context, RelayPrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "") }
        coVerify { HookPreferenceMirror.publish(context) }
    }
}
