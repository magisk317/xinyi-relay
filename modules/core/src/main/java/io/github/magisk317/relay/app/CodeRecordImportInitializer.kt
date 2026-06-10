package io.github.magisk317.relay.app

import android.app.Application
import android.content.Context
import io.github.magisk317.relay.ui.record.CodeRecordRestoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CodeRecordImportInitializer(
    private val initRunner: (
        Application,
        CoroutineScope,
        String,
        suspend () -> Unit,
    ) -> Unit = AppInitExecution::runWhenUserUnlocked,
    private val recordImporter: (Context) -> Boolean = CodeRecordRestoreManager::importToDatabase,
) : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        initRunner(application, scope, "CodeRecordImportInitializer") {
            recordImporter(application)
        }
    }
}
