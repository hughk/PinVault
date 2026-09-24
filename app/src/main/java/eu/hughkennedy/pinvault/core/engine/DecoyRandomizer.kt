package eu.hughkennedy.pinvault.core.engine

import eu.hughkennedy.pinvault.core.model.PaletteColor
import eu.hughkennedy.pinvault.core.model.TileData
import kotlin.math.abs
import kotlin.random.Random

object DecoyRandomizer {

    enum class LineOrientation {
        HORIZONTAL,
        VERTICAL,
        DIAGONAL_DOWN_RIGHT,
        DIAGONAL_DOWN_LEFT
    }

    data class LineCandidate(
        val tiles: List<Pair<Int, Int>>,
        val orientation: LineOrientation
    )

    /**
     * Checks if cell (r, c) is adjacent (8-neighborhood: horizontal, vertical, or diagonal)
     * to any genuine PIN tile with the given color.
     */
    fun isAdjacentToPin(r: Int, c: Int, pinTiles: List<TileData>, colorId: String): Boolean {
        return pinTiles.any { pin ->
            pin.isPinTile && pin.colorId == colorId &&
                    abs(pin.row - r) <= 1 && abs(pin.col - c) <= 1 &&
                    !(pin.row == r && pin.col == c)
        }
    }

    /**
     * Generates all straight-line candidates (horizontal, vertical, diagonal) of the given length.
     */
    fun generateAllLineCandidates(rows: Int, cols: Int, length: Int = 4): List<LineCandidate> {
        val candidates = mutableListOf<LineCandidate>()
        if (rows < length && cols < length) return candidates

        // 1. Horizontal lines
        if (cols >= length) {
            for (r in 0 until rows) {
                for (c in 0..cols - length) {
                    val cells = (0 until length).map { Pair(r, c + it) }
                    candidates.add(LineCandidate(cells, LineOrientation.HORIZONTAL))
                }
            }
        }

        // 2. Vertical lines
        if (rows >= length) {
            for (r in 0..rows - length) {
                for (c in 0 until cols) {
                    val cells = (0 until length).map { Pair(r + it, c) }
                    candidates.add(LineCandidate(cells, LineOrientation.VERTICAL))
                }
            }
        }

        // 3. Diagonal Down-Right
        if (rows >= length && cols >= length) {
            for (r in 0..rows - length) {
                for (c in 0..cols - length) {
                    val cells = (0 until length).map { Pair(r + it, c + it) }
                    candidates.add(LineCandidate(cells, LineOrientation.DIAGONAL_DOWN_RIGHT))
                }
            }
        }

        // 4. Diagonal Down-Left
        if (rows >= length && cols >= length) {
            for (r in 0..rows - length) {
                for (c in (length - 1) until cols) {
                    val cells = (0 until length).map { Pair(r + it, c - it) }
                    candidates.add(LineCandidate(cells, LineOrientation.DIAGONAL_DOWN_LEFT))
                }
            }
        }

        return candidates
    }

    /**
     * Finds a valid decoy line of at least 4 tiles where all tiles share the same color.
     * If the chosen color is the secret PIN color, none of the line tiles may be adjacent
     * to any genuine PIN tile.
     */
    fun findValidDecoyLine(
        rows: Int,
        cols: Int,
        pinTiles: List<TileData>,
        secretColor: String,
        palette: List<String> = PaletteColor.ALL.map { it.id },
        length: Int = 4,
        random: Random = Random.Default
    ): Pair<LineCandidate, String>? {
        val allLines = generateAllLineCandidates(rows, cols, length)
        if (allLines.isEmpty()) return null

        val shuffledPalette = palette.shuffled(random)

        for (candidateColor in shuffledPalette) {
            val validLinesForColor = allLines.filter { line ->
                // No cell in the decoy line can overlap a genuine PIN tile
                val noOverlap = line.tiles.none { (r, c) ->
                    pinTiles.any { p -> p.row == r && p.col == c }
                }

                // If the decoy line shares the secret PIN color, none of its tiles can be adjacent to the PIN
                val nonAdjacentIfSecret = if (candidateColor == secretColor) {
                    line.tiles.none { (r, c) -> isAdjacentToPin(r, c, pinTiles, secretColor) }
                } else {
                    true
                }

                noOverlap && nonAdjacentIfSecret
            }

            if (validLinesForColor.isNotEmpty()) {
                val chosenLine = validLinesForColor.random(random)
                return Pair(chosenLine, candidateColor)
            }
        }

        return null
    }

    /**
     * Picks a decoy color adhering strictly to the user requirement:
     * "No decoy of the same colour appears adjacent to the pin"
     */
    fun pickDecoyColor(
        r: Int,
        c: Int,
        secretColor: String,
        pinTiles: List<TileData>,
        palette: List<String> = PaletteColor.ALL.map { it.id },
        random: Random = Random.Default
    ): String {
        val adjacent = isAdjacentToPin(r, c, pinTiles, secretColor)
        val otherColors = palette.filter { it != secretColor }

        return if (adjacent) {
            // STRICT RULE: No decoy of the same color may appear adjacent to the PIN
            otherColors.random(random)
        } else {
            // Non-adjacent decoy: 15% probability of using secretColor for statistical camouflage
            if (random.nextFloat() < 0.15f) {
                secretColor
            } else {
                otherColors.random(random)
            }
        }
    }

    /**
     * Re-randomizes all decoy tiles in the matrix while keeping all genuine PIN tiles unchanged.
     * Places a linear decoy PIN of at least 4 tiles (horizontal, vertical, or diagonal)
     * where every tile in the line has the same uniform color.
     */
    fun randomizeDecoys(
        tiles: List<TileData>,
        secretColor: String,
        random: Random = Random.Default
    ): List<TileData> {
        val pinTiles = tiles.filter { it.isPinTile && it.digit != "?" }
        val rows = (tiles.maxOfOrNull { it.row } ?: 0) + 1
        val cols = (tiles.maxOfOrNull { it.col } ?: 0) + 1

        // Find a linear decoy PIN candidate (length 4, uniform color)
        val decoyLineResult = findValidDecoyLine(
            rows = rows,
            cols = cols,
            pinTiles = pinTiles,
            secretColor = secretColor,
            length = 4,
            random = random
        )

        val decoyLineCells = decoyLineResult?.first?.tiles?.toSet() ?: emptySet()
        val decoyLineColor = decoyLineResult?.second

        return tiles.map { tile ->
            if (tile.isPinTile && tile.digit != "?") {
                // Keep genuine PIN tile intact
                tile.copy()
            } else if (decoyLineCells.contains(Pair(tile.row, tile.col)) && decoyLineColor != null) {
                // Part of the linear decoy PIN (uniform color)
                val randomDigit = random.nextInt(10).toString()
                TileData(
                    row = tile.row,
                    col = tile.col,
                    digit = randomDigit,
                    colorId = decoyLineColor,
                    isPinTile = false
                )
            } else {
                // Regular decoy tile: random digit [0-9] and strictly non-adjacent color if matching PIN
                val randomDigit = random.nextInt(10).toString()
                val decoyColor = pickDecoyColor(tile.row, tile.col, secretColor, pinTiles, random = random)
                TileData(
                    row = tile.row,
                    col = tile.col,
                    digit = randomDigit,
                    colorId = decoyColor,
                    isPinTile = false
                )
            }
        }
    }

    /**
     * Creates an empty matrix filled with '?' placeholders.
     */
    fun createBlankMatrix(cols: Int, rows: Int, defaultColor: String = "blue"): List<TileData> {
        val list = mutableListOf<TileData>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                list.add(
                    TileData(
                        row = r,
                        col = c,
                        digit = "?",
                        colorId = defaultColor,
                        isPinTile = false
                    )
                )
            }
        }
        return list
    }
}
