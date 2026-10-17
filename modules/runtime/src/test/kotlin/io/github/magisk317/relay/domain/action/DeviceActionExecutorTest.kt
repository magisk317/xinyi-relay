package io.github.magisk317.relay.domain.action

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import io.github.magisk317.relay.contract.constant.DeviceActionConst
import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.contract.remote.DeviceActionCommandResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceActionExecutorTest {

    @Test
    fun `rejects unknown action with unsupported_action`() {
        val audioManager = audioManagerMock()
        val executor = DeviceActionExecutor(contextWithAudio(audioManager))

        val outcome = executor.execute(action(DeviceActionConst.SET_RINGER_MODE + "_UNKNOWN", """{"mode":"silent"}"""))

        assertFalse(outcome.success)
        assertEquals(DeviceActionConst.FAILURE_UNSUPPORTED_ACTION, outcome.failureReason)
    }

    @Test
    fun `rejects ringer mode action with missing mode param`() {
        val audioManager = audioManagerMock()
        val executor = DeviceActionExecutor(contextWithAudio(audioManager))

        val outcome = executor.execute(action(DeviceActionConst.SET_RINGER_MODE, "{}"))

        assertFalse(outcome.success)
        assertEquals(DeviceActionConst.FAILURE_INVALID_PARAMS, outcome.failureReason)
    }

    @Test
    fun `rejects ringer mode action with non-string mode param`() {
        val audioManager = audioManagerMock()
        val executor = DeviceActionExecutor(contextWithAudio(audioManager))

        val outcome = executor.execute(action(DeviceActionConst.SET_RINGER_MODE, """{"mode":3}"""))

        assertFalse(outcome.success)
        assertEquals(DeviceActionConst.FAILURE_INVALID_PARAMS, outcome.failureReason)
    }

    @Test
    fun `rejects ringer mode action with unknown mode value`() {
        val audioManager = audioManagerMock()
        val executor = DeviceActionExecutor(contextWithAudio(audioManager))

        val outcome = executor.execute(action(DeviceActionConst.SET_RINGER_MODE, """{"mode":"bogus"}"""))

        assertFalse(outcome.success)
        assertEquals(DeviceActionConst.FAILURE_INVALID_PARAMS, outcome.failureReason)
    }

    @Test
    fun `applies whitelisted ringer modes through audio manager`() {
        val audioManager = audioManagerMock()
        val executor = DeviceActionExecutor(contextWithAudio(audioManager))

        assertTrue(executor.execute(action(DeviceActionConst.SET_RINGER_MODE, """{"mode":"normal"}""")).success)
        verify { audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL }
        assertTrue(executor.execute(action(DeviceActionConst.SET_RINGER_MODE, """{"mode":"silent"}""")).success)
        verify { audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT }
        assertTrue(executor.execute(action(DeviceActionConst.SET_RINGER_MODE, """{"mode":"vibrate"}""")).success)
        verify { audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
    }

    @Test
    fun `fails ringer mode action when audio manager is unavailable`() {
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns null
        val executor = DeviceActionExecutor(context)

        val outcome = executor.execute(action(DeviceActionConst.SET_RINGER_MODE, """{"mode":"silent"}"""))

        assertFalse(outcome.success)
        assertEquals(DeviceActionConst.FAILURE_AUDIO_SERVICE_UNAVAILABLE, outcome.failureReason)
    }

    @Test
    fun `fails interruption filter action without notification policy access`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { notificationManager.isNotificationPolicyAccessGranted } returns false
        val executor = DeviceActionExecutor(contextWithNotifications(notificationManager))

        val outcome = executor.execute(action(DeviceActionConst.SET_INTERRUPTION_FILTER, """{"filter":"none"}"""))

        assertFalse(outcome.success)
        assertEquals(DeviceActionConst.FAILURE_NOTIFICATION_POLICY_ACCESS_REQUIRED, outcome.failureReason)
    }

    @Test
    fun `applies whitelisted interruption filters with notification policy access`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { notificationManager.isNotificationPolicyAccessGranted } returns true
        val executor = DeviceActionExecutor(contextWithNotifications(notificationManager))

        assertTrue(executor.execute(action(DeviceActionConst.SET_INTERRUPTION_FILTER, """{"filter":"all"}""")).success)
        verify { notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        assertTrue(executor.execute(action(DeviceActionConst.SET_INTERRUPTION_FILTER, """{"filter":"priority"}""")).success)
        verify { notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
        assertTrue(executor.execute(action(DeviceActionConst.SET_INTERRUPTION_FILTER, """{"filter":"none"}""")).success)
        verify { notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE) }
        assertTrue(executor.execute(action(DeviceActionConst.SET_INTERRUPTION_FILTER, """{"filter":"alarms"}""")).success)
        verify { notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS) }
    }

    @Test
    fun `rejects interruption filter action with unknown filter value`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { notificationManager.isNotificationPolicyAccessGranted } returns true
        val executor = DeviceActionExecutor(contextWithNotifications(notificationManager))

        val outcome = executor.execute(action(DeviceActionConst.SET_INTERRUPTION_FILTER, """{"filter":"bogus"}"""))

        assertFalse(outcome.success)
        assertEquals(DeviceActionConst.FAILURE_INVALID_PARAMS, outcome.failureReason)
    }

    @Test
    fun `reports execution_error when platform call throws`() {
        val audioManager = audioManagerMock()
        every { audioManager.ringerMode = any() } throws SecurityException("denied")
        val executor = DeviceActionExecutor(contextWithAudio(audioManager))

        val outcome = executor.execute(action(DeviceActionConst.SET_RINGER_MODE, """{"mode":"silent"}"""))

        assertFalse(outcome.success)
        assertEquals(DeviceActionConst.FAILURE_EXECUTION_ERROR, outcome.failureReason)
    }

    private fun action(name: String, params: String): DeviceActionCommandResponse {
        return DeviceActionCommandResponse(
            id = 21L,
            action = name,
            params = RelayJson.parseElement(params).jsonObject,
        )
    }

    private fun audioManagerMock(): AudioManager = mockk(relaxed = true)

    private fun contextWithAudio(audioManager: AudioManager): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        return context
    }

    private fun contextWithNotifications(notificationManager: NotificationManager): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        return context
    }
}
