package io.github.magisk317.relay.android.data.db.legacy.entity

import androidx.room.TypeConverter
import java.util.Date

class LegacyConvertersDate {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
