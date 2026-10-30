package io.github.magisk317.relay.android.data.db.legacy.entity

import androidx.room.TypeConverter

class LegacyConvertersSenderList {
    @TypeConverter
    fun stringToObject(value: String): List<LegacySender> = emptyList()

    @TypeConverter
    fun objectToString(list: List<LegacySender>): String = list.joinToString(",") { it.id.toString() }
}
