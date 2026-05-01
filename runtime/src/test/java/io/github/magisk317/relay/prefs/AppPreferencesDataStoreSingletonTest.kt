package io.github.magisk317.relay.prefs

import io.github.magisk317.relay.android.prefs.AppPreferencesDataStore
import android.content.Context
import dev.mokkery.MockMode.autofill
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.answering.returns
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppPreferencesDataStoreSingletonTest {

    @Test
    fun `getInstance should return a single DataStore instance under concurrency`() {
        resetInstance()
        val context = mock<Context>(autofill)
        val tempDir = Files.createTempDirectory("datastore-singleton-test").toFile()
        every { context.applicationContext } returns context
        every { context.dataDir } returns tempDir

        val method = AppPreferencesDataStore::class.java.getDeclaredMethod("getInstance", Context::class.java)
        method.isAccessible = true

        val executor = Executors.newFixedThreadPool(8)
        val tasks = (1..64).map {
            Callable {
                method.invoke(AppPreferencesDataStore, context)
            }
        }

        val futures = executor.invokeAll(tasks)
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        val instances = futures.map { it.get() }.toSet()
        assertEquals(1, instances.size)

        resetInstance()
        tempDir.deleteRecursively()
    }

    private fun resetInstance() {
        val field = AppPreferencesDataStore::class.java.getDeclaredField("INSTANCE\$1")
        field.isAccessible = true
        field.set(null, null)
    }
}
