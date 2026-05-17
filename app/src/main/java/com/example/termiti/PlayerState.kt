// ============================================================
// PlayerState.kt
// ============================================================
package com.example.termiti

import java.util.UUID

const val MAX_RESOURCE = 999
const val MAX_MINES    = 99

/** Odložená surovina – aplikuje se na začátku tahu po [turnsLeft] kolech. */
data class PendingResource(
    val type     : ResourceType,
    val amount   : Int,
    var turnsLeft: Int
)

/**
 * Mutable třída (záměrně NE data class) — obsahuje mutable kolekce (MutableList, MutableMap).
 * Kotlin `data class copy()` vytvoří pouze plytkou kopii (stejné reference na kolekce),
 * což by způsobilo sdílení stavu mezi hráčem a AI. Proto existuje [deepCopy].
 *
 * Při přidání nového pole: aktualizuj konstruktor + [deepCopy].
 */
class PlayerState(
    var castleHP: Int = 30,
    var wallHP: Int = 15,
    var resources: MutableMap<ResourceType, Int> = mutableMapOf(
        ResourceType.MAGIC  to 0,
        ResourceType.ATTACK to 0,
        ResourceType.STONES to 0,
        ResourceType.CHAOS  to 0
    ),
    var mines: MutableMap<ResourceType, Int> = mutableMapOf(
        ResourceType.MAGIC  to 1,
        ResourceType.ATTACK to 1,
        ResourceType.STONES to 1
        // CHAOS záměrně chybí – žádný výchozí důl
    ),
    /** Zbývající kola zablokování produkce pro každý typ dolu. */
    var mineBlockedTurns: MutableMap<ResourceType, Int> = mutableMapOf(),
    /** Suroviny, které dojdou příští kola (hrané karty s AddResourceDelayed). */
    var pendingResources: MutableList<PendingResource> = mutableListOf(),
    val deck: MutableList<Card> = mutableListOf(),
    val hand: MutableList<Card> = mutableListOf(),
    val discardPile: MutableList<Card> = mutableListOf(),
    /** Typ naposledy zahrané karty (např. "Útok", "Stavba"). Nastavuje se těsně před applyEffects. */
    var lastPlayedType: String? = null,
    /**
     * Snapshot zdrojů PŘED zaplacením ceny aktuálně hrané karty. Používá se jen
     * během applyEffects, aby ConditionalEffect (ResourceAbove) viděl stav před
     * odečtem ceny. Mimo playCard/aiPlay je vždy null.
     */
    var preCostResources: Map<ResourceType, Int>? = null,
    /**
     * true = efekt DrawPerCardPlayed je aktivní: za každou DALŠÍ zahranou kartu v tomto kole
     * líznout 1 kartu. Resetuje se při přechodu na nový tah (v finishTurn / startMyTurn).
     */
    /** null = neaktivní; "" = aktivní pro libovolný typ; "Magie" = jen Magie atd. */
    var drawCardOnPlay: String? = null,
    /**
     * Seznam aktivních GainResourcePerCardPlayed efektů v tomto kole.
     * Každý efekt se triggeruje na každou DALŠÍ zahranou kartu (s volitelným filtrem cardType).
     * Resetuje se při přechodu na nový tah.
     */
    var gainResourcePerCardPlayed: MutableList<CardEffect.GainResourcePerCardPlayed> = mutableListOf(),
    /**
     * Seznam aktivních GainCastlePerCardPlayed efektů v tomto kole.
     * Každý efekt se triggeruje na každou DALŠÍ zahranou kartu (s volitelným filtrem cardType).
     * Resetuje se při přechodu na nový tah.
     */
    var gainCastlePerCardPlayed: MutableList<CardEffect.GainCastlePerCardPlayed> = mutableListOf()
) {
    fun deepCopy(): PlayerState = PlayerState(
        castleHP                 = castleHP,
        wallHP                   = wallHP,
        resources                = resources.toMutableMap(),
        mines                    = mines.toMutableMap(),
        mineBlockedTurns         = mineBlockedTurns.toMutableMap(),
        pendingResources         = pendingResources.map { it.copy() }.toMutableList(),
        deck                     = deck.toMutableList(),
        hand                     = hand.toMutableList(),
        discardPile              = discardPile.toMutableList(),
        lastPlayedType           = lastPlayedType,
        preCostResources         = preCostResources?.toMap(),
        drawCardOnPlay           = drawCardOnPlay,
        gainResourcePerCardPlayed = gainResourcePerCardPlayed.toMutableList(),
        gainCastlePerCardPlayed   = gainCastlePerCardPlayed.toMutableList()
    )

    /**
     * Volá se na začátku každého tahu hráče:
     * 1. Aplikuje odložené suroviny, které dozrály (turnsLeft → 0).
     * 2. Generuje produkci dolů (přeskočí zablokované a sníží čítač).
     */
    fun generateResources() {
        // 1. Odložené suroviny
        val iter = pendingResources.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.turnsLeft--
            if (p.turnsLeft <= 0) {
                resources[p.type] = ((resources[p.type] ?: 0) + p.amount).coerceAtMost(MAX_RESOURCE)
                iter.remove()
            }
        }

        // 2. Produkce dolů (s kontrolou blokády)
        for ((type, amount) in mines) {
            val blocked = mineBlockedTurns[type] ?: 0
            if (blocked > 0) {
                mineBlockedTurns[type] = blocked - 1
            } else {
                resources[type] = ((resources[type] ?: 0) + amount).coerceAtMost(MAX_RESOURCE)
            }
        }
    }

    /** Líže [count] karet; vrátí seznam karet, které byly spáleny (ruka plná). */
    fun drawCards(count: Int, maxHandSize: Int = 7): List<Card> {
        val burned = mutableListOf<Card>()
        repeat(count) {
            if (deck.isNotEmpty()) {
                val card = deck.removeFirst()
                if (hand.size < maxHandSize) hand.add(card)
                else { discardPile.add(card); burned.add(card) }
            }
        }
        return burned
    }

    /** Zaplatí správný zdroj podle effectiveCost karty. */
    fun playCard(card: Card): Boolean {
        val available = resources[card.costType] ?: 0
        if (available < card.effectiveCost) return false
        resources[card.costType] = available - card.effectiveCost
        hand.remove(card)
        discardPile.add(card)
        return true
    }
}

/** Vrátí balíček s unikátními instanceID, aby LazyRow neměl duplicitní klíče. */
fun List<Card>.withUniqueIds(): List<Card> =
    map { it.copy(id = "${it.id}_${UUID.randomUUID()}") }
