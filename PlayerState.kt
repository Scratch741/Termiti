// Termiti/src/main/kotlin/model/PlayerState.kt
data class PlayerState(
    var castleHp: Int = 25,
    var wallHp: Int = 10,
    val resources: MutableMap<ResourceType, Int> = mutableMapOf(ResourceType.MAGIC to 0, ResourceType.ATTACK to 0, ResourceType.STONES to 0),
    val deck: MutableList<Card> = mutableListOf(),
    val hand: MutableList<Card> = mutableListOf(),
    val discardPile: MutableList<Card> = mutableListOf()
) {
    fun drawCards(numCards: Int) {
        for (i in 1..numCards) {
            if (deck.isNotEmpty()) {
                val card = deck.removeAt(0)
                hand.add(card)
            } else {
                // Handle empty deck scenario
                // For now, we'll just move cards from discard pile to deck and shuffle
                deck.addAll(discardPile)
                discardPile.clear()
                deck.shuffle()
                drawCards(numCards - i) // Retry drawing the remaining cards
                break
            }
        }
    }

    fun playCard(card: Card): Boolean {
        if (card.cost <= resources[ResourceType.MAGIC] ?: 0) {
            resources[ResourceType.MAGIC] = (resources[ResourceType.MAGIC] ?: 0) - card.cost
            applyEffects(card.effects)
            hand.remove(card)
            discardPile.add(card)
            return true
        }
        return false
    }

    private fun applyEffects(effects: List<CardEffect>) {
        for (effect in effects) {
            when (effect) {
                is CardEffect.AddResource -> resources[effect.type] = (resources[effect.type] ?: 0) + effect.amount
                is CardEffect.BuildWall -> wallHp += effect.amount.coerceAtMost(100 - wallHp)
                is CardEffect.BuildCastle -> castleHp += effect.amount.coerceAtMost(100 - castleHp)
                is CardEffect.AttackWall -> TODO("Implement AttackWall")
                is CardEffect.AttackCastle -> TODO("Implement AttackCastle")
                is CardEffect.ConditionalEffect -> {
                    if (checkCondition(effect.condition)) {
                        applyEffects(listOf(effect.effect))
                    }
                }
            }
        }
    }

    private fun checkCondition(condition: Condition): Boolean {
        return when (condition) {
            is Condition.ResourceAbove -> resources[condition.type] ?: 0 > condition.threshold
            is Condition.WallAbove -> wallHp > condition.threshold
            is Condition.WallBelow -> wallHp < condition.threshold
            is Condition.CastleAbove -> castleHp > condition.threshold
        }
    }
}