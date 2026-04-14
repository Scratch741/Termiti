package com.example.termiti

data class Deck(
    val id: Int,
    val name: String,
    val cardCounts: Map<String, Int> = emptyMap()
) {
    val totalCards: Int get() = cardCounts.values.sum()
    val isValid: Boolean get() = totalCards == 30

    fun toCardList(allCards: List<Card>): List<Card> =
        allCards.flatMap { card -> List(cardCounts[card.id] ?: 0) { card } }
}
