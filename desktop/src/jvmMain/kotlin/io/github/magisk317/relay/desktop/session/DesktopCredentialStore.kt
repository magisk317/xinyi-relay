package io.github.magisk317.relay.desktop.session

import java.io.IOException
import java.nio.charset.StandardCharsets

/** A pair of bearer credentials kept outside the profile metadata file. */
data class DesktopCredentials(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Secret storage used by [ProfileStore]. Implementations must never put the
 * bearer values in JSON, logs, exception messages, or process arguments.
 */
interface DesktopCredentialStore {
    fun load(profileId: String): DesktopCredentials?
    fun save(profileId: String, credentials: DesktopCredentials)
    fun clear(profileId: String)
}

/** Deterministic store for JVM tests; production uses [SystemCredentialStore]. */
class InMemoryDesktopCredentialStore : DesktopCredentialStore {
    private val values = mutableMapOf<String, DesktopCredentials>()

    override fun load(profileId: String): DesktopCredentials? = values[profileId]

    override fun save(profileId: String, credentials: DesktopCredentials) {
        values[profileId] = credentials
    }

    override fun clear(profileId: String) {
        values.remove(profileId)
    }
}

/**
 * Native credential-store bridge for the three desktop families supported by
 * the Compose distribution. macOS uses `security`, Linux uses `secret-tool`;
 * Windows is intentionally rejected until a DPAPI/Windows Credential Manager
 * implementation is added, rather than silently writing bearer tokens to a
 * preferences file.
 */
class SystemCredentialStore(
    private val osName: String = System.getProperty("os.name", ""),
    private val runner: CommandRunner = ProcessCommandRunner,
) : DesktopCredentialStore {

    override fun load(profileId: String): DesktopCredentials? {
        val access = read(secretAccount(profileId, ACCESS_SUFFIX)) ?: return null
        val refresh = read(secretAccount(profileId, REFRESH_SUFFIX)) ?: return null
        return DesktopCredentials(access, refresh)
    }

    override fun save(profileId: String, credentials: DesktopCredentials) {
        write(secretAccount(profileId, ACCESS_SUFFIX), credentials.accessToken)
        try {
            write(secretAccount(profileId, REFRESH_SUFFIX), credentials.refreshToken)
        } catch (failure: RuntimeException) {
            runCatching { delete(secretAccount(profileId, ACCESS_SUFFIX)) }
            throw failure
        }
    }

    override fun clear(profileId: String) {
        runCatching { delete(secretAccount(profileId, ACCESS_SUFFIX)) }
        runCatching { delete(secretAccount(profileId, REFRESH_SUFFIX)) }
    }

    private fun read(account: SecretAccount): String? {
        return when (platform()) {
            Platform.MACOS -> {
                val result = runner.run(
                    listOf("/usr/bin/security", "find-generic-password", "-a", account.account, "-s", account.service, "-w"),
                    null,
                )
                result.stdout.trim().takeIf { result.exitCode == 0 && it.isNotEmpty() }
            }
            Platform.LINUX -> {
                val result = runner.run(
                    listOf("secret-tool", "lookup", "service", account.service, "account", account.account),
                    null,
                )
                result.stdout.trim().takeIf { result.exitCode == 0 && it.isNotEmpty() }
            }
            Platform.WINDOWS, Platform.OTHER -> unsupported()
        }
    }

    private fun write(account: SecretAccount, value: String) {
        if (value.isEmpty()) throw IllegalArgumentException("credential cannot be empty")
        when (platform()) {
            Platform.MACOS -> checkSuccess(
                runner.run(
                    listOf(
                        "/usr/bin/security", "add-generic-password", "-a", account.account,
                        "-s", account.service, "-U", "-w",
                    ),
                    stdin = "$value\n",
                ),
            )
            Platform.LINUX -> checkSuccess(
                runner.run(
                    listOf("secret-tool", "store", "--label", "Xinyi Relay desktop session", "service", account.service, "account", account.account),
                    stdin = value,
                ),
            )
            Platform.WINDOWS, Platform.OTHER -> unsupported()
        }
    }

    private fun delete(account: SecretAccount) {
        when (platform()) {
            Platform.MACOS -> {
                val result = runner.run(listOf("/usr/bin/security", "delete-generic-password", "-a", account.account, "-s", account.service), null)
                if (result.exitCode != 0 && !result.stderr.contains("SecKeychainSearchCopyNext", ignoreCase = true)) {
                    checkSuccess(result)
                }
            }
            Platform.LINUX -> {
                val result = runner.run(listOf("secret-tool", "clear", "service", account.service, "account", account.account), null)
                if (result.exitCode != 0) checkSuccess(result)
            }
            Platform.WINDOWS, Platform.OTHER -> unsupported()
        }
    }

    private fun secretAccount(profileId: String, suffix: String): SecretAccount {
        require(profileId.isNotBlank()) { "profile id cannot be empty" }
        return SecretAccount(
            service = "io.github.magisk317.relay.desktop.$suffix",
            account = profileId,
        )
    }

    private fun platform(): Platform = when {
        osName.startsWith("Mac", ignoreCase = true) -> Platform.MACOS
        osName.startsWith("Linux", ignoreCase = true) -> Platform.LINUX
        osName.startsWith("Windows", ignoreCase = true) -> Platform.WINDOWS
        else -> Platform.OTHER
    }

    private fun checkSuccess(result: CommandResult) {
        if (result.exitCode != 0) {
            throw IllegalStateException("native credential store unavailable (exit ${result.exitCode})")
        }
    }

    private fun unsupported(): Nothing = throw IllegalStateException("native credential store is unsupported on this platform")

    private data class SecretAccount(val service: String, val account: String)

    private enum class Platform { MACOS, LINUX, WINDOWS, OTHER }

    private companion object {
        const val ACCESS_SUFFIX = "access"
        const val REFRESH_SUFFIX = "refresh"
    }
}

data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

fun interface CommandRunner {
    fun run(command: List<String>, stdin: String?): CommandResult
}

private object ProcessCommandRunner : CommandRunner {
    override fun run(command: List<String>, stdin: String?): CommandResult {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(false).start()
            if (stdin != null) {
                process.outputStream.use { it.write(stdin.toByteArray(StandardCharsets.UTF_8)) }
            } else {
                process.outputStream.close()
            }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            CommandResult(process.waitFor(), stdout, stderr)
        } catch (failure: IOException) {
            CommandResult(127, "", failure.javaClass.simpleName)
        }
    }
}
