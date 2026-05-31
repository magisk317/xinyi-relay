package io.github.magisk317.relay.contract.backup

interface AutoBackupTrigger {
    fun scheduleAutoBackup(reason: String)
}

object NoOpAutoBackupTrigger : AutoBackupTrigger {
    override fun scheduleAutoBackup(reason: String) = Unit
}
