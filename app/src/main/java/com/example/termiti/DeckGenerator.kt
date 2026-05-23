package com.example.termiti

import kotlin.random.Random

// ─── Sdílené pomocné funkce ───────────────────────────────────────────────────

private fun mineResType(card: Card): ResourceType? =
    (card.effects.firstOrNull { it is CardEffect.AddMine } as? CardEffect.AddMine)?.type

private fun isChaosGen(card: Card) = card.effects.any {
    (it is CardEffect.AddResource && it.type == ResourceType.CHAOS) ||
    (it is CardEffect.AddMine     && it.type == ResourceType.CHAOS)
}

/**
 * Vážené promíchání: preferuje cost 2–4, lehce penalizuje 5+.
 * Karty za 0 záměrně NEPENALIZUJEME – jsou bezplatné a herně zajímavé.
 */
private fun cardWeight(card: Card) = when (card.cost) {
    in 2..4 -> 1.00
    0       -> 0.80   // dříve 0.45 – teď férová šance
    1       -> 0.85
    else    -> 0.55   // 5+
}

private fun weightedShuffle(pool: List<Card>): List<Card> =
    pool.map { it to Random.nextDouble() * cardWeight(it) }
        .sortedByDescending { it.second }
        .map { it.first }

// ─── Obecný builder ──────────────────────────────────────────────────────────

private fun buildDeck(
    allCards   : List<Card>,
    quota      : Map<ResourceType, Int>,   // costType → target počet karet
    totalTarget: Int,
    rarityCaps : Map<Rarity, Int>          // celkový strop dle vzácnosti v celém balíčku
): Map<String, Int> {

    val cards = allCards.filterNot { it.id.startsWith("T") || it.isPlaceholder }

    val counts = mutableMapOf<String, Int>()

    fun total()              = counts.values.sum()
    fun countByCT(ct: ResourceType) = cards.filter { it.costType == ct }.sumOf { counts[it.id] ?: 0 }
    fun rarityTotal(r: Rarity)      = cards.filter { it.rarity == r }.sumOf { counts[it.id] ?: 0 }
    fun chaosGenCount()      = cards.filter { isChaosGen(it) }.sumOf { counts[it.id] ?: 0 }

    fun tryAdd(card: Card): Boolean {
        if (total() >= totalTarget) return false
        val cur = counts[card.id] ?: 0
        if (cur >= card.rarity.maxCopies) return false
        // Stropní limit vzácnosti pro celý balíček
        val cap = rarityCaps[card.rarity]
        if (cap != null && rarityTotal(card.rarity) >= cap) return false
        counts[card.id] = cur + 1
        return true
    }

    // ── KROK 1: Povinné doly (1× pro každý zdroj krom chaosu) ────────────────

    for (res in listOf(ResourceType.MAGIC, ResourceType.ATTACK, ResourceType.STONES)) {
        val cands = cards.filter { mineResType(it) == res }.shuffled()
        for (c in cands) {
            if (countByCT(c.costType) >= (quota[c.costType] ?: 0)) continue
            if (tryAdd(c)) break
        }
    }

    // ── KROK 2: Alespoň 3 chaos generátory ───────────────────────────────────

    for (c in cards.filter { isChaosGen(it) }.shuffled()) {
        if (chaosGenCount() >= 3) break
        if (countByCT(c.costType) >= (quota[c.costType] ?: 0)) continue
        tryAdd(c)
    }

    // ── KROK 3: Doplnit každý costType bucket na kvótu ───────────────────────
    // Projdeme celý pool opakovaně (dle max kopií rarity COMMON = 3 průchodů).
    // tryAdd() zastaví přidávání jakmile karta dosáhne svého maxCopies NEBO
    // rarity-strop celého balíčku je naplněn.

    for ((ct, target) in quota) {
        val pool = weightedShuffle(cards.filter { it.costType == ct })
        if (pool.isEmpty()) continue

        for (pass in 0 until Rarity.COMMON.maxCopies) {
            if (countByCT(ct) >= target || total() >= totalTarget) break
            for (c in pool) {
                if (countByCT(ct) >= target || total() >= totalTarget) break
                tryAdd(c)
            }
        }
    }

    // ── KROK 4: Filler do cíle (kdyby některý bucket nestačil) ───────────────

    if (total() < totalTarget) {
        val filler = weightedShuffle(cards)
        for (pass in 0 until Rarity.COMMON.maxCopies) {
            if (total() >= totalTarget) break
            for (c in filler) {
                if (total() >= totalTarget) break
                tryAdd(c)
            }
        }
    }

    return counts
}

// ─── Veřejné API ─────────────────────────────────────────────────────────────

/**
 * Vyvážený náhodný balíček – 30 karet (9 MAGIC / 9 ATTACK / 9 STONES / 3 CHAOS).
 *
 * Rarity stropy v celém balíčku:
 *   LEGENDARY ≤ 2 · EPIC ≤ 6 · RARE ≤ 12 · COMMON: zbytek
 */
fun buildBalancedDeck(allCards: List<Card>): Map<String, Int> = buildDeck(
    allCards    = allCards,
    quota       = mapOf(
        ResourceType.MAGIC  to 9,
        ResourceType.ATTACK to 9,
        ResourceType.STONES to 9,
        ResourceType.CHAOS  to 3
    ),
    totalTarget = 30,
    rarityCaps  = mapOf(
        Rarity.LEGENDARY to 2,
        Rarity.EPIC      to 6,
        Rarity.RARE      to 12
        // COMMON: bez stropu
    )
)

/**
 * Super náhodný balíček – 50 karet (15 MAGIC / 15 ATTACK / 15 STONES / 5 CHAOS).
 *
 * Rarity stropy v celém balíčku:
 *   LEGENDARY ≤ 4 · EPIC ≤ 10 · RARE ≤ 20 · COMMON: zbytek
 */
fun buildSuperRandomDeck(allCards: List<Card>): Map<String, Int> = buildDeck(
    allCards    = allCards,
    // CHAOS jde první – jeho karty jsou výhradně RARE/EPIC/LEGENDARY,
    // takže musí dostat prioritu než ostatní buckety spotřebují rarity stropy.
    quota       = mapOf(
        ResourceType.CHAOS  to 5,
        ResourceType.MAGIC  to 15,
        ResourceType.ATTACK to 15,
        ResourceType.STONES to 15
    ),
    totalTarget = 50,
    rarityCaps  = mapOf(
        Rarity.LEGENDARY to 8,
        Rarity.EPIC      to 16,
        Rarity.RARE      to 24
        // COMMON: bez stropu
    )
)
