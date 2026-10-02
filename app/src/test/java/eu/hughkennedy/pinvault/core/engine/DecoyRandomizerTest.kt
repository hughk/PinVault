package eu.hughkennedy.pinvault.core.engine

import eu.hughkennedy.pinvault.core.model.TileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun testRandomizeDoesNotRandomiseDigitsOnDrawnPath() {
        val secretColor = "blue"
        val cols = 6
        val rows = 7
        val blank = DecoyRandomizer.createBlankMatrix(cols = cols, rows = rows, defaultColor = secretColor)

        // Draw 8 cells and enter "1234"
        val paintedPath = (0 until 8).map { PaintedCell(it / cols, it % cols) }
        val matrixWithPin = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = "1234",
            secretColor = secretColor,
            existingTiles = blank
        )

        // Verify initial state:
        // First 4 cells have digits "1", "2", "3", "4"
        // Next 4 cells (indices 4..7) have "?"
        assertEquals("1", matrixWithPin.first { it.row == paintedPath[0].row && it.col == paintedPath[0].col }.digit)
        assertEquals("?", matrixWithPin.first { it.row == paintedPath[4].row && it.col == paintedPath[4].col }.digit)

        // Now randomize decoys
        val randomized = DecoyRandomizer.randomizeDecoys(matrixWithPin, secretColor)

        // 1. All cells on the drawn path MUST NOT be randomised
        for (i in 0 until 4) {
            val cell = paintedPath[i]
            val tile = randomized.first { it.row == cell.row && it.col == cell.col }
            assertEquals((i + 1).toString(), tile.digit)
            assertTrue("Path cell must remain a PIN tile", tile.isPinTile)
            assertEquals(secretColor, tile.colorId)
        }

        for (i in 4 until 8) {
            val cell = paintedPath[i]
            val tile = randomized.first { it.row == cell.row && it.col == cell.col }
            assertEquals("?", tile.digit)
            assertTrue("Path cell must remain a PIN tile", tile.isPinTile)
            assertEquals(secretColor, tile.colorId)
        }

        // Exactly 8 PIN tiles on the drawn path
        assertEquals(8, randomized.count { it.isPinTile })

        // 2. All tiles OUTSIDE the drawn path (34 tiles) MUST be randomized as decoys
        val nonPathTiles = randomized.filter { !it.isPinTile }
        assertEquals(34, nonPathTiles.size)
        assertTrue("Non-path decoys must have numeric digits", nonPathTiles.all { it.digit in "0".."9" })
        assertFalse("Decoys must not have '?'", nonPathTiles.any { it.digit == "?" })
    }

    @Test
    fun testLinearDecoyPinCreatedWithUniformColorFor6DigitPinAndTotp() {
        val secretColor = "rose"
        val rows = 7
        val cols = 6
        // Define a 6-digit PIN (such as a TOTP code along top row: (0,0)..(0,5))
        val initialTiles = DecoyRandomizer.createBlankMatrix(cols = cols, rows = rows, defaultColor = secretColor)
        val pinPositions = (0 until 6).map { Pair(0, it) }.toSet()

        val matrixWithPin = initialTiles.map { tile ->
            if (Pair(tile.row, tile.col) in pinPositions) {
                tile.copy(digit = (tile.col + 1).toString(), colorId = secretColor, isPinTile = true)
            } else {
                tile
            }
        }

        val allLines6 = DecoyRandomizer.generateAllLineCandidates(rows, cols, 6)
        assertTrue("Grid must have 6-tile line candidates", allLines6.isNotEmpty())

        repeat(50) { iteration ->
            // Randomize decoys for this 6-digit PIN
            val randomized = DecoyRandomizer.randomizeDecoys(matrixWithPin, secretColor)
            val decoyTilesMap = randomized.filter { !it.isPinTile }.associateBy { Pair(it.row, it.col) }

            // Verify that at least one straight line of length 6 exists where all tiles have the SAME color
            val hasUniformDecoyLine6 = allLines6.any { line ->
                val lineTiles = line.tiles.mapNotNull { decoyTilesMap[it] }
                lineTiles.size == 6 && lineTiles.map { it.colorId }.distinct().size == 1
            }

            assertTrue(
                "A straight line (horizontal, vertical, or diagonal) of length 6 with uniform color must exist for 6-digit PIN (iteration $iteration)",
                hasUniformDecoyLine6
            )
        }
    }

    @Test
    fun testLinearDecoyPinWithExplicitDecoyLength6() {
        val secretColor = "blue"
        val rows = 7
        val cols = 6
        // Matrix in TOTP mode with blank tiles and explicit decoyLength = 6
        val blank = DecoyRandomizer.createBlankMatrix(cols = cols, rows = rows, defaultColor = secretColor)
        val allLines6 = DecoyRandomizer.generateAllLineCandidates(rows, cols, 6)

        repeat(20) { iteration ->
            val randomized = DecoyRandomizer.randomizeDecoys(blank, secretColor, decoyLength = 6)
            val decoyTilesMap = randomized.associateBy { Pair(it.row, it.col) }

            val hasUniformDecoyLine6 = allLines6.any { line ->
                val lineTiles = line.tiles.mapNotNull { decoyTilesMap[it] }
                lineTiles.size == 6 && lineTiles.map { it.colorId }.distinct().size == 1
            }

            assertTrue(
                "A straight line of length 6 with uniform color must exist when decoyLength=6 is specified (iteration $iteration)",
                hasUniformDecoyLine6
            )
        }
    }

    @Test
    fun testFallbackWhen6DigitLineCannotFitOnSmall5x5Matrix() {
        val secretColor = "amber"
        val rows = 5
        val cols = 5
        val blank5x5 = DecoyRandomizer.createBlankMatrix(cols = cols, rows = rows, defaultColor = secretColor)
        val allLines4 = DecoyRandomizer.generateAllLineCandidates(rows, cols, 4)

        // Requesting 6-digit decoy line on a 5x5 matrix must gracefully fall back to 4 without error
        val randomized = DecoyRandomizer.randomizeDecoys(blank5x5, secretColor, decoyLength = 6)
        assertEquals(25, randomized.size)
        assertTrue(randomized.all { it.digit in "0".."9" })

        val decoyTilesMap = randomized.associateBy { Pair(it.row, it.col) }
        val hasUniformDecoyLine4 = allLines4.any { line ->
            val lineTiles = line.tiles.mapNotNull { decoyTilesMap[it] }
            lineTiles.size == 4 && lineTiles.map { it.colorId }.distinct().size == 1
        }
        assertTrue("Must fall back to at least a length-4 uniform decoy line on 5x5 grid", hasUniformDecoyLine4)
    }

    @Test
    fun testNoDecoyEverHasSecretColor() {
        val testColors = listOf("blue", "emerald", "amber", "rose", "purple", "cyan")

        for (secretColor in testColors) {
            val cols = 6
            val rows = 7
            val initialTiles = DecoyRandomizer.createBlankMatrix(cols = cols, rows = rows, defaultColor = secretColor)
            val pinPositions = setOf(Pair(1, 1), Pair(2, 2), Pair(3, 3), Pair(4, 4))
            val matrixWithPin = initialTiles.map { tile ->
                if (Pair(tile.row, tile.col) in pinPositions) {
                    tile.copy(digit = "5", colorId = secretColor, isPinTile = true)
                } else {
                    tile
                }
            }

            repeat(20) { iteration ->
                val randomized = DecoyRandomizer.randomizeDecoys(matrixWithPin, secretColor)
                val pinTiles = randomized.filter { it.isPinTile }
                val decoyTiles = randomized.filter { !it.isPinTile }

                assertEquals(4, pinTiles.size)
                assertTrue("All PIN tiles must have secret color $secretColor", pinTiles.all { it.colorId == secretColor })

                // STRICT: No decoy in the entire matrix may EVER use secretColor
                for (decoy in decoyTiles) {
                    assertFalse(
                        "Decoy at (${decoy.row}, ${decoy.col}) must NOT have secret color '$secretColor' (iteration $iteration)",
                        decoy.colorId == secretColor
                    )
                }
            }
        }
    }

    @Test
    fun testArtistModePreservesManuallyDefinedPinPositions() {
        val secretColor = "cyan"
        val cols = 6
        val rows = 7
        val blank = DecoyRandomizer.createBlankMatrix(cols = cols, rows = rows, defaultColor = secretColor)

        // User painted a path of 4 cells: (1,1), (1,2), (2,3), (3,3)
        val paintedPath = listOf(
            PaintedCell(1, 1),
            PaintedCell(1, 2),
            PaintedCell(2, 3),
            PaintedCell(3, 3)
        )

        // User typed 2 digits: "48"
        val matrixWithPartialDigits = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = "48",
            secretColor = secretColor,
            existingTiles = blank
        )

        // When generating the full static PIN, fill remaining unset digits ("48" + "91")
        val fullPin = "4891"
        val matrixWithFullPin = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = fullPin,
            secretColor = secretColor,
            existingTiles = matrixWithPartialDigits
        )

        val randomized = DecoyRandomizer.randomizeDecoys(matrixWithFullPin, secretColor)

        // Verify that the 4 manually defined positions are strictly preserved:
        val pin1 = randomized.first { it.row == 1 && it.col == 1 }
        val pin2 = randomized.first { it.row == 1 && it.col == 2 }
        val pin3 = randomized.first { it.row == 2 && it.col == 3 }
        val pin4 = randomized.first { it.row == 3 && it.col == 3 }

        assertEquals("4", pin1.digit)
        assertEquals("8", pin2.digit)
        assertEquals("9", pin3.digit)
        assertEquals("1", pin4.digit)

        assertTrue(pin1.isPinTile && pin2.isPinTile && pin3.isPinTile && pin4.isPinTile)
        assertEquals(secretColor, pin1.colorId)
        assertEquals(secretColor, pin2.colorId)
        assertEquals(secretColor, pin3.colorId)
        assertEquals(secretColor, pin4.colorId)

        // Non-path tiles are all decoys without secretColor
        val decoys = randomized.filter { !it.isPinTile }
        assertEquals(cols * rows - 4, decoys.size)
        assertTrue(decoys.all { it.colorId != secretColor })
    }
}
