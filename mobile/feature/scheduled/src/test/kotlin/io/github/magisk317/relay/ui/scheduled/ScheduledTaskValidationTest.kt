package io.github.magisk317.relay.ui.scheduled

import io.github.magisk317.forward.engine.model.ScheduledTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScheduledTaskValidationTest {
    private fun validate(
        taskType: String = ScheduledTask.TASK_TYPE_SMS,
        smsChannelEnabled: Boolean = true,
        cronExpression: String = "0 0 * * *",
        simSlot: Int = 0,
        content: String = "hello",
        mobiles: String = "+8613800138000",
        cronValidator: (String) -> String? = { null },
    ) = ScheduledTaskValidation.validate(taskType, smsChannelEnabled, cronExpression, simSlot, content, mobiles, cronValidator)

    @Test
    fun `a well-formed task passes`() = assertNull(validate())

    @Test
    fun `sms tasks are rejected when the channel is disabled`() {
        assertEquals(ScheduledTaskProblem.SMS_CHANNEL_DISABLED, validate(smsChannelEnabled = false)?.kind)
    }

    @Test
    fun `non-sms tasks skip the channel check`() {
        assertNull(validate(taskType = "other", smsChannelEnabled = false))
    }

    @Test
    fun `invalid cron expressions are rejected`() {
        assertEquals(ScheduledTaskProblem.CRON_INVALID, validate(cronValidator = { "bad cron" })?.kind)
    }

    @Test
    fun `sim slots outside 0-2 are rejected`() {
        assertEquals(ScheduledTaskProblem.SIM_SLOT_INVALID, validate(simSlot = 3)?.kind)
        assertEquals(ScheduledTaskProblem.SIM_SLOT_INVALID, validate(simSlot = -1)?.kind)
    }

    @Test
    fun `blank content is rejected`() {
        assertEquals(ScheduledTaskProblem.CONTENT_BLANK, validate(content = "  ")?.kind)
    }

    @Test
    fun `blank mobile lists are rejected`() {
        assertEquals(ScheduledTaskProblem.MOBILES_BLANK, validate(mobiles = " , ； ")?.kind)
    }

    @Test
    fun `mobile lists accept mixed separators and report the first invalid target`() {
        assertNull(validate(mobiles = "+8613800138000，13800138001; 13800138002"))
        val report = validate(mobiles = "+8613800138000,not-a-phone")!!
        assertEquals(ScheduledTaskProblem.MOBILE_INVALID, report.kind)
        assertEquals("not-a-phone", report.invalidTarget)
    }
}
