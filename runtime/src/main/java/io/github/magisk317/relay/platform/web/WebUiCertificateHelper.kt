package io.github.magisk317.relay.platform.web

import android.content.Context
import android.net.Uri
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate

object WebUiCertificateHelper {

    data class CertificateInfo(
        val sha256Fingerprint: String,
        val notBeforeTimeMillis: Long,
        val notAfterTimeMillis: Long,
        val derBytes: ByteArray,
    )

    private const val KEYSTORE_FILE_NAME = "webui_tls_keystore.p12"
    private const val KEYSTORE_TYPE = "PKCS12"
    private const val KEY_ALIAS = "xinyi-relay-webui"
    private const val KEY_CA_ALIAS = "xinyi-relay-webui-ca"
    private const val LEGACY_KEY_ALIAS = "xsmscode-webui"
    private const val LEGACY_KEY_CA_ALIAS = "xsmscode-webui-ca"

    suspend fun loadCertificateInfo(
        context: Context,
        preferenceDataSource: PreferenceDataSource,
    ): Result<CertificateInfo> = runCatching {
        val appContext = context.applicationContext ?: context
        val keyStorePassword = preferenceDataSource.getString(
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_PASS,
            "",
        )
        require(keyStorePassword.isNotBlank()) { "webui keystore password missing" }

        val keyStoreFile = File(appContext.filesDir, KEYSTORE_FILE_NAME)
        require(keyStoreFile.exists()) { "webui keystore missing" }

        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        FileInputStream(keyStoreFile).use { input ->
            keyStore.load(input, keyStorePassword.toCharArray())
        }

        val alias = when {
            keyStore.containsAlias(KEY_ALIAS) -> KEY_ALIAS
            keyStore.containsAlias(LEGACY_KEY_ALIAS) -> LEGACY_KEY_ALIAS
            else -> {
                val aliases = keyStore.aliases()
                if (aliases.hasMoreElements()) aliases.nextElement() else null
            }
        } ?: error("webui certificate alias missing")

        val cert = (
            (keyStore.getCertificate(KEY_CA_ALIAS) as? X509Certificate)
                ?: (keyStore.getCertificate(LEGACY_KEY_CA_ALIAS) as? X509Certificate)
                ?: keyStore.getCertificateChain(alias)
                    ?.lastOrNull()
                    ?.let { it as? X509Certificate }
                ?: (keyStore.getCertificate(alias) as? X509Certificate)
            )
            ?: error("webui certificate invalid")
        val der = cert.encoded
        CertificateInfo(
            sha256Fingerprint = buildSha256Fingerprint(der),
            notBeforeTimeMillis = cert.notBefore.time,
            notAfterTimeMillis = cert.notAfter.time,
            derBytes = der,
        )
    }

    suspend fun exportCertificateDerToUri(
        context: Context,
        preferenceDataSource: PreferenceDataSource,
        targetUri: Uri,
    ): Result<Unit> = runCatching {
        val info = loadCertificateInfo(context, preferenceDataSource).getOrThrow()
        val resolver = context.contentResolver
        resolver.openOutputStream(targetUri)?.use { output ->
            output.write(info.derBytes)
            output.flush()
        } ?: error("openOutputStream returned null")
    }

    suspend fun exportKeystoreP12ToUri(
        context: Context,
        preferenceDataSource: PreferenceDataSource,
        targetUri: Uri,
    ): Result<String> = runCatching {
        val appContext = context.applicationContext ?: context
        val keyStorePassword = preferenceDataSource.getString(
            PrefConst.KEY_INTERNAL_WEBUI_TLS_KEYSTORE_PASS,
            "",
        )
        require(keyStorePassword.isNotBlank()) { "webui keystore password missing" }

        val keyStoreFile = File(appContext.filesDir, KEYSTORE_FILE_NAME)
        require(keyStoreFile.exists()) { "webui keystore missing" }

        val resolver = appContext.contentResolver
        resolver.openOutputStream(targetUri)?.use { output ->
            FileInputStream(keyStoreFile).use { input ->
                input.copyTo(output)
            }
            output.flush()
        } ?: error("openOutputStream returned null")

        keyStorePassword
    }

    private fun buildSha256Fingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = ":") { "%02X".format(it) }
    }
}
