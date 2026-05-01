package io.github.magisk317.relay.data.mapper

import io.github.magisk317.relay.android.data.mapper.ConfigMapper
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toEntity
import io.github.magisk317.relay.android.data.db.entity.SenderEntity
import io.github.magisk317.relay.engine.sender.SenderActiveSchedule
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleConst
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleRange
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class ConfigMapperTest {

    @Test
    fun senderEntity_roundTripsActiveSchedule() {
        val sender = SenderEntity(
            id = 7L,
            type = 3,
            name = "Webhook",
            jsonSetting = "{}",
            status = 1,
            time = Date(),
            activeScheduleJson =
                """{"sms":{"enabled":true,"mode":"whitelist","weekdays":[1,2,3,4,5],"ranges":[{"start":"09:00","end":"18:00"}]}}""",
        )

        val domain = with(ConfigMapper) { sender.toDomain() }
        assertTrue(domain.activeSchedule.sms.enabled)
        assertEquals("09:00", domain.activeSchedule.sms.ranges.first().start)

        val entity = with(ConfigMapper) {
            domain.copy(
                activeSchedule = SenderActiveSchedule(
                    sms = SenderActiveScheduleRule(
                        enabled = true,
                        mode = SenderActiveScheduleConst.MODE_BLACKLIST,
                        weekdays = listOf(6, 7),
                        ranges = listOf(SenderActiveScheduleRange("10:00", "12:00")),
                    ),
                ),
            ).toEntity()
        }

        assertTrue(entity.activeScheduleJson.contains("\"10:00\""))
        assertTrue(entity.activeScheduleJson.contains("\"blacklist\""))
    }
}
