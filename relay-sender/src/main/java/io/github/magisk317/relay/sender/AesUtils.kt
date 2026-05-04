package io.github.magisk317.relay.sender

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES 加密工具类，支持 AES-GCM 和 AES-CBC 模式。
 * 用于 Bark 发送通道的消息加密。
 */
object AesUtils {

    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val GCM_IV_LENGTH = 12  // 96 bits
    private const val GCM_TAG_LENGTH = 128
    private const val CBC_IV_LENGTH = 16  // 128 bits

    /**
     * 加密结果
     */
    data class EncryptResult(
        val ciphertext: String,  // Base64 编码的密文
        val iv: String,          // Base64 编码的 IV/Nonce
    )

    /**
     * 使用 AES-256-GCM 模式加密
     * @param key Base64 编码的密钥（256 位 = 32 字节）
     * @param plaintext 明文
     * @return 加密结果，包含密文和随机生成的 nonce
     */
    fun encryptAesGcm(key: String, plaintext: String): EncryptResult {
        val keyBytes = Base64.decode(key, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // 生成随机 IV (nonce)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptResult(
            ciphertext = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
        )
    }

    /**
     * 使用 AES-256-CBC 模式加密
     * @param key Base64 编码的密钥（256 位 = 32 字节）
     * @param iv Base64 编码的 IV（128 位 = 16 字节）
     * @param plaintext 明文
     * @return Base64 编码的密文
     */
    fun encryptAesCbc(key: String, iv: String, plaintext: String): String {
        val keyBytes = Base64.decode(key, Base64.NO_WRAP)
        val ivBytes = Base64.decode(iv, Base64.NO_WRAP)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance(AES_CBC_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * 验证密钥格式是否有效
     * @param key Base64 编码的密钥
     * @param transformation 加密模式
     * @return 是否有效
     */
    fun isValidKey(key: String, transformation: String): Boolean {
        if (key.isBlank()) return false

        return runCatching {
            val keyBytes = Base64.decode(key, Base64.NO_WRAP)
            when (transformation) {
                "AES/GCM/NoPadding", "AES/CBC/PKCS5Padding" -> keyBytes.size == 32  // AES-256
                else -> false
            }
        }.getOrDefault(false)
    }

    /**
     * 验证 IV 格式是否有效
     * @param iv Base64 编码的 IV
     * @param transformation 加密模式
     * @return 是否有效
     */
    fun isValidIv(iv: String, transformation: String): Boolean {
        if (iv.isBlank()) return false

        return runCatching {
            val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
            when (transformation) {
                "AES/GCM/NoPadding" -> ivBytes.size == GCM_IV_LENGTH
                "AES/CBC/PKCS5Padding" -> ivBytes.size == CBC_IV_LENGTH
                else -> false
            }
        }.getOrDefault(false)
    }

    /**
     * 生成随机密钥（Base64 编码）
     * @return 256 位密钥的 Base64 字符串
     */
    fun generateKey(): String {
        val key = ByteArray(32)  // AES-256
        SecureRandom().nextBytes(key)
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    /**
     * 生成随机 IV（Base64 编码）
     * @param transformation 加密模式
     * @return IV 的 Base64 字符串
     */
    fun generateIv(transformation: String): String {
        val length = when (transformation) {
            "AES/GCM/NoPadding" -> GCM_IV_LENGTH
            "AES/CBC/PKCS5Padding" -> CBC_IV_LENGTH
            else -> CBC_IV_LENGTH
        }
        val iv = ByteArray(length)
        SecureRandom().nextBytes(iv)
        return Base64.encodeToString(iv, Base64.NO_WRAP)
    }
}
