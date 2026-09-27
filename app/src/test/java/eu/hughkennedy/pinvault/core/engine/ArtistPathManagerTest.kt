package eu.hughkennedy.pinvault.core.engine

import eu.hughkennedy.pinvault.core.model.TileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPathManagerTest {

    private fun createEmpty6x7Matrix(): List<TileData> {
        val tiles = mutableListOf<TileData>()
        for (r in 0 until 7) {
            for (c in 0 until 6) {
                tiles.add(TileData(row = r, col = c, digit = "?", colorId = "blue", isPinTile = false))
            }
        }
        return tiles
    }

    @Test
    fun `paint from right to left with 1234 places 4321 on matrix left-to-right`() {
        // User paints a 4-tile line from right (col 3) to left (col 0) along Row 1:
        // (1, 3) -> (1, 2) -> (1, 1) -> (1, 0)
        val paintedPath = listOf(
            PaintedCell(1, 3), // 1st touched
            PaintedCell(1, 2), // 2nd touched
            PaintedCell(1, 1), // 3rd touched
            PaintedCell(1, 0)  // 4th touched
        )
        val digitString = "1234"
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "emerald",
            existingTiles = emptyTiles
        )

        // Find the tiles along Row 1
        val row1Tiles = updatedTiles.filter { it.row == 1 }.sortedBy { it.col }
        val col0 = row1Tiles.find { it.col == 0 }!!
        val col1 = row1Tiles.find { it.col == 1 }!!
        val col2 = row1Tiles.find { it.col == 2 }!!
        val col3 = row1Tiles.find { it.col == 3 }!!

        // Reading row 1 left-to-right on the matrix:
        // Col 0: '4'
        // Col 1: '3'
        // Col 2: '2'
        // Col 3: '1'
        assertEquals("4", col0.digit)
        assertEquals("3", col1.digit)
        assertEquals("2", col2.digit)
        assertEquals("1", col3.digit)

        // All 4 are genuine PIN tiles in the secret color
        assertTrue(col0.isPinTile)
        assertTrue(col1.isPinTile)
        assertTrue(col2.isPinTile)
        assertTrue(col3.isPinTile)
        assertEquals("emerald", col0.colorId)
        assertEquals("emerald", col3.colorId)

        // Verify remaining row tiles remain untouched
        val col4 = row1Tiles.find { it.col == 4 }!!
        assertFalse(col4.isPinTile)
        assertEquals("?", col4.digit)
    }

    @Test
    fun `paint from left to right with 1234 places 1234 on matrix left-to-right`() {
        val paintedPath = listOf(
            PaintedCell(0, 0),
            PaintedCell(0, 1),
            PaintedCell(0, 2),
            PaintedCell(0, 3)
        )
        val digitString = "1234"
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "blue",
            existingTiles = emptyTiles
        )

        val row0 = updatedTiles.filter { it.row == 0 }.sortedBy { it.col }
        assertEquals("1", row0[0].digit)
        assertEquals("2", row0[1].digit)
        assertEquals("3", row0[2].digit)
        assertEquals("4", row0[3].digit)
    }

    @Test
    fun `paint vertical top to bottom with 9876`() {
        val paintedPath = listOf(
            PaintedCell(0, 2),
            PaintedCell(1, 2),
            PaintedCell(2, 2),
            PaintedCell(3, 2)
        )
        val digitString = "9876"
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "amber",
            existingTiles = emptyTiles
        )

        val col2Tiles = updatedTiles.filter { it.col == 2 }.sortedBy { it.row }
        assertEquals("9", col2Tiles[0].digit)
        assertEquals("8", col2Tiles[1].digit)
        assertEquals("7", col2Tiles[2].digit)
        assertEquals("6", col2Tiles[3].digit)
    }

    @Test
    fun `paint vertical bottom to top with 9876`() {
        val paintedPath = listOf(
            PaintedCell(3, 2),
            PaintedCell(2, 2),
            PaintedCell(1, 2),
            PaintedCell(0, 2)
        )
        val digitString = "9876"
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "amber",
            existingTiles = emptyTiles
        )

        val col2Tiles = updatedTiles.filter { it.col == 2 }.sortedBy { it.row }
        // Looking top-to-bottom on column 2:
        assertEquals("6", col2Tiles[0].digit) // Row 0 is 4th painted
        assertEquals("7", col2Tiles[1].digit) // Row 1 is 3rd painted
        assertEquals("8", col2Tiles[2].digit) // Row 2 is 2nd painted
        assertEquals("9", col2Tiles[3].digit) // Row 3 is 1st painted
    }

    @Test
    fun `partial digit entry leaves unentered path cells as question marks`() {
        val paintedPath = listOf(
            PaintedCell(1, 3),
            PaintedCell(1, 2),
            PaintedCell(1, 1),
            PaintedCell(1, 0)
        )
        val digitString = "12" // Only 2 digits entered so far
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "cyan",
            existingTiles = emptyTiles
        )

        val row1Tiles = updatedTiles.filter { it.row == 1 }.sortedBy { it.col }
        val col3 = row1Tiles.find { it.col == 3 }!! // 1st
        val col2 = row1Tiles.find { it.col == 2 }!! // 2nd
        val col1 = row1Tiles.find { it.col == 1 }!! // 3rd (pending)
        val col0 = row1Tiles.find { it.col == 0 }!! // 4th (pending)

        assertEquals("1", col3.digit)
        assertEquals("2", col2.digit)
        assertEquals("?", col1.digit)
        assertEquals("?", col0.digit)
        assertTrue(col1.isPinTile) // Marked as part of PIN path
        assertTrue(col0.isPinTile)
    }

    @Test
    fun `findCellAtOffset calculates slot coordinates correctly without gaps`() {
        val tileSize = 50f
        val spacing = 10f
        val padding = 12f
        val cols = 6
        val rows = 7

        // Center of Col 0: padding + 25 = 37f
        val cellCol0 = ArtistPathManager.findCellAtOffset(
            x = 37f,
            y = 37f,
            cols = cols,
            rows = rows,
            tileSizePx = tileSize,
            spacingPx = spacing,
            paddingPx = padding
        )
        assertNotNull(cellCol0)
        assertEquals(0, cellCol0!!.row)
        assertEquals(0, cellCol0.col)

        // Midpoint of gap between Col 0 and Col 1: padding + 50 + 5 = 67f
        val cellGapMidpoint = ArtistPathManager.findCellAtOffset(
            x = 68f,
            y = 37f,
            cols = cols,
            rows = rows,
            tileSizePx = tileSize,
            spacingPx = spacing,
            paddingPx = padding
        )
        assertNotNull(cellGapMidpoint)
        assertEquals(1, cellGapMidpoint!!.col)

        // Far outside
        val outside = ArtistPathManager.findCellAtOffset(
            x = -100f,
            y = 500f,
            cols = cols,
            rows = rows,
            tileSizePx = tileSize,
            spacingPx = spacing,
            paddingPx = padding
        )
        assertNull(outside)
    }

    @Test
    fun `generateRuleHint describes right-to-left horizontal line`() {
        val paintedPath = listOf(
            PaintedCell(1, 3),
            PaintedCell(1, 2),
            PaintedCell(1, 1),
            PaintedCell(1, 0)
        )
        val hintEn = ArtistPathManager.generateRuleHint(paintedPath, "Royal Blue", isGerman = false)
        val hintDe = ArtistPathManager.generateRuleHint(paintedPath, "Königsblau", isGerman = true)

        assertEquals("Read Royal Blue tiles right-to-left along Row 2", hintEn)
        assertEquals("Königsblau-Kacheln von rechts nach links in Zeile 2 lesen", hintDe)
    }

    @Test
    fun `generateRuleHint describes left-to-right horizontal line`() {
        val paintedPath = listOf(
            PaintedCell(3, 0),
            PaintedCell(3, 1),
            PaintedCell(3, 2),
            PaintedCell(3, 3)
        )
        val hintEn = ArtistPathManager.generateRuleHint(paintedPath, "Emerald", isGerman = false)
        assertEquals("Read Emerald tiles left-to-right along Row 4", hintEn)
    }

    @Test
    fun `paint diagonal down-right places digits and generates down-right hint`() {
        val paintedPath = listOf(
            PaintedCell(0, 0),
            PaintedCell(1, 1),
            PaintedCell(2, 2),
            PaintedCell(3, 3)
        )
        val digitString = "1234"
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "emerald",
            existingTiles = emptyTiles
        )

        val cell00 = updatedTiles.find { it.row == 0 && it.col == 0 }!!
        val cell11 = updatedTiles.find { it.row == 1 && it.col == 1 }!!
        val cell22 = updatedTiles.find { it.row == 2 && it.col == 2 }!!
        val cell33 = updatedTiles.find { it.row == 3 && it.col == 3 }!!

        assertEquals("1", cell00.digit)
        assertEquals("2", cell11.digit)
        assertEquals("3", cell22.digit)
        assertEquals("4", cell33.digit)
        assertTrue(cell00.isPinTile)
        assertTrue(cell33.isPinTile)

        val hintEn = ArtistPathManager.generateRuleHint(paintedPath, "Emerald", isGerman = false)
        val hintDe = ArtistPathManager.generateRuleHint(paintedPath, "Smaragd", isGerman = true)
        assertEquals("Read Emerald tiles diagonally down-right starting at Row 1, Col 1", hintEn)
        assertEquals("Smaragd-Kacheln diagonal nach rechts-unten ab Zeile 1, Spalte 1 lesen", hintDe)
    }

    @Test
    fun `paint diagonal up-left places digits in reverse order and generates up-left hint`() {
        val paintedPath = listOf(
            PaintedCell(3, 3),
            PaintedCell(2, 2),
            PaintedCell(1, 1),
            PaintedCell(0, 0)
        )
        val digitString = "1234"
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "blue",
            existingTiles = emptyTiles
        )

        val cell33 = updatedTiles.find { it.row == 3 && it.col == 3 }!!
        val cell22 = updatedTiles.find { it.row == 2 && it.col == 2 }!!
        val cell11 = updatedTiles.find { it.row == 1 && it.col == 1 }!!
        val cell00 = updatedTiles.find { it.row == 0 && it.col == 0 }!!

        assertEquals("1", cell33.digit)
        assertEquals("2", cell22.digit)
        assertEquals("3", cell11.digit)
        assertEquals("4", cell00.digit)

        val hintEn = ArtistPathManager.generateRuleHint(paintedPath, "Blue", isGerman = false)
        val hintDe = ArtistPathManager.generateRuleHint(paintedPath, "Königsblau", isGerman = true)
        assertEquals("Read Blue tiles diagonally up-left starting at Row 4, Col 4", hintEn)
        assertEquals("Königsblau-Kacheln diagonal nach links-oben ab Zeile 4, Spalte 4 lesen", hintDe)
    }

    @Test
    fun `paint diagonal down-left places digits and generates down-left hint`() {
        val paintedPath = listOf(
            PaintedCell(0, 3),
            PaintedCell(1, 2),
            PaintedCell(2, 1),
            PaintedCell(3, 0)
        )
        val digitString = "1234"
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "amber",
            existingTiles = emptyTiles
        )

        assertEquals("1", updatedTiles.find { it.row == 0 && it.col == 3 }!!.digit)
        assertEquals("2", updatedTiles.find { it.row == 1 && it.col == 2 }!!.digit)
        assertEquals("3", updatedTiles.find { it.row == 2 && it.col == 1 }!!.digit)
        assertEquals("4", updatedTiles.find { it.row == 3 && it.col == 0 }!!.digit)

        val hintEn = ArtistPathManager.generateRuleHint(paintedPath, "Amber", isGerman = false)
        val hintDe = ArtistPathManager.generateRuleHint(paintedPath, "Bernstein", isGerman = true)
        assertEquals("Read Amber tiles diagonally down-left starting at Row 1, Col 4", hintEn)
        assertEquals("Bernstein-Kacheln diagonal nach links-unten ab Zeile 1, Spalte 4 lesen", hintDe)
    }

    @Test
    fun `paint diagonal up-right places digits and generates up-right hint`() {
        val paintedPath = listOf(
            PaintedCell(3, 0),
            PaintedCell(2, 1),
            PaintedCell(1, 2),
            PaintedCell(0, 3)
        )
        val digitString = "1234"
        val emptyTiles = createEmpty6x7Matrix()

        val updatedTiles = ArtistPathManager.mapDigitsToPath(
            paintedPath = paintedPath,
            digitString = digitString,
            secretColor = "cyan",
            existingTiles = emptyTiles
        )

        assertEquals("1", updatedTiles.find { it.row == 3 && it.col == 0 }!!.digit)
        assertEquals("2", updatedTiles.find { it.row == 2 && it.col == 1 }!!.digit)
        assertEquals("3", updatedTiles.find { it.row == 1 && it.col == 2 }!!.digit)
        assertEquals("4", updatedTiles.find { it.row == 0 && it.col == 3 }!!.digit)

        val hintEn = ArtistPathManager.generateRuleHint(paintedPath, "Cyan", isGerman = false)
        val hintDe = ArtistPathManager.generateRuleHint(paintedPath, "Cyan", isGerman = true)
        assertEquals("Read Cyan tiles diagonally up-right starting at Row 4, Col 1", hintEn)
        assertEquals("Cyan-Kacheln diagonal nach rechts-oben ab Zeile 4, Spalte 1 lesen", hintDe)
    }

    @Test
    fun `radial activation threshold rejects diagonal corner transit zone preventing corner-clipping`() {
        val tileSize = 50f
        val spacing = 10f
        val padding = 12f
        val cols = 6
        val rows = 7
        val maxRadius = tileSize * 0.52f // 26f

        // Center of (0,0) is at (37f, 37f)
        val atCenter00 = ArtistPathManager.findCellAtOffset(
            x = 37f, y = 37f,
            cols = cols, rows = rows,
            tileSizePx = tileSize, spacingPx = spacing, paddingPx = padding,
            maxRadiusPx = maxRadius
        )
        assertNotNull(atCenter00)
        assertEquals(0, atCenter00!!.row)
        assertEquals(0, atCenter00.col)

        // Midpoint on diagonal path towards (1,1): (67f, 67f).
        // Distance to (0,1) center (97f, 37f) is sqrt(30^2 + 30^2) = 42.4f > 26f.
        // Distance to (1,0) center (37f, 97f) is 42.4f > 26f.
        // Distance to (0,0) and (1,1) is also 42.4f > 26f.
        // In the transit zone, findCellAtOffset must return null!
        val inDiagonalTransit = ArtistPathManager.findCellAtOffset(
            x = 67f, y = 67f,
            cols = cols, rows = rows,
            tileSizePx = tileSize, spacingPx = spacing, paddingPx = padding,
            maxRadiusPx = maxRadius
        )
        assertNull("Transit point must return null so orthogonal neighbors are never clipped", inDiagonalTransit)

        // As finger reaches near (1,1) center (97f, 97f), say at (95f, 95f):
        val atTarget11 = ArtistPathManager.findCellAtOffset(
            x = 95f, y = 95f,
            cols = cols, rows = rows,
            tileSizePx = tileSize, spacingPx = spacing, paddingPx = padding,
            maxRadiusPx = maxRadius
        )
        assertNotNull(atTarget11)
        assertEquals(1, atTarget11!!.row)
        assertEquals(1, atTarget11.col)
    }

    @Test
    fun `interpolateBetween fills in skipped diagonal intermediate cells during fast swipes`() {
        val from = PaintedCell(0, 0)
        val to = PaintedCell(3, 3)

        val interpolated = ArtistPathManager.interpolateBetween(from, to)
        assertEquals(3, interpolated.size)
        assertEquals(PaintedCell(1, 1), interpolated[0])
        assertEquals(PaintedCell(2, 2), interpolated[1])
        assertEquals(PaintedCell(3, 3), interpolated[2])
    }

    @Test
    fun `interpolateBetween fills in skipped horizontal and vertical cells`() {
        val horiz = ArtistPathManager.interpolateBetween(PaintedCell(1, 0), PaintedCell(1, 3))
        assertEquals(3, horiz.size)
        assertEquals(PaintedCell(1, 1), horiz[0])
        assertEquals(PaintedCell(1, 2), horiz[1])
        assertEquals(PaintedCell(1, 3), horiz[2])

        val vert = ArtistPathManager.interpolateBetween(PaintedCell(3, 2), PaintedCell(0, 2))
        assertEquals(3, vert.size)
        assertEquals(PaintedCell(2, 2), vert[0])
        assertEquals(PaintedCell(1, 2), vert[1])
        assertEquals(PaintedCell(0, 2), vert[2])
    }
}
