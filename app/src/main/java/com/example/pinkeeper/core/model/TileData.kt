package com.example.pinkeeper.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TileData(
    val row: Int,
    val col: Int,
    var digit: String = "?",
    var colorId: String = "blue",
    var isPinTile: Boolean = false
) {
    val isBlank: Boolean get() = digit == "?"
}
