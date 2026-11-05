package com.example.termiti

// ============================================================
// RogueData.kt
// Datová vrstva roguelike módu (Varianta A – lineární žebřík).
//   • Vybereš 15 karet z bodového rozpočtu.
//   • Probíjíš se 12 bitvami (3 akty × 4), soupeři sílí.
//   • HP hradu se PŘENÁŠÍ mezi bitvami (zastropování na maxCastle),
//     hradby se resetují každou bitvu. Léčení jen přes výhru/odměny.
//   • Obtížnost (RogueDifficulty v RogueEnemies.kt) mění soupeře, léčení,
//     rerolly i odměny; soupeři mají archetyp (RogueArchetype).
//   • Po každé výhře: POVINNĚ vyber 2 (po bossovi 3) karty Rare+ – nelze
//     přeskočit, balíček musí růst. Po každém výběru se nabídka obnoví (ne
//     jen zmizí vybraná karta). Reroll nabídky lze udělat jen pár× za CELÝ
//     run. Až po povinných kartách, jen po zabití bosse, ještě bonus navíc
//     (stat/oprava/důl) nebo přeskoč.
//   • Prohra bitvy = konec runu.
// ============================================================

enum class RoguePhase { DRAFT, BATTLE, REWARD, ENDED }

/** Uložená šablona roguelike draftu (baseId → počet), obdoba [Deck] z konstruovaného módu. */
data class RoguePreset(val name: String, val cardCounts: Map<String, Int> = emptyMap())

/** Všechna laditelná čísla módu na jednom místě. */
object RogueConfig {
    const val DECK_SIZE         = 20
    const val BUDGET            = 20     // body nad common (Leg 4 / Epic 2 / Rare 1 / Common 0)
    const val ACTS              = 3
    const val BATTLES_PER_ACT   = 4
    const val TOTAL_BATTLES     = ACTS * BATTLES_PER_ACT   // 12
    const val START_MAX_CASTLE  = 30
    const val START_WALL        = 15
    const val REWARD_MAX_CASTLE = 6      // bonus po bossovi: +max hrad (+ vyléčí stejně)
    const val REWARD_WALL       = 6      // bonus po bossovi: +startovní hradby
    const val REWARD_REPAIR     = 15     // bonus po bossovi: oprava hradu (heal)

    // Povinné kartové odměny – balíček MUSÍ růst, nejde jen stavět staty.
    const val REWARD_CARD_PICKS      = 2   // běžná výhra: kolik karet si musíš vzít
    const val REWARD_CARD_PICKS_BOSS = 3   // výhra nad bossem: kolik karet si musíš vzít
    const val REWARD_CARD_OFFERS      = 4  // z kolika běžná výhra vybírá
    const val REWARD_CARD_OFFERS_BOSS = 5  // z kolika výhra nad bossem vybírá

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

    /** Lokalizované – čte se z jazykového balíčku (oddělovač "|"). */
    val ACT_TITLES: List<String> get() = LanguageManager.currentStrings.rogueActTitles.split("|")
}

/**
 * BONUSOVÁ odměna (jen po zabití bosse, navíc k povinným kartám). Výběr karet
 * na povinnou část NENÍ součástí tohoto typu – řeší ho GameViewModel.pickRewardCard(Card)
 * přímo, protože je to jiný (nepřeskočitelný) krok.
 */
sealed class RogueReward {
    object MaxCastle : RogueReward()   // +max hrad
    object Wall      : RogueReward()   // +hradby
    object Repair    : RogueReward()   // oprava HP
    data class Mine(val type: ResourceType) : RogueReward()   // +1 startovní důl zvoleného typu
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
    val rewardCards : List<Card> = emptyList(),
    /** Kolik POVINNÝCH karet ještě musí hráč vybrat na aktuální odměňovací obrazovce. */
    val rewardCardPicksLeft   : Int = 0,
    /** true = po vybrání povinných karet ještě čeká bonus (jen po bossovi). */
    val rewardBonusAvailable  : Boolean = false,
    /** Kolik rerollů nabídky karet zbývá – sdílená zásoba na CELÝ run, ne na 1 odměnu. */
    val rerollsLeft : Int = RogueDifficulty.KNIGHT.rerolls,
    val difficulty  : RogueDifficulty = RogueDifficulty.KNIGHT,
    /** Archetyp posledního soupeře – další soupeř bude jiný. */
    val lastArchetype: RogueArchetype? = null,
    /** Zlaťáky, které run zatím vydělal za bosse (připisují se hned, tady jen pro souhrn). */
    val goldEarned  : Int = 0
) {
    val act: Int get() = battleIndex / RogueConfig.BATTLES_PER_ACT
    val isBoss: Boolean get() =
        (battleIndex % RogueConfig.BATTLES_PER_ACT) == RogueConfig.BATTLES_PER_ACT - 1
    val actTitle: String get() = RogueConfig.ACT_TITLES.getOrElse(act) { "?" }
    /** 1-based popis pokroku, např. "Bitva 3 / 12". */
    val battleLabel: String get() = LanguageManager.currentStrings.rogueBattleLabel
        .format((battleIndex + 1).coerceAtMost(RogueConfig.TOTAL_BATTLES), RogueConfig.TOTAL_BATTLES)
}

/** Souhrn odměn skončeného runu pro obrazovku konce (všechno už je připsané na účet). */
data class RogueEndReward(
    /** Zlaťáky za celý run (bossové + dokončení). */
    val gold           : Int = 0,
    /** Balíčky celkem, včetně bonusu za první dokončení. */
    val packs          : Int = 0,
    val xp             : Int = 0,
    /** Z toho balíčky za PRVNÍ dokončení obtížnosti (0 = už dřív dokončeno). */
    val firstClearPacks: Int = 0,
    /** Obtížnost, kterou tenhle run nově odemkl. */
    val unlocked       : RogueDifficulty? = null
)
