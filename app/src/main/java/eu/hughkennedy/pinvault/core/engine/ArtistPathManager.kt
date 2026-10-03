package eu.hughkennedy.pinvault.core.engine

import eu.hughkennedy.pinvault.core.model.PaletteColor
import eu.hughkennedy.pinvault.core.model.TileData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = PaintedCellSerializer::class)
data class PaintedCell(
    val row: Int,
    val col: Int
)

object PaintedCellSerializer : KSerializer<PaintedCell> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PaintedCell") {
        element<Int>("row")
        element<Int>("col")
    }

    override fun serialize(encoder: Encoder, value: PaintedCell) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeIntElement(descriptor, 0, value.row)
        composite.encodeIntElement(descriptor, 1, value.col)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PaintedCell {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonObject) {
                val row = element["row"]?.jsonPrimitive?.intOrNull
                    ?: element["r"]?.jsonPrimitive?.intOrNull
                    ?: element["row"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: element["r"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: 0
                val col = element["col"]?.jsonPrimitive?.intOrNull
                    ?: element["c"]?.jsonPrimitive?.intOrNull
                    ?: element["col"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: element["c"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: 0
                return PaintedCell(row, col)
            }
        }
        val composite = decoder.beginStructure(descriptor)
        var row = 0
        var col = 0
        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> row = composite.decodeIntElement(descriptor, 0)
                1 -> col = composite.decodeIntElement(descriptor, 1)
                else -> {}
            }
        }
        composite.endStructure(descriptor)
        return PaintedCell(row, col)
    }
}

object ArtistPathManager {

    /**
     * Maps a string of digits (e.g. "1234") onto a sequence of fingerpainted cells
     * in the exact order and direction they were drawn.
     *
     * For example, if a line of 4 cells is drawn from right to left along Row 1:
     * - paintedPath[0] = (1, 3) receives '1'
     * - paintedPath[1] = (1, 2) receives '2'
     * - paintedPath[2] = (1, 1) receives '3'
     * - paintedPath[3] = (1, 0) receives '4'
     *
     * Looking at Row 1 left-to-right, the matrix displays: 4, 3, 2, 1.
     *
     * @param paintedPath The flat list of unique cells in chronological traversal order.
     * @param digitString The user's entered PIN or digit sequence.
     * @param secretColor The secret color ID chosen for this matrix.
     * @param existingTiles The current list of tiles in the matrix.
     * @return Updated list of tiles with digits placed on painted cells.
     */
    fun mapDigitsToPath(
        paintedPath: List<PaintedCell>,
        digitString: String,
        secretColor: String,
        existingTiles: List<TileData>
    ): List<TileData> {
        val cellIndexMap = paintedPath.mapIndexed { idx, cell -> cell to idx }.toMap()

        return existingTiles.map { tile ->
            val cell = PaintedCell(tile.row, tile.col)
            val pathIndex = cellIndexMap[cell]

            if (pathIndex != null) {
                // This tile is part of the painted path
                if (pathIndex < digitString.length) {
                    val digitChar = digitString[pathIndex].toString()
                    tile.copy(
                        digit = digitChar,
                        colorId = secretColor,
                        isPinTile = true
                    )
                } else {
                    // Painted cell: preserve existing digit if already numeric, otherwise '?'
                    val preservedDigit = if (tile.digit.isNotEmpty() && tile.digit != "?") tile.digit else "?"
                    tile.copy(
                        digit = preservedDigit,
                        colorId = secretColor,
                        isPinTile = true
                    )
                }
            } else {
                // Not in painted path: if it was previously marked as PIN tile in artist mode, clear it
                if (tile.isPinTile) {
                    val decoysActive = existingTiles.any { !it.isPinTile && !it.isBlank }
                    if (decoysActive) {
                        val decoyColor = DecoyRandomizer.pickDecoyColor(tile.row, tile.col, secretColor, emptyList())
                        val randomDigit = (0..9).random().toString()
                        tile.copy(
                            digit = randomDigit,
                            colorId = decoyColor,
                            isPinTile = false
                        )
                    } else {
                        val fallbackColor = PaletteColor.ALL.map { it.id }.firstOrNull { it != secretColor } ?: "blue"
                        tile.copy(
                            digit = "?",
                            colorId = fallbackColor,
                            isPinTile = false
                        )
                    }
                } else {
                    tile
                }
            }
        }
    }

    /**
     * Removes a specific [cellToRemove] from the list of [strokes].
     * Preserves the relative order of all other cells and drops any strokes
     * that become empty as a result.
     * Subsequent cells in the flattened path are automatically renumbered.
     */
    fun removeCellFromStrokes(
        strokes: List<List<PaintedCell>>,
        cellToRemove: PaintedCell
    ): List<List<PaintedCell>> {
        return strokes.map { stroke ->
            stroke.filter { it != cellToRemove }
        }.filter { it.isNotEmpty() }
    }

    /**
     * Rebuilds the ordered PIN path so it always matches the tiles currently marked as PIN tiles.
     * Cells from [previousPath] that are still PIN tiles keep their order (so the digit sequence
     * stays explicit); PIN tiles not yet in the path are appended in the order they appear in
     * [tiles]; cells that are no longer PIN tiles are dropped. This prevents stale strokes from
     * clearing or reordering PIN tiles after switching between Manual and Artist mode.
     */
    fun syncPathWithPinTiles(
        previousPath: List<PaintedCell>,
        tiles: List<TileData>
    ): List<PaintedCell> {
        val pinCells = tiles.filter { it.isPinTile }.map { PaintedCell(it.row, it.col) }
        val pinSet = pinCells.toSet()
        val kept = previousPath.filter { it in pinSet }.distinct()
        val keptSet = kept.toSet()
        return kept + pinCells.filter { it !in keptSet }
    }

    /**
     * Derives the PIN digit string from the tiles along [path], stopping at the first unset ('?')
     * digit so later digits are never shifted onto the wrong cell.
     */
    fun pinStringFromPath(path: List<PaintedCell>, tiles: List<TileData>): String {
        val byCell = tiles.associateBy { PaintedCell(it.row, it.col) }
        val sb = StringBuilder()
        for (cell in path) {
            val digit = byCell[cell]?.digit ?: break
            if (digit.length != 1 || !digit[0].isDigit()) break
            sb.append(digit)
        }
        return sb.toString()
    }

    /**
     * Pads [pin] with cryptographically random digits up to [pathSize]. Returns [pin] unchanged
     * if it is already long enough.
     */
    fun fillMissingDigits(
        pin: String,
        pathSize: Int,
        random: java.util.Random = java.security.SecureRandom()
    ): String {
        if (pin.length >= pathSize) return pin
        val sb = StringBuilder(pin)
        repeat(pathSize - pin.length) { sb.append(random.nextInt(10)) }
        return sb.toString()
    }

    /**
     * Trims a list of strokes to retain at most [targetCellCount] cells in total,
     * preserving stroke sequence and order.
     */
    fun trimStrokes(strokes: List<List<PaintedCell>>, targetCellCount: Int): List<List<PaintedCell>> {
        if (targetCellCount <= 0) return emptyList()
        var remaining = targetCellCount
        val result = mutableListOf<List<PaintedCell>>()
        for (stroke in strokes) {
            if (remaining <= 0) break
            if (stroke.size <= remaining) {
                result.add(stroke)
                remaining -= stroke.size
            } else {
                result.add(stroke.take(remaining))
                remaining = 0
            }
        }
        return result
    }

    /**
     * Computes the bounding slot for a cell given pixel coordinates on the matrix canvas.
     * When [maxRadiusPx] is provided, only offsets within that Euclidean distance from the
     * cell's center are accepted. This creates a deadband between tiles that prevents
     * accidental diagonal corner-clipping of orthogonal neighbor cells.
     */
    fun findCellAtOffset(
        x: Float,
        y: Float,
        cols: Int,
        rows: Int,
        tileSizePx: Float,
        spacingPx: Float,
        paddingPx: Float,
        maxRadiusPx: Float? = null
    ): PaintedCell? {
        val slotSize = tileSizePx + spacingPx
        val relX = x - paddingPx
        val relY = y - paddingPx

        if (relX < -spacingPx / 2f || relY < -spacingPx / 2f) return null

        val col = ((relX + spacingPx / 2f) / slotSize).toInt()
        val row = ((relY + spacingPx / 2f) / slotSize).toInt()

        if (col !in 0 until cols || row !in 0 until rows) return null

        if (maxRadiusPx != null) {
            val centerX = paddingPx + col * slotSize + tileSizePx / 2f
            val centerY = paddingPx + row * slotSize + tileSizePx / 2f
            val dx = x - centerX
            val dy = y - centerY
            if (dx * dx + dy * dy > maxRadiusPx * maxRadiusPx) {
                return null
            }
        }

        return PaintedCell(row, col)
    }

    /**
     * Interpolates intermediate cells between [from] and [to] during fast drag gestures
     * so that high-velocity swipes do not skip cells along diagonal, horizontal, or vertical paths.
     */
    fun interpolateBetween(from: PaintedCell, to: PaintedCell): List<PaintedCell> {
        val dr = to.row - from.row
        val dc = to.col - from.col
        val absDr = kotlin.math.abs(dr)
        val absDc = kotlin.math.abs(dc)

        // Directly adjacent or identical
        if (maxOf(absDr, absDc) <= 1) {
            return listOf(to)
        }

        val result = mutableListOf<PaintedCell>()

        // Pure diagonal jump (e.g. absDr == absDc)
        if (absDr == absDc) {
            val stepR = if (dr > 0) 1 else -1
            val stepC = if (dc > 0) 1 else -1
            for (i in 1..absDr) {
                result.add(PaintedCell(from.row + i * stepR, from.col + i * stepC))
            }
            return result
        }

        // Pure horizontal jump
        if (absDr == 0) {
            val stepC = if (dc > 0) 1 else -1
            for (i in 1..absDc) {
                result.add(PaintedCell(from.row, from.col + i * stepC))
            }
            return result
        }

        // Pure vertical jump
        if (absDc == 0) {
            val stepR = if (dr > 0) 1 else -1
            for (i in 1..absDr) {
                result.add(PaintedCell(from.row + i * stepR, from.col))
            }
            return result
        }

        // General linear interpolation
        val steps = maxOf(absDr, absDc)
        for (i in 1..steps) {
            val r = kotlin.math.round(from.row + (dr.toFloat() * i / steps)).toInt()
            val c = kotlin.math.round(from.col + (dc.toFloat() * i / steps)).toInt()
            val cell = PaintedCell(r, c)
            if (result.isEmpty() || result.last() != cell) {
                result.add(cell)
            }
        }
        return result
    }

    /**
     * Automatically generates a natural-language description / hint of the painted path direction.
     * Supports horizontal (left-to-right, right-to-left), vertical (top-to-bottom, bottom-to-top),
     * diagonal directions, and arbitrary multi-point paths.
     */
    fun generateRuleHint(
        paintedPath: List<PaintedCell>,
        secretColorName: String,
        isGerman: Boolean = false
    ): String {
        if (paintedPath.size < 2) {
            return if (isGerman) {
                "Geheime $secretColorName-Kacheln in der markierten Reihenfolge lesen"
            } else {
                "Read $secretColorName tiles in the marked order"
            }
        }

        val first = paintedPath.first()
        val second = paintedPath[1]
        val allSameRow = paintedPath.all { it.row == first.row }
        val allSameCol = paintedPath.all { it.col == first.col }

        // Horizontal line
        if (allSameRow) {
            val isLeftToRight = second.col > first.col
            return if (isGerman) {
                if (isLeftToRight) {
                    "$secretColorName-Kacheln von links nach rechts in Zeile ${first.row + 1} lesen"
                } else {
                    "$secretColorName-Kacheln von rechts nach links in Zeile ${first.row + 1} lesen"
                }
            } else {
                if (isLeftToRight) {
                    "Read $secretColorName tiles left-to-right along Row ${first.row + 1}"
                } else {
                    "Read $secretColorName tiles right-to-left along Row ${first.row + 1}"
                }
            }
        }

        // Vertical line
        if (allSameCol) {
            val isTopToBottom = second.row > first.row
            return if (isGerman) {
                if (isTopToBottom) {
                    "$secretColorName-Kacheln von oben nach unten in Spalte ${first.col + 1} lesen"
                } else {
                    "$secretColorName-Kacheln von unten nach oben in Spalte ${first.col + 1} lesen"
                }
            } else {
                if (isTopToBottom) {
                    "Read $secretColorName tiles top-to-bottom along Column ${first.col + 1}"
                } else {
                    "Read $secretColorName tiles bottom-to-top along Column ${first.col + 1}"
                }
            }
        }

        // Diagonal checks
        val dRow = second.row - first.row
        val dCol = second.col - first.col
        val isStrictDiagonal = kotlin.math.abs(dRow) == 1 && kotlin.math.abs(dCol) == 1 &&
            paintedPath.zipWithNext().all { (a, b) ->
                (b.row - a.row) == dRow && (b.col - a.col) == dCol
            }

        if (isStrictDiagonal) {
            val dirDesc = when {
                dRow > 0 && dCol > 0 -> if (isGerman) "diagonal nach rechts-unten" else "diagonally down-right"
                dRow > 0 && dCol < 0 -> if (isGerman) "diagonal nach links-unten" else "diagonally down-left"
                dRow < 0 && dCol > 0 -> if (isGerman) "diagonal nach rechts-oben" else "diagonally up-right"
                else -> if (isGerman) "diagonal nach links-oben" else "diagonally up-left"
            }
            return if (isGerman) {
                "$secretColorName-Kacheln $dirDesc ab Zeile ${first.row + 1}, Spalte ${first.col + 1} lesen"
            } else {
                "Read $secretColorName tiles $dirDesc starting at Row ${first.row + 1}, Col ${first.col + 1}"
            }
        }

        // Arbitrary / Custom stroke path
        return if (isGerman) {
            "$secretColorName-Kacheln in der gezeichneten Reihenfolge lesen (${paintedPath.size} Kacheln)"
        } else {
            "Read $secretColorName tiles in painted stroke order (${paintedPath.size} tiles)"
        }
    }
}
