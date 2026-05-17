// ============================================================
// Card.kt
// ============================================================
package com.example.termiti

/**
 * Zvukový efekt, který karta přehraje při sehrání.
 * Pokud je [Card.sound] null, zvuk se odvodí automaticky z efektů karty.
 */
enum class CardSound {
    ATTACK,       // útočný zvuk  (card_attack / card_attack_2)
    MINE_DESTROY, // zničení dolu (mine_destroy)
    BUILD,        // stavební zvuk (build)
    RESOURCE,     // surovinový zvuk
    DRAW,         // zvuk líznutí karty
    CARD_PLAY     // obecný zvuk zahrání
}

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
     * Pokud true, je karta "základní" — vždy dostupná v plném počtu, nelze ji rozebrat
     * ani ji nenajdeš v balíčcích. Ostatní karty (i COMMON) jsou sběratelské.
     */
    val isBasic: Boolean = false,
    /**
     * Pokud true, karta stojí VŠECHEN dostupný zdroj daného costType (X-kost mechnika).
     * card.cost je v tomto případě 0 a slouží jen jako fallback; skutečná cena = veškeré
     * zásoby. Efekty třídy XScaled* dostanou hodnotu X = odebrané zásoby.
     */
    val isXCost: Boolean = false,
    /**
     * Volitelný vlastní zvuk karty (kategorie). Pokud null, zvuk se odvozuje automaticky
     * ze seznamu efektů pomocí [playSoundForCard] v GameViewModel.
     * Ignoruje se, pokud je nastaven [soundResId].
     */
    val sound: CardSound? = null,
    /**
     * Zcela vlastní zvukový soubor pro tuto kartu (R.raw.xxx).
     * Má nejvyšší prioritu – přebije jak [sound], tak auto-detekci.
     * Příklad: soundResId = R.raw.my_special_card
     */
    val soundResId: Int? = null,
    /**
     * True = karta přišla do ruky mimořádnou cestou (Rozhodnutí, krádež, výměna rukou atd.),
     * nikoli normálním líznutím z vlastního balíčku. UI zobrazí ✨ odznak.
     */
    val isGenerated: Boolean = false,
    /**
     * Modifikátor ceny (delta od základní ceny). Záporná = sleva, kladná = zdražení.
     * Výsledná cena [effectiveCost] je vždy v rozsahu 0–99.
     * Neaplikuje se na X-kost karty (ty spotřebují všechny zásoby bez ohledu).
     */
    val costModifier: Int = 0
) {
    /**
     * Skutečná cena, která se platí a zobrazuje.
     * = (cost + costModifier) omezeno na 0..99.
     * Pro X-kost karty vždy 0 (skutečná hodnota X se určí z dostupných zdrojů).
     */
    val effectiveCost: Int get() =
        if (isXCost) 0 else (cost + costModifier).coerceIn(0, 99)
}