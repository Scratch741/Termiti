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
    val costModifier: Int = 0,
    /**
     * True = karta je pouze gameplay-placeholder (např. "Explodovaná bomba").
     * Nesmí se zobrazovat v katalogu karet, ani být targetem Decision efektů
     * (DecisionBurnOpponent, DecisionFromDeck, DecisionFromDiscard).
     */
    val isPlaceholder: Boolean = false,
    /**
     * Skloňovaný název karty ve 4. pádu (akuzativ) pro log zprávy jako "lízl Bombu".
     * Pokud null, použije se [name] bez skloňování.
     */
    val nameAccusative: String? = null,
    /**
     * Override id for localization lookup. Used by Shapeshifter: a transformed
     * instance keeps its `C34_…` [id] (to re-transform + keep the UI slot), but
     * its localized text must resolve to the card it became — set to that
     * template's base id. Null = use [baseId].
     */
    val localizationId: String? = null,
    /**
     * Podmínkové efekty zkopírované ze zdrojové karty (Mirror/Clone).
     * Slouží POUZE k zobrazení ✓/✗ indikátoru — při hraní karty se NESPOUŠTĚJÍ.
     */
    val overlayEffects: List<CardEffect> = emptyList()
) {
    /**
     * Skutečná cena, která se platí a zobrazuje.
     * = (cost + costModifier) omezeno na 0..99.
     * Pro X-kost karty vždy 0 (skutečná hodnota X se určí z dostupných zdrojů).
     */
    val effectiveCost: Int get() =
        if (isXCost) 0 else (cost + costModifier).coerceIn(0, 99)

    /**
     * Base card id stripped of any runtime suffix (clone/stolen/decision copies are
     * `<baseId>_<uuid>`). Real base ids never contain '_' except the `__res_*`
     * resource placeholders, which are returned unchanged.
     */
    val baseId: String get() = if (id.startsWith("__")) id else id.substringBefore('_')

    /**
     * Localized display name — resolves through the active language pack
     * (by [baseId]); falls back to the built-in Czech [name] when untranslated.
     * Read inside a Composable to recompose automatically on language change.
     */
    val displayName: String get() = LanguageManager.cardName(localizationId ?: baseId, name)

    /**
     * Localized display description — active language pack (by [localizationId]
     * or [baseId]) → built-in Czech [description] fallback.
     */
    val displayDescription: String get() = LanguageManager.cardDesc(localizationId ?: baseId, description)

    /**
     * Localized card-type band label (Magie/Útok/Stavba/Chaos/Důl → active language).
     * Unknown values (e.g. already-localized resource placeholders) pass through.
     */
    val displayType: String get() {
        val s = LanguageManager.currentStrings
        return when (type) {
            "Magie"  -> s.typeMagic
            "Útok"   -> s.typeAttack
            "Stavba" -> s.typeBuild
            "Chaos"  -> s.typeChaos
            "Důl"    -> s.typeMines
            else     -> type
        }
    }
}
