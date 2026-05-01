package com.encryptchat.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 加解密引擎
 * 密文格式: 🔒ENC:Base64(salt[16] + iv[12] + ciphertext + authTag[16])
 */
object CryptoEngine {

    private const val PREFIX = "🔒ENC:"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val TAG_LENGTH = 128
    private const val ITERATIONS = 100_000

    /**
     * 加密明文，返回 🔒ENC:... 格式的密文字符串
     */
    fun encrypt(plaintext: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 拼接: salt + iv + ciphertext(含authTag)
        val combined = ByteArray(SALT_LENGTH + IV_LENGTH + ciphertext.size)
        System.arraycopy(salt, 0, combined, 0, SALT_LENGTH)
        System.arraycopy(iv, 0, combined, SALT_LENGTH, IV_LENGTH)
        System.arraycopy(ciphertext, 0, combined, SALT_LENGTH + IV_LENGTH, ciphertext.size)

        return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * 解密 🔒ENC:... 格式的密文
     */
    fun decrypt(encryptedText: String, password: String): String {
        val data = encryptedText.trim().removePrefix(PREFIX)
        val combined = Base64.decode(data, Base64.NO_WRAP)

        require(combined.size > SALT_LENGTH + IV_LENGTH) { "密文数据格式错误" }

        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * 检测文本是否为加密密文
     */
    fun isEncryptedText(text: String): Boolean {
        return text.trim().startsWith(PREFIX)
    }

    /**
     * 从文本中提取密文片段
     */
    fun extractEncryptedText(text: String): String? {
        val regex = Regex("🔒ENC:[A-Za-z0-9+/=]+")
        return regex.find(text)?.value
    }

    /**
     * PBKDF2 密钥派生
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
