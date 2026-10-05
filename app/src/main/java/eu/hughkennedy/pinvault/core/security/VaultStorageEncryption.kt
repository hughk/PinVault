package eu.hughkennedy.pinvault.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Transparent hardware-backed storage encryption for the local cards database (vault_cards.json).
 *
 * Uses AES-256-GCM authenticated encryption. On Android, the AES key is stored inside the hardware
 * Android KeyStore (TEE / StrongBox). In non-Android / JVM unit testing environments, falls back to
 * an in-memory AES-256 secret key so tests execute seamlessly.
 *
 * Provides backwards compatibility: if the input is legacy plaintext JSON, it returns it unmolested
 * so upstream loaders can parse and automatically rewrite it in encrypted format.
 */
object VaultStorageEncryption {

    const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val STORAGE_KEY_ALIAS = "pin_vault_storage_encryption_key"
    private const val AES_GCM_CIPHER = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH_BYTES = 12
    const val ENCRYPTED_MAGIC = "PINVAULT_ENCRYPTED_DB"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class EncryptedEnvelope(
        val version: Int = 1,
        val magic: String = ENCRYPTED_MAGIC,
        val ivBase64: String,
        val ciphertextBase64: String
    )

    private var fallbackTestKey: SecretKey? = null

    private fun isAndroidKeyStoreAvailable(): Boolean {
        return Security.getProvider(KEYSTORE_PROVIDER) != null
    }

    private fun getOrCreateStorageKey(): SecretKey {
        if (!isAndroidKeyStoreAvailable()) {
            if (fallbackTestKey == null) {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                fallbackTestKey = keyGen.generateKey()
            }
            return fallbackTestKey!!
        }

        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(STORAGE_KEY_ALIAS)) {
            val key = keyStore.getKey(STORAGE_KEY_ALIAS, null) as? SecretKey
            if (key != null) return key
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        val spec = KeyGenParameterSpec.Builder(
            STORAGE_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Storage at rest key is accessible to the app process
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Checks if the given raw file content is encrypted with the storage envelope.
     */
    fun isEncrypted(rawContent: String): Boolean {
        val trimmed = rawContent.trim()
        return trimmed.startsWith("{") && trimmed.contains("\"$ENCRYPTED_MAGIC\"")
    }

    /**
     * Encrypts plaintext database JSON using hardware KeyStore AES-256-GCM.
     */
    fun encrypt(plaintext: String): String {
        val key = getOrCreateStorageKey()
        val iv = CryptoManager.generateRandomBytes(IV_LENGTH_BYTES)

        val cipher = Cipher.getInstance(AES_GCM_CIPHER)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val envelope = EncryptedEnvelope(
            ivBase64 = CryptoManager.toBase64(iv),
            ciphertextBase64 = CryptoManager.toBase64(ciphertext)
        )
        return json.encodeToString(envelope)
    }

    /**
     * Decrypts the raw file content. If the content is legacy unencrypted JSON (e.g. starts with '['),
     * it returns the raw content directly to allow seamless upwards migration.
     */
    fun decrypt(rawContent: String): String {
        val trimmed = rawContent.trim()
        if (trimmed.isBlank()) return ""

        if (!isEncrypted(trimmed)) {
            // Legacy unencrypted cards JSON: return as-is for automated migration
            return rawContent
        }

        val envelope = json.decodeFromString<EncryptedEnvelope>(trimmed)
        val key = getOrCreateStorageKey()
        val iv = CryptoManager.fromBase64(envelope.ivBase64)
        val ciphertext = CryptoManager.fromBase64(envelope.ciphertextBase64)

        val cipher = Cipher.getInstance(AES_GCM_CIPHER)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    fun resetFallbackKeyForTesting() {
        fallbackTestKey = null
    }
}
