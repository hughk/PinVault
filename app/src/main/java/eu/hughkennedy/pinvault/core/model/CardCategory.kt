package eu.hughkennedy.pinvault.core.model

import androidx.annotation.StringRes
import eu.hughkennedy.pinvault.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CardCategorySerializer::class)
enum class CardCategory(
    val displayName: String,
    @get:StringRes val titleRes: Int
) {
    CREDIT("Credit Card", R.string.category_credit),
    DEBIT("Debit Card", R.string.category_debit),
    BANKING("Telephone Banking", R.string.category_banking),
    SAFE("Safe & Door Access", R.string.category_safe),
    AUTHENTICATOR("Authenticator (TOTP)", R.string.category_authenticator),
    OTHER("Other PIN", R.string.category_other);

    companion object {
        fun fromString(value: String?): CardCategory {
            if (value.isNullOrBlank()) return CREDIT
            val cleaned = value.trim()

            // 1. Direct match with enum name (case-insensitive)
            entries.find { it.name.equals(cleaned, ignoreCase = true) }?.let { return it }

            // 2. Direct match with displayName (case-insensitive)
            entries.find { it.displayName.equals(cleaned, ignoreCase = true) }?.let { return it }

            // 3. Normalized string matching: remove underscores, dashes, spaces
            val normalized = cleaned.lowercase().replace("[-_ ]".toRegex(), "")
            return when {
                normalized.contains("authenticator") || normalized.contains("totp") || normalized.contains("2fa") || normalized.contains("mfa") -> AUTHENTICATOR
                normalized.contains("credit") -> CREDIT
                normalized.contains("debit") -> DEBIT
                normalized.contains("bank") || normalized.contains("telephone") || normalized.contains("phone") -> BANKING
                normalized.contains("safe") || normalized.contains("door") || normalized.contains("lock") || normalized.contains("access") -> SAFE
                else -> OTHER
            }
        }
    }
}

object CardCategorySerializer : KSerializer<CardCategory> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CardCategory", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CardCategory) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): CardCategory {
        val str = decoder.decodeString()
        return CardCategory.fromString(str)
    }
}
