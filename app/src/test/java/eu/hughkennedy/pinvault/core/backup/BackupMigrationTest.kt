package eu.hughkennedy.pinvault.core.backup

import eu.hughkennedy.pinvault.core.engine.DecoyRandomizer
import eu.hughkennedy.pinvault.core.model.CardCategory
import eu.hughkennedy.pinvault.core.model.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupMigrationTest {

    @Test
    fun testBackupAndRestoreCryptographicRoundtrip() {
        val sampleCards = listOf(
            CardEntity(
                name = "Barclays Visa Debit",
                category = CardCategory.DEBIT,
                folder = "Personal",
                cols = 6,
                rows = 7,
                secretColor = "blue",
                ruleHint = "Read diagonal down-right",
                tiles = DecoyRandomizer.createBlankMatrix(6, 7)
            ),
            CardEntity(
                name = "Telephone Banking PIN",
                category = CardCategory.BANKING,
                folder = "Finance",
                cols = 5,
                rows = 6,
                secretColor = "emerald",
                ruleHint = "Column 3 down",
                tiles = DecoyRandomizer.createBlankMatrix(5, 6)
            )
        )

        val passphrase = "SuperSecurePassword123!#"

        // Export to encrypted backup
        val encryptedBackup = BackupMigrationManager.exportVault(sampleCards, passphrase)

        // Verify it contains expected fields
        org.junit.Assert.assertTrue(encryptedBackup.contains("PIN_VAULT_ANDROID"))
        org.junit.Assert.assertTrue(encryptedBackup.contains("AES-256-GCM-PBKDF2"))

        // Decrypt with correct passphrase
        val restoredCards = BackupMigrationManager.importVault(encryptedBackup, passphrase)
        assertEquals(2, restoredCards.size)
        assertEquals(sampleCards[0].name, restoredCards[0].name)
        assertEquals(sampleCards[1].name, restoredCards[1].name)
        assertEquals(sampleCards[0].folder, restoredCards[0].folder)
        assertEquals(sampleCards[1].folder, restoredCards[1].folder)
        assertEquals(sampleCards[0].cols, restoredCards[0].cols)

        // Attempt decryption with wrong passphrase -> must fail with authentication tag error
        assertThrows(Exception::class.java) {
            BackupMigrationManager.importVault(encryptedBackup, "WrongPassword!")
        }
    }

    @Test
    fun testLegacyPinKeeperBackupCompatibility() {
        val sampleCards = listOf(
            CardEntity(
                name = "Legacy Card",
                category = CardCategory.SAFE,
                cols = 5,
                rows = 5,
                secretColor = "amber",
                ruleHint = "L-shape",
                tiles = DecoyRandomizer.createBlankMatrix(5, 5)
            )
        )
        val passphrase = "LegacyPassphrase456!"
        val modernBackup = BackupMigrationManager.exportVault(sampleCards, passphrase)
        // Simulate a legacy backup exported by older version with PIN_KEEPER_ANDROID
        val legacyBackup = modernBackup.replace("PIN_VAULT_ANDROID", "PIN_KEEPER_ANDROID")

        val restored = BackupMigrationManager.importVault(legacyBackup, passphrase)
        assertEquals(1, restored.size)
        assertEquals("Legacy Card", restored[0].name)
    }
}
