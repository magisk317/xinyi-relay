package io.github.magisk317.relay.android.data.db.ext

import androidx.room.TypeConverter
import java.util.Date

class ConvertersDate {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}