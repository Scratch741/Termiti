package com.example.termiti

import kotlin.random.Random

/**
 * Vygeneruje vyvážený náhodný balíček (9 magie / 9 útoku / 9 stavby / 3 chaos).
 *
 * Podmínky:
 *  • Alespoň 1 důl pro každý zdroj (MAGIC, ATTACK, STONES)
 *  • Alespoň 3 karty generující Chaos (AddResource CHAOS nebo AddMine CHAOS)
 *  • Respektuje max kopie dle rarity (COMMON=4, RARE=3, EPIC=2, LEGENDARY=1)
 *  • Lehce penalizuje karty za 0 a 5+ many pro zdravou mana-curve
 *
 * @return mapa cardId → počet kopií (celkem 30 karet)
 */
fun buildBalancedDeck(allCards: List<Card>): Map<String, Int> {

    // ── Pomocné klasifikace ──────────────────────────────────────────────────

    fun mineResType(card: Card): ResourceType? =
        (card.effects.firstOrNull { it is CardEffect.AddMine } as? CardEffect.AddMine)?.type

    fun isChaosGen(card: Card) = card.effects.any {
        (it is CardEffect.AddResource && it.type == ResourceType.CHAOS) ||
        (it is CardEffect.AddMine     && it.type == ResourceType.CHAOS)
    }

    val cards = allCards.filterNot { it.id.startsWith("T") }

    val QUOTA = mapOf(
        ResourceType.MAGIC  to 9,
        ResourceType.ATTACK to 9,
        ResourceType.STONES to 9,
        ResourceType.CHAOS  to 3
    )

    // ── Stav balíčku ────────────────────────────────────────────────────────

    val counts = mutableMapOf<String, Int>()
    fun total()          = counts.values.sum()
    fun countByCT(ct: ResourceType) = cards
        .filter { it.costType == ct }
        .sumOf { counts[it.id] ?: 0 }
    fun chaosGenCount()  = cards.filter { isChaosGen(it) }.sumOf { counts[it.id] ?: 0 }

    fun tryAdd(card: Card): Boolean {
        if (total() >= 30) return false
        val cur = counts[card.id] ?: 0
        if (cur >= card.rarity.maxCopies) return false
        counts[card.id] = cur + 1
        return true
    }

    // ── Vážené míchání (preferuje cost 2–4, penalizuje 0 a 5+) ─────────────

    fun weight(card: Card) = when (card.cost) {
        0        -> 0.45
        1        -> 0.85
        in 2..4  -> 1.00
        else     -> 0.55
    }

    fun weightedShuffle(pool: List<Card>): List<Card> =
        pool.map { it to Random.nextDouble() * weight(it) }
            .sortedByDescending { it.second }
            .map { it.first }

    // ── KROK 1: Povinné doly (1× pro každý zdroj krom chaosu) ──────────────

    for (res in listOf(ResourceType.MAGIC, ResourceType.ATTACK, ResourceType.STONES)) {
        val cands = cards.filter { mineResType(it) == res }.shuffled()
        for (c in cands) {
            if (countByCT(c.costType) >= (QUOTA[c.costType] ?: 0)) continue
            if (tryAdd(c)) break
        }
    }

    // ── KROK 2: Alespoň 3 chaos generátory ─────────────────────────────────

    val cgCands = cards.filter { isChaosGen(it) }.shuffled()
    for (c in cgCands) {
        if (chaosGenCount() >= 3) break
        if (countByCT(c.costType) >= (QUOTA[c.costType] ?: 0)) continue
        tryAdd(c)
    }

    // ── KROK 3: Doplnit každý costType bucket na kvótu ──────────────────────
    // Rarity využití: záměrně omezíme počet unikátních karet v poolu,
    // aby COMMON karty mohly obsadit 2–4 sloty a LEGENDARY jen 1.
    // uniqueLimit = náhodně mezi (target÷3) a (target-1), max = velikost poolu.
    //   → pro MAGIC/ATTACK/STONES (target=9): 3–8 unikátních typů karet
    //   → pro CHAOS (target=3): 1–2 unikátní typy

    for ((ct, target) in QUOTA) {
        val pool = weightedShuffle(cards.filter { it.costType == ct })
        if (pool.isEmpty()) continue

        val uniqueLimit = Random.nextInt(target * 2 / 3, target + 1)
            .coerceIn(1, pool.size)
        val chosenPool  = pool.take(uniqueLimit)

        // Přidávej kopie z omezeného poolu (max dle rarity přes tryAdd)
        for (pass in 0 until Rarity.COMMON.maxCopies) {
            for (c in weightedShuffle(chosenPool)) {
                if (countByCT(ct) >= target || total() >= 30) break
                tryAdd(c)
            }
        }
        // Fallback: pokud pool nestačil (málo karet nebo všechny na max kopiích),
        // doplň ze zbytku plného poolu
        if (countByCT(ct) < target) {
            for (c in weightedShuffle(pool)) {
                if (countByCT(ct) >= target || total() >= 30) break
                tryAdd(c)
            }
        }
    }

    // ── KROK 4: Filler do 30 (kdyby nestačily karty v bucketu) ─────────────

    if (total() < 30) {
        for (pass in 0 until Rarity.COMMON.maxCopies) {
            val filler = weightedShuffle(cards)
            for (c in filler) {
                if (total() >= 30) break
                tryAdd(c)
            }
        }
    }

    return counts
}

/**
 * Super náhodný balíček – 50 karet s rozdělením 15 / 15 / 15 / 5
 * (MAGIC / ATTACK / STONES / CHAOS). Jinak stejná logika jako [buildBalancedDeck].
 *
 * @return mapa cardId → počet kopií (celkem 50 karet)
 */
fun buildSuperRandomDeck(allCards: List<Card>): Map<String, Int> {

    fun mineResType(card: Card): ResourceType? =
        (card.effects.firstOrNull { it is CardEffect.AddMine } as? CardEffect.AddMine)?.type

    fun isChaosGen(card: Card) = card.effects.any {
        (it is CardEffect.AddResource && it.type == ResourceType.CHAOS) ||
        (it is CardEffect.AddMine     && it.type == ResourceType.CHAOS)
    }

    val cards = allCards.filterNot { it.id.startsWith("T") }

    val QUOTA = mapOf(
        ResourceType.MAGIC  to 15,
        ResourceType.ATTACK to 15,
        ResourceType.STONES to 15,
        ResourceType.CHAOS  to 5
    )
    val TOTAL = 50

    val counts = mutableMapOf<String, Int>()
    fun total()               = counts.values.sum()
    fun countByCT(ct: ResourceType) = cards
        .filter { it.costType == ct }
        .sumOf { counts[it.id] ?: 0 }
    fun chaosGenCount()       = cards.filter { isChaosGen(it) }.sumOf { counts[it.id] ?: 0 }

    fun tryAdd(card: Card): Boolean {
        if (total() >= TOTAL) return false
        val cur = counts[card.id] ?: 0
        if (cur >= card.rarity.maxCopies) return false
        counts[card.id] = cur + 1
        return true
    }

    fun weight(card: Card) = when (card.cost) {
        0       -> 0.45
        1       -> 0.85
        in 2..4 -> 1.00
        else    -> 0.55
    }

    fun weightedShuffle(pool: List<Card>): List<Card> =
        pool.map { it to Random.nextDouble() * weight(it) }
            .sortedByDescending { it.second }
            .map { it.first }

    // Povinné doly
    for (res in listOf(ResourceType.MAGIC, ResourceType.ATTACK, ResourceType.STONES)) {
        val cands = cards.filter { mineResType(it) == res }.shuffled()
        for (c in cands) {
            if (countByCT(c.costType) >= (QUOTA[c.costType] ?: 0)) continue
            if (tryAdd(c)) break
        }
    }

    // Alespoň 3 chaos generátory
    val cgCands = cards.filter { isChaosGen(it) }.shuffled()
    for (c in cgCands) {
        if (chaosGenCount() >= 3) break
        if (countByCT(c.costType) >= (QUOTA[c.costType] ?: 0)) continue
        tryAdd(c)
    }

    // Doplnit buckety na kvótu
    for ((ct, target) in QUOTA) {
        val pool = weightedShuffle(cards.filter { it.costType == ct })
        if (pool.isEmpty()) continue

        val uniqueLimit = Random.nextInt(target * 2 / 3, target + 1)
            .coerceIn(1, pool.size)
        val chosenPool = pool.take(uniqueLimit)

        for (pass in 0 until Rarity.COMMON.maxCopies) {
            for (c in weightedShuffle(chosenPool)) {
                if (countByCT(ct) >= target || total() >= TOTAL) break
                tryAdd(c)
            }
        }
        if (countByCT(ct) < target) {
            for (c in weightedShuffle(pool)) {
                if (countByCT(ct) >= target || total() >= TOTAL) break
                tryAdd(c)
            }
        }
    }

    // Filler do 50
    if (total() < TOTAL) {
        for (pass in 0 until Rarity.COMMON.maxCopies) {
            for (c in weightedShuffle(cards)) {
                if (total() >= TOTAL) break
                tryAdd(c)
            }
        }
    }

    return counts
}
