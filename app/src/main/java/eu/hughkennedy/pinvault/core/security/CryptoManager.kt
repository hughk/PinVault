package eu.hughkennedy.pinvault.core.security

import java.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_GCM_CIPHER = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val ITERATION_COUNT = 100000
    private const val KEY_LENGTH = 256

    private val secureRandom = SecureRandom()

    fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /**
     * Derives an AES-256 SecretKey from a user passphrase and salt using PBKDF2.
     * Clears the PBEKeySpec internal password cache upon completion.
     */
    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(passphrase, salt, ITERATION_COUNT, KEY_LENGTH)
        return try {
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Constant-time byte array equality comparison resistant to timing side-channels.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return java.security.MessageDigest.isEqual(a, b)
    }

    /**
     * Overwrites character arrays with zeros to minimize exposure in memory.
     */
    fun wipe(chars: CharArray) {
        java.util.Arrays.fill(chars, '\u0000')
    }

    /**
     * Overwrites byte arrays with zeros to minimize exposure in memory.
     */
    fun wipe(bytes: ByteArray) {
        java.util.Arrays.fill(bytes, 0.toByte())
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with the derived key.
     * Returns the ciphertext with the GCM authentication tag.
     */
    fun encryptAesGcm(plaintext: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_CIPHER)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypts ciphertext bytes using AES-256-GCM.
     * Throws an exception if the tag is invalid (e.g. wrong password or modified ciphertext).
     */
    fun decryptAesGcm(ciphertext: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_CIPHER)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    fun toBase64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(base64: String): ByteArray =
        Base64.getDecoder().decode(base64.trim())
}
