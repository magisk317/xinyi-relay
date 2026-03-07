package com.github.magisk317.smscode.forwarder.database.ext

import androidx.room.TypeConverter
import com.github.magisk317.smscode.forwarder.entity.Sender

class ConvertersSenderList {

    @TypeConverter
    fun stringToObject(value: String): List<Sender> {
        // 由于此处跨线程直接查询 DB 会引起死锁和架构问题，且在轻量化版本可能不再需要立刻连表加载 Sender 对象，暂时返回空列表或者采用其它规避方式
        // 真正需要时可在 Repository/ViewModel 层面组合
        return emptyList()
    }

    @TypeConverter
    fun objectToString(list: List<Sender>): String {
        return list.joinToString(",") { it.id.toString() }
    }
}