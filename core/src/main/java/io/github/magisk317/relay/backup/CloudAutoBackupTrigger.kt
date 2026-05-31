package io.github.magisk317.relay.backup

import io.github.magisk317.relay.contract.backup.AutoBackupTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CloudAutoBackupTrigger(
    private val coordinator: AutoCloudBackupCoordinator,
) : AutoBackupTrigger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun scheduleAutoBackup(reason: String) {
        coordinator.schedule(scope, reason)
    }
}
