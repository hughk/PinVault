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
}
