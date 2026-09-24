package com.example.pinkeeper.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardCategory(val displayName: String) {
    CREDIT("Credit Card"),
    DEBIT("Debit Card"),
    BANKING("Telephone Banking"),
    SAFE("Safe & Door Access"),
    OTHER("Other PIN")
}
