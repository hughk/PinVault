package eu.hughkennedy.pinvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.hughkennedy.pinvault.core.model.CardCategory

object CategoryVisuals {

    fun getIcon(category: CardCategory): ImageVector {
        return when (category) {
            CardCategory.CREDIT -> Icons.Default.CreditCard
            CardCategory.DEBIT -> Icons.Default.Payment
            CardCategory.BANKING -> Icons.Default.AccountBalance
            CardCategory.SAFE -> Icons.Default.Lock
            CardCategory.OTHER -> Icons.Default.Shield
        }
    }

    fun getColor(category: CardCategory, isDark: Boolean): Color {
        return when (category) {
            CardCategory.CREDIT -> if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8) // Vibrant Blue
            CardCategory.DEBIT -> if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E) // Mint Teal
            CardCategory.BANKING -> if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D) // Emerald Green
            CardCategory.SAFE -> if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309) // Amber Gold
            CardCategory.OTHER -> if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE) // Vibrant Purple
        }
    }
}

@Composable
fun CategoryIconBadge(
    category: CardCategory,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val color = CategoryVisuals.getColor(category, isDark)
    val icon = CategoryVisuals.getIcon(category)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                color.copy(alpha = if (isDark) 0.22f else 0.12f)
            )
            .border(
                width = if (isDark) 1.5.dp else 1.dp,
                color = color.copy(alpha = if (isDark) 0.85f else 0.40f),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.displayName,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
    }
}
