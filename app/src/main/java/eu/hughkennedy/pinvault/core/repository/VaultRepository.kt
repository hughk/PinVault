package eu.hughkennedy.pinvault.core.repository

import android.content.Context
import eu.hughkennedy.pinvault.core.backup.BackupMigrationManager
import eu.hughkennedy.pinvault.core.engine.ArtistPathManager
import eu.hughkennedy.pinvault.core.engine.DecoyRandomizer
import eu.hughkennedy.pinvault.core.engine.PaintedCell
import eu.hughkennedy.pinvault.core.model.CardCategory
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.model.TileData
import eu.hughkennedy.pinvault.core.security.CryptoManager
import eu.hughkennedy.pinvault.core.totp.TotpManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

class VaultRepository(private val context: Context) {

    private val vaultFile: File get() = File(context.filesDir, "vault_cards.json")
    private val prefs = context.getSharedPreferences("pin_keeper_prefs", Context.MODE_PRIVATE)

    private val _cards = MutableStateFlow<List<CardEntity>>(emptyList())
    val cards: StateFlow<List<CardEntity>> = _cards.asStateFlow()

    init {
        loadCards()
    }

    private fun loadCards() {
        val legacyCandidates = listOf(
            vaultFile,
            File(context.filesDir, "cards.json"),
            File(context.filesDir, "pin_keeper_cards.json")
        )

        val targetFile = legacyCandidates.firstOrNull { it.exists() && it.length() > 0 }

        if (targetFile == null) {
            val initialCards = createSampleCards()
            saveCards(initialCards)
            _cards.value = initialCards
            return
        }

        try {
            val content = targetFile.readText(Charsets.UTF_8)
            val loaded = BackupMigrationManager.parseCardsJson(content)
            if (loaded.isNotEmpty()) {
                _cards.value = loaded
                // If loaded from a legacy file or if primary vaultFile needs syncing
                if (targetFile != vaultFile || !vaultFile.exists()) {
                    saveCards(loaded)
                }
            } else {
                // Preserve the corrupt file before sample cards overwrite
                createCorruptBackup(targetFile)
                val initialCards = createSampleCards()
                saveCards(initialCards)
                _cards.value = initialCards
            }
        } catch (e: Exception) {
            e.printStackTrace()
            createCorruptBackup(targetFile)
            val initialCards = createSampleCards()
            saveCards(initialCards)
            _cards.value = initialCards
        }
    }

    private fun createCorruptBackup(file: File) {
        try {
            val backup = File(context.filesDir, "vault_cards.json.corrupt_bak")
            file.copyTo(backup, overwrite = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveCards(cardList: List<CardEntity>) {
        try {
            val serialized = BackupMigrationManager.json.encodeToString(cardList)
            vaultFile.writeText(serialized, Charsets.UTF_8)
            _cards.value = cardList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCard(card: CardEntity) {
        val normalized = BackupMigrationManager.normalizeCard(card)
        val updated = _cards.value + normalized
        saveCards(updated)
    }

    fun updateCard(updatedCard: CardEntity) {
        val normalized = BackupMigrationManager.normalizeCard(updatedCard)
        val updated = _cards.value.map {
            if (it.id == normalized.id) normalized else it
        }
        saveCards(updated)
    }

    fun deleteCard(cardId: String) {
        val updated = _cards.value.filter { it.id != cardId }
        saveCards(updated)
    }

    fun restoreBackup(importedCards: List<CardEntity>, replace: Boolean) {
        val normalized = importedCards.map { BackupMigrationManager.normalizeCard(it) }
        val updated = if (replace) {
            normalized
        } else {
            val currentMap = _cards.value.associateBy { it.id }.toMutableMap()
            normalized.forEach { currentMap[it.id] = it }
            currentMap.values.toList()
        }
        saveCards(updated)
    }

    fun getCardById(cardId: String): CardEntity? {
        return _cards.value.find { it.id == cardId }
    }

    // Master PIN Management
    fun isMasterPinSet(): Boolean {
        return prefs.contains("master_pin_hash")
    }

    fun setMasterPin(pin: String) {
        val salt = "pin_keeper_local_salt"
        val hash = sha256("$pin:$salt")
        prefs.edit().putString("master_pin_hash", hash).apply()
    }

    fun verifyMasterPin(pin: String): Boolean {
        if (!isMasterPinSet()) {
            // Default master PIN on first launch: 1234
            return pin == "1234"
        }
        val salt = "pin_keeper_local_salt"
        val expected = prefs.getString("master_pin_hash", "") ?: ""
        return sha256("$pin:$salt") == expected
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun createSampleCards(): List<CardEntity> {
        // Sample 1: Barclays Visa Debit (6x7, Blue, diagonal 4-7-1-9)
        val card1Tiles = DecoyRandomizer.createBlankMatrix(6, 7, "blue").map { tile ->
            when {
                tile.row == 0 && tile.col == 0 -> tile.copy(digit = "4", colorId = "blue", isPinTile = true)
                tile.row == 1 && tile.col == 1 -> tile.copy(digit = "7", colorId = "blue", isPinTile = true)
                tile.row == 2 && tile.col == 2 -> tile.copy(digit = "1", colorId = "blue", isPinTile = true)
                tile.row == 3 && tile.col == 3 -> tile.copy(digit = "9", colorId = "blue", isPinTile = true)
                else -> tile
            }
        }
        val card1Randomized = DecoyRandomizer.randomizeDecoys(card1Tiles, "blue")

        // Sample 2: Telephone Banking PIN (6x7, Emerald, Column 3 down: 8-3-0-5-2-9)
        val card2Tiles = DecoyRandomizer.createBlankMatrix(6, 7, "emerald").map { tile ->
            when {
                tile.row == 1 && tile.col == 2 -> tile.copy(digit = "8", colorId = "emerald", isPinTile = true)
                tile.row == 2 && tile.col == 2 -> tile.copy(digit = "3", colorId = "emerald", isPinTile = true)
                tile.row == 3 && tile.col == 2 -> tile.copy(digit = "0", colorId = "emerald", isPinTile = true)
                tile.row == 4 && tile.col == 2 -> tile.copy(digit = "5", colorId = "emerald", isPinTile = true)
                tile.row == 5 && tile.col == 2 -> tile.copy(digit = "2", colorId = "emerald", isPinTile = true)
                tile.row == 6 && tile.col == 2 -> tile.copy(digit = "9", colorId = "emerald", isPinTile = true)
                else -> tile
            }
        }
        val card2Randomized = DecoyRandomizer.randomizeDecoys(card2Tiles, "emerald")

        // Sample 3: Amex Gold Corporate (5x6, Amber, L-shape: 9-2-6-4)
        val card3Tiles = DecoyRandomizer.createBlankMatrix(5, 6, "amber").map { tile ->
            when {
                tile.row == 0 && tile.col == 1 -> tile.copy(digit = "9", colorId = "amber", isPinTile = true)
                tile.row == 0 && tile.col == 2 -> tile.copy(digit = "2", colorId = "amber", isPinTile = true)
                tile.row == 0 && tile.col == 3 -> tile.copy(digit = "6", colorId = "amber", isPinTile = true)
                tile.row == 1 && tile.col == 3 -> tile.copy(digit = "4", colorId = "amber", isPinTile = true)
                else -> tile
            }
        }
        val card3Randomized = DecoyRandomizer.randomizeDecoys(card3Tiles, "amber")

        // Sample 4: GitHub 2FA Authenticator (6x7, Rose, TOTP Authenticator, 6-digit rolling code)
        val card4Path = listOf(
            PaintedCell(0, 0), PaintedCell(0, 1), PaintedCell(0, 2),
            PaintedCell(0, 3), PaintedCell(0, 4), PaintedCell(0, 5)
        )
        val card4TotpCode = TotpManager.generateCode("JBSWY3DPEHPK3PXP", digits = 6)
        val card4Tiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = card4Path,
            digitString = card4TotpCode,
            secretColor = "rose",
            existingTiles = DecoyRandomizer.createBlankMatrix(6, 7, "rose")
        )
        val card4Randomized = DecoyRandomizer.randomizeDecoys(card4Tiles, "rose", decoyLength = 6)

        return listOf(
            CardEntity(
                id = "sample-card-1",
                name = "Barclays Visa Debit",
                category = CardCategory.DEBIT,
                folder = "Personal",
                cols = 6,
                rows = 7,
                secretColor = "blue",
                ruleHint = "Read blue tiles diagonally starting from top-left (Row 1, Col 1)",
                tiles = card1Randomized
            ),
            CardEntity(
                id = "sample-card-2",
                name = "Telephone Banking PIN",
                category = CardCategory.BANKING,
                folder = "Personal",
                cols = 6,
                rows = 7,
                secretColor = "emerald",
                ruleHint = "Emerald tiles reading top-to-bottom along Column 3",
                tiles = card2Randomized
            ),
            CardEntity(
                id = "sample-card-3",
                name = "Amex Gold Corporate",
                category = CardCategory.CREDIT,
                folder = "Work",
                cols = 5,
                rows = 6,
                secretColor = "amber",
                ruleHint = "Amber tiles in reverse L-shape: Row 1 across, then down Col 4",
                tiles = card3Randomized
            ),
            CardEntity(
                id = "sample-card-4",
                name = "GitHub 2FA Authenticator",
                category = CardCategory.AUTHENTICATOR,
                folder = "Work",
                cols = 6,
                rows = 7,
                secretColor = "rose",
                ruleHint = "Dynamic 6-digit rolling TOTP code along top row: (Row 1, Cols 1 to 6)",
                tiles = card4Randomized,
                isTotp = true,
                totpSecret = "JBSWY3DPEHPK3PXP",
                totpDigits = 6,
                totpPeriod = 30,
                totpAlgorithm = "SHA1",
                totpIssuer = "GitHub",
                pinPath = card4Path
            )
        )
    }
}
