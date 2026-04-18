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
     * Hodnoty se přičítají ke globálním konstantám ART_GLOBAL_BIAS_X / ART_GLOBAL_BIAS_Y.
     */
    val artBiasX: Float = 0f,
    val artBiasY: Float = 0f,
    /**
     * Per-karta korekce zoomu ilustrace (multiplikátor na ART_GLOBAL_SCALE).
     * 1.0f = beze změny, 1.2f = 20 % přiblížit, 0.85f = 15 % oddálit.
     */
    val artScale: Float = 1f,
    /**
     * Pokud true, karta stojí VŠECHEN dostupný zdroj daného costType (X-kost mechnika).
     * card.cost je v tomto případě 0 a slouží jen jako fallback; skutečná cena = veškeré
     * zásoby. Efekty třídy XScaled* dostanou hodnotu X = odebrané zásoby.
     */
    val isXCost: Boolean = false
)