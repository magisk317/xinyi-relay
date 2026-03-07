package com.github.magisk317.smscode.web

import android.content.Context
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.NetworkInterface
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.TimeUnit
import okhttp3.tls.HeldCertificate
import timber.log.Timber

internal data class WebUiTlsMaterial(
    val keyStore: KeyStore,
    val keyAlias: String,
    val storePassword: String,
    val keyPassword: String,
)

internal object WebUiTlsManager {

    private const val KEYSTORE_FILE_NAME = "webui_tls_keystore.p12"
    private const val KEYSTORE_TYPE = "PKCS12"
    private const val KEY_ALIAS = "xinyi-relay-webui"
    private const val KEY_CA_ALIAS = "xinyi-relay-webui-ca"
    private const val LEGACY_KEY_ALIAS = "xsmscode-webui"
    private const val PASSWORD_LENGTH = 16

    suspend fun loadOrCreate(context: Context): WebUiTlsMaterial {
        val safeContext = context.applicationContext ?: context
        val keyStoreFile = File(safeContext.filesDir, KEYSTORE_FILE_NAME)
        val keyStoreVersion = AppPreferencesDataStore.getString(
            safeContext,
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION,
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION_DEFAULT,
        )
        var storePassword = AppPreferencesDataStore.getString(
            safeContext,
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_PASS,
            "",
        )
        if (storePassword.isBlank()) {
            storePassword = generateRandomCredential(PASSWORD_LENGTH)
            AppPreferencesDataStore.setString(
                safeContext,
                PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_PASS,
                storePassword,
            )
        }

        if (
            keyStoreFile.exists() &&
            keyStoreVersion == PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION_DEFAULT
        ) {
            runCatching {
                val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
                FileInputStream(keyStoreFile).use { input ->
                    keyStore.load(input, storePassword.toCharArray())
                }
                val resolvedAlias = resolveServerAlias(keyStore)
                if (resolvedAlias != null) {
                    return WebUiTlsMaterial(
                        keyStore = keyStore,
                        keyAlias = resolvedAlias,
                        storePassword = storePassword,
                        keyPassword = storePassword,
                    )
                }
                throw IllegalStateException("TLS keystore missing alias")
            }.onFailure {
                Timber.w(it, "WebUI TLS keystore invalid, regenerating")
            }
        }

        return regenerateKeystore(
            context = safeContext,
            keyStoreFile = keyStoreFile,
            storePassword = storePassword,
        )
    }

    private suspend fun regenerateKeystore(
        context: Context,
        keyStoreFile: File,
        storePassword: String,
    ): WebUiTlsMaterial {
        val rootCaCertificate = HeldCertificate.Builder()
            .commonName("信驿 Relay WebUI Root CA")
            .organizationalUnit("信驿 Relay")
            .certificateAuthority(0)
            .duration(3650L, TimeUnit.DAYS)
            .build()

        val serverCertificateBuilder = HeldCertificate.Builder()
            .commonName("信驿 Relay WebUI")
            .organizationalUnit("信驿 Relay")
            .duration(825L, TimeUnit.DAYS)
            .signedBy(rootCaCertificate)
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")

        collectLocalIpCandidates().forEach { ip ->
            serverCertificateBuilder.addSubjectAlternativeName(ip)
        }

        val serverCertificate = serverCertificateBuilder.build()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
            load(null, null)
            setKeyEntry(
                KEY_ALIAS,
                serverCertificate.keyPair.private,
                storePassword.toCharArray(),
                arrayOf(serverCertificate.certificate, rootCaCertificate.certificate),
            )
            setCertificateEntry(KEY_CA_ALIAS, rootCaCertificate.certificate)
        }

        FileOutputStream(keyStoreFile).use { out ->
            keyStore.store(out, storePassword.toCharArray())
        }

        AppPreferencesDataStore.setString(
            context,
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION,
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_VERSION_DEFAULT,
        )

        Timber.i("WebUI TLS keystore generated: %s", keyStoreFile.absolutePath)
        return WebUiTlsMaterial(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            storePassword = storePassword,
            keyPassword = storePassword,
        )
    }

    private fun collectLocalIpCandidates(): List<String> {
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface -> Collections.list(iface.inetAddresses) }
                .map { it.hostAddress ?: "" }
                .filter { it.isNotBlank() && !it.contains(':') }
                .distinct()
        }.getOrDefault(emptyList())
    }

    private fun resolveServerAlias(keyStore: KeyStore): String? {
        return when {
            keyStore.containsAlias(KEY_ALIAS) -> KEY_ALIAS
            keyStore.containsAlias(LEGACY_KEY_ALIAS) -> LEGACY_KEY_ALIAS
            else -> null
        }
    }

    internal fun generateRandomCredential(length: Int = 8): String {
        val realLength = length.coerceAtLeast(1)
        return buildString(realLength) {
            repeat(realLength) {
                append(CREDENTIAL_ALPHABET[SECURE_RANDOM.nextInt(CREDENTIAL_ALPHABET.length)])
            }
        }
    }

    private val SECURE_RANDOM = SecureRandom()
    private const val CREDENTIAL_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_"
}
