package com.example.termiti

// ── Výsledek otevření balíčku ─────────────────────────────────────────────────

/** Jedna karta získaná z balíčku. */
data class CardGain(
    val card       : Card,
    /** True = hráč již měl max kopií → karta se přeměnila na prach. */
    val isDuplicate: Boolean,
    /** Kolik prachu hráč dostal (0 pokud nebyl duplikát). */
    val dustGained : Int
)

/** Výsledek otevření celého balíčku. */
data class PackResult(
    val cards          : List<CardGain>,
    val totalDustGained: Int
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

    /** True pokud má hráč dost zlata na otevření balíčku. */
    fun canOpenPack(): Boolean =
        (PlayerProfileManager.profile?.gold ?: 0) >= PACK_COST_GOLD

    /**
     * Otevře jeden balíček:
     *  • Odečte [PACK_COST_GOLD] ze zlata.
     *  • Vygeneruje [PACK_SIZE] karet (poslední slot garantuje vzácnou+).
     *  • Duplikáty nad [Rarity.maxCopies] se přemění na prach.
     *  • Uloží aktualizovaný profil.
     *
     * Vrátí [PackResult] nebo null pokud nemá hráč dost zlata / žádný profil.
     */
    fun openPack(allCards: List<Card>): PackResult? {
        val p = PlayerProfileManager.profile ?: return null
        if (p.gold < PACK_COST_GOLD) return null

        // Balíčky obsahují pouze sběratelské karty (ne základní COMMON)
        val collectible = allCards.filter { !isBasicCard(it) }.ifEmpty { allCards }

        val collection = p.cardCollection.toMutableMap()
        val gains      = mutableListOf<CardGain>()
        var dustTotal  = 0

        // Sloty 1 až (PACK_SIZE-1): standardní náhodný výběr
        repeat(PACK_SIZE - 1) {
            val card = randomCard(collectible, guaranteeRare = false)
            val gain = processCardGain(card, collection)
            dustTotal += gain.dustGained
            gains     += gain
        }

        // Poslední slot: garantovaná vzácná nebo lepší
        val bonusCard = randomCard(collectible, guaranteeRare = true)
        val bonusGain = processCardGain(bonusCard, collection)
        dustTotal += bonusGain.dustGained
        gains     += bonusGain

        PlayerProfileManager.save(
            p.copy(
                gold           = p.gold - PACK_COST_GOLD,
                cardCollection = collection,
                dust           = p.dust + dustTotal
            )
        )
        return PackResult(gains, dustTotal)
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
            CardGain(card, isDuplicate = false, dustGained = 0)
        }
    }

    /**
     * Vybere náhodnou kartu vážit dle rarity.
     * [guaranteeRare] = true → pouze vzácná nebo lepší (garantovaný slot).
     */
    private fun randomCard(allCards: List<Card>, guaranteeRare: Boolean): Card {
        // Použij pouze rarity, které mají alespoň jednu kartu v poolu
        val availableRarities = allCards.map { it.rarity }.toSet()
        val rarity = if (guaranteeRare) {
            drawRarity(RARE_PLUS_WEIGHTS.filter { it.first in availableRarities })
        } else {
            drawRarity(Rarity.entries.filter { it in availableRarities }.map { it to it.packWeight })
        }
        // Fallback: pokud žádná karta dané rarity neexistuje, vrátíme náhodnou
        val pool = allCards.filter { it.rarity == rarity }.ifEmpty { allCards }
        return pool.random()
    }

    /** Váhované losování rarity z předané tabulky (rarity → weight). */
    private fun drawRarity(weights: List<Pair<Rarity, Int>>): Rarity {
        val total = weights.sumOf { it.second }
        var roll  = (1..total).random()
        for ((rarity, weight) in weights) {
            roll -= weight
            if (roll <= 0) return rarity
        }
        return weights.last().first
    }
}
