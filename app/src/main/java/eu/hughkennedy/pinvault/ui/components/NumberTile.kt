package eu.hughkennedy.pinvault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hughkennedy.pinvault.core.model.PaletteColor
import eu.hughkennedy.pinvault.core.model.TileData

@Composable
fun NumberTile(
    tile: TileData,
    size: Dp,
    modifier: Modifier = Modifier,
    isDimmed: Boolean = false,
    isHighlighted: Boolean = false,
    sequenceOrder: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val palette = PaletteColor.find(tile.colorId)
    val isBlank = tile.isBlank

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.12f else if (isDimmed) 0.92f else 1.0f,
        label = "tile_scale"
    )

    val alpha = if (isDimmed) 0.15f else 1.0f

    val backgroundColor = if (isBlank) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        palette.color.copy(alpha = alpha)
    }

    val textColor = if (isBlank) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
    } else {
        palette.textColor.copy(alpha = alpha)
    }

    val fontSize = (size.value * 0.60f).sp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .aspectRatio(1f)
            .scale(scale)
    ) {
        // Base Circular Token
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .aspectRatio(1f)
                .shadow(
                    elevation = if (isHighlighted) 12.dp else if (isBlank) 0.dp else 3.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (isHighlighted) {
                        Modifier.border(3.dp, Color.White, CircleShape)
                    } else if (isBlank) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                    } else {
                        Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    }
                )
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = tile.digit,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }

        // Emergency Sequence Step Badge (#1, #2, #3, ...)
        if (sequenceOrder != null && sequenceOrder > 0) {
            val badgeSize = (size.value * 0.36f).dp
            val badgeFontSize = (size.value * 0.18f).sp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(badgeSize)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color.DarkGray, CircleShape)
            ) {
                Text(
                    text = "#$sequenceOrder",
                    color = Color.Black,
                    fontSize = badgeFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}
