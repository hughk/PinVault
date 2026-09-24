package com.example.pinkeeper.core.model

import androidx.compose.ui.graphics.Color

data class PaletteColor(
    val id: String,
    val displayName: String,
    val color: Color,
    val textColor: Color,
    val hex: String
) {
    companion object {
        val ALL = listOf(
            PaletteColor("blue", "Royal Blue", Color(0xFF2563EB), Color.White, "#2563EB"),
            PaletteColor("emerald", "Emerald Green", Color(0xFF059669), Color.White, "#059669"),
            PaletteColor("amber", "Amber Gold", Color(0xFFF59E0B), Color(0xFF1E293B), "#F59E0B"),
            PaletteColor("rose", "Rose Crimson", Color(0xFFE11D48), Color.White, "#E11D48"),
            PaletteColor("purple", "Deep Purple", Color(0xFF9333EA), Color.White, "#9333EA"),
            PaletteColor("cyan", "Vivid Cyan", Color(0xFF06B6D4), Color(0xFF0F172A), "#06B6D4")
        )

        fun find(id: String): PaletteColor {
            return ALL.find { it.id == id } ?: ALL[0]
        }
    }
}
