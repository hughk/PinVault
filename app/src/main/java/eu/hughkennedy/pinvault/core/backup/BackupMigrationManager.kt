package eu.hughkennedy.pinvault.core.backup

import eu.hughkennedy.pinvault.core.engine.DecoyRandomizer
import eu.hughkennedy.pinvault.core.engine.PaintedCell
import eu.hughkennedy.pinvault.core.model.CardEntity
import eu.hughkennedy.pinvault.core.model.TileData
import eu.hughkennedy.pinvault.core.security.CryptoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
data class BackupContainer(
    val app: String = "PIN_VAULT_ANDROID",
    val version: Int = 1,
    val algorithm: String = "AES-256-GCM-PBKDF2",
    val saltBase64: String = "",
    val ivBase64: String = "",
    val ciphertextBase64: String = "",
    val data: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val cardCount: Int = 0
)

object BackupMigrationManager {

    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
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
     * Decrypts an encrypted .pinvault or legacy .pinkeeper backup payload, prototype export,
     * or parses raw card JSON arrays.
     * Throws an exception if passphrase is incorrect or format is corrupt.
     */
    fun importVault(backupJson: String, passphrase: String = ""): List<CardEntity> {
        val trimmed = backupJson.trim()
        require(trimmed.isNotBlank()) { "Backup content must not be blank" }

        // If the backup is a raw JSON array of cards directly
        if (trimmed.startsWith("[")) {
            return parseCardsJson(trimmed)
        }

        val container = json.decodeFromString<BackupContainer>(trimmed)
        val appUpper = container.app.uppercase().trim()
        val validApps = setOf("PIN_VAULT_ANDROID", "PIN_KEEPER_ANDROID", "PIN_VAULT", "PIN_KEEPER", "PINVAULT", "PINKEEPER")
        require(appUpper in validApps) {
            "Invalid backup format: unrecognized application identifier '${container.app}'"
        }

        // Case 1: Standard encrypted backup (AES-256-GCM)
        if (container.ciphertextBase64.isNotBlank()) {
            require(passphrase.isNotBlank()) { "Passphrase must not be blank for encrypted backup" }
            val salt = CryptoManager.fromBase64(container.saltBase64)
            val iv = CryptoManager.fromBase64(container.ivBase64)
            val ciphertext = CryptoManager.fromBase64(container.ciphertextBase64)

            val key = CryptoManager.deriveKey(passphrase.toCharArray(), salt)
            val decryptedBytes = CryptoManager.decryptAesGcm(ciphertext, key, iv)
            val decryptedJson = String(decryptedBytes, Charsets.UTF_8)
            return parseCardsJson(decryptedJson)
        }

        // Case 2: Prototype / Web unencrypted base64 payload in 'data'
        if (container.data.isNotBlank()) {
            val decodedJson = String(CryptoManager.fromBase64(container.data), Charsets.UTF_8)
            return parseCardsJson(decodedJson)
        }

        return emptyList()
    }

    /**
     * Robust parser for card JSON strings.
     * Handles:
     * - Top-level JSON array `[ ... ]`
     * - Top-level JSON object wrapping cards `{ "cards": [ ... ] }` or `{ "data": [ ... ] }`
     * - Single card object `{ ... }`
     * - Prototype cards with `pinCells` and empty/missing `tiles`
     * - Missing fields (`folder`, `isTotp`, `pinPath`)
     * - Legacy `r`/`c` tile keys
     * - Normalizes cards (reconstructs `pinPath` from PIN tiles if missing)
     */
    fun parseCardsJson(rawJson: String): List<CardEntity> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) return emptyList()

        val jsonElement = try {
            json.parseToJsonElement(trimmed)
        } catch (e: Exception) {
            return emptyList()
        }

        val cardArray: List<JsonElement> = when (jsonElement) {
            is JsonArray -> jsonElement
            is JsonObject -> {
                val inner = jsonElement["cards"] ?: jsonElement["data"] ?: jsonElement["CARDS"]
                if (inner is JsonArray) inner else listOf(jsonElement)
            }
            else -> emptyList()
        }

        return cardArray.mapNotNull { element ->
            try {
                if (element !is JsonObject) return@mapNotNull null
                val tilesElement = element["tiles"]
                val pinCellsElement = element["pinCells"]

                // If tiles is missing or empty, but pinCells exists (e.g. web prototype export)
                val transformedObj = if ((tilesElement == null || (tilesElement is JsonArray && tilesElement.isEmpty())) &&
                    pinCellsElement is JsonArray && pinCellsElement.isNotEmpty()
                ) {
                    val cols = element["cols"]?.jsonPrimitive?.intOrNull ?: 6
                    val rows = element["rows"]?.jsonPrimitive?.intOrNull ?: 7
                    val secretColor = element["secretColor"]?.jsonPrimitive?.contentOrNull ?: "blue"

                    val pinCellsList = pinCellsElement.mapNotNull { cellElem ->
                        if (cellElem is JsonObject) {
                            val r = cellElem["row"]?.jsonPrimitive?.intOrNull
                                ?: cellElem["r"]?.jsonPrimitive?.intOrNull ?: 0
                            val c = cellElem["col"]?.jsonPrimitive?.intOrNull
                                ?: cellElem["c"]?.jsonPrimitive?.intOrNull ?: 0
                            val d = cellElem["digit"]?.jsonPrimitive?.contentOrNull
                                ?: cellElem["d"]?.jsonPrimitive?.contentOrNull ?: "?"
                            TileData(row = r, col = c, digit = d, colorId = secretColor, isPinTile = true)
                        } else null
                    }

                    val fullMatrix = DecoyRandomizer.createBlankMatrix(cols, rows, secretColor).map { blankTile ->
                        val matchingPin = pinCellsList.find { it.row == blankTile.row && it.col == blankTile.col }
                        matchingPin ?: blankTile
                    }

                    val mutableMap = element.toMutableMap()
                    mutableMap["tiles"] = json.encodeToJsonElement(fullMatrix)
                    if (element["pinPath"] == null || (element["pinPath"] is JsonArray && (element["pinPath"] as JsonArray).isEmpty())) {
                        val pathList = pinCellsList.map { PaintedCell(it.row, it.col) }
                        mutableMap["pinPath"] = json.encodeToJsonElement(pathList)
                    }
                    JsonObject(mutableMap)
                } else {
                    element
                }

                val card = json.decodeFromJsonElement<CardEntity>(transformedObj)
                normalizeCard(card)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Normalizes a card:
     * - Reconstructs pinPath from isPinTile tiles if pinPath is empty
     * - Generates matrix tiles if empty
     * - Assigns valid UUID if blank
     */
    fun normalizeCard(card: CardEntity): CardEntity {
        val normalizedPath = if (card.pinPath.isEmpty()) {
            val pinTiles = card.tiles.filter { it.isPinTile }
            if (pinTiles.isNotEmpty()) {
                pinTiles.map { PaintedCell(it.row, it.col) }
            } else {
                emptyList()
            }
        } else {
            card.pinPath
        }

        val normalizedTiles = if (card.tiles.isEmpty() && card.cols > 0 && card.rows > 0) {
            DecoyRandomizer.createBlankMatrix(card.cols, card.rows, card.secretColor)
        } else {
            card.tiles
        }

        val normalizedId = if (card.id.isBlank()) UUID.randomUUID().toString() else card.id
        val normalizedModified = if (card.lastModified <= 0L) System.currentTimeMillis() else card.lastModified

        return card.copy(
            id = normalizedId,
            pinPath = normalizedPath,
            tiles = normalizedTiles,
            lastModified = normalizedModified
        )
    }
}
