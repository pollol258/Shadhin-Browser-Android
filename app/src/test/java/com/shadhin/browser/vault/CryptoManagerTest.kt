package com.shadhin.browser.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setUp() {
        cryptoManager = CryptoManager(keyAlias = "test_shadhin_key")
    }

    @Test
    fun testEncryptionAndDecryptionRoundTrip() {
        val plaintexts = listOf(
            "hunter2",
            "P@\$\$w0rd!_With#Special&Chars~123",
            "বাংলা_পাসওয়ার্ড_utf8_🔒",
            "A very long passphrase containing spaces and numbers: 9876543210 and symbols: ^&*()_+-="
        )

        for (plain in plaintexts) {
            val encrypted = cryptoManager.encrypt(plain)
            assertNotEquals("Ciphertext must not match plaintext", plain, encrypted)
            assertTrue("Encrypted string should be non-empty", encrypted.isNotBlank())
            assertFalseEncryptedContainsPlaintext(plain, encrypted)

            val decrypted = cryptoManager.decrypt(encrypted)
            assertEquals("Decrypted output must match original plaintext", plain, decrypted)
        }
    }

    @Test
    fun testEmptyInputReturnsEmpty() {
        val encrypted = cryptoManager.encrypt("")
        assertEquals("", encrypted)

        val decrypted = cryptoManager.decrypt("")
        assertEquals("", decrypted)
    }

    @Test
    fun testMalformedCiphertextReturnsEmptyStringGracefully() {
        val corrupted1 = "not_valid_base64_!@#"
        assertEquals("", cryptoManager.decrypt(corrupted1))

        val shortPayload = "ABCD" // Less than 12 bytes IV
        assertEquals("", cryptoManager.decrypt(shortPayload))

        // Valid base64 but corrupted payload
        val corrupted2 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        assertEquals("", cryptoManager.decrypt(corrupted2))
    }

    @Test
    fun testDifferentInvocationsProduceDifferentIVAndCiphertext() {
        val plain = "SecretPassword123"
        val enc1 = cryptoManager.encrypt(plain)
        val enc2 = cryptoManager.encrypt(plain)

        // AES-GCM with fresh random 12-byte IV per encryption ensures non-deterministic ciphertexts
        assertNotEquals("Fresh random IV should yield different ciphertexts for same plaintext", enc1, enc2)

        assertEquals(plain, cryptoManager.decrypt(enc1))
        assertEquals(plain, cryptoManager.decrypt(enc2))
    }

    private fun assertFalseEncryptedContainsPlaintext(plain: String, encrypted: String) {
        assertTrue("Ciphertext should not contain plaintext in raw form", !encrypted.contains(plain))
    }
}
