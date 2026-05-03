package io.github.magisk317.relay.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "scheduled_task",
    indices = [
        Index(value = ["id"], unique = true)
    ]
)
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long = 0,
    @ColumnInfo(name = "name", defaultValue = "") var name: String = "",
    @ColumnInfo(name = "task_type", defaultValue = "sms") var taskType: String = "sms",
    @ColumnInfo(name = "cron_expression", defaultValue = "") var cronExpression: String = "",
    @ColumnInfo(name = "sim_slot", defaultValue = "0") var simSlot: Int = 0,
    @ColumnInfo(name = "mobiles", defaultValue = "") var mobiles: String = "",
    @ColumnInfo(name = "content", defaultValue = "") var content: String = "",
    @ColumnInfo(name = "status", defaultValue = "1") var status: Int = 1,
    @ColumnInfo(name = "last_run_time", defaultValue = "0") var lastRunTime: Long = 0,
    @ColumnInfo(name = "next_run_time", defaultValue = "0") var nextRunTime: Long = 0,
    @ColumnInfo(name = "created_at") var createdAt: Long = System.currentTimeMillis()
)
