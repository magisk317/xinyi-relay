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
            "app/src/xposed/java/io/github/magisk317/relay/ui/app/XposedServiceBridge.kt",
        ).readText()
        val beforeBind = source.substringBefore("override fun onServiceBind")
        val bindCallback = source
            .substringAfter("override fun onServiceBind")
            .substringBefore("override fun onServiceDied")
        val diedCallback = source
            .substringAfter("override fun onServiceDied")
            .substringBefore("}", missingDelimiterValue = source)
        val restartCall = "PhoneProcessRestartCoordinator.requestAfterInstallOrUpdate"
        val handleBindCall = "XposedServiceRuntimeCoordinator.handleServiceBound"

        assertFalse(restartCall in beforeBind)
        assertEquals(1, bindCallback.windowed(restartCall.length).count { it == restartCall })
        assertTrue(handleBindCall in bindCallback)
        assertTrue(bindCallback.indexOf(handleBindCall) < bindCallback.indexOf(restartCall))
        assertFalse(restartCall in diedCallback)
    }

    private fun projectFile(relativePath: String): File {
        val fromRoot = File(relativePath)
        return if (fromRoot.exists()) fromRoot else File("../$relativePath")
    }
}
