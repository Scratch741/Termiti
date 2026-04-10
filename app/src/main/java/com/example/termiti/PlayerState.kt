// ============================================================
// PlayerState.kt
// ============================================================
package com.example.termiti

import java.util.UUID

class PlayerState(
    var castleHP: Int = 30,
    var wallHP: Int = 10,
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
    val deck: MutableList<Card> = mutableListOf(),
    val hand: MutableList<Card> = mutableListOf(),
    val discardPile: MutableList<Card> = mutableListOf()
) {
    fun deepCopy(): PlayerState = PlayerState(
        castleHP    = castleHP,
        wallHP      = wallHP,
        resources   = resources.toMutableMap(),
        mines       = mines.toMutableMap(),
        deck        = deck.toMutableList(),
        hand        = hand.toMutableList(),
        discardPile = discardPile.toMutableList()
    )

    fun generateResources() {
        for ((type, amount) in mines) {
            resources[type] = (resources[type] ?: 0) + amount
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

    /** Zaplatí správný zdroj podle costType karty. */
    fun playCard(card: Card): Boolean {
        val available = resources[card.costType] ?: 0
        if (available < card.cost) return false
        resources[card.costType] = available - card.cost
        hand.remove(card)
        discardPile.add(card)
        return true
    }
}

/** Vrátí balíček s unikátními instanceID, aby LazyRow neměl duplicitní klíče. */
fun List<Card>.withUniqueIds(): List<Card> =
    map { it.copy(id = "${it.id}_${UUID.randomUUID()}") }