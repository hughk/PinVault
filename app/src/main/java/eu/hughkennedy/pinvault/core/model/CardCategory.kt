package eu.hughkennedy.pinvault.core.model

import androidx.annotation.StringRes
import eu.hughkennedy.pinvault.R
import kotlinx.serialization.Serializable

@Serializable
enum class CardCategory(
    val displayName: String,
    @get:StringRes val titleRes: Int
) {
    CREDIT("Credit Card", R.string.category_credit),
    DEBIT("Debit Card", R.string.category_debit),
    BANKING("Telephone Banking", R.string.category_banking),
    SAFE("Safe & Door Access", R.string.category_safe),
    AUTHENTICATOR("Authenticator (TOTP)", R.string.category_authenticator),
    OTHER("Other PIN", R.string.category_other)
}
