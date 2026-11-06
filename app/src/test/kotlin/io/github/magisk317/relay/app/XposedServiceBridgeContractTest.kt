package io.github.magisk317.relay.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class XposedServiceBridgeContractTest {
    @Test
    fun `phone processes restart only after xposed service bind`() {
        val source = projectFile(
            "app/src/xposed/kotlin/io/github/magisk317/relay/ui/app/XposedServiceBridge.kt",
        ).readText()
        val beforeBind = source.substringBefore("private fun onBound")
        val bindCallback = source
            .substringAfter("private fun onBound")
            .substringBefore("private fun onRetracted")
        val diedCallback = source
            .substringAfter("private fun onRetracted")
            .substringBefore("private fun publishScope", missingDelimiterValue = source)
        val restartCall = "PhoneProcessRestartCoordinator.requestAfterInstallOrUpdate"
        val handleBindCall = "XposedServiceRuntimeCoordinator.handleServiceBound"

        assertFalse(restartCall in beforeBind)
        assertEquals(1, bindCallback.windowed(restartCall.length).count { it == restartCall })
        assertTrue(handleBindCall in bindCallback)
        assertTrue(bindCallback.indexOf(handleBindCall) < bindCallback.indexOf(restartCall))
        assertFalse(restartCall in diedCallback)
    }

    @Test
    fun `the framework hook targets are recorded on the bind path`() {
        val source = projectFile(
            "app/src/xposed/kotlin/io/github/magisk317/relay/ui/app/XposedServiceBridge.kt",
        ).readText()
        val bindCallback = source
            .substringAfter("private fun onBound")
            .substringBefore("private fun onRetracted")
        val recordCall = "FrameworkHookTargets.record"

        // The activation status has to survive a content provider the system refuses to start, so
        // the framework's own view of the hooked processes is recorded on the bind path.
        assertTrue(recordCall in bindCallback)
    }

    private fun projectFile(relativePath: String): File {
        val fromRoot = File(relativePath)
        return if (fromRoot.exists()) fromRoot else File("../$relativePath")
    }
}
