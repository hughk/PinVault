package eu.hughkennedy.pinvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.hughkennedy.pinvault.core.model.PaletteColor

@Composable
fun ColorPickerRow(
    selectedColorId: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaletteColor.ALL.forEach { palette ->
            val isSelected = palette.id == selectedColorId

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(palette.id) }
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = palette.textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
