package com.example.termiti

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

// ============================================================
// RogueEnemies.kt
// Obtížnosti roguelike a generování soupeřů (balíček + staty).
// Čistý Kotlin bez Androidu – používá ho GameViewModel i simulace
// v src/test (EngineSimulation.rogueRuns).
// ============================================================

/**
 * Obtížnost runu. Učeň a Rytíř jsou dostupné hned, každá další se odemkne
 * dokončením předchozí ([requires]). Těžší obtížnost = silnější soupeři i lepší odměny.
 *
 * @param statMul       násobek hradu a hradeb soupeřů
 * @param healOnWin     léčení hradu po každé výhře
 * @param qualityShift  posun kvality balíčků soupeřů v bitvách (+3 = o tři bitvy napřed)
 * @param rerolls       nových nabídek karet za celý run
 * @param enemyStartBonus suroviny navíc pro soupeře na startu (magie, útok i kámen)
 * @param enemyHandBonus  karty navíc do startovní ruky soupeře
 * @param goldMul       násobek zlaťáků za bosse a za dokončení
 * @param finalPacks    balíčky za dokončení runu
 * @param firstClearPacks balíčky navíc za PRVNÍ dokončení této obtížnosti
 * @param finalXp       XP za dokončení runu
 *
 * Vyladěno simulací celých runů (EngineSimulation.rogueRuns, hráče hraje AI): Rytíř dokončí
 * zhruba stejně často jako dřív bez obtížností (~10 %), Učeň ~2× častěji, Vojevůdce ~1 %,
 * Noční můra skoro nikdy – člověk hraje líp než AI, absolutní čísla jsou proto nižší.
 * Násobek statů má na výsledek malý vliv; hlavní páky jsou kvalita balíčku a léčení.
 */
enum class RogueDifficulty(
    val statMul        : Double,
    val healOnWin      : Int,
    val qualityShift   : Int,
    val rerolls        : Int,
    val enemyStartBonus: Int,
    val enemyHandBonus : Int,
    val goldMul        : Double,
    val finalPacks     : Int,
    val firstClearPacks: Int,
    val finalXp        : Int
) {
    APPRENTICE(0.60,10, -4, 5, 0, 0, 0.5, 0, 1,  50),
    KNIGHT    (0.80, 8, -2, 3, 0, 0, 1.0, 1, 2, 100),
    WARLORD   (0.95, 5,  2, 2, 1, 0, 1.5, 2, 3, 150),
    NIGHTMARE (1.10, 2,  6, 1, 2, 1, 2.0, 3, 5, 200);

    /** Obtížnost, kterou je potřeba dokončit pro odemčení této (null = odemčená hned). */
    val requires: RogueDifficulty? get() = when (this) {
        APPRENTICE, KNIGHT -> null
        WARLORD            -> KNIGHT
        NIGHTMARE          -> WARLORD
    }

    /** Další obtížnost, kterou dokončení této odemkne (null = žádná). */
    val unlocks: RogueDifficulty? get() = entries.firstOrNull { it.requires == this }

    fun isUnlocked(cleared: Set<String>): Boolean = requires?.let { it.name in cleared } ?: true

    /** Zlaťáky za poraženého bosse aktu [act] (0-based, jen akty před posledním). */
    fun actBossGold(act: Int): Int = (RogueRewards.ACT_BOSS_GOLD.getOrElse(act) { 0 } * goldMul).roundToInt()

    /** Zlaťáky za dokončení runu (poražení posledního bosse). */
    val finalGold: Int get() = (RogueRewards.FINAL_GOLD * goldMul).roundToInt()

    /** Kolik zlaťáků celkem dá dokončený run (bossové + konec) – pro nabídku obtížností. */
    val totalRunGold: Int get() =
        (0 until RogueConfig.ACTS - 1).sumOf { actBossGold(it) } + finalGold

    /** Lokalizovaný název (oddělovač "|", pořadí jako enum). */
    val displayName: String get() =
        LanguageManager.currentStrings.rogueDifficultyNames.split("|").getOrElse(ordinal) { name }

    /** Lokalizovaný krátký popis (oddělovač "|", pořadí jako enum). */
    val displayDesc: String get() =
        LanguageManager.currentStrings.rogueDifficultyDescs.split("|").getOrElse(ordinal) { "" }

    companion object {
        fun fromName(name: String?): RogueDifficulty =
            entries.firstOrNull { it.name == name } ?: KNIGHT
    }
}

/** Odměny za postup runem (základ pro Rytíře, ostatní obtížnosti násobí [RogueDifficulty.goldMul]). */
object RogueRewards {
    /** Zlaťáky za bosse 1. a 2. aktu. */
    val ACT_BOSS_GOLD = listOf(40, 80)
    /** Zlaťáky za dokončení runu. */
    const val FINAL_GOLD = 150
}

/**
 * Archetyp soupeře: z jaké šablony balíčku ([PRESET_DECKS]) vychází. Šablony jsou
 * vyladěné constructed balíčky (hodně epických karet) – pro slabší soupeře se jejich
 * karty nahrazují levnějšími se stejnou surovinou, viz [RogueEnemyGen.buildDeck].
 *
 * @param minQuality od jaké kvality (viz [RogueEnemyGen.quality]) se archetyp může objevit –
 *   Sabotér a Chaos stojí na kartách za chaos, které jsou jen vzácné a lepší
 */
enum class RogueArchetype(val presets: List<String>, val minQuality: Int) {
    ATTACKER(listOf("⚔️ Útočník", "⚔️ Útočník2", "⚔️ AI Útočník"), 0),
    MAGE    (listOf("🔮 Mágik"), 0),
    DEFENDER(listOf("🏰 Obránce", "🏰 AI Obránce"), 0),
    COLOSSUS(listOf("🗿 Kolos"), 2),
    SABOTEUR(listOf("🕵️ Sabotér"), 4),
    CHAOS   (listOf("🌀 Chaos", "🌀 AI Chaos"), 5);

    /** Jméno soupeře v aktu [act] (lokalizované, jedno jméno na akt, oddělovač "|"). */
    fun enemyName(act: Int): String {
        val s = LanguageManager.currentStrings
        val raw = when (this) {
            ATTACKER -> s.rogueArchAttacker
            MAGE     -> s.rogueArchMage
            DEFENDER -> s.rogueArchDefender
            COLOSSUS -> s.rogueArchColossus
            SABOTEUR -> s.rogueArchSaboteur
            CHAOS    -> s.rogueArchChaos
        }
        val names = raw.split("|")
        return names.getOrElse(act) { names.last() }
    }
}

/** Staty soupeře na začátku bitvy. */
data class RogueEnemyStats(
    val castle     : Int,
    val wall       : Int,
    val startMagic : Int,
    val startAttack: Int,
    val startStones: Int,
    val handSize   : Int,
    val extraMines : Map<ResourceType, Int>
)

object RogueEnemyGen {

    /** Strop vzácností v balíčku soupeře (běžné karty bez stropu). */
    data class RarityCaps(val rare: Int, val epic: Int, val legendary: Int) {
        fun of(r: Rarity): Int = when (r) {
            Rarity.COMMON    -> Int.MAX_VALUE
            Rarity.RARE      -> rare
            Rarity.EPIC      -> epic
            Rarity.LEGENDARY -> legendary
        }
    }

    /**
     * Kvalita balíčku soupeře: roste po bitvách (ne skokem po aktech), boss má +1,
     * obtížnost posouvá celou křivku. Rytíř: 0 v 1.–3. bitvě → 10 u posledního bosse,
     * Noční můra 6 → 18.
     */
    fun quality(battleIndex: Int, isBoss: Boolean, difficulty: RogueDifficulty): Int =
        (battleIndex + (if (isBoss) 1 else 0) + difficulty.qualityShift).coerceAtLeast(0)

    /**
     * Stropy vzácností podle kvality, bez skoků mezi akty. Rytíř: 1. akt bez epických,
     * poslední boss 5 epických a 1 legendární; Noční můra (kvalita až 18) 11 epických a 3 legendární.
     */
    fun caps(q: Int): RarityCaps = RarityCaps(
        rare      = 8 + q,
        epic      = ((q - 3) * 3 / 4).coerceAtLeast(0),
        legendary = when { q >= 15 -> 3; q >= 11 -> 2; q >= 9 -> 1; else -> 0 }
    )

    /** Karty vhodné do balíčku soupeře (sbírané karty bez zakázaných, X-karet a pastí). */
    fun usable(c: Card): Boolean =
        !c.id.startsWith("T") && !c.isPlaceholder && !c.isGenerated && !c.isXCost &&
        c.id !in RogueConfig.ENEMY_DECK_BANLIST &&
        c.effects.none { it is CardEffect.TrapOnDraw }

    private fun isMine(c: Card) = c.effects.any {
        it is CardEffect.AddMine || it is CardEffect.DecisionMine || it is CardEffect.ConvertMine
    }

    /** Archetypy, které se při kvalitě [q] můžou objevit; [avoid] (předchozí soupeř) se vynechá. */
    fun pickArchetype(q: Int, avoid: RogueArchetype?, rnd: Random = Random): RogueArchetype {
        val ok = RogueArchetype.entries.filter { q >= it.minQuality }
        return (ok - setOfNotNull(avoid)).ifEmpty { ok }.random(rnd)
    }

    /**
     * Balíček soupeře z archetypu: náhodná šablona archetypu, karty v náhodném pořadí,
     * a co se nevejde do stropů vzácností (nebo je zakázané), nahradí karta
     * se STEJNOU surovinou, podobnou cenou, stejným druhem (důl / typ karty)
     * a co nejvyšší vzácností, která se ještě vejde. Výsledek má vždy tolik karet
     * jako šablona (30).
     */
    fun buildDeck(
        archetype: RogueArchetype,
        q        : Int,
        allCards : List<Card>,
        rnd      : Random = Random
    ): Map<String, Int> {
        val presetName = archetype.presets.random(rnd)
        val preset = PRESET_DECKS.firstOrNull { it.first == presetName }?.second ?: PRESET_DECKS.first().second
        // Soudržnost: slabý soupeř má z archetypu jen část balíčku, zbytek je náhodný
        // vyvážený balíček – s kvalitou roste podíl archetypu až na celý balíček.
        val total  = preset.values.sum()
        val fromArch = (total * coherence(q)).roundToInt().coerceIn(0, total)
        val archCopies = preset.flatMap { (id, n) -> List(n) { id } }.shuffled(rnd).take(fromArch)
        val filler = buildBalancedDeck(allCards.filter(::usable))
            .flatMap { (id, n) -> List(n) { id } }.shuffled(rnd).take(total - fromArch)
        val mixed = (archCopies + filler).groupingBy { it }.eachCount()
        return buildDeck(mixed, caps(q), allCards, rnd)
    }

    /**
     * Podíl balíčku soupeře, který pochází z archetypu, podle kvality [q] (10 % → 35 % u Rytíře,
     * až 55 % u Noční můry). Soudržný balíček je pro soupeře nejsilnější páka – plná šablona
     * i zlevněná na běžné karty byla výrazně těžší než dřívější náhodné balíčky.
     */
    fun coherence(q: Int): Double = (COHERENCE_BASE + q * COHERENCE_STEP).coerceIn(0.0, 1.0)
    private const val COHERENCE_BASE = 0.10
    private const val COHERENCE_STEP = 0.025

    fun buildDeck(
        preset  : Map<String, Int>,
        caps    : RarityCaps,
        allCards: List<Card>,
        rnd     : Random = Random
    ): Map<String, Int> {
        val byId   = allCards.associateBy { it.id }
        val pool   = allCards.filter(::usable)
        val counts = mutableMapOf<String, Int>()
        val byRarity = mutableMapOf<Rarity, Int>()

        fun fits(c: Card) =
            (counts[c.id] ?: 0) < c.rarity.maxCopies && (byRarity[c.rarity] ?: 0) < caps.of(c.rarity)
        fun add(c: Card) {
            counts[c.id] = (counts[c.id] ?: 0) + 1
            byRarity[c.rarity] = (byRarity[c.rarity] ?: 0) + 1
        }
        fun replacement(orig: Card): Card? {
            fun closest(cands: List<Card>) = cands.minByOrNull { c ->
                abs(c.cost - orig.cost) +
                (if (isMine(c) != isMine(orig)) 4.0 else 0.0) +
                (if (orig.type.isNotEmpty() && c.type != orig.type) 1.5 else 0.0) +
                rnd.nextDouble() * 2.0
            }
            // Nejvyšší vzácnost, která se vejde (nejvýš původní), napřed se stejnou surovinou
            val rarities = Rarity.entries.filter { it <= orig.rarity }.sortedDescending()
            for (sameType in listOf(true, false)) {
                for (r in rarities) {
                    val cands = pool.filter {
                        it.rarity == r && it.id != orig.id && fits(it) &&
                        (!sameType || it.costType == orig.costType)
                    }
                    closest(cands)?.let { return it }
                }
            }
            return null
        }

        val copies = preset.flatMap { (id, n) -> byId[id]?.let { c -> List(n) { c } } ?: emptyList() }.shuffled(rnd)
        for (c in copies) {
            if (usable(c) && fits(c)) add(c) else replacement(c)?.let(::add)
        }
        return counts
    }

    /**
     * Staty soupeře. Základ je dřívější křivka (hrad 16 → 42, hradby 6 → 19),
     * startovní magie a útok podle aktu, doly navíc od 2. aktu. Obtížnost násobí hrad
     * a hradby a přidává suroviny / karty do ruky.
     */
    fun stats(battleIndex: Int, difficulty: RogueDifficulty): RogueEnemyStats {
        val act    = battleIndex / RogueConfig.BATTLES_PER_ACT
        val isBoss = battleIndex % RogueConfig.BATTLES_PER_ACT == RogueConfig.BATTLES_PER_ACT - 1
        val boss   = if (isBoss) 1 else 0
        val castle = 16 + battleIndex * 2 + boss * 4
        val wall   = 6 + battleIndex + boss * 2
        val bonus  = difficulty.enemyStartBonus
        return RogueEnemyStats(
            castle      = (castle * difficulty.statMul).roundToInt(),
            wall        = (wall * difficulty.statMul).roundToInt(),
            startMagic  = act + boss + bonus,
            startAttack = act + boss + bonus,
            startStones = bonus,
            handSize    = 4 + (if (act >= 2) 1 else 0) + difficulty.enemyHandBonus,
            extraMines  = buildMap {
                if (act >= 1) put(ResourceType.ATTACK, 1)
                if (act >= 2) put(ResourceType.MAGIC, 1)
                if (isBoss)   put(ResourceType.STONES, 1)
            }
        )
    }

    /**
     * Cíl výhry stavbou: škáluje s maximem hradu hráče (+15), ne pevná konstanta – jinak
     * by šlo odměnami nastřádat hrad nad cíl a vyhrát dřív, než padne první karta.
     */
    fun winTarget(maxCastle: Int): Int = (maxCastle + 15).coerceAtLeast(70)
}
