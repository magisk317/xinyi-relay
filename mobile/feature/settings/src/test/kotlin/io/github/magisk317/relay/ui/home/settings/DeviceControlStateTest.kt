package io.github.magisk317.relay.ui.home.settings

import android.app.NotificationManager
import android.media.AudioManager
import io.github.magisk317.relay.contract.constant.DeviceActionConst
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DeviceControlStateTest {

    @Test
    fun `ringer label maps platform modes`() {
        assertEquals(RingerLabel.NORMAL, ringerLabelOf(AudioManager.RINGER_MODE_NORMAL))
        assertEquals(RingerLabel.SILENT, ringerLabelOf(AudioManager.RINGER_MODE_SILENT))
        assertEquals(RingerLabel.VIBRATE, ringerLabelOf(AudioManager.RINGER_MODE_VIBRATE))
        assertEquals(RingerLabel.UNKNOWN, ringerLabelOf(99))
    }

    @Test
    fun `filter label maps platform filters`() {
        assertEquals(FilterLabel.ALL, filterLabelOf(NotificationManager.INTERRUPTION_FILTER_ALL))
        assertEquals(
            FilterLabel.PRIORITY,
            filterLabelOf(NotificationManager.INTERRUPTION_FILTER_PRIORITY),
        )
        assertEquals(FilterLabel.NONE, filterLabelOf(NotificationManager.INTERRUPTION_FILTER_NONE))
        assertEquals(
            FilterLabel.ALARMS,
            filterLabelOf(NotificationManager.INTERRUPTION_FILTER_ALARMS),
        )
        assertEquals(FilterLabel.UNKNOWN, filterLabelOf(99))
    }

    @Test
    fun `label text resources cover every state`() {
        val ringerLabels = RingerLabel.entries.map { ringerLabelTextRes(it) }
        assertEquals(RingerLabel.entries.size, ringerLabels.distinct().size)
        val filterLabels = FilterLabel.entries.map { filterLabelTextRes(it) }
        assertEquals(FilterLabel.entries.size, filterLabels.distinct().size)
    }

    @Test
    fun `failure reasons map to distinct resources`() {
        val reasons = listOf(
            DeviceActionConst.FAILURE_UNSUPPORTED_ACTION,
            DeviceActionConst.FAILURE_INVALID_PARAMS,
            DeviceActionConst.FAILURE_NOTIFICATION_POLICY_ACCESS_REQUIRED,
            DeviceActionConst.FAILURE_AUDIO_SERVICE_UNAVAILABLE,
            DeviceActionConst.FAILURE_NOTIFICATION_SERVICE_UNAVAILABLE,
            DeviceActionConst.FAILURE_EXECUTION_ERROR,
        )
        val resources = reasons.map { failureReasonTextRes(it) }
        assertEquals(reasons.size, resources.distinct().size)
        assertNotEquals(
            failureReasonTextRes(DeviceActionConst.FAILURE_INVALID_PARAMS),
            failureReasonTextRes("some_unknown_reason"),
        )
        assertEquals(
            failureReasonTextRes(DeviceActionConst.FAILURE_EXECUTION_ERROR),
            failureReasonTextRes("some_unknown_reason"),
        )
    }

    @Test
    fun `local actions match contract whitelist values`() {
        assertEquals(
            DeviceActionConst.SET_RINGER_MODE,
            DeviceControlAction.NORMAL.action,
        )
        assertEquals(DeviceActionConst.RINGER_MODE_SILENT, DeviceControlAction.SILENT.paramValue)
        assertEquals(DeviceActionConst.RINGER_MODE_VIBRATE, DeviceControlAction.VIBRATE.paramValue)
        assertEquals(
            DeviceActionConst.INTERRUPTION_FILTER_PRIORITY,
            DeviceControlAction.PRIORITY.paramValue,
        )
        assertEquals(
            DeviceActionConst.INTERRUPTION_FILTER_ALL,
            DeviceControlAction.ALL.paramValue,
        )
    }
}
