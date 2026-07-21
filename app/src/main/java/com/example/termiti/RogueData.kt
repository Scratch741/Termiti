package com.example.termiti

// ============================================================
// RogueData.kt
// Datová vrstva roguelike módu (Varianta A – lineární žebřík).
//   • Vybereš 15 karet z bodového rozpočtu.
//   • Probíjíš se 12 bitvami (3 akty × 4), soupeři sílí.
//   • HP hradu se PŘENÁŠÍ mezi bitvami (zastropování na maxCastle),
//     hradby se resetují každou bitvu. Léčení jen přes výhru/odměny.
//   • Po každé výhře: vyber 1 ze 3 karet, nebo stat-upgrade, nebo skip.
//   • Prohra bitvy = konec runu.
// ============================================================

enum class RoguePhase { DRAFT, BATTLE, REWARD, ENDED }

/** Všechna laditelná čísla módu na jednom místě. */
object RogueConfig {
    const val DECK_SIZE         = 20
    const val BUDGET            = 20     // body nad common (Leg 4 / Epic 2 / Rare 1 / Common 0)
    const val ACTS              = 3
    const val BATTLES_PER_ACT   = 4
    const val TOTAL_BATTLES     = ACTS * BATTLES_PER_ACT   // 12
    const val START_MAX_CASTLE  = 30
    const val START_WALL        = 15
    const val AUTO_HEAL_ON_WIN  = 5      // heal po každé výhře (i při skipu odměny)
    const val REWARD_MAX_CASTLE = 6      // +max hrad (+ vyléčí stejně)
    const val REWARD_WALL       = 6      // +startovní hradby
    const val REWARD_REPAIR     = 15     // oprava hradu (heal)

    fun rarityBudgetCost(r: Rarity): Int = when (r) {
        Rarity.COMMON    -> 0
        Rarity.RARE      -> 1
        Rarity.EPIC      -> 2
        Rarity.LEGENDARY -> 4
    }

    /**
     * Karty vyřazené z GENEROVÁNÍ nepřátelských balíčků – morph/bomba/rozbíjivé
     * karty, které v náhodném decku působí divně nebo je AI hraje slabě.
     * (Hráč si je do svého balíčku draftovat MŮŽE – ban platí jen pro nepřátele.)
     */
    val ENEMY_DECK_BANLIST: Set<String> = setOf(
        "C33",  // Krádež identity (SwapHands)
        "C34",  // Shapeshifter
        "C36",  // Skrytá bomba (sype bomby do balíčku)
        "C39",  // Velký zmatek (RandomizeHands)
        "C41",  // Zrcadlo (Mirror)
        "C42",  // Klon (Clone)
        "101", "102", "103"  // X-kost karty
    )

    val ACT_TITLES = listOf("Hranice", "Válečná pole", "Citadela")

    val ENEMY_NAMES = listOf(
        listOf("Goblin zvěd", "Pěšák", "Nájezdník", "Lučištník", "Zloděj"),
        listOf("Válečník", "Sabotér", "Temný učeň", "Obléhatel", "Žoldnéř"),
        listOf("Generál", "Arcimág", "Pán citadely", "Válečný vládce", "Katan")
    )
}

/** Odměna po vyhrané bitvě. Kartové jsou generované, statové fixní. */
sealed class RogueReward {
    data class AddCard(val card: Card) : RogueReward()
    object MaxCastle : RogueReward()   // +max hrad
    object Wall      : RogueReward()   // +hradby
    object Repair    : RogueReward()   // oprava HP
    object MineMagic : RogueReward()   // +1 startovní důl magie (permanentní ekonomika)
}

/**
 * Neměnný stav jednoho runu. Mění se výměnou celé instance (Compose detekuje
 * změnu → recompose), stejně jako GameState.
 */
data class RogueRun(
    val deck        : List<Card>,
    val maxCastle   : Int,
    val hp          : Int,
    val wall        : Int,
    val bonusMines  : Map<ResourceType, Int> = emptyMap(),
    /** 0-based index bitvy, KTERÁ SE PRÁVĚ HRAJE (nebo je připravena po odměně). */
    val battleIndex : Int = 0,
    val enemy       : CampaignOpponent? = null,
    val rewardCards : List<Card> = emptyList()
) {
    val act: Int get() = battleIndex / RogueConfig.BATTLES_PER_ACT
    val isBoss: Boolean get() =
        (battleIndex % RogueConfig.BATTLES_PER_ACT) == RogueConfig.BATTLES_PER_ACT - 1
    val actTitle: String get() = RogueConfig.ACT_TITLES.getOrElse(act) { "?" }
    /** 1-based popis pokroku, např. "Bitva 3 / 12". */
    val battleLabel: String get() = "Bitva ${(battleIndex + 1).coerceAtMost(RogueConfig.TOTAL_BATTLES)} / ${RogueConfig.TOTAL_BATTLES}"
}
