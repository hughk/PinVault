package eu.hughkennedy.pinvault.core.engine

import eu.hughkennedy.pinvault.core.model.PaletteColor
import eu.hughkennedy.pinvault.core.model.TileData
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.random.Random
import kotlin.random.asKotlinRandom

object DecoyRandomizer {

    val CSPRNG: Random = SecureRandom().asKotlinRandom()

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
     * Finds a valid decoy line where all tiles share the same colour.
     * The secret colour is allowed, but then no line tile may be adjacent to a genuine PIN tile,
     * so the line cannot be mistaken for a continuation of the real PIN.
     */
    fun findValidDecoyLine(
        rows: Int,
        cols: Int,
        pinTiles: List<TileData>,
        secretColor: String,
        palette: List<String> = PaletteColor.ALL.map { it.id },
        length: Int = 4,
        random: Random = CSPRNG
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

                // A secret-colour line must not touch the genuine PIN
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
     * Picks a decoy colour. Decoys may use the secret colour (15% chance) so the secret colour
     * is not a statistical outlier, but never adjacent to a genuine PIN tile.
     */
    fun pickDecoyColor(
        r: Int,
        c: Int,
        secretColor: String,
        pinTiles: List<TileData>,
        palette: List<String> = PaletteColor.ALL.map { it.id },
        random: Random = CSPRNG
    ): String {
        val adjacent = isAdjacentToPin(r, c, pinTiles, secretColor)
        val otherColors = palette.filter { it != secretColor }

        return if (adjacent || random.nextFloat() >= SECRET_COLOR_DECOY_PROBABILITY) {
            otherColors.random(random)
        } else {
            secretColor
        }
    }

    private const val SECRET_COLOR_DECOY_PROBABILITY = 0.15f

    /**
     * Re-randomizes all decoy tiles in the matrix while keeping all genuine PIN tiles unchanged.
     * Places a linear decoy PIN (horizontal, vertical, or diagonal) where every tile in the line
     * has the same uniform color.
     * For 6-digit PINs (e.g. TOTP authenticators), the decoy line is 6 digits long.
     * For shorter PINs, the decoy line matches the PIN length (minimum 4).
     */
    @JvmOverloads
    fun randomizeDecoys(
        tiles: List<TileData>,
        secretColor: String,
        decoyLength: Int? = null,
        random: Random = CSPRNG
    ): List<TileData> {
        val pinTiles = tiles.filter { it.isPinTile }
        val rows = (tiles.maxOfOrNull { it.row } ?: 0) + 1
        val cols = (tiles.maxOfOrNull { it.col } ?: 0) + 1

        val targetLength = decoyLength ?: when {
            pinTiles.size >= 6 -> 6
            pinTiles.isNotEmpty() -> pinTiles.size.coerceAtLeast(4)
            else -> 4
        }

        // Try placing a linear decoy PIN of targetLength (or fallback down to 4 if needed)
        var decoyLineResult = findValidDecoyLine(
            rows = rows,
            cols = cols,
            pinTiles = pinTiles,
            secretColor = secretColor,
            length = targetLength,
            random = random
        )

        if (decoyLineResult == null && targetLength > 4) {
            for (fallbackLen in (targetLength - 1) downTo 4) {
                decoyLineResult = findValidDecoyLine(
                    rows = rows,
                    cols = cols,
                    pinTiles = pinTiles,
                    secretColor = secretColor,
                    length = fallbackLen,
                    random = random
                )
                if (decoyLineResult != null) break
            }
        }

        val decoyLineCells = decoyLineResult?.first?.tiles?.toSet() ?: emptySet()
        val decoyLineColor = decoyLineResult?.second

        return tiles.map { tile ->
            if (tile.isPinTile) {
                // Keep genuine PIN tile on drawn path intact - do NOT randomise
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
