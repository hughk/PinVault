package com.example.pinkeeper.core.repository

import android.content.Context
import com.example.pinkeeper.core.engine.DecoyRandomizer
import com.example.pinkeeper.core.model.CardCategory
import com.example.pinkeeper.core.model.CardEntity
import com.example.pinkeeper.core.model.TileData
import com.example.pinkeeper.core.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class VaultRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val vaultFile: File get() = File(context.filesDir, "vault_cards.json")
    private val prefs = context.getSharedPreferences("pin_keeper_prefs", Context.MODE_PRIVATE)

    private val _cards = MutableStateFlow<List<CardEntity>>(emptyList())
    val cards: StateFlow<List<CardEntity>> = _cards.asStateFlow()

    init {
        loadCards()
    }

    private fun loadCards() {
        if (!vaultFile.exists()) {
            val initialCards = createSampleCards()
            saveCards(initialCards)
            _cards.value = initialCards
        } else {
            try {
                val content = vaultFile.readText(Charsets.UTF_8)
                val loaded = json.decodeFromString<List<CardEntity>>(content)
                _cards.value = loaded
            } catch (e: Exception) {
                val initialCards = createSampleCards()
                saveCards(initialCards)
                _cards.value = initialCards
            }
        }
    }

    private fun saveCards(cardList: List<CardEntity>) {
        try {
            val serialized = json.encodeToString(cardList)
            vaultFile.writeText(serialized, Charsets.UTF_8)
            _cards.value = cardList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCard(card: CardEntity) {
        val updated = _cards.value + card
        saveCards(updated)
    }

    fun updateCard(updatedCard: CardEntity) {
        val updated = _cards.value.map {
            if (it.id == updatedCard.id) updatedCard else it
        }
        saveCards(updated)
    }

    fun deleteCard(cardId: String) {
        val updated = _cards.value.filter { it.id != cardId }
        saveCards(updated)
    }

    fun restoreBackup(importedCards: List<CardEntity>, replace: Boolean) {
        val updated = if (replace) {
            importedCards
        } else {
            val currentMap = _cards.value.associateBy { it.id }.toMutableMap()
            importedCards.forEach { currentMap[it.id] = it }
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

        return listOf(
            CardEntity(
                id = "sample-card-1",
                name = "Barclays Visa Debit",
                category = CardCategory.DEBIT,
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
                cols = 5,
                rows = 6,
                secretColor = "amber",
                ruleHint = "Amber tiles in reverse L-shape: Row 1 across, then down Col 4",
                tiles = card3Randomized
            )
        )
    }
}
