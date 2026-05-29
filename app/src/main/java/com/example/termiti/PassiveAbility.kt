package com.example.termiti

/**
 * Definice všech pasivních schopností.
 * Efekty se aplikují v [GameViewModel.createInitialState] na startovní [PlayerState].
 */
enum class PassiveAbility(
    val id:          String,
    val title:       String,
    val description: String,
    val icon:        String,
    val unlockLevel: Int,
    val goldCost:    Int
) {
    EXTRA_CASTLE(
        id          = "extra_castle",
        title       = "Pevný hrad",
        description = "+5 startovní hrad, ale vítězný cíl se zvýší z 60 na 65.",
        icon        = "🏰",
        unlockLevel = 2,
        goldCost    = 150
    ),
    EXTRA_WALL(
        id          = "extra_wall",
        title       = "Silné hradby",
        description = "+5 startovní hradba.",
        icon        = "🧱",
        unlockLevel = 3,
        goldCost    = 100
    ),
    EXTRA_MAGIC(
        id          = "extra_magic",
        title       = "Magický talent",
        description = "+1 magie na začátku hry.",
        icon        = "✨",
        unlockLevel = 4,
        goldCost    = 120
    ),
    EXTRA_ATTACK(
        id          = "extra_attack",
        title       = "Bojový výcvik",
        description = "+1 útok na začátku hry.",
        icon        = "⚔️",
        unlockLevel = 4,
        goldCost    = 120
    ),
    EXTRA_STONES(
        id          = "extra_stones",
        title       = "Kamenný základ",
        description = "+1 kameny na začátku hry.",
        icon        = "🪨",
        unlockLevel = 5,
        goldCost    = 120
    ),
    EXTRA_CHAOS(
        id          = "extra_chaos",
        title       = "Chaotická mysl",
        description = "+1 chaos na začátku hry.",
        icon        = "🌀",
        unlockLevel = 6,
        goldCost    = 150
    ),

    // ── Velká ruka ───────────────────────────────────────────────────────────
    EXTRA_HAND_CARD(
        id          = "extra_hand_card",
        title       = "Velká ruka",
        description = "Maximální velikost ruky vzroste na 8 (místo 7).",
        icon        = "🃏",
        unlockLevel = 5,
        goldCost    = 140
    ),

    // ── Nedobytná pevnost ────────────────────────────────────────────────────
    IRON_BASTION(
        id          = "iron_bastion",
        title       = "Nedobytná pevnost",
        description = "Soupeř potřebuje dosáhnout 75 bodů hradu k výhře výstavbou (místo 70).",
        icon        = "🛡️",
        unlockLevel = 7,
        goldCost    = 160
    ),

    // ── Rychlý tah ────────────────────────────────────────────────────────────
    QUICK_DRAW(
        id          = "quick_draw",
        title       = "Rychlý tah",
        description = "Na začátku prvního kola lízneš 1 kartu navíc.",
        icon        = "🤌",
        unlockLevel = 8,
        goldCost    = 130
    ),

    // ── Posila balíčku ────────────────────────────────────────────────────────
    BOOST_ATTACK(
        id          = "boost_attack",
        title       = "Útočná posila",
        description = "Do balíčku se přidají 2 náhodné útočné karty.",
        icon        = "⚔️",
        unlockLevel = 8,
        goldCost    = 160
    ),
    BOOST_BUILD(
        id          = "boost_build",
        title       = "Stavební posila",
        description = "Do balíčku se přidají 2 náhodné stavební karty.",
        icon        = "🏗️",
        unlockLevel = 8,
        goldCost    = 160
    ),
    BOOST_MAGIC(
        id          = "boost_magic",
        title       = "Magická posila",
        description = "Do balíčku se přidají 2 náhodné magické karty.",
        icon        = "✨",
        unlockLevel = 8,
        goldCost    = 160
    ),
    BOOST_CHAOS(
        id          = "boost_chaos",
        title       = "Chaotická posila",
        description = "Do balíčku se přidají 2 náhodné chaos karty.",
        icon        = "🌀",
        unlockLevel = 9,
        goldCost    = 180
    ),
    BOOST_RANDOM(
        id          = "boost_random",
        title       = "Náhodná posila",
        description = "Do balíčku se přidají 3 náhodné karty libovolného typu.",
        icon        = "🎲",
        unlockLevel = 8,
        goldCost    = 150
    );

    /** Lokalizovaný název (jazykový balíček podle [id]) → vestavěná čeština [title]. */
    fun localizedTitle(): String = LanguageManager.abilityTitle(id, title)

    /** Lokalizovaný popis → vestavěná čeština [description]. */
    fun localizedDescription(): String = LanguageManager.abilityDesc(id, description)

    companion object {
        fun fromId(id: String): PassiveAbility? = entries.find { it.id == id }

        /** Maximální počet aktivních schopností najednou. */
        const val MAX_ACTIVE = 2
    }
}
