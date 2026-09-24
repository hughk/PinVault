package eu.hughkennedy.pinvault.core.backup

import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.security.CryptoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupContainer(
    val app: String = "PIN_VAULT_ANDROID",
    val version: Int = 1,
    val algorithm: String = "AES-256-GCM-PBKDF2",
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cardCount: Int
)

object BackupMigrationManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Serializes cards into an encrypted .pinvault JSON envelope.
     */
    fun exportVault(cards: List<CardEntity>, passphrase: String): String {
        require(passphrase.isNotBlank()) { "Passphrase must not be blank" }

        val salt = CryptoManager.generateRandomBytes(16)
        val iv = CryptoManager.generateRandomBytes(12)
        val key = CryptoManager.deriveKey(passphrase.toCharArray(), salt)

        val plaintextBytes = json.encodeToString(cards).toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoManager.encryptAesGcm(plaintextBytes, key, iv)

        val container = BackupContainer(
            app = "PIN_VAULT_ANDROID",
            saltBase64 = CryptoManager.toBase64(salt),
            ivBase64 = CryptoManager.toBase64(iv),
            ciphertextBase64 = CryptoManager.toBase64(ciphertext),
            cardCount = cards.size
        )

        return json.encodeToString(container)
    }

    /**
     * Decrypts an encrypted .pinvault or legacy .pinkeeper backup payload using the passphrase.
     * Throws an exception if passphrase is incorrect or format is corrupt.
     */
    fun importVault(backupJson: String, passphrase: String): List<CardEntity> {
        require(passphrase.isNotBlank()) { "Passphrase must not be blank" }

        val container = json.decodeFromString<BackupContainer>(backupJson)
        require(container.app == "PIN_VAULT_ANDROID" || container.app == "PIN_KEEPER_ANDROID") {
            "Invalid backup format: unrecognized application identifier '${container.app}'"
        }
        val salt = CryptoManager.fromBase64(container.saltBase64)
        val iv = CryptoManager.fromBase64(container.ivBase64)
        val ciphertext = CryptoManager.fromBase64(container.ciphertextBase64)

        val key = CryptoManager.deriveKey(passphrase.toCharArray(), salt)
        val decryptedBytes = CryptoManager.decryptAesGcm(ciphertext, key, iv)
        val decryptedJson = String(decryptedBytes, Charsets.UTF_8)

        return json.decodeFromString(decryptedJson)
    }
}
