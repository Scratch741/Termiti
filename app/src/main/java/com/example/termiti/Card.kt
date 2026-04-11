// ============================================================
// Card.kt
// ============================================================
package com.example.termiti

data class Card(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val costType: ResourceType = ResourceType.MAGIC,
    val rarity: Rarity = Rarity.COMMON,
    val effects: List<CardEffect>,
    val isCombo: Boolean = false,   // Combo: sehrání neukončí tah hráče
    /** Volitelná ilustrace karty (R.drawable.xxx). Pokud null, použije se výchozí design. */
    val artResId: Int? = null,
    /** Typ karty zobrazený v dolním pruhu (jen u karet s artResId). */
    val type: String = ""
)