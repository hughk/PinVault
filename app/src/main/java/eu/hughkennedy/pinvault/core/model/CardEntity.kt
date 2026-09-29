package eu.hughkennedy.pinvault.core.model

import eu.hughkennedy.pinvault.core.engine.PaintedCell
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CardEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: CardCategory = CardCategory.CREDIT,
    val folder: String = "",
    val cols: Int = 6,
    val rows: Int = 7,
    val secretColor: String = "blue",
    val ruleHint: String = "",
    val tiles: List<TileData> = emptyList(),
    val lastModified: Long = System.currentTimeMillis(),
    val isTotp: Boolean = false,
    val totpSecret: String? = null,
    val totpDigits: Int = 6,
    val totpPeriod: Int = 30,
    val totpAlgorithm: String = "SHA1",
    val totpIssuer: String? = null,
    val pinPath: List<PaintedCell> = emptyList()
)
