package eu.hughkennedy.pinvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import eu.hughkennedy.pinvault.core.engine.ArtistPathManager
import eu.hughkennedy.pinvault.core.engine.PaintedCell
import eu.hughkennedy.pinvault.core.model.PaletteColor
import eu.hughkennedy.pinvault.core.model.TileData
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MatrixGridView(
    cols: Int,
    rows: Int,
    tiles: List<TileData>,
    modifier: Modifier = Modifier,
    isPeeking: Boolean = false,
    isArtistMode: Boolean = false,
    paintedPath: List<PaintedCell> = emptyList(),
    strokes: List<List<PaintedCell>> = emptyList(),
    activeStroke: List<PaintedCell> = emptyList(),
    secretColorId: String = "blue",
    onTileClick: ((TileData) -> Unit)? = null,
    onCellTraversed: ((PaintedCell) -> Unit)? = null,
    onStrokeFinished: (() -> Unit)? = null
) {
    // Collect genuine PIN tiles to determine sequence order during peeking
    val pinTiles = tiles.filter { it.isPinTile && !it.isBlank }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val spacing = 6.dp
        val padding = 10.dp

        val availableW = maxWidth - (padding * 2) - (spacing * (cols - 1))
        val availableH = maxHeight - (padding * 2) - (spacing * (rows - 1))

        // Calculate tile size: scale dynamically to fit available viewport, but maintain
        // an ergonomic minimum floor (42.dp) so numbers remain clear, legible and tappable.
        val fitTileW = if (cols > 0 && availableW > 0.dp) availableW / cols else 44.dp
        val fitTileH = if (rows > 0 && availableH > 0.dp) availableH / rows else 44.dp
        val fitSize = min(fitTileW, fitTileH)
        val minTileSize = 42.dp

        val tileSize = max(fitSize, minTileSize)

        val gridWidth = (tileSize * cols) + (spacing * (cols - 1)) + (padding * 2)
        val gridHeight = (tileSize * rows) + (spacing * (rows - 1)) + (padding * 2)

        val density = LocalDensity.current
        val tileSizePx = with(density) { tileSize.toPx() }
        val spacingPx = with(density) { spacing.toPx() }
        val paddingPx = with(density) { padding.toPx() }
        val slotSizePx = tileSizePx + spacingPx

        val horizScrollState = rememberScrollState()
        val vertScrollState = rememberScrollState()

        // Two-way scrollable container: ensures that if the matrix exceeds screen edges
        // on small screens or in landscape mode, the user can always pan and scroll smoothly.
        // In Artist Mode, scrolling is disabled so drag gestures belong 100% to fingerpainting.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isArtistMode) {
                        Modifier
                            .horizontalScroll(horizScrollState)
                            .verticalScroll(vertScrollState)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(gridWidth, gridHeight)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(24.dp)
                    )
                    .then(
                        if (isArtistMode && onCellTraversed != null) {
                            Modifier.pointerInput(cols, rows, tileSizePx, spacingPx, paddingPx) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    ArtistPathManager.findCellAtOffset(
                                        down.position.x, down.position.y,
                                        cols, rows, tileSizePx, spacingPx, paddingPx
                                    )?.let { cell ->
                                        onCellTraversed(cell)
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        ArtistPathManager.findCellAtOffset(
                                            change.position.x, change.position.y,
                                            cols, rows, tileSizePx, spacingPx, paddingPx,
                                            maxRadiusPx = tileSizePx * 0.52f
                                        )?.let { cell ->
                                            onCellTraversed(cell)
                                        }
                                    }
                                    onStrokeFinished?.invoke()
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                // Base Grid of Tiles
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (r in 0 until rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (c in 0 until cols) {
                                val tile = tiles.find { it.row == r && it.col == c }
                                    ?: TileData(row = r, col = c)

                                val isPin = tile.isPinTile && !tile.isBlank
                                val pathIndex = if (isArtistMode) {
                                    paintedPath.indexOfFirst { it.row == r && it.col == c }
                                } else -1

                                val isDimmed = if (isArtistMode) {
                                    false
                                } else {
                                    isPeeking && !isPin
                                }

                                val isHighlighted = if (isArtistMode) {
                                    pathIndex >= 0
                                } else {
                                    isPeeking && isPin
                                }

                                val order = when {
                                    isArtistMode && pathIndex >= 0 -> pathIndex + 1
                                    isPeeking && isPin -> pinTiles.indexOf(tile) + 1
                                    else -> null
                                }

                                NumberTile(
                                    tile = tile,
                                    size = tileSize,
                                    isDimmed = isDimmed,
                                    isHighlighted = isHighlighted,
                                    sequenceOrder = order,
                                    onClick = if (onTileClick != null && !isArtistMode) {
                                        { onTileClick(tile) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Overlay Canvas: draws directional strokes and arrows connecting painted cells in Artist Mode
                if (isArtistMode && (strokes.isNotEmpty() || activeStroke.isNotEmpty() || paintedPath.size >= 2)) {
                    val lineColor = PaletteColor.find(secretColorId).color.copy(alpha = 0.85f)
                    val allStrokes = strokes + if (activeStroke.isNotEmpty()) listOf(activeStroke) else emptyList()

                    val segments = mutableListOf<Pair<PaintedCell, PaintedCell>>()
                    for (stroke in allStrokes) {
                        if (stroke.size >= 2) {
                            for (i in 0 until stroke.size - 1) {
                                segments.add(stroke[i] to stroke[i + 1])
                            }
                        }
                    }

                    // If user defined the path via individual tile taps (strokes of size 1):
                    if (segments.isEmpty() && paintedPath.size >= 2) {
                        for (i in 0 until paintedPath.size - 1) {
                            segments.add(paintedPath[i] to paintedPath[i + 1])
                        }
                    }

                    if (segments.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for ((c1, c2) in segments) {
                                val p1X = c1.col * slotSizePx + tileSizePx / 2f
                                val p1Y = c1.row * slotSizePx + tileSizePx / 2f
                                val p2X = c2.col * slotSizePx + tileSizePx / 2f
                                val p2Y = c2.row * slotSizePx + tileSizePx / 2f

                                // Draw stroke line connecting consecutive cells
                                drawLine(
                                    color = lineColor,
                                    start = Offset(p1X, p1Y),
                                    end = Offset(p2X, p2Y),
                                    strokeWidth = 4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // Draw directional chevron along the stroke
                                val angle = atan2(p2Y - p1Y, p2X - p1X)
                                val midX = (p1X + p2X) / 2f
                                val midY = (p1Y + p2Y) / 2f
                                val chevronLen = 10.dp.toPx()
                                val wingAngle = PI.toFloat() / 4f

                                val w1X = midX - chevronLen * cos(angle - wingAngle)
                                val w1Y = midY - chevronLen * sin(angle - wingAngle)
                                val w2X = midX - chevronLen * cos(angle + wingAngle)
                                val w2Y = midY - chevronLen * sin(angle + wingAngle)

                                drawLine(
                                    color = Color.White,
                                    start = Offset(midX, midY),
                                    end = Offset(w1X, w1Y),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(midX, midY),
                                    end = Offset(w2X, w2Y),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
