package io.github.magisk317.relay.sender

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object SenderSigning {
    fun hmacSha256Base64(key: String, payload: String): String {
        val mac = Mac.getInstance(HMAC_SHA_256)
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), HMAC_SHA_256))
        val signature = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature)
    }

    fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private const val HMAC_SHA_256 = "HmacSHA256"
}
