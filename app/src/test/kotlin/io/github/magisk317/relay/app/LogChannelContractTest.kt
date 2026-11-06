package io.github.magisk317.relay.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogChannelContractTest {
    @Test
    fun `app and hook derive diagnostics from debug or CI only`() {
        val sources = listOf(
            projectFile("app/src/main/kotlin/io/github/magisk317/relay/ui/app/SmsCodeApplication.kt"),
            projectFile("modules/hook/entry/src/main/kotlin/io/github/magisk317/relay/xp/LibXposedEntry.kt"),
        ).joinToString("\n") { it.readText() }
        val diagnosticExpression = "isDiagnosticBuild = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD"

        assertEquals(2, sources.split(diagnosticExpression).size - 1)
        assertFalse(sources.contains("ALLOW_" + "PLAINTEXT_LOGS"))
        assertFalse(sources.contains("allow" + "PlaintextLogs"))
    }

    @Test
    fun `CI AAB intentionally uses release shape with diagnostic logging`() {
        val yaml = projectFile(".gitlab-ci.yml").readText()
        val job = yaml.substringAfter("android:ci:aab:").substringBefore("android:ci:publish-play:")

        assertTrue(job.contains("MAGISK_IS_CI_BUILD: \"true\""))
        assertTrue(job.contains(":app:bundlePlayRelease"))
        assertTrue(job.contains("signed, non-debuggable AAB"))
    }

    @Test
    fun `public release strips every owned Android log surface`() {
        val rules = projectFile("app/proguard-release.pro").readText()

        listOf("void v", "void d", "void i", "void w", "void e", "void log", "void structured").forEach {
            assertTrue(rules.contains(it), "Missing release XLog rule: $it")
        }
        assertTrue(rules.contains("class android.util.Log"))
        assertTrue(rules.contains("class io.github.magisk317.xposed.logging.TaggedLog"))
        assertTrue(rules.contains("int println"))
    }

    @Test
    fun `desktop logging is an explicit build property`() {
        val build = projectFile("desktop/build.gradle.kts").readText()
        val entry = projectFile("desktop/src/jvmMain/kotlin/io/github/magisk317/relay/desktop/Main.kt").readText()

        assertTrue(build.contains("MAGISK_IS_CI_BUILD"))
        assertTrue(build.contains("DIAGNOSTIC_LOGGING"))
        assertTrue(build.contains("!providers.gradleProperty(\"desktopVersion\").isPresent"))
        assertTrue(entry.contains("MagiskLogPolicy.install(isDiagnosticBuild = DesktopBuildConfig.DIAGNOSTIC_LOGGING)"))
        assertTrue(entry.contains("if (logPolicy.loggingEnabled)"))
    }

    @Test
    fun `sender logs never contain request or response bodies`() {
        val senderRoot = projectFile("forward/core/sender/src/main/kotlin")
        val source = senderRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertFalse(source.contains("SLog.i(TAG, \"requestMsg:"))
        assertFalse(source.contains("SLog.i(TAG, \"Response:"))
        assertFalse(Regex("SLog\\.[diwe]\\([^\\n]*(body|respBody|responseBody)").containsMatchIn(source))
    }

    @Test
    fun `custom log bridges never print directly to logcat`() {
        val sources = listOf(
            projectFile("forward/core/sender/src/main/kotlin/io/github/magisk317/forward/sender/SenderLogger.kt"),
            projectFile("modules/relay/android/src/main/kotlin/io/github/magisk317/relay/android/common/utils/RelayLogger.kt"),
            projectFile("modules/relay/android/src/main/kotlin/io/github/magisk317/relay/android/diagnostics/ForwardFlowLog.kt"),
        ).joinToString("\n") { it.readText() }

        assertFalse(Regex("Log\\.(v|d|i|w|e|wtf|println)\\(").containsMatchIn(sources))
    }

    @Test
    fun `release does not install providers clients or sinks`() {
        val app = projectFile(
            "mobile/core/src/main/kotlin/io/github/magisk317/relay/app/AppInfrastructureCoordinator.kt",
        ).readText()
        val hook = projectFile(
            "modules/hook/entry/src/main/kotlin/io/github/magisk317/relay/xp/LibXposedEntry.kt",
        ).readText()
        val provider = projectFile(
            "modules/relay/android/src/xposed/kotlin/io/github/magisk317/relay/android/platform/xpbridge/RelayXposedLogProvider.kt",
        ).readText()
        val plugin = projectFile(
            "features/matrix-e2ee-plugin/src/main/kotlin/io/github/magisk317/relay/matrix/e2ee/plugin/PluginEntry.kt",
        ).readText()

        assertTrue(app.contains("if (logPolicy.loggingEnabled)"))
        assertTrue(hook.contains("if (logPolicy.loggingEnabled)"))
        assertTrue(provider.contains("if (!buildLoggingEnabled) return true"))
        assertTrue(plugin.contains("if (BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD)"))
    }

    @Test
    fun `production code never re-enables the in-memory log test seam`() {
        val seamCall = Regex("\\.\\s*(setTestSink|setLogPrinterForTest)\\s*\\(")
        val offenders = productionSources().filter { source ->
            seamCall.containsMatchIn(source.readText())
        }.map { it.relativeTo(projectRoot()).invariantSeparatorsPath }

        assertTrue(
            offenders.isEmpty(),
            "A test sink bypasses the release log gate, so production code must not install one: $offenders",
        )
    }

    private fun projectRoot(): File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun productionSources(): List<File> {
        val skipped = setOf("build", ".gradle", ".kotlin", "node_modules")
        return projectRoot().walkTopDown()
            .onEnter { dir -> dir.name !in skipped && !dir.name.startsWith(".") }
            .filter { it.isFile && it.extension in setOf("kt", "java") && it.invariantSeparatorsPath.contains("/src/main") }
            .toList()
    }

    private fun projectFile(relativePath: String): File {
        return generateSequence(File(".").canonicalFile) { it.parentFile }
            .map { root -> File(root, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("Cannot resolve project file: $relativePath")
    }
}
