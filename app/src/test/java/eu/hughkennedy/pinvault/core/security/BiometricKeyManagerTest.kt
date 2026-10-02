package eu.hughkennedy.pinvault.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BiometricKeyManagerTest {

    @Before
    fun setUp() {
        BiometricKeyManager.deleteKey()
    }

    @Test
    fun testKeyGenerationAndBasicEncryptionDecryptionRoundtrip() {
        val keyPair = BiometricKeyManager.getOrCreateKeyPair()
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)
        assertEquals("RSA", keyPair.public.algorithm)

        val plaintext = "SensitiveMasterTokenPayload123456!".toByteArray(Charsets.UTF_8)
        val encrypted = BiometricKeyManager.encryptData(plaintext)

        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())
        assertFalse(plaintext.contentEquals(encrypted))

        val cipher = BiometricKeyManager.createDecryptionCipher()
        val decrypted = BiometricKeyManager.decryptData(cipher, encrypted)

        assertEquals("SensitiveMasterTokenPayload123456!", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testCardSecretSealingAndUnsealing() {
        val secretColor = "rose"
        val ruleHint = "Read rose tiles diagonally down-right starting at Row 1, Col 1"
        val totpSecret = "JBSWY3DPEHPK3PXP"

        // 1. Seal card secret using Keystore public key
        val encryptedSecretBase64 = BiometricKeyManager.encryptCardSecret(
            secretColor = secretColor,
            ruleHint = ruleHint,
            totpSecret = totpSecret
        )

        assertNotNull(encryptedSecretBase64)
        assertTrue(encryptedSecretBase64.isNotBlank())
        assertFalse(encryptedSecretBase64.contains("rose"))
        assertFalse(encryptedSecretBase64.contains("JBSWY3DPEHPK3PXP"))

        // 2. Unseal card secret using authenticated decryption cipher
        val cipher = BiometricKeyManager.createDecryptionCipher()
        val decrypted = BiometricKeyManager.decryptCardSecret(cipher, encryptedSecretBase64)

        assertEquals(secretColor, decrypted.secretColor)
        assertEquals(ruleHint, decrypted.ruleHint)
        assertEquals(totpSecret, decrypted.totpSecret)
    }

    @Test
    fun testCardSecretWithoutTotpUnsealsCorrectly() {
        val secretColor = "emerald"
        val ruleHint = "Emerald tiles along column 2"

        val encryptedBase64 = BiometricKeyManager.encryptCardSecret(
            secretColor = secretColor,
            ruleHint = ruleHint,
            totpSecret = null
        )

        val cipher = BiometricKeyManager.createDecryptionCipher()
        val decrypted = BiometricKeyManager.decryptCardSecret(cipher, encryptedBase64)

        assertEquals("emerald", decrypted.secretColor)
        assertEquals("Emerald tiles along column 2", decrypted.ruleHint)
        assertEquals(null, decrypted.totpSecret)
    }

    @Test
    fun testDecryptionFailsOnCorruptedOrTamperedCiphertext() {
        val original = "HighlyConfidentialData".toByteArray(Charsets.UTF_8)
        val encrypted = BiometricKeyManager.encryptData(original)

        // Corrupt several bytes in the ciphertext
        val corrupted = encrypted.clone()
        corrupted[10] = (corrupted[10] + 1).toByte()
        corrupted[15] = (corrupted[15] + 2).toByte()

        val cipher = BiometricKeyManager.createDecryptionCipher()
        assertThrows(Exception::class.java) {
            BiometricKeyManager.decryptData(cipher, corrupted)
        }
    }

    @Test
    fun testMasterTokenPayloadGenerationAndVerification() {
        val (encryptedToken, expectedHash) = BiometricKeyManager.createMasterTokenPayload()
        assertNotNull(encryptedToken)
        assertNotNull(expectedHash)
        assertEquals(64, expectedHash.length) // SHA-256 hex string

        // Decrypt using Keystore cipher
        val cipher = BiometricKeyManager.createDecryptionCipher()
        val decryptedToken = BiometricKeyManager.decryptData(cipher, encryptedToken)

        // Verification must pass with genuine decrypted token
        assertTrue(BiometricKeyManager.verifyTokenHash(decryptedToken, expectedHash))

        // Verification must fail with tampered token
        val fakeToken = decryptedToken.clone()
        fakeToken[0] = (fakeToken[0] + 1).toByte()
        assertFalse(BiometricKeyManager.verifyTokenHash(fakeToken, expectedHash))
    }

    @Test
    fun testDeleteKeyGeneratesNewKeyPair() {
        val keyPair1 = BiometricKeyManager.getOrCreateKeyPair()
        val pubKey1Encoded = keyPair1.public.encoded

        BiometricKeyManager.deleteKey()

        val keyPair2 = BiometricKeyManager.getOrCreateKeyPair()
        val pubKey2Encoded = keyPair2.public.encoded

        // A new distinct keypair must be generated
        assertFalse(pubKey1Encoded.contentEquals(pubKey2Encoded))
    }
}
