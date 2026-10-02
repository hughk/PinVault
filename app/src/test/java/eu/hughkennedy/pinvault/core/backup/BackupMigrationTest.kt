package eu.hughkennedy.pinvault.core.backup

import eu.hughkennedy.pinvault.core.engine.DecoyRandomizer
import eu.hughkennedy.pinvault.core.engine.PaintedCell
import eu.hughkennedy.pinvault.core.model.CardCategory
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.model.TileData
import eu.hughkennedy.pinvault.core.security.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(encryptedBackup.contains("PIN_VAULT_ANDROID"))
        assertTrue(encryptedBackup.contains("AES-256-GCM-PBKDF2"))

        // Decrypt with correct passphrase
        val restoredCards = BackupMigrationManager.importVault(encryptedBackup, passphrase)
        assertEquals(2, restoredCards.size)
        assertEquals(sampleCards[0].name, restoredCards[0].name)
        assertEquals(sampleCards[1].name, restoredCards[1].name)
        assertEquals(sampleCards[0].folder, restoredCards[0].folder)
        assertEquals(sampleCards[1].folder, restoredCards[1].folder)
        assertEquals(sampleCards[0].cols, restoredCards[0].cols)

        // Attempt decryption with wrong passphrase -> must fail
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

        // Test with legacy PIN_KEEPER_ANDROID
        val legacyBackup1 = modernBackup.replace("PIN_VAULT_ANDROID", "PIN_KEEPER_ANDROID")
        val restored1 = BackupMigrationManager.importVault(legacyBackup1, passphrase)
        assertEquals(1, restored1.size)
        assertEquals("Legacy Card", restored1[0].name)

        // Test with legacy PIN_KEEPER
        val legacyBackup2 = modernBackup.replace("PIN_VAULT_ANDROID", "PIN_KEEPER")
        val restored2 = BackupMigrationManager.importVault(legacyBackup2, passphrase)
        assertEquals(1, restored2.size)
        assertEquals("Legacy Card", restored2[0].name)
    }

    @Test
    fun testVersion0990CardsCompatibilityWithoutFolderTotpOrPinPath() {
        // Raw JSON from v0.990 release without 'folder', 'isTotp', 'totpSecret', 'pinPath'
        val legacyJson = """
            [
              {
                "id": "legacy-990-1",
                "name": "Old Debit Card",
                "category": "DEBIT",
                "cols": 6,
                "rows": 7,
                "secretColor": "blue",
                "ruleHint": "Row 1",
                "tiles": [
                  { "row": 0, "col": 0, "digit": "1", "colorId": "blue", "isPinTile": true },
                  { "row": 0, "col": 1, "digit": "2", "colorId": "blue", "isPinTile": true },
                  { "row": 0, "col": 2, "digit": "3", "colorId": "blue", "isPinTile": true },
                  { "row": 0, "col": 3, "digit": "4", "colorId": "blue", "isPinTile": true },
                  { "row": 0, "col": 4, "digit": "9", "colorId": "emerald", "isPinTile": false }
                ],
                "lastModified": 1727000000000
              }
            ]
        """.trimIndent()

        val cards = BackupMigrationManager.parseCardsJson(legacyJson)
        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("legacy-990-1", card.id)
        assertEquals("Old Debit Card", card.name)
        assertEquals(CardCategory.DEBIT, card.category)
        assertEquals("", card.folder) // Safely defaulted
        assertFalse(card.isTotp) // Safely defaulted
        assertEquals(5, card.tiles.size)

        // Automatic pinPath reconstruction from isPinTile
        assertEquals(4, card.pinPath.size)
        assertEquals(PaintedCell(0, 0), card.pinPath[0])
        assertEquals(PaintedCell(0, 1), card.pinPath[1])
        assertEquals(PaintedCell(0, 2), card.pinPath[2])
        assertEquals(PaintedCell(0, 3), card.pinPath[3])
    }

    @Test
    fun testLegacyTileCoordinatesAliasing() {
        // Tile JSON using 'r', 'c', 'd', 'color', 'isPin'
        val legacyTileJson = """
            [
              {
                "id": "tile-alias-card",
                "name": "Aliased Tiles",
                "category": "CREDIT",
                "cols": 3,
                "rows": 3,
                "secretColor": "amber",
                "tiles": [
                  { "r": 1, "c": 2, "digit": "7", "color": "amber", "isPin": true },
                  { "r": 2, "c": 0, "d": "9", "colorId": "blue", "pin": false }
                ]
              }
            ]
        """.trimIndent()

        val cards = BackupMigrationManager.parseCardsJson(legacyTileJson)
        assertEquals(1, cards.size)
        val tiles = cards[0].tiles
        assertEquals(2, tiles.size)

        val tile1 = tiles[0]
        assertEquals(1, tile1.row)
        assertEquals(2, tile1.col)
        assertEquals("7", tile1.digit)
        assertEquals("amber", tile1.colorId)
        assertTrue(tile1.isPinTile)

        val tile2 = tiles[1]
        assertEquals(2, tile2.row)
        assertEquals(0, tile2.col)
        assertEquals("9", tile2.digit)
        assertEquals("blue", tile2.colorId)
        assertFalse(tile2.isPinTile)
    }

    @Test
    fun testCardCategoryLegacyAndCaseInsensitive() {
        val json = """
            [
              { "name": "Card 1", "category": "credit" },
              { "name": "Card 2", "category": "debit" },
              { "name": "Card 3", "category": "banking" },
              { "name": "Card 4", "category": "safe" },
              { "name": "Card 5", "category": "authenticator" },
              { "name": "Card 6", "category": "totp" },
              { "name": "Card 7", "category": "2fa" },
              { "name": "Card 8", "category": "Credit Card" },
              { "name": "Card 9", "category": "Safe & Door Access" },
              { "name": "Card 10", "category": "unknown_future_category" }
            ]
        """.trimIndent()

        val cards = BackupMigrationManager.parseCardsJson(json)
        assertEquals(10, cards.size)
        assertEquals(CardCategory.CREDIT, cards[0].category)
        assertEquals(CardCategory.DEBIT, cards[1].category)
        assertEquals(CardCategory.BANKING, cards[2].category)
        assertEquals(CardCategory.SAFE, cards[3].category)
        assertEquals(CardCategory.AUTHENTICATOR, cards[4].category)
        assertEquals(CardCategory.AUTHENTICATOR, cards[5].category)
        assertEquals(CardCategory.AUTHENTICATOR, cards[6].category)
        assertEquals(CardCategory.CREDIT, cards[7].category)
        assertEquals(CardCategory.SAFE, cards[8].category)
        assertEquals(CardCategory.OTHER, cards[9].category)
    }

    @Test
    fun testPrototypeUnencryptedDataBackup() {
        val cardsJson = """
            [
              {
                "id": "proto-1",
                "name": "Prototype Matrix",
                "category": "credit",
                "cols": 6,
                "rows": 7,
                "secretColor": "blue",
                "tiles": [
                  { "r": 0, "c": 0, "digit": "5", "colorId": "blue", "isPinTile": true }
                ]
              }
            ]
        """.trimIndent()

        val base64Data = CryptoManager.toBase64(cardsJson.toByteArray(Charsets.UTF_8))
        val prototypeBackupEnvelope = """
            {
              "app": "PIN_VAULT_ANDROID",
              "version": 2,
              "algorithm": "AES-256-GCM-PBKDF2",
              "cardCount": 1,
              "data": "$base64Data"
            }
        """.trimIndent()

        val restored = BackupMigrationManager.importVault(prototypeBackupEnvelope)
        assertEquals(1, restored.size)
        assertEquals("Prototype Matrix", restored[0].name)
        assertEquals(CardCategory.CREDIT, restored[0].category)
    }

    @Test
    fun testPrototypePinCellsConversion() {
        val prototypeCardJson = """
            [
              {
                "id": "card-totp",
                "name": "GitHub 2FA Authenticator",
                "category": "authenticator",
                "folder": "Work",
                "cols": 6,
                "rows": 7,
                "secretColor": "rose",
                "ruleHint": "Dynamic 6-digit rolling TOTP code",
                "isTotp": true,
                "totpSecret": "JBSWY3DPEHPK3PXP",
                "pinCells": [
                  { "r": 0, "c": 0, "digit": "4" },
                  { "r": 0, "c": 1, "digit": "8" },
                  { "r": 0, "c": 2, "digit": "1" },
                  { "r": 1, "c": 2, "digit": "9" },
                  { "r": 1, "c": 1, "digit": "2" },
                  { "r": 1, "c": 0, "digit": "7" }
                ],
                "tiles": []
              }
            ]
        """.trimIndent()

        val cards = BackupMigrationManager.parseCardsJson(prototypeCardJson)
        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("GitHub 2FA Authenticator", card.name)
        assertEquals(CardCategory.AUTHENTICATOR, card.category)
        assertTrue(card.isTotp)
        assertEquals(42, card.tiles.size) // 6x7 matrix generated

        // Verify pinPath
        assertEquals(6, card.pinPath.size)
        assertEquals(PaintedCell(0, 0), card.pinPath[0])
        assertEquals(PaintedCell(0, 1), card.pinPath[1])
        assertEquals(PaintedCell(0, 2), card.pinPath[2])
        assertEquals(PaintedCell(1, 2), card.pinPath[3])
        assertEquals(PaintedCell(1, 1), card.pinPath[4])
        assertEquals(PaintedCell(1, 0), card.pinPath[5])

        // Verify pin tiles in matrix
        val pinTiles = card.tiles.filter { it.isPinTile }
        assertEquals(6, pinTiles.size)
    }

    @Test
    fun testPaintedCellLegacyCoordinates() {
        val json = """
            [
              {
                "name": "Card With Path",
                "pinPath": [
                  { "r": 2, "c": 3 },
                  { "row": 2, "col": 4 }
                ]
              }
            ]
        """.trimIndent()

        val cards = BackupMigrationManager.parseCardsJson(json)
        assertEquals(1, cards.size)
        assertEquals(2, cards[0].pinPath.size)
        assertEquals(PaintedCell(2, 3), cards[0].pinPath[0])
        assertEquals(PaintedCell(2, 4), cards[0].pinPath[1])
    }

    @Test
    fun testRawJsonArrayImport() {
        val rawJson = """
            [
              { "name": "Direct Card 1", "category": "DEBIT" },
              { "name": "Direct Card 2", "category": "CREDIT" }
            ]
        """.trimIndent()

        val imported = BackupMigrationManager.importVault(rawJson)
        assertEquals(2, imported.size)
        assertEquals("Direct Card 1", imported[0].name)
        assertEquals("Direct Card 2", imported[1].name)
    }

    @Test
    fun testWrappedJsonObjectsWithCardsKey() {
        val wrappedJson = """
            {
              "cards": [
                { "name": "Wrapped Card 1", "category": "SAFE" },
                { "name": "Wrapped Card 2", "category": "BANKING" }
              ]
            }
        """.trimIndent()

        val cards = BackupMigrationManager.parseCardsJson(wrappedJson)
        assertEquals(2, cards.size)
        assertEquals("Wrapped Card 1", cards[0].name)
        assertEquals(CardCategory.SAFE, cards[0].category)
        assertEquals("Wrapped Card 2", cards[1].name)
        assertEquals(CardCategory.BANKING, cards[1].category)
    }

    @Test
    fun testCorruptedOrEmptyJsonReturnsEmptyList() {
        assertEquals(0, BackupMigrationManager.parseCardsJson("").size)
        assertEquals(0, BackupMigrationManager.parseCardsJson("   ").size)
        assertEquals(0, BackupMigrationManager.parseCardsJson("not a valid json {]}").size)
        assertEquals(0, BackupMigrationManager.parseCardsJson("12345").size)
    }

    @Test
    fun testCardNormalizationPreservesExistingValues() {
        val modernCard = CardEntity(
            id = "custom-id-999",
            name = "Modern Card",
            category = CardCategory.CREDIT,
            folder = "Business",
            cols = 6,
            rows = 7,
            secretColor = "emerald",
            ruleHint = "Custom rule",
            tiles = DecoyRandomizer.createBlankMatrix(6, 7),
            lastModified = 1729000000000L,
            isTotp = true,
            totpSecret = "MYSECRETKEY",
            pinPath = listOf(PaintedCell(1, 1), PaintedCell(1, 2))
        )

        val normalized = BackupMigrationManager.normalizeCard(modernCard)
        assertEquals("custom-id-999", normalized.id)
        assertEquals("Modern Card", normalized.name)
        assertEquals("Business", normalized.folder)
        assertEquals(1729000000000L, normalized.lastModified)
        assertTrue(normalized.isTotp)
        assertEquals("MYSECRETKEY", normalized.totpSecret)
        assertEquals(2, normalized.pinPath.size)
        assertEquals(PaintedCell(1, 1), normalized.pinPath[0])
        assertEquals(PaintedCell(1, 2), normalized.pinPath[1])
    }
}
