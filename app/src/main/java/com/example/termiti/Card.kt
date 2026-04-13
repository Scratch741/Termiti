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
    val type: String = "",
    /**
     * Zarovnání ořezu ilustrace. Rozsah -1.0 až 1.0.
     * artBiasX: -1.0 = obrázek přitažen doleva, 0.0 = střed, 1.0 = doprava
     * artBiasY: -1.0 = obrázek přitažen nahoru,  0.0 = střed, 1.0 = dolů
     * Příklad: artBiasX = -0.3f, artBiasY = -0.5f  → subjekt vpravo dole zůstane vidět
     */
    val artBiasX: Float = 0f,
    val artBiasY: Float = 0f
)