package com.example.termiti

import kotlin.random.Random

// ── Výsledek otevření balíčku ─────────────────────────────────────────────────

/** Jedna karta získaná z balíčku. */
data class CardGain(
    val card       : Card,
    /** True = hráč již měl max kopií → karta se přeměnila na prach. */
    val isDuplicate: Boolean,
    /** Kolik prachu hráč dostal (0 pokud nebyl duplikát). */
    val dustGained : Int,
    /** True = první kopie této karty ve sbírce (před balíčkem ji hráč neměl vůbec). */
    val isNew      : Boolean = false
)

/** Výsledek otevření celého balíčku. */
data class PackResult(
    /** Karty v pořadí, v jakém se ukážou – ZAMÍCHANÉ, pozice neprozradí vzácnost. */
    val cards          : List<CardGain>,
    val totalDustGained: Int,
    /** True = legendární kartu v tomto balíčku zajistila záruka ([CardCollectionManager.PITY_PACKS]). */
    val pityUsed       : Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Správa sbírky karet: otevírání balíčků, crafting, dismantling, dotazy.
 *
 * Veškerý stav je uložen v [PlayerProfile] přes [PlayerProfileManager].
 * CardCollectionManager nemá vlastní SharedPreferences.
 */
object CardCollectionManager {

    // ── Konstanty ─────────────────────────────────────────────────────────────

    /** Cena jednoho balíčku v zlatě. */
    const val PACK_COST_GOLD = 100

    /** Počet karet v jednom balíčku. */
    const val PACK_SIZE = 5

    /**
     * Záruka legendární karty: padne nejpozději v tolikátém balíčku od té poslední.
     * (Bez záruky má legendární zhruba každý čtvrtý až pátý balíček; série deseti
     * balíčků bez ní vyjde asi v 8 % případů – záruka tu smůlu zastropuje.)
     */
    const val PITY_PACKS = 10

    /**
     * Váhy pro garantovaný slot (poslední slot v balíčku, vždy vzácná nebo lepší).
     * Záměrně jiné váhy než standardní slot — lepší šance na Epic/Legendární.
     */
    private val RARE_PLUS_WEIGHTS = listOf(
        Rarity.RARE      to 70,
        Rarity.EPIC      to 25,
        Rarity.LEGENDARY to  5
    )

    // ── Dotazy ────────────────────────────────────────────────────────────────

    /**
     * True = "základní" karta — označena příznakem [Card.isBasic].
     * Vždy dostupná v plném počtu kopií, nelze ji rozebrat ani ji nenajdeš v balíčcích.
     */
    fun isBasicCard(card: Card): Boolean = card.isBasic

    /** Kolik kopií dané karty hráč vlastní (bez ohledu na allCardsUnlocked). */
    fun ownedCopies(cardId: String): Int =
        PlayerProfileManager.profile?.cardCollection?.getOrDefault(cardId, 0) ?: 0

    /**
     * True pokud lze kartu vložit do balíčku nebo ji jinak "použít".
     * Respektuje základní karty (vždy odemčeny) + přepínač allCardsUnlocked.
     */
    fun isUnlocked(card: Card): Boolean {
        val p = PlayerProfileManager.profile ?: return false
        return p.allCardsUnlocked || isBasicCard(card) || ownedCopies(card.id) > 0
    }

    /**
     * Kolik kopií dané karty smí hráč skutečně dát do balíčku.
     * Základní (COMMON) karty mají vždy maxCopies.
     * Sběratelské (RARE+) karty jsou omezeny skutečně vlastněným počtem.
     */
    fun usableCopies(card: Card): Int {
        val p = PlayerProfileManager.profile ?: return 0
        if (p.allCardsUnlocked || isBasicCard(card)) return card.rarity.maxCopies
        return minOf(ownedCopies(card.id), card.rarity.maxCopies)
    }

    // ── Balíčky ───────────────────────────────────────────────────────────────

    /** Karty, které můžou padnout z balíčku: ne základní (ty má hráč vždy), ne zástupné. */
    private fun packPool(allCards: List<Card>): List<Card> =
        allCards.filter { !isBasicCard(it) && !it.isPlaceholder }.ifEmpty { allCards }

    /** Kolik neotevřených balíčků má hráč v zásobě. */
    fun unopenedPacks(): Int = PlayerProfileManager.profile?.unopenedPacks ?: 0

    /** Za kolik balíčků nejpozději padne legendární karta (1 = hned v příštím). */
    fun packsUntilGuaranteedLegendary(): Int =
        (PITY_PACKS - (PlayerProfileManager.profile?.packsSinceLegendary ?: 0)).coerceIn(1, PITY_PACKS)

    /** True pokud má hráč dost zlata na [count] balíčků. */
    fun canBuyPacks(count: Int = 1): Boolean =
        count > 0 && (PlayerProfileManager.profile?.gold ?: 0) >= count * PACK_COST_GOLD

    /**
     * Koupí [count] balíčků do zásoby (neotevírá je). Obsah se losuje až při otevření –
     * viz [openStoredPack]. Vrátí false, pokud hráč nemá dost zlata.
     */
    fun buyPacks(count: Int = 1): Boolean {
        val p = PlayerProfileManager.profile ?: return false
        if (!canBuyPacks(count)) return false
        PlayerProfileManager.save(
            p.copy(gold = p.gold - count * PACK_COST_GOLD, unopenedPacks = p.unopenedPacks + count)
        )
        return true
    }

    /** Přidá balíčky do zásoby zdarma (odměny). */
    fun grantPacks(count: Int) {
        val p = PlayerProfileManager.profile ?: return
        if (count > 0) PlayerProfileManager.save(p.copy(unopenedPacks = p.unopenedPacks + count))
    }

    /**
     * Otevře jeden balíček ze zásoby:
     *  • vylosuje [PACK_SIZE] karet (jeden slot garantuje vzácnou+, při záruce legendární),
     *  • duplikáty nad [Rarity.maxCopies] přemění na prach,
     *  • uloží sbírku, prach, zásobu a počitadlo záruky HNED – karty hráč vlastní, i kdyby
     *    aplikaci zavřel uprostřed odhalování.
     *
     * Vrátí [PackResult], nebo null bez profilu / s prázdnou zásobou.
     */
    fun openStoredPack(allCards: List<Card>, random: Random = Random.Default): PackResult? {
        val p = PlayerProfileManager.profile ?: return null
        if (p.unopenedPacks <= 0) return null

        val collection = p.cardCollection.toMutableMap()
        val result = rollPack(
            pool           = packPool(allCards),
            collection     = collection,
            forceLegendary = p.packsSinceLegendary >= PITY_PACKS - 1,
            random         = random
        )
        val gotLegendary = result.cards.any { it.card.rarity == Rarity.LEGENDARY }
        PlayerProfileManager.save(
            p.copy(
                unopenedPacks       = p.unopenedPacks - 1,
                cardCollection      = collection,
                dust                = p.dust + result.totalDustGained,
                packsSinceLegendary = if (gotLegendary) 0 else p.packsSinceLegendary + 1
            )
        )
        return result
    }

    /**
     * Vylosuje obsah jednoho balíčku a zapíše nové karty do [collection] (in-place).
     * Čistá funkce bez profilu – dá se testovat se seedovaným [random].
     *
     * @param forceLegendary záruka: když mezi běžnými sloty legendární nepadne, garantovaný
     *   slot ji dostane (místo běžného losu vzácná/epická/legendární).
     */
    internal fun rollPack(
        pool: List<Card>,
        collection: MutableMap<String, Int>,
        forceLegendary: Boolean,
        random: Random = Random.Default
    ): PackResult {
        val cards = mutableListOf<Card>()
        // Sloty 1 až (PACK_SIZE-1): standardní náhodný výběr
        repeat(PACK_SIZE - 1) { cards += randomCard(pool, guaranteeRare = false, random = random) }

        // Poslední slot: garantovaná vzácná nebo lepší – při záruce legendární
        val legendaries = pool.filter { it.rarity == Rarity.LEGENDARY }
        val pity = forceLegendary && legendaries.isNotEmpty() && cards.none { it.rarity == Rarity.LEGENDARY }
        cards += if (pity) legendaries.random(random) else randomCard(pool, guaranteeRare = true, random = random)

        val gains = cards.map { processCardGain(it, collection) }
        return PackResult(
            cards           = gains.shuffled(random),   // pozice ve vějíři nesmí prozradit garantovaný slot
            totalDustGained = gains.sumOf { it.dustGained },
            pityUsed        = pity
        )
    }

    /**
     * Postup sbírky: (kolik různých karet hráč má, kolik jich lze mít).
     * Počítají se karty, které jdou dát do balíčku; základní má hráč vždy.
     */
    fun collectionProgress(allCards: List<Card>): Pair<Int, Int> {
        val all = allCards.filter { !it.isPlaceholder && it.effects.none { e -> e is CardEffect.TrapOnDraw } }
        return all.count { isBasicCard(it) || ownedCopies(it.id) > 0 } to all.size
    }

    // ── Crafting a dismantling ────────────────────────────────────────────────

    /**
     * Vyrobí 1 kopii karty z prachu.
     * Vrátí true při úspěchu, false pokud nemá dost prachu nebo
     * má hráč již max kopií.
     */
    fun craftCard(cardId: String, allCards: List<Card>): Boolean {
        val p    = PlayerProfileManager.profile ?: return false
        val card = allCards.find { it.id == cardId } ?: return false
        val cost = card.rarity.craftCost
        if (p.dust < cost) return false
        val current = p.cardCollection.getOrDefault(cardId, 0)
        if (current >= card.rarity.maxCopies) return false
        PlayerProfileManager.save(
            p.copy(
                dust           = p.dust - cost,
                cardCollection = p.cardCollection + (cardId to current + 1)
            )
        )
        return true
    }

    /**
     * Rozmontuje 1 kopii karty → přidá prach.
     * Vrátí true při úspěchu, false pokud hráč kartu nevlastní.
     */
    fun dismantleCard(cardId: String, allCards: List<Card>): Boolean {
        val p    = PlayerProfileManager.profile ?: return false
        val card = allCards.find { it.id == cardId } ?: return false
        if (isBasicCard(card)) return false   // základní karty nelze rozebrat
        val current = p.cardCollection.getOrDefault(cardId, 0)
        if (current <= 0) return false
        val newCollection = if (current == 1)
            p.cardCollection - cardId
        else
            p.cardCollection + (cardId to current - 1)
        PlayerProfileManager.save(
            p.copy(
                dust           = p.dust + card.rarity.dustValue,
                cardCollection = newCollection
            )
        )
        return true
    }

    // ── Dev / debug přepínač ──────────────────────────────────────────────────

    /** Zapne nebo vypne režim "všechny karty odemčeny" a uloží profil. */
    fun setAllCardsUnlocked(value: Boolean) {
        val p = PlayerProfileManager.profile ?: return
        PlayerProfileManager.save(p.copy(allCardsUnlocked = value))
    }

    // ── Startovní kolekce ─────────────────────────────────────────────────────

    /**
     * Udělí hráči startovní kolekci: 2 kopie každé COMMON karty.
     * Zavolej jednou při vytvoření nového profilu (pokud allCardsUnlocked = false).
     * Existující kopie se neovepíší (bere maximum).
     */
    fun grantStarterCollection(allCards: List<Card>) {
        val p = PlayerProfileManager.profile ?: return
        val starter = allCards
            .filter { it.isBasic }
            .associate { it.id to 2 }
        val merged = (p.cardCollection.keys + starter.keys).distinct().associateWith { id ->
            maxOf(p.cardCollection.getOrDefault(id, 0), starter.getOrDefault(id, 0))
        }
        PlayerProfileManager.save(p.copy(cardCollection = merged))
    }

    // ── Interní ───────────────────────────────────────────────────────────────

    /**
     * Zpracuje zisk jedné karty — přidá ji do kolekce nebo ji přemění na prach.
     * Modifikuje [collection] in-place.
     */
    private fun processCardGain(card: Card, collection: MutableMap<String, Int>): CardGain {
        val current = collection.getOrDefault(card.id, 0)
        return if (current >= card.rarity.maxCopies) {
            // Duplikát → prach (kolekce se nemění)
            CardGain(card, isDuplicate = true, dustGained = card.rarity.dustValue)
        } else {
            collection[card.id] = current + 1
            CardGain(card, isDuplicate = false, dustGained = 0, isNew = current == 0)
        }
    }

    /**
     * Vybere náhodnou kartu vážit dle rarity.
     * [guaranteeRare] = true → pouze vzácná nebo lepší (garantovaný slot).
     */
    private fun randomCard(allCards: List<Card>, guaranteeRare: Boolean, random: Random = Random.Default): Card {
        // Použij pouze rarity, které mají alespoň jednu kartu v poolu
        val availableRarities = allCards.map { it.rarity }.toSet()
        val rarity = if (guaranteeRare) {
            drawRarity(RARE_PLUS_WEIGHTS.filter { it.first in availableRarities }, random)
        } else {
            drawRarity(Rarity.entries.filter { it in availableRarities }.map { it to it.packWeight }, random)
        }
        // Fallback: pokud žádná karta dané rarity neexistuje, vrátíme náhodnou
        val pool = allCards.filter { it.rarity == rarity }.ifEmpty { allCards }
        return pool.random(random)
    }

    /** Váhované losování rarity z předané tabulky (rarity → weight). */
    private fun drawRarity(weights: List<Pair<Rarity, Int>>, random: Random = Random.Default): Rarity {
        val total = weights.sumOf { it.second }
        var roll  = random.nextInt(1, total + 1)
        for ((rarity, weight) in weights) {
            roll -= weight
            if (roll <= 0) return rarity
        }
        return weights.last().first
    }
}
