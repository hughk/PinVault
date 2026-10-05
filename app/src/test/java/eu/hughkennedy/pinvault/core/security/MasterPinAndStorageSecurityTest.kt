package eu.hughkennedy.pinvault.core.security

import android.content.SharedPreferences
import eu.hughkennedy.pinvault.core.backup.BackupMigrationManager
import eu.hughkennedy.pinvault.core.model.CardCategory
import eu.hughkennedy.pinvault.core.model.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class MasterPinAndStorageSecurityTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var pinManager: MasterPinManager

    @Before
    fun setUp() {
        VaultStorageEncryption.resetFallbackKeyForTesting()
        fakePrefs = FakeSharedPreferences()
        pinManager = MasterPinManager(fakePrefs)
    }

    @Test
    fun testStorageEncryptionRoundtrip() {
        val sampleDatabaseJson = """[{"id":"c1","name":"Card 1","tiles":[]},{"id":"c2","name":"Card 2","tiles":[]}]"""
        val encrypted = VaultStorageEncryption.encrypt(sampleDatabaseJson)

        assertTrue(VaultStorageEncryption.isEncrypted(encrypted))
        assertTrue(encrypted.contains("PINVAULT_ENCRYPTED_DB"))
        assertFalse(encrypted.contains("Card 1"))

        val decrypted = VaultStorageEncryption.decrypt(encrypted)
        assertEquals(sampleDatabaseJson, decrypted)
    }

    @Test
    fun testLegacyStoragePlaintextCompatibility() {
        val legacyPlaintextJson = """[{"id":"legacy-1","name":"Legacy Plaintext","tiles":[]}]"""

        assertFalse(VaultStorageEncryption.isEncrypted(legacyPlaintextJson))
        val result = VaultStorageEncryption.decrypt(legacyPlaintextJson)
        assertEquals(legacyPlaintextJson, result)
    }

    @Test
    fun testStorageTamperDetection() {
        val plaintext = """[{"id":"secret","name":"Private Card"}]"""
        val encrypted = VaultStorageEncryption.encrypt(plaintext)

        // Corrupt the ciphertext payload inside the envelope
        val corrupted = encrypted.replace(""""ciphertextBase64":"""", """"ciphertextBase64":"AAAA"""")
        assertThrows(Exception::class.java) {
            VaultStorageEncryption.decrypt(corrupted)
        }
    }

    @Test
    fun testMasterPinPBKDF2CreationAndVerification() {
        assertFalse(pinManager.isMasterPinSet())

        pinManager.setMasterPin("7890")
        assertTrue(pinManager.isMasterPinSet())

        // Verify correct PIN passes
        assertTrue(pinManager.verifyMasterPin("7890"))

        // Verify wrong PIN fails
        assertFalse(pinManager.verifyMasterPin("1234"))

        // Verify salt is stored and unique
        val salt1 = fakePrefs.getString("master_pin_salt", null)
        assertNotNull(salt1)
        assertEquals("PBKDF2WithHmacSHA256", fakePrefs.getString("master_pin_kdf", null))

        // Re-setting same PIN generates new unique salt
        pinManager.setMasterPin("7890")
        val salt2 = fakePrefs.getString("master_pin_salt", null)
        assertNotNull(salt2)
        assertNotEquals(salt1, salt2)
    }

    @Test
    fun testDefaultInitialPinMigration() {
        assertFalse(pinManager.isMasterPinSet())

        // Default 1234 should verify and trigger auto-migration
        assertTrue(pinManager.verifyMasterPin("1234"))
        assertTrue(pinManager.isMasterPinSet())
        assertEquals("PBKDF2WithHmacSHA256", fakePrefs.getString("master_pin_kdf", null))

        // Now subsequent attempts verify against the PBKDF2 hash
        assertTrue(pinManager.verifyMasterPin("1234"))
        assertFalse(pinManager.verifyMasterPin("9999"))
    }

    @Test
    fun testLegacySha256PinUpwardsMigration() {
        val legacyPin = "4321"
        val staticSalt = "pin_keeper_local_salt"
        val md = MessageDigest.getInstance("SHA-256")
        val legacyHashBytes = md.digest("$legacyPin:$staticSalt".toByteArray(Charsets.UTF_8))
        val legacyHashHex = legacyHashBytes.joinToString("") { "%02x".format(it) }

        // Store legacy format in prefs (without salt or kdf key)
        fakePrefs.edit().putString("master_pin_hash", legacyHashHex).apply()
        assertFalse(fakePrefs.contains("master_pin_salt"))
        assertFalse(fakePrefs.contains("master_pin_kdf"))

        // First verification should detect legacy hash, verify constant-time, and auto-upgrade to PBKDF2
        assertTrue(pinManager.verifyMasterPin(legacyPin))

        // Verify it was upgraded
        assertEquals("PBKDF2WithHmacSHA256", fakePrefs.getString("master_pin_kdf", null))
        assertNotNull(fakePrefs.getString("master_pin_salt", null))

        // Verify new hash still verifies correctly
        assertTrue(pinManager.verifyMasterPin(legacyPin))
        assertFalse(pinManager.verifyMasterPin("0000"))
    }

    @Test
    fun testLockoutRateLimiting() {
        pinManager.setMasterPin("9876")
        assertFalse(pinManager.isLockedOut())
        assertEquals(0L, pinManager.getRemainingLockoutSeconds())

        // 4 failed attempts: not locked out yet
        for (i in 1..4) {
            assertFalse(pinManager.verifyMasterPin("0000"))
            assertFalse(pinManager.isLockedOut())
        }

        // 5th failed attempt: triggers 30s lockout
        assertFalse(pinManager.verifyMasterPin("0000"))
        assertTrue(pinManager.isLockedOut())
        assertTrue(pinManager.getRemainingLockoutSeconds() > 0)

        // While locked out, even the correct PIN is rejected
        assertFalse(pinManager.verifyMasterPin("9876"))
    }

    @Test
    fun testPassphraseLengthValidationForExportVsImport() {
        val card = CardEntity(name = "Test Card", category = CardCategory.OTHER)

        // Export requires >= 8 characters
        assertThrows(IllegalArgumentException::class.java) {
            BackupMigrationManager.exportVault(listOf(card), "short")
        }

        val validBackup = BackupMigrationManager.exportVault(listOf(card), "EightChars1!")
        assertTrue(validBackup.isNotBlank())

        // Import works with valid backup
        val restored = BackupMigrationManager.importVault(validBackup, "EightChars1!")
        assertEquals(1, restored.size)
    }

    /**
     * In-memory mock implementation of SharedPreferences for JVM tests.
     */
    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val storage: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) if (value != null) pending[key] = value else pending.remove(key)
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) if (values != null) pending[key] = values else pending.remove(key)
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearFlag) {
                    storage.clear()
                    clearFlag = false
                }
                for ((k, v) in pending) {
                    if (v == null) storage.remove(k) else storage[k] = v
                }
                pending.clear()
            }
        }
    }
}
