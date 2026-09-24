package eu.hughkennedy.pinvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import eu.hughkennedy.pinvault.core.model.TileData

@Composable
fun MatrixGridView(
    cols: Int,
    rows: Int,
    tiles: List<TileData>,
    modifier: Modifier = Modifier,
    isPeeking: Boolean = false,
    onTileClick: ((TileData) -> Unit)? = null
) {
    // Collect genuine PIN tiles to determine sequence order during peeking
    val pinTiles = tiles.filter { it.isPinTile && !it.isBlank }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val spacing = 6.dp
        val padding = 12.dp

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

        val horizScrollState = rememberScrollState()
        val vertScrollState = rememberScrollState()

        // Two-way scrollable container: ensures that if the matrix exceeds screen edges
        // on small screens or in landscape mode, the user can always pan and scroll smoothly.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizScrollState)
                .verticalScroll(vertScrollState),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(gridWidth, gridHeight)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
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
                                val isDimmed = isPeeking && !isPin
                                val isHighlighted = isPeeking && isPin
                                val order = if (isPeeking && isPin) pinTiles.indexOf(tile) + 1 else null

                                NumberTile(
                                    tile = tile,
                                    size = tileSize,
                                    isDimmed = isDimmed,
                                    isHighlighted = isHighlighted,
                                    sequenceOrder = order,
                                    onClick = if (onTileClick != null) { { onTileClick(tile) } } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
