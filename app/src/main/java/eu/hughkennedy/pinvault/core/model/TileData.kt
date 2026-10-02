package eu.hughkennedy.pinvault.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = TileDataSerializer::class)
data class TileData(
    val row: Int,
    val col: Int,
    var digit: String = "?",
    var colorId: String = "blue",
    var isPinTile: Boolean = false
) {
    val isBlank: Boolean get() = digit == "?"
}

object TileDataSerializer : KSerializer<TileData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TileData") {
        element<Int>("row")
        element<Int>("col")
        element<String>("digit")
        element<String>("colorId")
        element<Boolean>("isPinTile")
    }

    override fun serialize(encoder: Encoder, value: TileData) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeIntElement(descriptor, 0, value.row)
        composite.encodeIntElement(descriptor, 1, value.col)
        composite.encodeStringElement(descriptor, 2, value.digit)
        composite.encodeStringElement(descriptor, 3, value.colorId)
        composite.encodeBooleanElement(descriptor, 4, value.isPinTile)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TileData {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonObject) {
                val row = element["row"]?.jsonPrimitive?.intOrNull
                    ?: element["r"]?.jsonPrimitive?.intOrNull
                    ?: element["row"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: element["r"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: 0

                val col = element["col"]?.jsonPrimitive?.intOrNull
                    ?: element["c"]?.jsonPrimitive?.intOrNull
                    ?: element["col"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: element["c"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: 0

                val digit = element["digit"]?.jsonPrimitive?.contentOrNull
                    ?: element["d"]?.jsonPrimitive?.contentOrNull
                    ?: "?"

                val colorId = element["colorId"]?.jsonPrimitive?.contentOrNull
                    ?: element["color"]?.jsonPrimitive?.contentOrNull
                    ?: "blue"

                val isPinTile = element["isPinTile"]?.jsonPrimitive?.booleanOrNull
                    ?: element["isPin"]?.jsonPrimitive?.booleanOrNull
                    ?: element["pin"]?.jsonPrimitive?.booleanOrNull
                    ?: element["isPinTile"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true)
                    ?: element["isPin"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true)
                    ?: (element["pin"]?.jsonPrimitive?.contentOrNull == "1")

                return TileData(
                    row = row,
                    col = col,
                    digit = digit,
                    colorId = colorId,
                    isPinTile = isPinTile
                )
            }
        }

        val composite = decoder.beginStructure(descriptor)
        var row = 0
        var col = 0
        var digit = "?"
        var colorId = "blue"
        var isPinTile = false
        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> row = composite.decodeIntElement(descriptor, 0)
                1 -> col = composite.decodeIntElement(descriptor, 1)
                2 -> digit = composite.decodeStringElement(descriptor, 2)
                3 -> colorId = composite.decodeStringElement(descriptor, 3)
                4 -> isPinTile = composite.decodeBooleanElement(descriptor, 4)
                else -> {}
            }
        }
        composite.endStructure(descriptor)
        return TileData(row, col, digit, colorId, isPinTile)
    }
}
