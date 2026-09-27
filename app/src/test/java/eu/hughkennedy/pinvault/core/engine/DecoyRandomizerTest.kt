package eu.hughkennedy.pinvault.core.engine

import eu.hughkennedy.pinvault.core.model.TileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DecoyRandomizerTest {

    @Test
    fun testNoDecoyOfSameColorAdjacentToPin() {
        val secretColor = "blue"
        // Define a 4-digit PIN along a diagonal: (0,0), (1,1), (2,2), (3,3)
        val initialTiles = DecoyRandomizer.createBlankMatrix(cols = 6, rows = 7, defaultColor = secretColor)
        val pinPositions = setOf(Pair(0, 0), Pair(1, 1), Pair(2, 2), Pair(3, 3))

        val matrixWithPin = initialTiles.map { tile ->
            if (Pair(tile.row, tile.col) in pinPositions) {
                tile.copy(digit = "7", colorId = secretColor, isPinTile = true)
            } else {
                tile
            }
        }

        // Test over 200 randomizations to guarantee consistency across random seeds
        repeat(200) { iteration ->
            val randomized = DecoyRandomizer.randomizeDecoys(matrixWithPin, secretColor)
            val pinTiles = randomized.filter { it.isPinTile }
            val decoyTiles = randomized.filter { !it.isPinTile }

            assertEquals("PIN tile count must be 4", 4, pinTiles.size)

            for (pin in pinTiles) {
                // Check all 8 neighboring positions around this pin tile
                for (decoy in decoyTiles) {
                    val dr = abs(decoy.row - pin.row)
                    val dc = abs(decoy.col - pin.col)
                    val isAdjacent = (dr <= 1 && dc <= 1) && !(dr == 0 && dc == 0)

                    if (isAdjacent) {
                        assertFalse(
                            "Decoy at (${decoy.row}, ${decoy.col}) is adjacent to PIN at (${pin.row}, ${pin.col}) and must NOT have color '$secretColor' (iteration $iteration)",
                            decoy.colorId == secretColor
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testLinearDecoyPinCreatedWithUniformColor() {
        val secretColor = "emerald"
        val rows = 7
        val cols = 6
        // Define PIN at (5, 4), (5, 5), (6, 4), (6, 5) in the bottom corner
        val initialTiles = DecoyRandomizer.createBlankMatrix(cols = cols, rows = rows, defaultColor = secretColor)
        val pinPositions = setOf(Pair(5, 4), Pair(5, 5), Pair(6, 4), Pair(6, 5))

        val matrixWithPin = initialTiles.map { tile ->
            if (Pair(tile.row, tile.col) in pinPositions) {
                tile.copy(digit = "3", colorId = secretColor, isPinTile = true)
            } else {
                tile
            }
        }

        val allLines = DecoyRandomizer.generateAllLineCandidates(rows, cols, 4)
        assertTrue("Grid must have line candidates", allLines.isNotEmpty())

        repeat(50) { iteration ->
            val randomized = DecoyRandomizer.randomizeDecoys(matrixWithPin, secretColor)
            val decoyTilesMap = randomized.filter { !it.isPinTile }.associateBy { Pair(it.row, it.col) }

            // Verify that at least one straight line of length 4 exists where all tiles have the SAME color
            val hasUniformDecoyLine = allLines.any { line ->
                val lineTiles = line.tiles.mapNotNull { decoyTilesMap[it] }
                lineTiles.size == 4 && lineTiles.map { it.colorId }.distinct().size == 1
            }

            assertTrue(
                "A straight line (horizontal, vertical, or diagonal) of length 4 with uniform color must exist (iteration $iteration)",
                hasUniformDecoyLine
            )
        }
    }

    @Test
    fun testPinTilesPreserved() {
        val secretColor = "emerald"
        val tiles = DecoyRandomizer.createBlankMatrix(cols = 5, rows = 5)
        val customTiles = tiles.map {
            if (it.row == 2 && it.col == 2) {
                it.copy(digit = "9", colorId = secretColor, isPinTile = true)
            } else {
                it
            }
        }

        val result = DecoyRandomizer.randomizeDecoys(customTiles, secretColor)
        val pin = result.first { it.row == 2 && it.col == 2 }

        assertTrue(pin.isPinTile)
        assertEquals("9", pin.digit)
        assertEquals(secretColor, pin.colorId)
    }

    @Test
    fun testBlankMatrixCreation() {
        val blank = DecoyRandomizer.createBlankMatrix(cols = 6, rows = 7)
        assertEquals(42, blank.size)
        assertTrue(blank.all { it.digit == "?" && !it.isPinTile })
    }

    @Test
    fun testRandomizeEverythingExceptManuallySetPinDigits() {
        val secretColor = "blue"
        val cols = 6
        val rows = 7
        val blank = DecoyRandomizer.createBlankMatrix(cols = cols, rows = rows, defaultColor = secretColor)

        // User placed PIN path (0,0) -> (1,1) -> (2,2) -> (3,3) and entered "1234"
        val paintedPath = listOf(
            PaintedCell(0, 0),
            PaintedCell(1, 1),
            PaintedCell(2, 2),
            PaintedCell(3, 3)
        )
        val matrixWithPin = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = "1234",
            secretColor = secretColor,
            existingTiles = blank
        )

        val randomized = DecoyRandomizer.randomizeDecoys(matrixWithPin, secretColor)

        // 1. Manually set PIN tiles MUST stay intact
        val pin0 = randomized.first { it.row == 0 && it.col == 0 }
        val pin1 = randomized.first { it.row == 1 && it.col == 1 }
        val pin2 = randomized.first { it.row == 2 && it.col == 2 }
        val pin3 = randomized.first { it.row == 3 && it.col == 3 }

        assertEquals("1", pin0.digit)
        assertEquals("2", pin1.digit)
        assertEquals("3", pin2.digit)
        assertEquals("4", pin3.digit)
        assertTrue(pin0.isPinTile)
        assertTrue(pin1.isPinTile)
        assertTrue(pin2.isPinTile)
        assertTrue(pin3.isPinTile)
        assertEquals(secretColor, pin0.colorId)

        // 2. EVERYTHING ELSE (all 38 other tiles) MUST be randomized
        val decoys = randomized.filter { !it.isPinTile }
        assertEquals("All 38 non-PIN tiles must be decoys", 38, decoys.size)
        assertTrue("All decoys must have numeric digits (no '?' remaining)", decoys.all { it.digit in "0".."9" })
        assertFalse("Decoys must not remain unassigned", decoys.any { it.digit == "?" })
    }
}
