package io.github.magisk317.relay.domain.pipeline

import android.util.Log
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.dao.AppInfoDao
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.domain.event.RelayEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventGatekeeperTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun appNotify_missingAppConfig_isBlocked() = runBlocking {
        val gatekeeper = createGatekeeper(appInfo = null)

        val decision = gatekeeper.check(appNotify(packageName = "com.tencent.mm"), traceId = "t1")

        assertFalse(decision.allowed)
        assertEquals("app_source_missing", decision.reason)
    }

    @Test
    fun appNotify_disabledAppConfig_isBlocked() = runBlocking {
        val gatekeeper = createGatekeeper(
            appInfo = AppInfo(
                packageName = "com.tencent.mm",
                forwarding = false,
                forwardingConfigured = true,
                notifyTemplate = "x",
            ),
        )

        val decision = gatekeeper.check(appNotify(packageName = "com.tencent.mm"), traceId = "t2")

        assertFalse(decision.allowed)
        assertEquals("app_source_disabled", decision.reason)
    }

    @Test
    fun appNotify_enabledAppConfig_isAllowed() = runBlocking {
        val gatekeeper = createGatekeeper(
            appInfo = AppInfo(
                packageName = "com.tencent.mm",
                forwarding = true,
                forwardingConfigured = true,
            ),
        )

        val decision = gatekeeper.check(appNotify(packageName = "com.tencent.mm"), traceId = "t3")

        assertTrue(decision.allowed)
        assertEquals("allowed", decision.reason)
    }

    @Test
    fun appNotify_templateOnlyConfig_withoutForwardingChoice_isTreatedAsMissing() = runBlocking {
        val gatekeeper = createGatekeeper(
            appInfo = AppInfo(
                packageName = "com.tencent.mm",
                forwarding = false,
                forwardingConfigured = false,
                notifyTemplate = "x",
            ),
        )

        val decision = gatekeeper.check(appNotify(packageName = "com.tencent.mm"), traceId = "t4")

        assertFalse(decision.allowed)
        assertEquals("app_source_missing", decision.reason)
    }

    private fun createGatekeeper(appInfo: AppInfo?): EventGatekeeper {
        val appInfoDao = mockk<AppInfoDao>()
        every { appInfoDao.getByPackageName(any()) } returns appInfo

        val database = mockk<AppDatabase>()
        every { database.appInfoDao() } returns appInfoDao

        val preferences = mockk<PreferenceDataSource>()
        coEvery { preferences.getBoolean(PrefConst.KEY_ENABLE, true) } returns true
        coEvery {
            preferences.getBooleanCompat(
                PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
                true,
            )
        } returns true

        return EventGatekeeper(database, preferences)
    }

    private fun appNotify(packageName: String): RelayEvent = RelayEvent(
        messageType = MessageType.APP_NOTIFY,
        sourceType = "nms_hook",
        packageName = packageName,
        sender = "sender",
        body = "body",
        timestamp = 1L,
        notifyChannelId = "main",
        companyOrAppName = "WeChat",
        smsCode = null,
        callType = 0,
        callStage = "",
        simSlot = -1,
        subId = 0,
    )
}
