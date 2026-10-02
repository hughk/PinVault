package eu.hughkennedy.pinvault.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import javax.crypto.Cipher

/**
 * Manages hardware-backed cryptographic keys in the Android KeyStore.
 *
 * Keys generated here require mandatory user biometric authentication (setUserAuthenticationRequired = true).
 * When BiometricPrompt is presented with a CryptoObject initialized from this KeyStore key,
 * Android's Keymaster/StrongBox TEE only unlocks the private key upon genuine biometric verification.
 *
 * This provides hardware-level protection against Frida/Xposed hooks and malicious memory tampering,
 * ensuring sensitive credentials and vault secrets can only be decrypted when biometric authentication
 * cryptographically succeeds in hardware.
 */
object BiometricKeyManager {

    const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val KEY_ALIAS = "pin_vault_biometric_key"
    const val RSA_CIPHER_MODE = "RSA/ECB/PKCS1Padding"
    private const val KEY_SIZE = 2048

    private const val PREFS_NAME = "pin_keeper_prefs"
    private const val PREF_MASTER_TOKEN_ENC = "biometric_master_token_enc"
    private const val PREF_MASTER_TOKEN_HASH = "biometric_master_token_hash"

    data class DecryptedCardSecret(
        val secretColor: String,
        val ruleHint: String,
        val totpSecret: String?
    )

    private var fallbackKeyPair: KeyPair? = null

    private fun isAndroidKeyStoreAvailable(): Boolean {
        return Security.getProvider(KEYSTORE_PROVIDER) != null
    }

    /**
     * Retrieves or generates the secure KeyStore RSA key pair.
     * In production on Android, the private key resides inside the Android KeyStore / TEE
     * and requires biometric authentication to use.
     */
    fun getOrCreateKeyPair(): KeyPair {
        if (!isAndroidKeyStoreAvailable()) {
            // JVM / Unit-test fallback (in-memory standard RSA)
            if (fallbackKeyPair == null) {
                val kpg = KeyPairGenerator.getInstance("RSA")
                kpg.initialize(KEY_SIZE)
                fallbackKeyPair = kpg.generateKeyPair()
            }
            return fallbackKeyPair!!
        }

        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            val certificate = keyStore.getCertificate(KEY_ALIAS)
            if (privateKey != null && certificate != null) {
                return KeyPair(certificate.publicKey, privateKey)
            }
        }

        return generateKeyStoreKeyPair()
    }

    private fun generateKeyStoreKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEYSTORE_PROVIDER
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setKeySize(KEY_SIZE)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            specBuilder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            specBuilder.setInvalidatedByBiometricEnrollment(true)
        }

        keyPairGenerator.initialize(specBuilder.build())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Initializes a Cipher in DECRYPT_MODE using the KeyStore-backed private key.
     * The resulting Cipher is wrapped in a BiometricPrompt.CryptoObject and can only be
     * authorized for decryption by the hardware TEE upon successful biometric verification.
     */
    fun createDecryptionCipher(): Cipher {
        val keyPair = getOrCreateKeyPair()
        val cipher = Cipher.getInstance(RSA_CIPHER_MODE)
        try {
            cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        } catch (e: Exception) {
            if (isAndroidKeyStoreAvailable() && (e is KeyPermanentlyInvalidatedException || e is java.security.UnrecoverableKeyException)) {
                // Key invalidated due to new biometric enrollment: delete and regenerate
                deleteKey()
                val newKeyPair = getOrCreateKeyPair()
                cipher.init(Cipher.DECRYPT_MODE, newKeyPair.private)
            } else {
                throw e
            }
        }
        return cipher
    }

    /**
     * Encrypts plaintext bytes using the public key from the Android Keystore.
     * Public key encryption does not require user authentication, enabling the app
     * to safely seal credentials and sensitive card secrets at any time without prompting the user.
     */
    fun encryptData(plaintext: ByteArray): ByteArray {
        val keyPair = getOrCreateKeyPair()
        val cipher = Cipher.getInstance(RSA_CIPHER_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.public)
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypts ciphertext bytes using the authenticated Cipher provided by BiometricPrompt.CryptoObject.
     * If authentication was bypassed or hooked (e.g. via Frida), calling cipher.doFinal()
     * will fail with UserNotAuthenticatedException because the Keymaster TEE has not authorized it.
     */
    fun decryptData(cipher: Cipher, ciphertext: ByteArray): ByteArray {
        return cipher.doFinal(ciphertext)
    }

    fun deleteKey() {
        if (isAndroidKeyStoreAvailable()) {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
        fallbackKeyPair = null
    }

    // --- Master Vault Token Management ---

    fun createMasterTokenPayload(): Pair<ByteArray, String> {
        val rawToken = CryptoManager.generateRandomBytes(32)
        val hash = sha256(rawToken)
        val encrypted = encryptData(rawToken)
        return Pair(encrypted, hash)
    }

    fun verifyTokenHash(token: ByteArray, expectedHash: String): Boolean {
        return sha256(token) == expectedHash
    }

    fun ensureMasterToken(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingEnc = prefs.getString(PREF_MASTER_TOKEN_ENC, null)
        val existingHash = prefs.getString(PREF_MASTER_TOKEN_HASH, null)

        if (existingEnc != null && existingHash != null) {
            return CryptoManager.fromBase64(existingEnc)
        }

        val (encrypted, hash) = createMasterTokenPayload()
        val encryptedBase64 = CryptoManager.toBase64(encrypted)

        prefs.edit()
            .putString(PREF_MASTER_TOKEN_ENC, encryptedBase64)
            .putString(PREF_MASTER_TOKEN_HASH, hash)
            .apply()

        return encrypted
    }

    fun getEncryptedMasterToken(context: Context): ByteArray {
        return ensureMasterToken(context)
    }

    fun verifyMasterToken(context: Context, decryptedToken: ByteArray): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expectedHash = prefs.getString(PREF_MASTER_TOKEN_HASH, null) ?: return false
        return verifyTokenHash(decryptedToken, expectedHash)
    }

    // --- Card Secret Sealing & Unsealing ---

    fun encryptCardSecret(secretColor: String, ruleHint: String, totpSecret: String?): String {
        val payload = "$secretColor|$ruleHint|${totpSecret ?: ""}"
        val encrypted = encryptData(payload.toByteArray(Charsets.UTF_8))
        return CryptoManager.toBase64(encrypted)
    }

    fun decryptCardSecret(cipher: Cipher, encryptedBase64: String): DecryptedCardSecret {
        val encryptedBytes = CryptoManager.fromBase64(encryptedBase64)
        val decryptedBytes = decryptData(cipher, encryptedBytes)
        val raw = String(decryptedBytes, Charsets.UTF_8)
        val parts = raw.split("|")
        return DecryptedCardSecret(
            secretColor = parts[0],
            ruleHint = parts.getOrNull(1) ?: "",
            totpSecret = parts.getOrNull(2)?.ifBlank { null }
        )
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
