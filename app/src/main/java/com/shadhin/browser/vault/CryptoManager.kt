package com.shadhin.browser.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256 GCM cryptographic manager for securing stored web passwords at rest.
 * Uses AndroidKeyStore in production with an in-memory fallback for local JVM unit testing.
 */
class CryptoManager(
    private val keyAlias: String = "shadhin_web_vault_key"
) {

    private val transformation = "AES/GCM/NoPadding"
    private val tagLengthBits = 128
    private val ivLengthBytes = 12

    private var fallbackKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            } else {
                val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey ?: throw IllegalStateException("KeyStore entry is not a SecretKey")
            }
        } catch (_: Exception) {
            // Local JVM test fallback
            synchronized(this) {
                fallbackKey ?: run {
                    val kgen = KeyGenerator.getInstance("AES")
                    kgen.init(256)
                    val generated = kgen.generateKey()
                    fallbackKey = generated
                    generated
                }
            }
        }
    }

    /**
     * Encrypts a plaintext string into a Base64 encoded payload: [12 bytes IV] + [Ciphertext + AuthTag].
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(iv.size + cipherText.size)
        buffer.put(iv)
        buffer.put(cipherText)
        return base64Encode(buffer.array())
    }

    /**
     * Decrypts a Base64 encoded payload produced by encrypt() back to plaintext.
     */
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isBlank()) return ""
        return try {
            val combined = base64Decode(encryptedBase64)
            if (combined.size <= ivLengthBytes) return ""

            val iv = combined.copyOfRange(0, ivLengthBytes)
            val cipherText = combined.copyOfRange(ivLengthBytes, combined.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(tagLengthBits, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun base64Encode(data: ByteArray): String {
        return try {
            Base64.encodeToString(data, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(data)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }
}
