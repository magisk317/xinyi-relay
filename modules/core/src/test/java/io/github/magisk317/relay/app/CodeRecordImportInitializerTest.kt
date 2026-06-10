package io.github.magisk317.relay.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeRecordImportInitializerTest {

    @Test
    fun init_runsRecordImportThroughUnlockedInitializerRunner() {
        val application = Application()
        var runnerApplication: Application? = null
        var runnerTaskName: String? = null
        var importerContext: Context? = null
        val initializer = CodeRecordImportInitializer(
            initRunner = { incomingApplication, _, taskName, block ->
                runnerApplication = incomingApplication
                runnerTaskName = taskName
                runBlocking { block() }
            },
            recordImporter = { context ->
                importerContext = context
                true
            },
        )

        initializer.init(application)

        assertSame(application, runnerApplication)
        assertEquals("CodeRecordImportInitializer", runnerTaskName)
        assertSame(application, importerContext)
    }

    @Test
    fun init_ignoresImporterReturnValueAfterExecution() {
        val application = Application()
        var importerCalls = 0
        var receivedScope: CoroutineScope? = null
        val initializer = CodeRecordImportInitializer(
            initRunner = { incomingApplication, scope, _, block ->
                assertSame(application, incomingApplication)
                receivedScope = scope
                runBlocking { block() }
            },
            recordImporter = {
                importerCalls += 1
                false
            },
        )

        initializer.init(application)

        assertEquals(1, importerCalls)
        assertTrue(receivedScope != null)
    }
}
