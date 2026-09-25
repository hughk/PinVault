package eu.hughkennedy.pinvault.core.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import eu.hughkennedy.pinvault.R

data class PaletteColor(
    val id: String,
    val displayName: String,
    @get:StringRes val nameRes: Int,
    val color: Color,
    val textColor: Color,
    val hex: String
) {
    companion object {
        val ALL = listOf(
            PaletteColor("blue", "Royal Blue", R.string.color_blue, Color(0xFF2563EB), Color.White, "#2563EB"),
            PaletteColor("emerald", "Emerald Green", R.string.color_emerald, Color(0xFF059669), Color.White, "#059669"),
            PaletteColor("amber", "Amber Gold", R.string.color_amber, Color(0xFFF59E0B), Color(0xFF1E293B), "#F59E0B"),
            PaletteColor("rose", "Rose Crimson", R.string.color_rose, Color(0xFFE11D48), Color.White, "#E11D48"),
            PaletteColor("purple", "Deep Purple", R.string.color_purple, Color(0xFF9333EA), Color.White, "#9333EA"),
            PaletteColor("cyan", "Vivid Cyan", R.string.color_cyan, Color(0xFF06B6D4), Color(0xFF0F172A), "#06B6D4")
        )

        fun find(id: String): PaletteColor {
            return ALL.find { it.id == id } ?: ALL[0]
        }
    }
}
