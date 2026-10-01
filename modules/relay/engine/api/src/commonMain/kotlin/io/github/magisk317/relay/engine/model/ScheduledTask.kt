package io.github.magisk317.relay.engine.model

data class ScheduledTask(
    val id: Long = 0,
    val name: String = "",
    val taskType: String = TASK_TYPE_SMS,
    val cronExpression: String = "",
    val simSlot: Int = 0,
    val mobiles: String = "",
    val content: String = "",
    val status: Int = STATUS_ENABLED,
    val lastRunTime: Long = 0,
    val nextRunTime: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TASK_TYPE_SMS = "sms"
        const val STATUS_DISABLED = 0
        const val STATUS_ENABLED = 1
    }
}
