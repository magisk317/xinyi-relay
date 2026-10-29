package io.github.magisk317.relay.desktop.remote

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.github.magisk317.relay.desktop.session.DesktopProfile
import io.github.magisk317.relay.desktop.session.InMemoryDesktopCredentialStore
import io.github.magisk317.relay.desktop.session.ProfileStore
import io.github.magisk317.relay.desktop.session.SessionManager
import io.github.magisk317.forward.net.RelayHttpClients
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Pins the `allowSelfSigned` wiring end to end against a real self-signed TLS
 * backend: a profile that opts in reaches it, a profile that does not keeps
 * the system trust store and fails the handshake, and the flag-off client is
 * the shared default one.
 */
class SelfSignedTlsTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var server: HttpsServer
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        val keystore = dir.resolve("self-signed.p12")
        generateSelfSignedKeystore(keystore)
        server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.httpsConfigurator = HttpsConfigurator(serverContext(keystore))
        server.createContext("/api/v1/system/info") { exchange ->
            val body = """{"service":"relay","appEnv":"prod","userCount":1}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        baseUrl = "https://127.0.0.1:${server.address.port}"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `a profile without the flag keeps the shared default client`() {
        assertSame(RelayHttpClients.default, SelfSignedTls.client(allowSelfSigned = false))
        assertNotSame(RelayHttpClients.default, SelfSignedTls.client(allowSelfSigned = true))
    }

    @Test
    fun `an opting-in profile reaches a self-signed backend`() = runBlocking {
        val manager = SessionManager(ProfileStore(dir, InMemoryDesktopCredentialStore()))

        val info = manager.clientFor(profile(allowSelfSigned = true)).systemInfo()

        assertEquals("relay", info.service)
    }

    @Test
    fun `a profile without the flag cannot handshake with a self-signed backend`() {
        val client = ConsoleClient(baseUrl, SelfSignedTls.client(allowSelfSigned = false))

        assertThrows(IOException::class.java) { runBlocking { client.systemInfo() } }
    }

    private fun profile(allowSelfSigned: Boolean) = DesktopProfile(
        id = "p1",
        name = "prod",
        baseUrl = baseUrl,
        allowSelfSigned = allowSelfSigned,
    )

    private fun generateSelfSignedKeystore(keystore: Path) {
        val keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString()
        val process = ProcessBuilder(
            keytool,
            "-genkeypair",
            "-alias", "relay-test",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "1",
            "-storetype", "PKCS12",
            "-keystore", keystore.toString(),
            "-storepass", STORE_PASSWORD,
            "-dname", "CN=localhost",
            "-ext", "SAN=dns:localhost,ip:127.0.0.1",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "keytool failed: $output")
    }

    private fun serverContext(keystore: Path): SSLContext {
        val store = KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(keystore).use { load(it, STORE_PASSWORD.toCharArray()) }
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        managers.init(store, STORE_PASSWORD.toCharArray())
        return SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
    }

    private companion object {
        const val STORE_PASSWORD = "changeit"
    }
}
