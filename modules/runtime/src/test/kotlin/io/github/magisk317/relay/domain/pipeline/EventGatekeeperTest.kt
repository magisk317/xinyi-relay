package io.github.magisk317.relay.domain.pipeline

import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.github.magisk317.forward.contract.constant.MessageType
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardAppInfoDao
import io.github.magisk317.forward.db.ForwardAppInfoEntity
import io.github.magisk317.relay.android.diagnostics.ForwardFlowLog
import io.github.magisk317.forward.engine.event.RelayEvent
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
        XLog.setTestSink { _, _ -> }
        ForwardFlowLog.setTestSink { _, _ -> }
    }

    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
        ForwardFlowLog.setTestSink(null)
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
            appInfo = ForwardAppInfoEntity(
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
            appInfo = ForwardAppInfoEntity(
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
            appInfo = ForwardAppInfoEntity(
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

    @Test
    fun appNotify_queryRunsOffCallingThread() = runBlocking {
        val callerThreadId = Thread.currentThread().threadId()
        var queryThreadId: Long? = null
        val appInfoDao = mockk<ForwardAppInfoDao>(relaxed = true)
        coEvery {
            appInfoDao.byPackage("com.tencent.mm")
        } coAnswers {
            queryThreadId = Thread.currentThread().threadId()
            ForwardAppInfoEntity(
                packageName = "com.tencent.mm",
                forwarding = true,
                forwardingConfigured = true,
            )
        }

        val database = mockk<ForwardDatabase>(relaxed = true)
        every { database.forwardAppInfoDao() } returns appInfoDao

        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        coEvery { preferences.getBoolean(PrefConst.KEY_ENABLE, true) } returns true
        coEvery {
            preferences.getBoolean(
                PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
                true,
            )
        } returns true

        val decision = EventGatekeeper(database, preferences)
            .check(appNotify(packageName = "com.tencent.mm"), traceId = "t5")

        assertTrue(decision.allowed)
        assertEquals("allowed", decision.reason)
        assertEquals(false, queryThreadId == null)
        assertFalse(queryThreadId == callerThreadId)
    }

    private fun createGatekeeper(appInfo: ForwardAppInfoEntity?): EventGatekeeper {
        val appInfoDao = mockk<ForwardAppInfoDao>(relaxed = true)
        coEvery { appInfoDao.byPackage("com.tencent.mm") } returns appInfo

        val database = mockk<ForwardDatabase>(relaxed = true)
        every { database.forwardAppInfoDao() } returns appInfoDao

        val preferences = mockk<PreferenceDataSource>(relaxed = true)
        coEvery { preferences.getBoolean(PrefConst.KEY_ENABLE, true) } returns true
        coEvery {
            preferences.getBoolean(
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
